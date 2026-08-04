package com.skaldoria.ui.components

import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathFormulaRendererTest {

    @Test
    fun testLatexSymbolMapping() {
        val raw = "\\Delta t = t_{elapsed} - \\left( \\frac{T_{target}}{N_{total}} \\right) \\cdot i_{current}"
        val processed = LatexSymbolMapper.preprocessDelimitersAndSymbols(raw)

        // Delta mapped to Δ
        assertTrue(processed.contains("Δ"))
        // \cdot mapped to ·
        assertTrue(processed.contains("·"))
        // \left( and \right) cleaned to ( and )
        assertTrue(processed.contains("("))
        assertTrue(processed.contains(")"))
        // \left and \right prefixes should be removed
        assertTrue(!processed.contains("\\left"))
        assertTrue(!processed.contains("\\right"))
    }

    @Test
    fun testBuildLatexAnnotatedString() {
        val theme = BuiltinThemes.SkaldoriaDark
        val formula = "Δ t = t_{elapsed} - ( T_{target} / N_{total} ) · i_{current}"
        val annotated = buildLatexAnnotatedString(formula, theme, baseFontSize = 28)

        // Verify full text is preserved in rendered sequence
        val fullText = annotated.text
        assertTrue(fullText.contains("Δ"))
        assertTrue(fullText.contains("elapsed"))
        assertTrue(fullText.contains("target"))
        assertTrue(fullText.contains("total"))
        assertTrue(fullText.contains("current"))
        assertTrue(fullText.contains("·"))

        // Verify span styles were applied
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun testCommonMathSymbols() {
        val input = "\\alpha + \\beta = \\gamma \\times \\pi \\leq \\infty"
        val out = LatexSymbolMapper.preprocessDelimitersAndSymbols(input)
        assertTrue(out.contains("α"))
        assertTrue(out.contains("β"))
        assertTrue(out.contains("γ"))
        assertTrue(out.contains("×"))
        assertTrue(out.contains("π"))
        assertTrue(out.contains("≤"))
        assertTrue(out.contains("∞"))
    }
}
