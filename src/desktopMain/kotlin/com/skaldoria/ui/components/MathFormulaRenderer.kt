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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.theme.PresentationTheme

/**
 * Visual mathematical formula and LaTeX equation renderer in Compose Desktop.
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
            // Formula Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
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
                        text = "LATEX MATHEMATICAL FORMULA",
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F131C))
                        .padding(24.dp)
                ) {
                    Text(
                        text = cleanedFormula,
                        color = Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                }
            } else {
                // Rendered Equation Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp, horizontal = 24.dp),
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
    // Check for fractions: \frac{A}{B}
    val fracRegex = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
    val fracMatch = fracRegex.find(rawFormula)

    if (fracMatch != null) {
        val before = rawFormula.substring(0, fracMatch.range.first).trim()
        val num = fracMatch.groupValues[1].trim()
        val den = fracMatch.groupValues[2].trim()
        val after = rawFormula.substring(fracMatch.range.last + 1).trim()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (before.isNotBlank()) {
                LatexTextSegment(before, theme)
            }

            // Fraction vertical stack
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                LatexTextSegment(num, theme, fontSize = 24)
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(maxOf(num.length, den.length).times(16).coerceAtLeast(40).dp)
                        .height(2.dp)
                        .background(theme.primary)
                )
                Spacer(Modifier.height(2.dp))
                LatexTextSegment(den, theme, fontSize = 24)
            }

            if (after.isNotBlank()) {
                LatexTextSegment(after, theme)
            }
        }
    } else {
        // Linear Math Expression with formatting
        LatexTextSegment(rawFormula, theme, fontSize = 32)
    }
}

@Composable
private fun LatexTextSegment(
    text: String,
    theme: PresentationTheme,
    fontSize: Int = 30
) {
    val renderedText = LatexSymbolMapper.replaceSymbols(text)

    Text(
        text = renderedText,
        color = theme.textPrimary,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        letterSpacing = 0.5.sp
    )
}

/**
 * Maps LaTeX symbol commands to Unicode mathematical characters.
 */
object LatexSymbolMapper {
    private val SYMBOLS = mapOf(
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

    fun replaceSymbols(input: String): String {
        var result = input
        for ((tex, unicode) in SYMBOLS) {
            result = result.replace(tex, unicode)
        }
        // Replace common superscripts and subscripts
        result = result
            .replace("^2", "²")
            .replace("^3", "³")
            .replace("^0", "⁰")
            .replace("^1", "¹")
            .replace("^n", "ⁿ")
            .replace("^x", "ˣ")
            .replace("^y", "ʸ")
            .replace("^+", "⁺")
            .replace("^-", "⁻")
            .replace("_0", "₀")
            .replace("_1", "₁")
            .replace("_2", "₂")
            .replace("_3", "₃")
            .replace("_i", "ᵢ")
            .replace("_j", "ⱼ")
            .replace("_k", "ₖ")
            .replace("_n", "ₙ")
            .replace("_x", "ₓ")
            .replace("{", "")
            .replace("}", "")
            .replace("\\", "")

        return result
    }
}
