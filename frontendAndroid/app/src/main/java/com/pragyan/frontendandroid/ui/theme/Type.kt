package com.pragyan.frontendandroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pragyan.frontendandroid.R

// Set of Material typography styles to start with
//val Typography = Typography(
//    bodyLarge = TextStyle(
//        fontFamily = FontFamily.Default,
//        fontWeight = FontWeight.Normal,
//        fontSize = 16.sp,
//        lineHeight = 24.sp,
//        letterSpacing = 0.5.sp
//    )
//    /* Other default text styles to override
//    titleLarge = TextStyle(
//        fontFamily = FontFamily.Default,
//        fontWeight = FontWeight.Normal,
//        fontSize = 22.sp,
//        lineHeight = 28.sp,
//        letterSpacing = 0.sp
//    ),
//    labelSmall = TextStyle(
//        fontFamily = FontFamily.Default,
//        fontWeight = FontWeight.Medium,
//        fontSize = 11.sp,
//        lineHeight = 16.sp,
//        letterSpacing = 0.5.sp
//    )
//    */
//)

val LexendFontFamily = FontFamily(
    Font(
        resId = R.font.lexend_variable,
        weight = FontWeight.Normal
    )
)
val Typography = Typography(
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = LexendFontFamily
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = LexendFontFamily
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = LexendFontFamily
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = LexendFontFamily
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = LexendFontFamily
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = LexendFontFamily
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = LexendFontFamily
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = LexendFontFamily
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = LexendFontFamily
    )
)
