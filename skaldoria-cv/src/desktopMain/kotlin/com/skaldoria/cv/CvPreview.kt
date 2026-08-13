package com.skaldoria.cv

import androidx.compose.foundation.background
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.cv.core.CvBlock
import com.skaldoria.cv.core.CvBlockKind
import com.skaldoria.cv.core.CvDocument
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvThemeId
import com.skaldoria.cv.core.CvTemplateLayout
import com.skaldoria.cv.core.MeasuredPageItem
import com.skaldoria.cv.core.PagePacker
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.components.inlineMarkdown
import kotlin.math.roundToInt

private val PAGE_GAP = 24.dp

private sealed interface PreviewItem {
    val keepTogetherGroup: String?

    data class Candidate(
        val text: String,
        val missing: Boolean,
        override val keepTogetherGroup: String? = "header"
    ) : PreviewItem

    data class Contacts(
        val text: String,
        override val keepTogetherGroup: String? = "header"
    ) : PreviewItem

    data class Headline(
        val text: String,
        override val keepTogetherGroup: String? = "header"
    ) : PreviewItem

    data class SectionHeading(
        val text: String,
        override val keepTogetherGroup: String
    ) : PreviewItem

    data class EntryHeading(
        val text: String,
        override val keepTogetherGroup: String
    ) : PreviewItem

    data class Block(
        val block: CvBlock,
        override val keepTogetherGroup: String?
    ) : PreviewItem
}

/** Page-independent geometry of the paper, in the density's pixels. */
internal data class CvPageGeometry(
    val pageWidth: Int,
    val pageHeight: Int,
    val horizontalMargin: Int,
    val topMargin: Int,
    val bottomReserved: Int,
    val pageGap: Int,
    val contentWidth: Int,
    val contentHeight: Int
)

/**
 * The fully measured, paginated preview: which content item lands on which page and how tall it is.
 * Extracted from [MeasuredCvPages] so the pagination can be exercised headlessly (see
 * `CvPaginationPlanTest`) without launching a window. Items are described as strings so this
 * internal seam never leaks the private [PreviewItem] type.
 */
internal class CvPagePlan(
    val geometry: CvPageGeometry,
    val descriptors: List<String>,
    val groups: List<String?>,
    val contentPlaceables: List<Placeable>,
    val pages: List<List<Int>>
) {
    val heights: List<Int> get() = contentPlaceables.map { it.height }
}

private fun PreviewItem.describe(): String = when (this) {
    is PreviewItem.Candidate -> "Candidate(${text})"
    is PreviewItem.Headline -> "Headline(${text})"
    is PreviewItem.Contacts -> "Contacts"
    is PreviewItem.SectionHeading -> "Section(${text})"
    is PreviewItem.EntryHeading -> "Entry(${text})"
    is PreviewItem.Block -> "Block(${block.kind}: ${block.markdown.take(28)})"
}

private sealed interface PreviewSlot {
    data class Content(val index: Int) : PreviewSlot
    data class Paper(val pageIndex: Int) : PreviewSlot
    data class Footer(val pageIndex: Int) : PreviewSlot
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CvPreview(
    document: CvDocument,
    templateId: CvTemplateId,
    themeId: CvThemeId,
    fontId: CvFontId,
    zoomPercent: Int = CvZoomPolicy.DefaultPercent,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onZoomReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val font = CvFontResolver.resolve(fontId)
    val theme = CvPreviewThemes.resolve(themeId).copy(
        bodyFont = font.family,
        headingFont = font.family
    )
    val paper = CvPaperSize.fromMetadata(document.metadata["paper"])
    val pageWidth = paper.widthPoints.dp
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val zoomScale = CvZoomPolicy.scale(zoomPercent)

    Box(
        modifier = modifier.fillMaxHeight().background(Color(0xFFE4E7EB))
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                val commandModifier = event.keyboardModifiers.isCtrlPressed ||
                    event.keyboardModifiers.isMetaPressed
                if (commandModifier) {
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    when {
                        delta < 0f -> onZoomIn()
                        delta > 0f -> onZoomOut()
                    }
                    event.changes.forEach { it.consume() }
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(28.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            MeasuredCvPages(
                document = document,
                theme = theme,
                layout = templateId.layout,
                paper = paper,
                modifier = Modifier.previewZoom(zoomScale).width(pageWidth)
            )
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(verticalScrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp)
        )
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)
        )
        ZoomControls(
            zoomPercent = zoomPercent,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onZoomReset = onZoomReset,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        )
    }
}

@Composable
private fun ZoomControls(
    zoomPercent: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shadowElevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onZoomOut,
                enabled = zoomPercent > CvZoomPolicy.MinimumPercent
            ) { Text("−") }
            TextButton(onClick = onZoomReset) { Text("$zoomPercent%") }
            TextButton(
                onClick = onZoomIn,
                enabled = zoomPercent < CvZoomPolicy.MaximumPercent
            ) { Text("+") }
        }
    }
}

