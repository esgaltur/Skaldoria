package com.skaldoria.theme

import androidx.compose.ui.graphics.Color

object BuiltinThemes {

    val NordDark = PresentationTheme(
        id = "nord-dark",
        name = "Nord Dark",
        isDark = true,
        background = Color(0xFF2E3440),
        surface = Color(0xFF3B4252),
        surfaceVariant = Color(0xFF434C5E),
        cardBorder = Color(0xFF4C566A),
        primary = Color(0xFF88C0D0),
        accent = Color(0xFF81A1C1),
        success = Color(0xFFA3BE8C),
        warning = Color(0xFFEBCB8B),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFFD8DEE9),
        textMuted = Color(0xFF9CA3AF),
        codeBackground = Color(0xFF242933),
        codeText = Color(0xFFECEFF4),
        codeKeyword = Color(0xFF81A1C1),
        codeString = Color(0xFFA3BE8C),
        codeComment = Color(0xFF616E88),
        codeNumber = Color(0xFFB48EAD),
        codeHighlightLine = Color(0x3388C0D0),
        badgeBackground = Color(0xFF434C5E),
        badgeText = Color(0xFF88C0D0)
    )

    val SleekLight = PresentationTheme(
        id = "sleek-light",
        name = "Sleek Light",
        isDark = false,
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        cardBorder = Color(0xFFE2E8F0),
        primary = Color(0xFF4F46E5),
        accent = Color(0xFF06B6D4),
        success = Color(0xFF10B981),
        warning = Color(0xFFF59E0B),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF334155),
        textMuted = Color(0xFF64748B),
        codeBackground = Color(0xFF0F172A),
        codeText = Color(0xFFF8FAFC),
        codeKeyword = Color(0xFF818CF8),
        codeString = Color(0xFF34D399),
        codeComment = Color(0xFF64748B),
        codeNumber = Color(0xFFF472B6),
        codeHighlightLine = Color(0x334F46E5),
        badgeBackground = Color(0xFFEEF2FF),
        badgeText = Color(0xFF4F46E5)
    )

    val CyberMidnight = PresentationTheme(
        id = "cyber-midnight",
        name = "Cyber Midnight",
        isDark = true,
        background = Color(0xFF090A0F),
        surface = Color(0xFF12141F),
        surfaceVariant = Color(0xFF1A1D2E),
        cardBorder = Color(0xFF282D47),
        primary = Color(0xFF00F0FF),
        accent = Color(0xFFFF007F),
        success = Color(0xFF00FF66),
        warning = Color(0xFFFFE600),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFD1D5DB),
        textMuted = Color(0xFF6B7280),
        codeBackground = Color(0xFF06070B),
        codeText = Color(0xFF00F0FF),
        codeKeyword = Color(0xFFFF007F),
        codeString = Color(0xFF00FF66),
        codeComment = Color(0xFF4B5563),
        codeNumber = Color(0xFFFFE600),
        codeHighlightLine = Color(0x3300F0FF),
        badgeBackground = Color(0x3300F0FF),
        badgeText = Color(0xFF00F0FF)
    )

    val MinimalistEditorial = PresentationTheme(
        id = "minimalist-editorial",
        name = "Minimalist Editorial",
        isDark = false,
        background = Color(0xFFFAF8F5),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF2ECE4),
        cardBorder = Color(0xFFE5DDD3),
        primary = Color(0xFFC2410C),
        accent = Color(0xFF78350F),
        success = Color(0xFF15803D),
        warning = Color(0xFFB45309),
        textPrimary = Color(0xFF1C1917),
        textSecondary = Color(0xFF44403C),
        textMuted = Color(0xFF78716C),
        codeBackground = Color(0xFF292524),
        codeText = Color(0xFFFAF8F5),
        codeKeyword = Color(0xFFFB923C),
        codeString = Color(0xFF86EFAC),
        codeComment = Color(0xFF78716C),
        codeNumber = Color(0xFFFDE047),
        codeHighlightLine = Color(0x33C2410C),
        badgeBackground = Color(0xFFFFEDD5),
        badgeText = Color(0xFFC2410C)
    )

    val DeutscheBorseExecutive = PresentationTheme(
        id = "deutsche-borse",
        name = "Deutsche Börse",
        isDark = false,
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF4F5F8),
        cardBorder = Color(0xFFD2D2D2),
        primary = Color(0xFF000099),
        accent = Color(0xFFFFCC00),
        success = Color(0xFF007A33),
        warning = Color(0xFFC77700),
        textPrimary = Color(0xFF1A1A2E),
        textSecondary = Color(0xFF666666),
        textMuted = Color(0xFF919191),
        codeBackground = Color(0xFF14142B),
        codeText = Color(0xFFF2F2F7),
        codeKeyword = Color(0xFF7A7AFF),
        codeString = Color(0xFF4CAF50),
        codeComment = Color(0xFF919191),
        codeNumber = Color(0xFFFFCC00),
        codeHighlightLine = Color(0x22000099),
        badgeBackground = Color(0xFFE6E6F5),
        badgeText = Color(0xFF000099)
    )

    val all = listOf(NordDark, SleekLight, CyberMidnight, MinimalistEditorial, DeutscheBorseExecutive)

    fun getById(id: String): PresentationTheme = all.find { it.id == id } ?: NordDark
}
