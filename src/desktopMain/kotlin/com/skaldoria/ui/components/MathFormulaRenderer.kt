package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.theme.PresentationTheme

/**
 * Visual mathematical formula and LaTeX equation renderer in Compose Desktop.
 * Renders publishing-quality mathematical formulas with proper fractions, Greek symbols,
 * superscripts, subscripts, and operators.
 */
@Composable
fun MathFormulaRenderer(
    formula: String,
    theme: PresentationTheme,
    isBlock: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showRawLatex by remember { mutableStateOf(false) }
    val cleanedFormula = remember(formula) { formula.trim().removeSurrounding("$$").removeSurrounding("$").trim() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp)),
        color = theme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Formula Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(theme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = "Math Equation",
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "MATHEMATICAL SPECIFICATION",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = { showRawLatex = !showRawLatex },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Toggle Raw LaTeX",
                        tint = if (showRawLatex) theme.primary else theme.textMuted
                    )
                }
            }

            if (showRawLatex) {
                // Theme-adaptive Raw LaTeX Code Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (theme.isDark) theme.codeBackground else theme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(20.dp)
                ) {
                    Text(
                        text = cleanedFormula,
                        color = if (theme.isDark) theme.codeText else theme.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // Rendered High-Fidelity Mathematical Formula Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RenderLatexExpression(cleanedFormula, theme)
                }
            }
        }
    }
}

/**
 * Decomposes LaTeX formula into formatted UI components (fractions, roots, superscripts, subscripts).
 */