/** Reserves scaled bounds while preserving the page's original measurement and line wrapping. */
private fun Modifier.previewZoom(scale: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val scaledWidth = (placeable.width * scale).roundToInt()
    val scaledHeight = (placeable.height * scale).roundToInt()
    layout(scaledWidth, scaledHeight) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

@Composable
private fun MeasuredCvPages(
    document: CvDocument,
    theme: CvPreviewTheme,
    layout: CvTemplateLayout,
    paper: CvPaperSize,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier) { _ ->
        val plan = planCvPages(document, theme, layout, paper)
        val geometry = plan.geometry
        val paperPlaceables = plan.pages.indices.map { pageIndex ->
            subcompose(PreviewSlot.Paper(pageIndex)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.pageColor,
                    shadowElevation = 6.dp
                ) {}
            }
                .single()
                .measure(Constraints.fixed(geometry.pageWidth, geometry.pageHeight))
        }
        val footerPlaceables = plan.pages.indices.map { pageIndex ->
            subcompose(PreviewSlot.Footer(pageIndex)) {
                Text(
                    "Page ${pageIndex + 1} of ${plan.pages.size}",
                    color = theme.secondaryText,
                    fontFamily = theme.bodyFont,
                    fontSize = 8.sp
                )
            }
                .single()
                .measure(Constraints(maxWidth = geometry.contentWidth, maxHeight = geometry.bottomReserved))
        }
        val totalHeight = geometry.pageHeight * plan.pages.size +
            geometry.pageGap * (plan.pages.size - 1).coerceAtLeast(0)

        layout(geometry.pageWidth, totalHeight) {
            paperPlaceables.forEachIndexed { pageIndex, placeable ->
                placeable.placeRelative(0, pageIndex * (geometry.pageHeight + geometry.pageGap))
            }
            plan.pages.forEachIndexed { pageIndex, pageItems ->
                var itemY = pageIndex * (geometry.pageHeight + geometry.pageGap) + geometry.topMargin
                pageItems.forEach { itemIndex ->
                    val placeable = plan.contentPlaceables[itemIndex]
                    placeable.placeRelative(geometry.horizontalMargin, itemY)
                    itemY += placeable.height
                }
            }
            footerPlaceables.forEachIndexed { pageIndex, placeable ->
                val pageTop = pageIndex * (geometry.pageHeight + geometry.pageGap)
                val footerX = (geometry.pageWidth - placeable.width) / 2
                val footerY = pageTop + geometry.pageHeight - (geometry.bottomReserved + placeable.height) / 2
                placeable.placeRelative(footerX, footerY)
            }
        }
    }
}

/**
 * Measures every preview item and packs them into pages. Placement-free so it can be driven from a
 * headless [SubcomposeMeasureScope] in tests as well as by [MeasuredCvPages].
 */
internal fun SubcomposeMeasureScope.planCvPages(
    document: CvDocument,
    theme: CvPreviewTheme,
    layout: CvTemplateLayout,
    paper: CvPaperSize
): CvPagePlan {
    val pageWidth = paper.widthPoints.dp.roundToPx()
    val pageHeight = paper.heightPoints.dp.roundToPx()
    val horizontalMargin = layout.horizontalMargin.dp.roundToPx()
    val geometry = CvPageGeometry(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        horizontalMargin = horizontalMargin,
        topMargin = layout.topMargin.dp.roundToPx(),
        bottomReserved = layout.bottomReserved.dp.roundToPx(),
        pageGap = PAGE_GAP.roundToPx(),
        contentWidth = pageWidth - horizontalMargin * 2,
        contentHeight = pageHeight - layout.topMargin.dp.roundToPx() - layout.bottomReserved.dp.roundToPx()
    )
    val items = previewItems(document, theme)
    val contentPlaceables = items.mapIndexed { index, item ->
        subcompose(PreviewSlot.Content(index)) { PreviewItemView(item, theme, layout) }
            .single()
            .measure(Constraints.fixedWidth(geometry.contentWidth))
    }
    val pages = PagePacker.pack(
        items = contentPlaceables.mapIndexed { index, placeable ->
            val item = items[index]
            MeasuredPageItem(
                content = index,
                extent = placeable.height.toDouble(),
                keepWithNext = item is PreviewItem.SectionHeading || item is PreviewItem.EntryHeading,
                keepTogetherGroup = item.keepTogetherGroup
            )
        },
        pageExtent = geometry.contentHeight.toDouble()
    )
    return CvPagePlan(
        geometry = geometry,
        descriptors = items.map { it.describe() },
        groups = items.map { it.keepTogetherGroup },
        contentPlaceables = contentPlaceables,
        pages = pages
    )
}

