package com.skaldoria.theme

import androidx.compose.ui.graphics.Color

object BuiltinThemes {

    val SkaldoriaDark = PresentationTheme(
        id = "skaldoria-dark",
        name = "Skaldoria Dark",
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
        codeText = Color(0xFF0F172A),
        codeKeyword = Color(0xFF4F46E5),
        codeString = Color(0xFF059669),
        codeComment = Color(0xFF64748B),
        codeNumber = Color(0xFFD97706),
        codeHighlightLine = Color(0x1F4F46E5),
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
        codeText = Color(0xFF1C1917),
        codeKeyword = Color(0xFFC2410C),
        codeString = Color(0xFF15803D),
        codeComment = Color(0xFF78716C),
        codeNumber = Color(0xFFB45309),
        codeHighlightLine = Color(0x1FC2410C),
        badgeBackground = Color(0xFFFFEDD5),
        badgeText = Color(0xFFC2410C)
    )

    /**
     * Corporate Enterprise Executive Theme (Restricted Access).
     * High-contrast, executive deep navy and gold styling for institutional presentations.
     */
    val DeutscheBorseExecutive = PresentationTheme(
        id = "deutsche-borse",
        name = "Deutsche Börse",
        isDark = false,
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        cardBorder = Color(0xFFCBD5E1),
        primary = Color(0xFF000099),       // Deutsche Börse Corporate Navy
        accent = Color(0xFF0055B8),        // Executive Accent Blue
        success = Color(0xFF00873E),       // Corporate Green
        warning = Color(0xFFC77700),       // Warning Amber
        textPrimary = Color(0xFF0A0E1A),   // Deep crisp dark navy/black (high contrast)
        textSecondary = Color(0xFF334155), // Slate dark gray
        textMuted = Color(0xFF64748B),     // Muted gray
        codeBackground = Color(0xFF0F172A),
        codeText = Color(0xFF000080),      // Deep visible navy
        codeKeyword = Color(0xFF000099),   // DB Blue
        codeString = Color(0xFF00873E),    // Deep green
        codeComment = Color(0xFF64748B),   // Crisp comment gray
        codeNumber = Color(0xFFB45309),    // Deep gold/amber
        codeHighlightLine = Color(0x1F000099),
        badgeBackground = Color(0xFFE2E8F0),
        badgeText = Color(0xFF000099)
    )

    val publicThemes = listOf(SkaldoriaDark, SleekLight, CyberMidnight, MinimalistEditorial)
    val allWithCorporate = listOf(SkaldoriaDark, SleekLight, CyberMidnight, MinimalistEditorial, DeutscheBorseExecutive)
    val all = allWithCorporate

    val corporateUnlockCodes = setOf(
        "DB_CORP_2026",
        "deutsche-borse",
        "DEUTSCHE_BORSE",
        "DB_EXECUTIVE",
        "FRANKFURT_FLOOR",
        "DEUTSCHE-BOERSE",
        "DB2026"
    )

    fun isCorporateCode(input: String): Boolean {
        val trimmed = input.trim()
        return corporateUnlockCodes.any { it.equals(trimmed, ignoreCase = true) }
    }

    fun getById(id: String): PresentationTheme = allWithCorporate.find { it.id == id } ?: SkaldoriaDark
}
