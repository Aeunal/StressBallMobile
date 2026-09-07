package com.aeunal.stressball.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette lifted from the physical toy: a red rubber ball with a dark-green gear cap. */
object BallColors {
    val Red = Color(0xFFE0243A)
    val RedDark = Color(0xFF8E1424)
    val RedLight = Color(0xFFF26075)
    val Seam = Color(0xFF3A0A12)
    val Green = Color(0xFF1F5F4A)
    val GreenLight = Color(0xFF2E8A6C)
    val GreenDark = Color(0xFF123A2D)
    val Heat = Color(0xFFFFB347)
    val Overdrive = Color(0xFF7CF7FF)
}

private val DarkScheme = darkColorScheme(
    primary = BallColors.GreenLight,
    onPrimary = Color(0xFF06231A),
    primaryContainer = BallColors.Green,
    onPrimaryContainer = Color(0xFFD5FFF0),
    secondary = BallColors.RedLight,
    onSecondary = Color(0xFF3A0A12),
    secondaryContainer = BallColors.RedDark,
    onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = BallColors.Heat,
    background = Color(0xFF12121A),
    onBackground = Color(0xFFECECF4),
    surface = Color(0xFF1B1B23),
    onSurface = Color(0xFFECECF4),
    surfaceVariant = Color(0xFF2A2A36),
    onSurfaceVariant = Color(0xFFB9B9C8),
    outline = Color(0xFF4A4A5A),
)

private val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 52.sp),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun StressBallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        content = content,
    )
}