@Composable
private fun PreviewItemView(item: PreviewItem, theme: CvPreviewTheme, layout: CvTemplateLayout) {
    when (item) {
        is PreviewItem.Candidate -> Text(
            item.text,
            fontSize = layout.candidateSize.sp,
            lineHeight = (layout.candidateSize + 4.0).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = theme.headingFont,
            color = if (item.missing) Color(0xFFB42318) else theme.primaryText
        )
        is PreviewItem.Contacts -> Text(
            item.text,
            modifier = Modifier.padding(top = 8.dp),
            color = theme.secondaryText,
            fontFamily = theme.bodyFont,
            fontSize = layout.bodySize.sp,
            lineHeight = layout.bodyLineHeight.sp
        )
        is PreviewItem.Headline -> Text(
            item.text,
            modifier = Modifier.padding(top = 3.dp),
            color = theme.accent,
            fontFamily = theme.headingFont,
            fontSize = layout.headlineSize.sp,
            lineHeight = (layout.headlineSize + 3.0).sp,
            fontWeight = FontWeight.Medium
        )
        is PreviewItem.SectionHeading -> Column(Modifier.padding(top = 22.dp)) {
            Text(
                item.text,
                fontSize = layout.sectionSize.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = theme.headingFont,
                letterSpacing = 1.sp,
                color = theme.accent
            )
            HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp), color = theme.divider)
        }
        is PreviewItem.EntryHeading -> Text(
            item.text,
            modifier = Modifier.padding(top = 10.dp),
            fontSize = layout.entrySize.sp,
            lineHeight = (layout.entrySize + 4.0).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = theme.headingFont,
            color = theme.primaryText
        )
        is PreviewItem.Block -> CvBlockView(item.block, theme, layout)
    }
}

@Composable
private fun CvBlockView(block: CvBlock, theme: CvPreviewTheme, layout: CvTemplateLayout) {
    when (block.kind) {
        CvBlockKind.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = theme.divider)
        CvBlockKind.Unsupported -> Unit
        CvBlockKind.ListItem -> Row(Modifier.padding(top = 3.dp)) {
            Text(
                if (block.isOrdered) "1. " else "• ",
                color = theme.primaryText,
                fontFamily = theme.bodyFont,
                fontSize = layout.bodySize.sp,
                lineHeight = layout.bodyLineHeight.sp
            )
            Text(
                inlineMarkdown(block.markdown, BuiltinThemes.SleekLight),
                modifier = Modifier.weight(1f),
                color = theme.primaryText,
                fontFamily = theme.bodyFont,
                fontSize = layout.bodySize.sp,
                lineHeight = layout.bodyLineHeight.sp
            )
        }
        CvBlockKind.Paragraph -> Text(
            inlineMarkdown(block.markdown, BuiltinThemes.SleekLight),
            modifier = Modifier.padding(top = 5.dp),
            color = theme.primaryText,
            fontFamily = theme.bodyFont,
            fontSize = layout.bodySize.sp,
            lineHeight = layout.bodyLineHeight.sp
        )
    }
}

private fun previewItems(document: CvDocument, theme: CvPreviewTheme): List<PreviewItem> = buildList {
    add(PreviewItem.Candidate(document.candidateName ?: "Candidate name required", document.candidateName == null))
    document.professionalHeadline?.let { add(PreviewItem.Headline(it)) }
    if (document.contacts.isNotEmpty()) {
        add(PreviewItem.Contacts(document.contacts.joinToString("  •  ") { it.label ?: it.value }))
    }
    document.headerContent.filterNot { it.kind == CvBlockKind.Unsupported }.forEach {
        add(PreviewItem.Block(it, keepTogetherGroup = "header"))
    }
    document.sections.forEachIndexed { sectionIndex, section ->
        val title = if (theme.uppercaseSections) section.title.uppercase() else section.title
        val intro = section.introduction.filterNot { it.kind == CvBlockKind.Unsupported }
        // The heading must never be stranded from the content beneath it, so it shares a
        // keep-together group with the first unit below it (its intro, or — when there is no
        // intro — its first entry). Every remaining entry is its own keep-together unit, so a long
        // section fills the current page and flows onto the next instead of being moved wholesale.
        val leadGroup = "section-$sectionIndex-lead"
        add(PreviewItem.SectionHeading(title, leadGroup))
        intro.forEach { add(PreviewItem.Block(it, leadGroup)) }
        section.entries.forEachIndexed { entryIndex, entry ->
            val entryGroup = if (entryIndex == 0 && intro.isEmpty()) {
                leadGroup
            } else {
                "section-$sectionIndex-entry-$entryIndex"
            }
            add(PreviewItem.EntryHeading(entry.title, entryGroup))
            entry.content.filterNot { it.kind == CvBlockKind.Unsupported }.forEach {
                add(PreviewItem.Block(it, entryGroup))
            }
        }
    }
}
