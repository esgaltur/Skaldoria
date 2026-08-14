package com.skaldoria.cv.core.layout

import com.skaldoria.cv.core.CvBlock
import com.skaldoria.cv.core.CvBlockKind
import com.skaldoria.cv.core.CvDocument
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateLayout
import com.skaldoria.cv.core.MeasuredPageItem
import com.skaldoria.cv.core.PagePacker
import com.skaldoria.markdown.parser.InlineRun
import com.skaldoria.markdown.parser.InlineRuns

/** Theme-level choices the engine needs because they change measured width. */
data class CvLayoutOptions(
    val uppercaseSections: Boolean = false,
    /** Shown when the source declares no candidate name; styled with [CvColorRole.Missing]. */
    val missingNamePlaceholder: String = "Candidate name required",
    val showPageFooter: Boolean = true
)

/**
 * Turns a [CvDocument] into positioned pages — CV-FR-040 through CV-FR-043.
 *
 * The engine measures every block once through the injected [CvTextMeasurer], packs the results
 * with the existing [PagePacker], then assigns coordinates. Preview and PDF export consume the
 * same [CvResolvedLayout], which is the whole point: pagination is decided here, once.
 *
 * **Widow and orphan handling** is expressed through two properties on the measured items, both of
 * which [PagePacker] already understood:
 * - `keepWithNext` on section and entry headings, so a heading cannot end a page alone (CV-FR-042).
 * - `keepTogetherGroup` per section, so a short section moves whole rather than leaving a single
 *   trailing bullet stranded on an otherwise empty page (CV-FR-043).
 */
object CvLayoutEngine {

    /**
     * One item in the vertical flow, before it is assigned to a page.
     *
     * [elements] are positioned relative to the item's own top, so packing can move an item to
     * another page by shifting a single offset rather than re-measuring anything.
     */
    private data class FlowItem(
        val elements: List<CvPageElement>,
        val heightPt: Double,
        val keepWithNext: Boolean = false,
        val group: String? = null
    )

    fun resolve(
        document: CvDocument,
        layout: CvTemplateLayout,
        paper: CvPaperSize,
        measurer: CvTextMeasurer,
        options: CvLayoutOptions = CvLayoutOptions()
    ): CvResolvedLayout {
        val contentWidth = paper.widthPoints - layout.horizontalMargin * 2
        val contentHeight = paper.heightPoints - layout.topMargin - layout.bottomReserved
        require(contentWidth > 0) { "Horizontal margins exceed the ${paper.displayName} page width" }
        require(contentHeight > 0) { "Vertical margins exceed the ${paper.displayName} page height" }

        val builder = FlowBuilder(layout, contentWidth, measurer, options)
        val flow = builder.build(document)

        val packed = PagePacker.pack(
            items = flow.mapIndexed { index, item ->
                MeasuredPageItem(
                    content = index,
                    extent = item.heightPt,
                    keepWithNext = item.keepWithNext,
                    keepTogetherGroup = item.group
                )
            },
            pageExtent = contentHeight
        )

        val pages = packed.mapIndexed { pageIndex, itemIndices ->
            var cursor = 0.0
            val elements = ArrayList<CvPageElement>()
            for (itemIndex in itemIndices) {
                val item = flow[itemIndex]
                for (element in item.elements) {
                    elements += when (element) {
                        is CvPageElement.TextBlock -> element.copy(yPt = cursor + element.yPt)
                        is CvPageElement.Rule -> element.copy(yPt = cursor + element.yPt)
                    }
                }
                cursor += item.heightPt
            }

            CvLayoutPage(
                pageNumber = pageIndex + 1,
                elements = elements,
                footer = if (options.showPageFooter) {
                    builder.footer(pageIndex + 1, packed.size)
                } else {
                    null
                }
            )
        }

        return CvResolvedLayout(
            paper = paper,
            pages = pages,
            title = document.candidateName?.let { "$it — CV" } ?: "Curriculum Vitae",
            author = document.candidateName
        )
    }

