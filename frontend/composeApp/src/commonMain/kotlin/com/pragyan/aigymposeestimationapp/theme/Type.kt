package com.pragyan.aigymposeestimationapp.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

// Coping the default Material 3 typography and override only the fontFamily.
@Composable
fun appTypography(): Typography {
    val lexend = LexendFontFamily()

    return Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = lexend),
            displayMedium = displayMedium.copy(fontFamily = lexend),
            displaySmall = displaySmall.copy(fontFamily = lexend),

            headlineLarge = headlineLarge.copy(fontFamily = lexend),
            headlineMedium = headlineMedium.copy(fontFamily = lexend),
            headlineSmall = headlineSmall.copy(fontFamily = lexend),

            titleLarge = titleLarge.copy(fontFamily = lexend),
            titleMedium = titleMedium.copy(fontFamily = lexend),
            titleSmall = titleSmall.copy(fontFamily = lexend),

            bodyLarge = bodyLarge.copy(fontFamily = lexend),
            bodyMedium = bodyMedium.copy(fontFamily = lexend),
            bodySmall = bodySmall.copy(fontFamily = lexend),

            labelLarge = labelLarge.copy(fontFamily = lexend),
            labelMedium = labelMedium.copy(fontFamily = lexend),
            labelSmall = labelSmall.copy(fontFamily = lexend),
        )
    }
}