@Composable
private fun RenderLatexExpression(
    rawFormula: String,
    theme: PresentationTheme
) {
    val fracInfo = findFraction(rawFormula)

    if (fracInfo != null) {
        val before = rawFormula.substring(0, fracInfo.startIndex).trim()
        val num = fracInfo.numerator
        val den = fracInfo.denominator
        val after = rawFormula.substring(fracInfo.endIndex).trim()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (before.isNotBlank()) {
                LatexTextSegment(before, theme, fontSize = 28)
            }

            if (fracInfo.isEnclosedInParens) {
                Text(
                    text = "(",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Serif,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Fraction vertical stack
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                LatexTextSegment(num, theme, fontSize = 22)
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(maxOf(num.length, den.length).times(14).coerceAtLeast(36).dp)
                        .height(2.5.dp)
                        .background(theme.primary)
                )
                Spacer(Modifier.height(3.dp))
                LatexTextSegment(den, theme, fontSize = 22)
            }

            if (fracInfo.isEnclosedInParens) {
                Text(
                    text = ")",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Serif,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            if (after.isNotBlank()) {
                LatexTextSegment(after, theme, fontSize = 28)
            }
        }
    } else {
        // Linear Math Expression with formatting
        LatexTextSegment(rawFormula, theme, fontSize = 32)
    }
}

data class FractionInfo(
    val startIndex: Int,
    val endIndex: Int,
    val numerator: String,
    val denominator: String,
    val isEnclosedInParens: Boolean
)

fun findFraction(text: String): FractionInfo? {
    val fracIdx = text.indexOf("\\frac")
    if (fracIdx == -1) return null

    var startIdx = fracIdx
    var isEnclosed = false

    // Check if preceded by \left( or (
    val prefix = text.substring(0, fracIdx).trimEnd()
    if (prefix.endsWith("\\left(")) {
        startIdx = text.lastIndexOf("\\left(", fracIdx)
        isEnclosed = true
    } else if (prefix.endsWith("(")) {
        startIdx = text.lastIndexOf("(", fracIdx)
        isEnclosed = true
    }

    // Find numerator: { ... }
    val firstBrace = text.indexOf('{', fracIdx + 5)
    if (firstBrace == -1) return null
    val numCloseBrace = findMatchingBrace(text, firstBrace) ?: return null
    val num = text.substring(firstBrace + 1, numCloseBrace).trim()

    // Find denominator: { ... }
    val secondBrace = text.indexOf('{', numCloseBrace + 1)
    if (secondBrace == -1 || text.substring(numCloseBrace + 1, secondBrace).isNotBlank()) return null
    val denCloseBrace = findMatchingBrace(text, secondBrace) ?: return null
    val den = text.substring(secondBrace + 1, denCloseBrace).trim()

    var endIdx = denCloseBrace + 1
    if (isEnclosed) {
        val suffix = text.substring(endIdx).trimStart()
        if (suffix.startsWith("\\right)")) {
            val rightIdx = text.indexOf("\\right)", endIdx)
            endIdx = rightIdx + 7
        } else if (suffix.startsWith(")")) {
            val parenIdx = text.indexOf(")", endIdx)
            endIdx = parenIdx + 1
        }
    }

    return FractionInfo(startIdx, endIdx, num, den, isEnclosed)
}

fun findMatchingBrace(text: String, openBraceIndex: Int): Int? {
    var depth = 0
    for (i in openBraceIndex until text.length) {
        if (text[i] == '{') depth++
        else if (text[i] == '}') {
            depth--
            if (depth == 0) return i
        }
    }
    return null
}

@Composable
private fun LatexTextSegment(
    text: String,
    theme: PresentationTheme,
    fontSize: Int = 28
) {
    val annotated = buildLatexAnnotatedString(text, theme, fontSize)

    Text(
        text = annotated,
        letterSpacing = 0.5.sp
    )
}

/**
 * Builds an AnnotatedString from a LaTeX string, converting Greek letters,
 * delimiters, subscripts (_x or _{sub}), and superscripts (^x or ^{sup}).
 */
fun buildLatexAnnotatedString(
    raw: String,
    theme: PresentationTheme,
    baseFontSize: Int
): AnnotatedString {
    val cleaned = LatexSymbolMapper.preprocessDelimitersAndSymbols(raw)

    return buildAnnotatedString {
        var i = 0
        while (i < cleaned.length) {
            val c = cleaned[i]

            // Subscript: _{...} or _char
            if (c == '_') {
                i++
                if (i < cleaned.length && cleaned[i] == '{') {
                    val endBrace = cleaned.indexOf('}', i + 1)
                    if (endBrace != -1) {
                        val subText = cleaned.substring(i + 1, endBrace)
                        withStyle(
                            SpanStyle(
                                fontSize = (baseFontSize * 0.62).sp,
                                baselineShift = BaselineShift.Subscript,
                                fontStyle = FontStyle.Italic,
                                fontFamily = FontFamily.Serif,
                                color = theme.textSecondary
                            )
                        ) {
                            append(subText)
                        }
                        i = endBrace + 1
                        continue
                    }
                } else if (i < cleaned.length) {
                    val subChar = cleaned[i].toString()
                    withStyle(
                        SpanStyle(
                            fontSize = (baseFontSize * 0.62).sp,
                            baselineShift = BaselineShift.Subscript,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = theme.textSecondary
                        )
                    ) {
                        append(subChar)
                    }
                    i++
                    continue
                }
            }

            // Superscript: ^{...} or ^char
            if (c == '^') {
                i++
                if (i < cleaned.length && cleaned[i] == '{') {
                    val endBrace = cleaned.indexOf('}', i + 1)
                    if (endBrace != -1) {
                        val supText = cleaned.substring(i + 1, endBrace)
                        withStyle(
                            SpanStyle(
                                fontSize = (baseFontSize * 0.62).sp,
                                baselineShift = BaselineShift.Superscript,
                                fontStyle = FontStyle.Italic,
                                fontFamily = FontFamily.Serif,
                                color = theme.textSecondary
                            )
                        ) {
                            append(supText)
                        }
                        i = endBrace + 1
                        continue
                    }
                } else if (i < cleaned.length) {
                    val supChar = cleaned[i].toString()
                    withStyle(
                        SpanStyle(
                            fontSize = (baseFontSize * 0.62).sp,
                            baselineShift = BaselineShift.Superscript,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = theme.textSecondary
                        )
                    ) {
                        append(supChar)
                    }
                    i++
                    continue
                }
            }

            // Mathematical Operators and Parentheses
            if (c in setOf('=', '+', '-', '·', '×', '÷', '±', '∓', '≈', '≠', '≤', '≥', '(', ')', '[', ']', '{', '}', '|')) {
                withStyle(
                    SpanStyle(
                        fontSize = baseFontSize.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Serif,
                        color = theme.textPrimary
                    )
                ) {
                    append(c)
                }
                i++
                continue
            }

            // Variables and Greek Letters
            withStyle(
                SpanStyle(
                    fontSize = baseFontSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = if (c.isLetter()) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = FontFamily.Serif,
                    color = if (c.isLetter() || c in "αβγδεζηθικλμνξπρστυφχψωΓΔΘΛΞΠΣΦΨΩ") theme.primary else theme.textPrimary
                )
            ) {
                append(c)
            }
            i++
        }
    }
}

/**
 * Maps LaTeX symbol commands to Unicode mathematical characters and cleans delimiters.
 */
object LatexSymbolMapper {
    private val SYMBOLS = mapOf(
        // Delimiters & Groupings
        "\\left(" to "(",
        "\\right)" to ")",
        "\\left[" to "[",
        "\\right]" to "]",
        "\\left\\{" to "{",
        "\\right\\}" to "}",
        "\\left|" to "|",
        "\\right|" to "|",
        "\\left." to "",
        "\\right." to "",

        // Greek letters
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\iota" to "ι",
        "\\kappa" to "κ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\upsilon" to "υ",
        "\\phi" to "φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Xi" to "Ξ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",

        // Operators & Relations
        "\\times" to " × ",
        "\\cdot" to " · ",
        "\\div" to " ÷ ",
        "\\pm" to " ± ",
        "\\mp" to " ∓ ",
        "\\approx" to " ≈ ",
        "\\neq" to " ≠ ",
        "\\leq" to " ≤ ",
        "\\geq" to " ≥ ",
        "\\ll" to " ≪ ",
        "\\gg" to " ≫ ",
        "\\equiv" to " ≡ ",
        "\\sim" to " ∼ ",
        "\\propto" to " ∝ ",
        "\\infty" to "∞",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\neg" to "¬",
        "\\in" to " ∈ ",
        "\\notin" to " ∉ ",
        "\\subset" to " ⊂ ",
        "\\subseteq" to " ⊆ ",
        "\\cup" to " ∪ ",
        "\\cap" to " ∩ ",
        "\\rightarrow" to " → ",
        "\\to" to " → ",
        "\\Rightarrow" to " ⇒ ",
        "\\leftrightarrow" to " ↔ ",
        "\\Leftrightarrow" to " ⇔ ",
        "\\leftarrow" to " ← ",
        "\\Leftarrow" to " ⇐ ",

        // Calculus & Functions
        "\\int" to "∫",
        "\\iint" to "∬",
        "\\oint" to "∮",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\lim" to "lim",
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tan",
        "\\log" to "log",
        "\\ln" to "ln",
        "\\exp" to "exp",
        "\\sqrt" to "√"
    )

    fun preprocessDelimitersAndSymbols(input: String): String {
        var result = input
        for ((tex, unicode) in SYMBOLS) {
            result = result.replace(tex, unicode)
        }
        return result
    }

    fun replaceSymbols(input: String): String {
        return preprocessDelimitersAndSymbols(input)
    }
}
