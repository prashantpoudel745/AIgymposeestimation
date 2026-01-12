package com.pragyan.aigymposeestimationapp.theme

import aigymposeestimationapp.composeapp.generated.resources.Lexend_VariableFont_wght
import aigymposeestimationapp.composeapp.generated.resources.Res
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font

@Composable
fun LexendFontFamily(): FontFamily {
    return FontFamily(
        Font(
            resource = Res.font.Lexend_VariableFont_wght,
            weight = FontWeight.Normal
        ),
        Font(
            resource = Res.font.Lexend_VariableFont_wght,
            weight = FontWeight.Medium
        ),
        Font(
            resource = Res.font.Lexend_VariableFont_wght,
            weight = FontWeight.SemiBold
        ),
        Font(
            resource = Res.font.Lexend_VariableFont_wght    ,
            weight = FontWeight.Bold
        )
    )
}
