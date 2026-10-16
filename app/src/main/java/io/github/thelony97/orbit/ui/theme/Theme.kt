package io.github.thelony97.orbit.ui.theme

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.R

/** Fixed "Dark Space" palette – the same on every phone. */
object Space {
    val Void = Color(0xFF06060C)
    val Deep = Color(0xFF0B0B15)
    val Panel = Color(0xFF11111D)
    val PanelHigh = Color(0xFF181827)
    val PanelHighest = Color(0xFF202033)
    val Line = Color(0x17FFFFFF)
    val LineStrong = Color(0x2BFFFFFF)

    val Text = Color(0xFFEEEDF7)
    val TextDim = Color(0xFF9A98B3)
    val TextFaint = Color(0xFF615F7C)

    val Violet = Color(0xFFA78BFF)
    val VioletDeep = Color(0xFF6E4BFF)
    val Orange = Color(0xFFFFB45E)
    val Teal = Color(0xFF5EE6CF)
    val Coral = Color(0xFFFF6B81)
    /** The WhatsApp way (reply line, quick actions) – recognisable, never a Licht-Code colour. */
    val WhatsApp = Color(0xFF3DDC84)
    val Blue = Color(0xFF6FB7FF)
}

private fun variable(res: Int, weight: Int) = Font(
    res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Grotesk = FontFamily(
    variable(R.font.space_grotesk, 300),
    variable(R.font.space_grotesk, 400),
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 600),
    variable(R.font.space_grotesk, 700),
)

val InterFamily = FontFamily(
    variable(R.font.inter, 400),
    variable(R.font.inter, 500),
    variable(R.font.inter, 600),
    variable(R.font.inter, 700),
)

private fun g(size: Int, weight: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Grotesk,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
)

private fun i(size: Double, weight: Int, line: Int) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
)

/** Compact type scale: Space Grotesk for headings and labels, Inter for reading. */
private val OrbitTypography = Typography(
    displayLarge = g(48, 600, 52, -0.02),
    displayMedium = g(38, 600, 42, -0.02),
    displaySmall = g(30, 600, 36, -0.01),
    headlineLarge = g(28, 600, 34, -0.01),
    headlineMedium = g(24, 600, 30, -0.01),
    headlineSmall = g(21, 600, 27),
    titleLarge = g(19, 600, 25),
    titleMedium = g(16, 500, 22),
    titleSmall = g(14, 500, 20),
    bodyLarge = i(15.0, 400, 21),
    bodyMedium = i(13.5, 400, 19),
    bodySmall = i(12.0, 400, 16),
    labelLarge = g(13, 500, 18, 0.01),
    labelMedium = g(11, 500, 15, 0.04),
    labelSmall = g(10, 500, 13, 0.08),
    displayLargeEmphasized = g(48, 700, 52, -0.02),
    displayMediumEmphasized = g(38, 700, 42, -0.02),
    displaySmallEmphasized = g(30, 700, 36, -0.01),
    headlineLargeEmphasized = g(28, 700, 34, -0.01),
    headlineMediumEmphasized = g(24, 700, 30, -0.01),
    headlineSmallEmphasized = g(21, 700, 27),
    titleLargeEmphasized = g(19, 700, 25),
    titleMediumEmphasized = g(16, 700, 22),
    titleSmallEmphasized = g(14, 700, 20),
    bodyLargeEmphasized = i(15.0, 600, 21),
    bodyMediumEmphasized = i(13.5, 600, 19),
    bodySmallEmphasized = i(12.0, 600, 16),
    labelLargeEmphasized = g(13, 700, 18, 0.01),
    labelMediumEmphasized = g(11, 700, 15, 0.04),
    labelSmallEmphasized = g(10, 700, 13, 0.08),
)

private val OrbitColors = darkColorScheme(
    primary = Space.Violet,
    onPrimary = Color(0xFF150B3A),
    primaryContainer = Color(0xFF2B2160),
    onPrimaryContainer = Color(0xFFE2DAFF),
    inversePrimary = Space.VioletDeep,
    secondary = Space.Orange,
    onSecondary = Color(0xFF3A2200),
    secondaryContainer = Color(0xFF221D33),
    onSecondaryContainer = Color(0xFFE6E2F7),
    tertiary = Space.Teal,
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF0E3631),
    onTertiaryContainer = Color(0xFFB4F7EA),
    background = Space.Void,
    onBackground = Space.Text,
    surface = Space.Void,
    onSurface = Space.Text,
    surfaceVariant = Space.PanelHigh,
    onSurfaceVariant = Space.TextDim,
    surfaceTint = Space.Violet,
    inverseSurface = Space.Text,
    inverseOnSurface = Space.Void,
    error = Space.Coral,
    onError = Color(0xFF3B0012),
    errorContainer = Color(0xFF3D1220),
    onErrorContainer = Color(0xFFFFD9DF),
    outline = Space.TextFaint,
    outlineVariant = Color(0xFF26253A),
    scrim = Color(0xCC000000),
    surfaceBright = Space.PanelHighest,
    surfaceDim = Space.Void,
    surfaceContainerLowest = Color(0xFF040408),
    surfaceContainerLow = Space.Deep,
    surfaceContainer = Space.Panel,
    surfaceContainerHigh = Space.PanelHigh,
    surfaceContainerHighest = Space.PanelHighest,
)

private val OrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = OrbitColors,
        motionScheme = MotionScheme.expressive(),
        shapes = OrbitShapes,
        typography = OrbitTypography,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp, content = content)
    }
}

/** Readable text colour on top of a group colour. */
fun onColor(c: Color): Color {
    val luminance = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (luminance > 0.55f) Color(0xFF14121F) else Color.White
}
