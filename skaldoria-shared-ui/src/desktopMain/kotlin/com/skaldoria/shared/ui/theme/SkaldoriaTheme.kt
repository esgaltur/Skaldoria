package com.skaldoria.shared.ui.theme

import androidx.compose.ui.graphics.Color

data class SkaldoriaTheme(
    val name: String,
    val bg: Color,
    val surface: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color
)

object Themes {
    val Catppuccin = SkaldoriaTheme(
        name = "Catppuccin",
        bg = Color(0xFF1E1E2E), surface = Color(0xFF313244),
        text = Color(0xFFCDD6F4), subtext = Color(0xFFA6ADC8), accent = Color(0xFF89B4FA)
    )
    val Nord = SkaldoriaTheme(
        name = "Nord",
        bg = Color(0xFF2E3440), surface = Color(0xFF3B4252),
        text = Color(0xFFD8DEE9), subtext = Color(0xFFE5E9F0), accent = Color(0xFF88C0D0)
    )
    val Light = SkaldoriaTheme(
        name = "Clean Light",
        bg = Color(0xFFF8F9FA), surface = Color(0xFFFFFFFF),
        text = Color(0xFF212529), subtext = Color(0xFF6C757D), accent = Color(0xFF0D6EFD)
    )
    val Monokai = SkaldoriaTheme(
        name = "Monokai",
        bg = Color(0xFF272822), surface = Color(0xFF3E3D32),
        text = Color(0xFFF8F8F2), subtext = Color(0xFF75715E), accent = Color(0xFFF92672)
    )
    
    val all = listOf(Catppuccin, Nord, Light, Monokai)
}
