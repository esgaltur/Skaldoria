package com.skaldoria.markdown.layout

import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.markdown.models.SlideLayoutType

/**
 * Automatically determines the most aesthetically pleasing slide layout
 * based on the composition of elements in standard Markdown.
 */
object SmartLayoutClassifier {

    fun classify(
        title: String,
        elements: List<SlideElement>,
        isFirstSlide: Boolean
    ): SlideLayoutType {
        // First slide or pure title slide
        if (isFirstSlide && (elements.isEmpty() || (elements.size <= 2 && elements.all { it is SlideElement.Text }))) {
            return SlideLayoutType.HERO_TITLE
        }

        if (elements.isEmpty()) {
            return SlideLayoutType.SECTION_HEADER
        }

        // Standalone single element hero layouts
        if (elements.size == 1) {
            return when (elements.first()) {
                is SlideElement.Quote -> SlideLayoutType.BIG_QUOTE
                is SlideElement.Metric -> SlideLayoutType.BIG_METRIC
                is SlideElement.CodeBlock -> SlideLayoutType.FULL_CODE
                is SlideElement.MermaidDiagram -> SlideLayoutType.DIAGRAM
                is SlideElement.MathFormula -> SlideLayoutType.MATH_FORMULA
                is SlideElement.Poll -> SlideLayoutType.POLL
                is SlideElement.Table -> SlideLayoutType.DATA_TABLE
                is SlideElement.BulletList -> SlideLayoutType.BULLET_LIST
                else -> SlideLayoutType.BULLET_LIST
            }
        }

        val hasPoll = elements.any { it is SlideElement.Poll }
        val hasDiagram = elements.any { it is SlideElement.MermaidDiagram }
        val hasMath = elements.any { it is SlideElement.MathFormula }
        val hasTable = elements.any { it is SlideElement.Table }
        val hasCode = elements.any { it is SlideElement.CodeBlock }
        val hasImage = elements.any { it is SlideElement.Image }
        val hasTextOrList = elements.any { it is SlideElement.Text || it is SlideElement.BulletList }
        val hasQuote = elements.any { it is SlideElement.Quote }

        return when {
            hasPoll -> SlideLayoutType.POLL
            hasDiagram -> SlideLayoutType.DIAGRAM
            hasMath && !hasCode && !hasImage -> SlideLayoutType.MATH_FORMULA
            hasTable -> SlideLayoutType.DATA_TABLE
            hasCode && hasTextOrList -> SlideLayoutType.SPLIT_TEXT_CODE
            hasImage && hasTextOrList -> SlideLayoutType.SPLIT_TEXT_MEDIA
            hasQuote && elements.size <= 2 -> SlideLayoutType.BIG_QUOTE
            hasCode && elements.count { it is SlideElement.CodeBlock } == 1 && elements.size <= 2 -> SlideLayoutType.SPLIT_TEXT_CODE
            else -> SlideLayoutType.BULLET_LIST
        }
    }
}
