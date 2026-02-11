package com.example.jawafai.view.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.jawafai.R // ✅ Make sure this is your app package

// 1️⃣ Define your custom font family
val KaiseiDecolFontFamily = FontFamily(
    Font(R.font.kaiseidecol_medium),
    Font(R.font.kaiseidecol_regular),
    Font(R.font.kaiseidecol_bold)


)

// 2️⃣ Apply the custom font family to your typography
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = KaiseiDecolFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = KaiseiDecolFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = KaiseiDecolFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
