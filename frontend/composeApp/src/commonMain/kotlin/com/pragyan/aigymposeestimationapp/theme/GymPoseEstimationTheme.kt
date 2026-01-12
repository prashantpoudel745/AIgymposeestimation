package com.pragyan.aigymposeestimationapp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorTheme = darkColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryGreen,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextSecondary
)

val LightColorTheme = lightColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryGreen,

    background = LightBackground,
    surface = LightSurface,

    onPrimary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextSecondaryLight
)


@Composable
fun GymPoseEstimationTheme(
    content: @Composable () -> Unit
) {
    val theme = if (isSystemInDarkTheme()) DarkColorTheme else LightColorTheme
    MaterialTheme(
        colorScheme = DarkColorTheme,
        content = content,
        typography = appTypography(),
        shapes = AppShapes
    )
}