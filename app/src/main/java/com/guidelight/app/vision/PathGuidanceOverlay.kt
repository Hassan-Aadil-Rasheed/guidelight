package com.guidelight.app.vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.guidelight.app.domain.GuidanceDirection
import com.guidelight.app.domain.NormalizedBox
import com.guidelight.app.domain.ObstacleEvaluator
import com.guidelight.app.domain.ObstacleRisk

class PathGuidanceOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    @Volatile private var detections: List<NormalizedBox> = emptyList()
    @Volatile private var direction = GuidanceDirection.STRAIGHT
    @Volatile private var routeActive = false
    @Volatile var useGroundProjection = false
    @Volatile var minimalDetections = false
    @Volatile private var groundRibbon: GroundRibbon? = null

    fun setGroundRibbon(ribbon: GroundRibbon?) {
        groundRibbon = ribbon
        postInvalidateOnAnimation()
    }

    private val corridorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 42, 210, 255)
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(85, 54, 220, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(91, 226, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val cautionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 216, 77)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 5f
    }
    private val stopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 73, 67)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 7f
    }
    private val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setRoute(active: Boolean, newDirection: GuidanceDirection) {
        routeActive = active
        direction = newDirection
        postInvalidateOnAnimation()
    }

    fun setDetections(newDetections: List<NormalizedBox>) {
        detections = newDetections
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        if (routeActive) {
            val ribbon = groundRibbon
            val line = if (useGroundProjection) ribbon?.centre?.let { pathFrom(it, false) } else buildCentreLine()
            val corridor = if (useGroundProjection) ribbon?.polygon?.let { pathFrom(it, true) } else buildCorridor()
            corridor?.let { canvas.drawPath(it, corridorFill) }
            glowPaint.strokeWidth = width * 0.075f
            linePaint.strokeWidth = width * 0.025f
            line?.let { canvas.drawPath(it, glowPaint); canvas.drawPath(it, linePaint) }
        }

        var marked = 0
        detections.sortedByDescending { it.area }.forEach { box ->
            val alert = ObstacleEvaluator.evaluate(listOf(box))
            if (minimalDetections && (alert.risk == ObstacleRisk.NONE || marked >= 2)) return@forEach
            marked++
            val rect = RectF(box.left * width, box.top * height, box.right * width, box.bottom * height)
            val paint = when (alert.risk) {
                ObstacleRisk.STOP -> stopPaint
                ObstacleRisk.CAUTION -> cautionPaint
                ObstacleRisk.NONE -> objectPaint
            }
            if (minimalDetections) {
                val corner = minOf(22 * resources.displayMetrics.density, rect.width() / 4, rect.height() / 4)
                val corners = Path().apply {
                    moveTo(rect.left, rect.top + corner); lineTo(rect.left, rect.top); lineTo(rect.left + corner, rect.top)
                    moveTo(rect.right - corner, rect.top); lineTo(rect.right, rect.top); lineTo(rect.right, rect.top + corner)
                    moveTo(rect.right, rect.bottom - corner); lineTo(rect.right, rect.bottom); lineTo(rect.right - corner, rect.bottom)
                    moveTo(rect.left + corner, rect.bottom); lineTo(rect.left, rect.bottom); lineTo(rect.left, rect.bottom - corner)
                }
                canvas.drawPath(corners, paint)
            } else canvas.drawRoundRect(rect, 18f, 18f, paint)
        }
    }

    private fun pathFrom(points: List<Pair<Float, Float>>, close: Boolean) = Path().apply {
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) moveTo(x * width, y * height) else lineTo(x * width, y * height)
        }
        if (close) close()
    }

    private fun buildCentreLine(): Path {
        val path = Path()
        for (step in 0..24) {
            val progress = step / 24f
            val yNorm = 0.96f - progress * 0.68f
            val x = ObstacleEvaluator.corridorCentreX(yNorm, direction) * width
            val y = yNorm * height
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        return path
    }

    private fun buildCorridor(): Path {
        val path = Path()
        val samples = (0..24).map { step ->
            val progress = step / 24f
            val yNorm = 0.96f - progress * 0.68f
            val centre = ObstacleEvaluator.corridorCentreX(yNorm, direction)
            val halfWidth = 0.08f + yNorm * 0.13f
            Triple(centre, halfWidth, yNorm)
        }
        samples.forEachIndexed { index, (centre, halfWidth, yNorm) ->
            val x = (centre - halfWidth) * width
            val y = yNorm * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        samples.asReversed().forEach { (centre, halfWidth, yNorm) ->
            path.lineTo((centre + halfWidth) * width, yNorm * height)
        }
        path.close()
        return path
    }
}
