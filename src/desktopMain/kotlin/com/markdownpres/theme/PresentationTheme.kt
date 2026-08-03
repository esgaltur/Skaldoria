package com.markdownpres.theme

import androidx.compose.ui.graphics.Color

/**
 * Visual design system tokens for styling presentation slides and components.
 */
data class PresentationTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,

    // Core Surfaces & Backgrounds
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBorder: Color,

    // Brand & Accent Colors
    val primary: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,

    // Text Colors
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Code Syntax Colors
    val codeBackground: Color,
    val codeText: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeComment: Color,
    val codeNumber: Color,
    val codeHighlightLine: Color,

    // Component Accents
    val badgeBackground: Color,
    val badgeText: Color
)
