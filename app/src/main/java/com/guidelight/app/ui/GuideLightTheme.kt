package com.guidelight.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A fixed, high-contrast palette keeps controls predictable across system themes. */
private val GuideColors = darkColorScheme(
    primary = Color(0xFFFFDF4D), onPrimary = Color(0xFF171500),
    primaryContainer = Color(0xFF3B3411), onPrimaryContainer = Color(0xFFFFE577),
    secondary = Color(0xFFE0DCC8), onSecondary = Color(0xFF232219),
    secondaryContainer = Color(0xFF2B2A23), onSecondaryContainer = Color(0xFFF1EEDC),
    tertiary = Color(0xFFFFCC80), onTertiary = Color(0xFF392600),
    tertiaryContainer = Color(0xFF473312), onTertiaryContainer = Color(0xFFFFDFAB),
    background = Color(0xFF0B0C0B), onBackground = Color(0xFFF5F5EF),
    surface = Color(0xFF171816), onSurface = Color(0xFFF5F5EF),
    surfaceVariant = Color(0xFF242521), onSurfaceVariant = Color(0xFFBCBEB3),
    surfaceDim = Color(0xFF111210), surfaceBright = Color(0xFF353630),
    surfaceContainerLowest = Color(0xFF080908), surfaceContainerLow = Color(0xFF131411),
    surfaceContainer = Color(0xFF1B1C18), surfaceContainerHigh = Color(0xFF252620),
    surfaceContainerHighest = Color(0xFF30312A),
    outline = Color(0xFF858779), outlineVariant = Color(0xFF37392F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF512321), onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFF5F5EF), inverseOnSurface = Color(0xFF171816),
    inversePrimary = Color(0xFF6C5900), scrim = Color.Black
)

object GuideSpace {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val section = 20.dp
    val page = 20.dp
    val control = 60.dp
    val icon = 24.dp
    val contentWidth = 600.dp
}

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

// Keep the parameter for existing callers and previews; GuideLight always uses its brand palette.
@Suppress("UNUSED_PARAMETER")
@Composable
fun GuideLightTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GuideColors,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