    /** Builds the vertical flow. Split out so the measuring helpers can share the configuration. */
    private class FlowBuilder(
        private val layout: CvTemplateLayout,
        private val contentWidth: Double,
        private val measurer: CvTextMeasurer,
        private val options: CvLayoutOptions
    ) {

        private val bodyStyle = CvTextStyle(
            fontRole = CvFontRole.Body,
            sizePt = layout.bodySize,
            lineHeightPt = layout.bodyLineHeight
        )

        fun build(document: CvDocument): List<FlowItem> = buildList {
            val header = "header"

            add(
                textItem(
                    runs = listOf(InlineRun(document.candidateName ?: options.missingNamePlaceholder)),
                    style = CvTextStyle(
                        fontRole = CvFontRole.Heading,
                        sizePt = layout.candidateSize,
                        lineHeightPt = layout.candidateSize + 4.0,
                        weight = CvFontWeight.Bold,
                        color = if (document.candidateName == null) {
                            CvColorRole.Missing
                        } else {
                            CvColorRole.PrimaryText
                        }
                    ),
                    group = header
                )
            )

            document.professionalHeadline?.let { headline ->
                add(
                    textItem(
                        runs = listOf(InlineRun(headline)),
                        style = CvTextStyle(
                            fontRole = CvFontRole.Heading,
                            sizePt = layout.headlineSize,
                            lineHeightPt = layout.headlineSize + 3.0,
                            weight = CvFontWeight.Medium,
                            color = CvColorRole.Accent
                        ),
                        spaceBefore = layout.headlineSpaceBefore,
                        group = header
                    )
                )
            }

            if (document.contacts.isNotEmpty()) {
                // Contacts keep their targets so CV-FR-063 can turn them into PDF link annotations.
                val runs = document.contacts.flatMapIndexed { index, contact ->
                    val separator = if (index == 0) emptyList() else listOf(InlineRun("  •  "))
                    separator + InlineRun(contact.label ?: contact.value, link = contact.target)
                }
                add(
                    textItem(
                        runs = runs,
                        style = bodyStyle.copy(color = CvColorRole.SecondaryText),
                        spaceBefore = layout.contactsSpaceBefore,
                        group = header
                    )
                )
            }

            document.headerContent.forEach { block -> addBlock(block, header) }

            document.sections.forEachIndexed { sectionIndex, section ->
                val group = "section-$sectionIndex"
                add(
                    sectionHeadingItem(
                        title = if (options.uppercaseSections) section.title.uppercase() else section.title,
                        group = group
                    )
                )
                section.introduction.forEach { block -> addBlock(block, group) }
                section.entries.forEach { entry ->
                    add(
                        textItem(
                            runs = listOf(InlineRun(entry.title)),
                            style = CvTextStyle(
                                fontRole = CvFontRole.Heading,
                                sizePt = layout.entrySize,
                                lineHeightPt = layout.entrySize + 4.0,
                                weight = CvFontWeight.SemiBold
                            ),
                            spaceBefore = layout.entrySpaceBefore,
                            keepWithNext = true,
                            group = group
                        )
                    )
                    entry.content.forEach { block -> addBlock(block, group) }
                }
            }
        }

        private fun MutableList<FlowItem>.addBlock(block: CvBlock, group: String) {
            when (block.kind) {
                // Unsupported content is reported as a diagnostic, never silently rendered.
                CvBlockKind.Unsupported -> Unit

                CvBlockKind.Divider -> add(
                    FlowItem(
                        elements = listOf(
                            CvPageElement.Rule(
                                xPt = 0.0,
                                yPt = layout.dividerSpaceAround,
                                widthPt = contentWidth,
                                thicknessPt = layout.ruleThickness
                            )
                        ),
                        heightPt = layout.dividerSpaceAround * 2 + layout.ruleThickness,
                        group = group
                    )
                )

                CvBlockKind.Paragraph -> add(
                    textItem(
                        runs = InlineRuns.parse(block.markdown),
                        style = bodyStyle,
                        spaceBefore = layout.paragraphSpaceBefore,
                        group = group
                    )
                )

                CvBlockKind.ListItem -> add(listItem(block, group))
            }
        }

        /**
         * A bullet is a marker at the left edge and a text column hanging beside it, so wrapped
         * lines align under the first character rather than under the bullet.
         */
        private fun listItem(block: CvBlock, group: String): FlowItem {
            // No trailing space in the marker — the gap is added explicitly, because a measurer
            // reports width to the last visible glyph and would discard it. See
            // CvTemplateLayout.listMarkerGap.
            val marker = if (block.isOrdered) "1." else "•"
            val markerText = measurer.measure(listOf(InlineRun(marker)), bodyStyle, contentWidth)
            val markerWidth = (markerText.lines.firstOrNull()?.runs?.sumOf { it.widthPt } ?: 0.0)
            val textInset = markerWidth + layout.listMarkerGap

            val body = measurer.measure(
                runs = InlineRuns.parse(block.markdown),
                style = bodyStyle,
                maxWidthPt = (contentWidth - textInset).coerceAtLeast(1.0)
            )
            val top = layout.listItemSpaceBefore

            return FlowItem(
                elements = listOf(
                    CvPageElement.TextBlock(xPt = 0.0, yPt = top, text = markerText, style = bodyStyle),
                    CvPageElement.TextBlock(xPt = textInset, yPt = top, text = body, style = bodyStyle)
                ),
                heightPt = top + maxOf(body.heightPt, markerText.heightPt),
                group = group
            )
        }

        private fun sectionHeadingItem(title: String, group: String): FlowItem {
            val style = CvTextStyle(
                fontRole = CvFontRole.Heading,
                sizePt = layout.sectionSize,
                lineHeightPt = 16.0,
                weight = CvFontWeight.Bold,
                letterSpacingPt = 1.0,
                color = CvColorRole.Accent
            )
            val measured = measurer.measure(listOf(InlineRun(title)), style, contentWidth)
            val top = layout.sectionSpaceBefore
            val ruleY = top + measured.heightPt + layout.sectionRuleSpaceBefore

            return FlowItem(
                elements = listOf(
                    CvPageElement.TextBlock(xPt = 0.0, yPt = top, text = measured, style = style),
                    CvPageElement.Rule(
                        xPt = 0.0,
                        yPt = ruleY,
                        widthPt = contentWidth,
                        thicknessPt = layout.ruleThickness
                    )
                ),
                heightPt = ruleY + layout.ruleThickness + layout.sectionRuleSpaceAfter,
                // The divider must never be the last thing on a page.
                keepWithNext = true,
                group = group
            )
        }

        private fun textItem(
            runs: List<InlineRun>,
            style: CvTextStyle,
            spaceBefore: Double = 0.0,
            keepWithNext: Boolean = false,
            group: String? = null
        ): FlowItem {
            val measured = measurer.measure(runs, style, contentWidth)
            return FlowItem(
                elements = listOf(
                    CvPageElement.TextBlock(xPt = 0.0, yPt = spaceBefore, text = measured, style = style)
                ),
                heightPt = spaceBefore + measured.heightPt,
                keepWithNext = keepWithNext,
                group = group
            )
        }

        fun footer(pageNumber: Int, pageCount: Int): CvPageElement.TextBlock {
            val style = CvTextStyle(
                fontRole = CvFontRole.Body,
                sizePt = layout.footerSize,
                lineHeightPt = layout.footerSize + 2.0,
                color = CvColorRole.SecondaryText
            )
            val measured = measurer.measure(
                listOf(InlineRun("Page $pageNumber of $pageCount")),
                style,
                contentWidth
            )
            val width = measured.lines.firstOrNull()?.runs?.sumOf { it.widthPt } ?: 0.0
            return CvPageElement.TextBlock(
                xPt = (contentWidth - width) / 2,
                yPt = 0.0,
                text = measured,
                style = style
            )
        }
    }
}
