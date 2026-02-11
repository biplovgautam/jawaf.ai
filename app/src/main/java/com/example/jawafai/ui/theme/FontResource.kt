package com.example.jawafai.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.jawafai.R

/**
 * Centralized font definitions for the app to avoid conflicting declarations
 */
object AppFonts {
    // KaiseiDecol font family used throughout the app
    val KaiseiDecolFontFamily = FontFamily(
        Font(R.font.kaiseidecol_regular, FontWeight.Normal),
        Font(R.font.kaiseidecol_medium, FontWeight.Medium),
        Font(R.font.kaiseidecol_bold, FontWeight.Bold)
    )

    // Regular font family (alias for consistency)
    val KaiseiRegularFontFamily = FontFamily(
        Font(R.font.kaiseidecol_regular, FontWeight.Normal)
    )

    // Karla font family for taglines/subtitles - Now using actual Karla font
    val KarlaFontFamily = FontFamily(
        Font(R.font.karla_bold, FontWeight.Bold)
    )

    // Kadwa font family for app names/titles - Now using actual Kadwa font
    val KadwaFontFamily = FontFamily(
        Font(R.font.kadwa_bold, FontWeight.Bold)
    )
}
