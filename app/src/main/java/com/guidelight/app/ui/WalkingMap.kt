package com.guidelight.app.ui

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guidelight.app.navigation.GeoPoint
import com.guidelight.app.navigation.WalkingPosition
import com.guidelight.app.domain.WalkVoiceAction
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*
import kotlin.math.*

/** The map consumes the navigation fix; it never starts a second location engine. */
@Composable
fun WalkingMap(state: GuideUiState, actions: GuideActions? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { WalkingMapController(context) }
    LaunchedEffect(controller.ready, state.mapCameraRequest, state.mapOverview) {
        if (state.mapOverview) controller.overview() else controller.recenter()
    }
    DisposableEffect(controller, owner) {
        var started = false
        var resumed = false
        fun sync() {
            val lifecycle = owner.lifecycle.currentState
            if (resumed && !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) { controller.view.onPause(); resumed = false }
            if (started && !lifecycle.isAtLeast(Lifecycle.State.STARTED)) { controller.view.onStop(); started = false }
            if (!started && lifecycle.isAtLeast(Lifecycle.State.STARTED)) { controller.view.onStart(); started = true }
            if (!resumed && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) { controller.view.onResume(); resumed = true }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        val memory = object : ComponentCallbacks {
            override fun onConfigurationChanged(config: Configuration) = Unit
            override fun onLowMemory() = controller.view.onLowMemory()
        }
        owner.lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        sync()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            if (resumed) controller.view.onPause()
            if (started) controller.view.onStop()
            controller.destroy()
        }
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("walking-map").semantics {
        stateDescription = when { controller.failed -> "Map unavailable"; controller.ready -> "Map ready"; else -> "Loading map" }
    }) {
        Column {
            AndroidView(factory = { controller.view }, update = { controller.update(state.routeShape, state.walkingPosition) },
                modifier = Modifier.fillMaxWidth().height(280.dp).semantics {
                    contentDescription = "Walking route map. ${state.walkingPosition?.label ?: "Finding location"}. ${state.remainingDistance} remaining."
                })
            if (controller.failed) {
                Text("Map could not load. Voice directions remain available when location is ready.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = controller::loadStyle) { Text("Retry map") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (actions != null) actions.onWalkAction(WalkVoiceAction.RECENTER) else controller.recenter() }, enabled = state.walkingPosition != null,
                    modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.MyLocation, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (controller.following) "Following you" else "Recenter")
                }
                TextButton(onClick = { if (actions != null) actions.onWalkAction(WalkVoiceAction.FULL_ROUTE) else controller.overview() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Route, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Full route")
                }
            }
        }
    }
}

private class WalkingMapController(context: Context) {
    val view: MapView
    var following by mutableStateOf(true)
        private set
    var failed by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var route = emptyList<GeoPoint>()
    private var position: WalkingPosition? = null
    private var drawnRoute: List<GeoPoint>? = null
    private var cameraPoint: GeoPoint? = null
    private var cameraZoom: Double? = null
    private var disposed = false

    init {
        MapLibre.getInstance(context)
        view = MapView(context)
        view.onCreate(null)
        view.addOnDidFailLoadingMapListener { if (!disposed) failed = true }
        view.addOnDidFinishLoadingMapListener { if (!disposed) { ready = true; failed = false } }
        view.getMapAsync { ready ->
            if (!disposed) {
                map = ready
                ready.uiSettings.isRotateGesturesEnabled = false
                ready.uiSettings.isTiltGesturesEnabled = false
                ready.addOnCameraMoveStartedListener { reason ->
                    // MapLibre's gesture reason is 1; programmatic animations keep follow mode.
                    if (reason == 1) following = false
                }
                loadStyle()
            }
        }
    }

