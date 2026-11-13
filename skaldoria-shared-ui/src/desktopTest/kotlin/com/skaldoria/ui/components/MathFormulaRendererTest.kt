package com.skaldoria.ui.components

import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathFormulaRendererTest {

    @Test
    fun `a command never consumes the prefix of a longer one`() {
        // \in used to be replaced inside \int and \infty, \le inside \left.
        val out = LatexSymbolMapper.preprocessDelimitersAndSymbols("\\int x \\in A \\le \\infty \\left( y \\right)")
        listOf("∫", "∈", "≤", "∞", "(", ")").forEach { assertTrue(it in out, "missing $it in: $out") }
        assertTrue('\\' !in out && "nt" !in out && "fty" !in out && "eft" !in out, "corrupted: $out")
    }

    @Test
    fun `short relations, spacing and delimiter sizing are rendered not printed`() {
        val out = LatexSymbolMapper.preprocessDelimitersAndSymbols("\\big[a\\big] \\ \\ge\\ b \\quad c")
        assertTrue('\\' !in out, "a raw command survived: $out")
        assertTrue("≥" in out && "[a]" in out, out)
    }

    @Test
    fun `text inside a subscript stays inside the subscript`() {
        assertEquals("x_{months}", LatexTextCommandParser.unwrapScriptedText("x_{\\text{months}}"))
        assertEquals("y^{max}", LatexTextCommandParser.unwrapScriptedText("y^{ \\mathrm{max} }"))
    }

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

    @Test
    fun testTextCommandsRenderAsPlainText() {
        val theme = BuiltinThemes.SkaldoriaDark
        val formula = "\\text{Period interest} = \\text{Daily interest} \\times \\mathrm{Days in period}"

        val rendered = buildLatexAnnotatedString(formula, theme, baseFontSize = 28).text
        assertTrue(!rendered.contains("\\text") && !rendered.contains("\\mathrm") && !rendered.contains("{"))
        assertTrue(rendered.contains("Period interest") && rendered.contains("×") && rendered.contains("Days in period"))

        assertEquals(
            "Total RIA / Days",
            LatexSymbolMapper.preprocessDelimitersAndSymbols("\\text{Total RIA} / \\text{Days}")
        )
    }

    @Test
    fun testFindFractionWithNestedSubscripts() {
        val formula = "\\Delta t = t_{elapsed} - \\left( \\frac{T_{target}}{N_{total}} \\right) \\cdot i_{current}"
        val frac = findFraction(formula)
        assertTrue(frac != null, "Fraction should be found with nested subscript braces")
        assertEquals("T_{target}", frac.numerator)
        assertEquals("N_{total}", frac.denominator)
        assertTrue(frac.isEnclosedInParens)
    }

    @Test
    fun testInlineMarkdownMathSupport() {
        val theme = BuiltinThemes.SkaldoriaDark
        val text = "- **Pacing Delta**: Computes $\\Delta t = t_{elapsed} - t_{target}$ offset"
        val annotated = inlineMarkdown(text, theme)
        val rendered = annotated.text
        assertTrue(rendered.contains("Pacing Delta"))
        assertTrue(rendered.contains("Δ"))
        assertTrue(rendered.contains("elapsed"))
    }
}