    fun loadStyle() {
        failed = false
        ready = false
        style = null
        map?.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { loaded ->
            if (!disposed) {
                style = loaded
                drawnRoute = null
                cameraPoint = null
                listOf("walk-route", "walk-destination", "walk-accuracy", "walk-heading", "walk-position").forEach {
                    loaded.addSource(GeoJsonSource(it, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                }
                loaded.addLayer(LineLayer("walk-route-outline", "walk-route").withProperties(
                    lineColor("#FFFFFF"), lineWidth(10f), lineCap("round"), lineJoin("round")))
                loaded.addLayer(LineLayer("walk-route-line", "walk-route").withProperties(
                    lineColor("#245CE5"), lineWidth(6f), lineCap("round"), lineJoin("round")))
                loaded.addLayer(CircleLayer("walk-destination-dot", "walk-destination").withProperties(
                    circleColor("#18412E"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                loaded.addLayer(FillLayer("walk-accuracy-area", "walk-accuracy").withProperties(
                    fillColor("#245CE5"), fillOpacity(.13f)))
                loaded.addLayer(LineLayer("walk-heading-line", "walk-heading").withProperties(
                    lineColor("#245CE5"), lineWidth(5f), lineCap("round")))
                loaded.addLayer(CircleLayer("walk-position-dot", "walk-position").withProperties(
                    circleColor("#245CE5"), circleRadius(9f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                failed = false
                render()
            }
        }
    }

    fun update(shape: List<GeoPoint>, fix: WalkingPosition?) {
        route = shape
        position = fix
        render()
    }

    private fun render() {
        val loaded = style ?: return
        fun source(id: String) = loaded.getSourceAs<GeoJsonSource>(id)!!
        val empty = FeatureCollection.fromFeatures(emptyArray<Feature>())
        if (drawnRoute != route) {
            source("walk-route").setGeoJson(if (route.size > 1)
                FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(LineString.fromLngLats(route.map { it.geoJson() })))) else empty)
            source("walk-destination").setGeoJson(route.lastOrNull()?.let {
                FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(it.geoJson()))) } ?: empty)
            drawnRoute = route
            if (position == null) overview()
        }
        val fix = position ?: return
        source("walk-position").setGeoJson(Feature.fromGeometry(fix.point.geoJson()))
        val color = if (fix.stale) "#697585" else if (!fix.precise) "#A15B00" else "#245CE5"
        loaded.getLayerAs<CircleLayer>("walk-position-dot")?.setProperties(circleColor(color))
        loaded.getLayerAs<FillLayer>("walk-accuracy-area")?.setProperties(fillColor(color))
        source("walk-accuracy").setGeoJson(Feature.fromGeometry(accuracyCircle(fix)))
        val heading = fix.heading?.takeIf { fix.precise && it.isFinite() }
        source("walk-heading").setGeoJson(if (heading != null) {
            val angle = Math.toRadians(heading)
            val end = GeoPoint(fix.point.latitude + cos(angle) * 15 / 111_195,
                fix.point.longitude + sin(angle) * 15 / (111_195 * cos(Math.toRadians(fix.point.latitude)).coerceAtLeast(.001)))
            FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(LineString.fromLngLats(listOf(fix.point.geoJson(), end.geoJson())))))
        } else empty)
        if (following && (cameraPoint != fix.point || cameraZoom != zoomFor(fix))) moveToFix()
    }

    fun recenter() { following = true; cameraPoint = null; moveToFix() }

    private fun moveToFix() {
        val fix = position ?: return
        val zoom = zoomFor(fix)
        val camera = CameraUpdateFactory.newLatLngZoom(LatLng(fix.point.latitude, fix.point.longitude), zoom)
        if (fix.precise && cameraPoint != null) map?.easeCamera(camera, 900) else map?.moveCamera(camera)
        cameraPoint = fix.point
        cameraZoom = zoom
    }

    private fun zoomFor(fix: WalkingPosition) = if (fix.precise) 17.0 else
        (ln(40_000_000 / (fix.accuracyMetres.coerceAtLeast(25f) * 8)) / ln(2.0)).coerceIn(3.0, 16.0)

    fun overview() {
        if (route.size < 2) return
        following = false
        val bounds = LatLngBounds.Builder().includes(route.map { LatLng(it.latitude, it.longitude) }).build()
        map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48), 500)
    }

    fun destroy() { disposed = true; style = null; map = null; view.onDestroy() }

    private fun GeoPoint.geoJson() = Point.fromLngLat(longitude, latitude)

    private fun accuracyCircle(fix: WalkingPosition): Polygon {
        val latitude = Math.toRadians(fix.point.latitude)
        val longitude = Math.toRadians(fix.point.longitude)
        val radius = fix.accuracyMetres.toDouble() / 6_371_000
        val ring = (0 until 48).map { index ->
            val bearing = index * 2 * PI / 48
            val lat = asin(sin(latitude) * cos(radius) + cos(latitude) * sin(radius) * cos(bearing))
            val lon = longitude + atan2(sin(bearing) * sin(radius) * cos(latitude), cos(radius) - sin(latitude) * sin(lat))
            Point.fromLngLat(Math.toDegrees(lon), Math.toDegrees(lat))
        }
        return Polygon.fromLngLats(listOf(ring + ring.first()))
    }
}
