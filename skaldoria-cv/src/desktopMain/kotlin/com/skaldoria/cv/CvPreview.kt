package com.skaldoria.cv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.cv.core.*
import com.skaldoria.cv.core.layout.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PAGE_GAP = 24.dp
private val PREVIEW_PADDING = 28.dp

/**
 * Resolves the document once and hands back both halves of the result.
 *
 * Preview and export share this, so the pages a user approves on screen are the pages that get
 * written — CV-FR-041. The measurer is returned alongside the layout because drawing reuses the
 * very [androidx.compose.ui.text.TextLayoutResult] objects the engine measured.
 */
data class CvPreviewLayout(
    val resolved: CvResolvedLayout,
    val measurer: ComposeCvTextMeasurer,
    val theme: CvPreviewTheme,
    val template: CvTemplateLayout
)

fun resolveCvLayout(
    document: CvDocument,
    templateId: CvTemplateId,
    themeId: CvThemeId,
    fontId: CvFontId
): CvPreviewLayout {
    // The font *program*, not a name-based lookup: the preview must measure with the same bytes
    // the PDF embeds, or the line breaks it computes will not hold on the exported page.
    val font = CvFontProgram.load(fontId)
    val theme = CvPreviewThemes.resolve(themeId).copy(
        bodyFont = font.family,
        headingFont = font.family
    )
    val measurer = ComposeCvTextMeasurer(bodyFont = theme.bodyFont, headingFont = theme.headingFont)

    return CvPreviewLayout(
        resolved = CvLayoutEngine.resolve(
            document = document,
            layout = templateId.layout,
            paper = CvPaperSize.fromMetadata(document.metadata["paper"]),
            measurer = measurer,
            options = CvLayoutOptions(uppercaseSections = theme.uppercaseSections)
        ),
        measurer = measurer,
        theme = theme,
        template = templateId.layout
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CvPreview(
    layout: CvPreviewLayout,
    zoomPercent: Int = CvZoomPolicy.DefaultPercent,
    zoomFit: CvZoomFit = CvZoomFit.None,
    showZoomControls: Boolean = false,
    navigation: CvNavigationRequest? = null,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onZoomReset: () -> Unit = {},
    onZoomFitPage: () -> Unit = {},
    onZoomFitWidth: () -> Unit = {},
    onZoomResolved: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
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
        val density = LocalDensity.current
        val paper = layout.resolved.paper
        val paddingPx = with(density) { PREVIEW_PADDING.toPx() }
        val unscaledPageHeight = with(density) { paper.heightPoints.dp.toPx() }
        val unscaledPageWidth = with(density) { paper.widthPoints.dp.toPx() }
        val unscaledGap = with(density) { PAGE_GAP.toPx() }
        val viewportHeight = constraints.maxHeight.toFloat()
        val viewportWidth = constraints.maxWidth.toFloat()

        // Fit is a standing instruction, so it is re-resolved against the viewport on every
        // layout; an explicit percentage is left exactly where the user put it.
        val effectivePercent = if (zoomFit == CvZoomFit.None) {
            zoomPercent
        } else {
            CvZoomPolicy.fitPercent(
                fit = zoomFit,
                viewportWidth = viewportWidth - paddingPx * 2,
                viewportHeight = viewportHeight - paddingPx * 2,
                pageWidth = unscaledPageWidth,
                pageHeight = unscaledPageHeight
            )
        }
        LaunchedEffect(effectivePercent, zoomFit) {
            if (zoomFit != CvZoomFit.None) onZoomResolved(effectivePercent)
        }

        val zoomScale = CvZoomPolicy.scale(effectivePercent)
        val pageExtent = unscaledPageHeight * zoomScale
        val gapExtent = unscaledGap * zoomScale

        val currentPage = CvPageWindow.currentPage(
            scrollOffset = verticalScrollState.value.toFloat(),
            viewportExtent = viewportHeight,
            firstPageTop = paddingPx,
            pageExtent = pageExtent,
            gap = gapExtent,
            pageCount = layout.resolved.pageCount
        )
        val drawnPages = CvPageWindow.visible(
            scrollOffset = verticalScrollState.value.toFloat(),
            viewportExtent = viewportHeight,
            firstPageTop = paddingPx,
            pageExtent = pageExtent,
            gap = gapExtent,
            pageCount = layout.resolved.pageCount
        )

        fun scrollToPage(pageNumber: Int) {
            scope.launch {
                verticalScrollState.animateScrollTo(
                    CvPageWindow.offsetOfPage(pageNumber, paddingPx, pageExtent, gapExtent)
                        .roundToInt()
                        .coerceIn(0, verticalScrollState.maxValue)
                )
            }
        }

        // CV-FR-024: picking an outline row moves the preview to the page showing that line.
        LaunchedEffect(navigation, layout) {
            val line = navigation?.line ?: return@LaunchedEffect
            layout.resolved.pageContaining(line)?.let { scrollToPage(it) }
        }

        Box(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(PREVIEW_PADDING),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.previewZoom(zoomScale)
                    .width(paper.widthPoints.dp),
                verticalArrangement = Arrangement.spacedBy(PAGE_GAP)
            ) {
                layout.resolved.pages.forEachIndexed { index, page ->
                    if (index in drawnPages) {
                        CvPageSheet(page, layout)
                    } else {
                        // Same box, no paint: the scroll geometry has to stay identical or the
                        // scrollbar would jump as the window moves.
                        Spacer(
                            Modifier.width(paper.widthPoints.dp).height(paper.heightPoints.dp)
                        )
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(verticalScrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp)
        )
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)
        )
        if (showZoomControls) {
            PreviewControls(
                zoomPercent = effectivePercent,
                zoomFit = zoomFit,
                currentPage = currentPage,
                pageCount = layout.resolved.pageCount,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onZoomReset = onZoomReset,
                onZoomFitPage = onZoomFitPage,
                onZoomFitWidth = onZoomFitWidth,
                onPageSelected = ::scrollToPage,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            )
        }
    }
}

/**
 * One sheet, drawn from the resolved page.
 *
 * The canvas is scaled so its coordinate space *is* the layout's — one unit is one typographic
 * point — which is why nothing here converts between units. The engine measured at density 1.0 for
 * exactly this reason.
 */
@Composable
private fun CvPageSheet(page: CvLayoutPage, layout: CvPreviewLayout) {
    val paper = layout.resolved.paper
    val template = layout.template

    Surface(
        modifier = Modifier.width(paper.widthPoints.dp).height(paper.heightPoints.dp),
        color = layout.theme.pageColor,
        shadowElevation = 6.dp
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / paper.widthPoints.toFloat()
            scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
                page.elements.forEach { element ->
                    drawElement(element, template.horizontalMargin, template.topMargin, layout)
                }
                page.footer?.let { footer ->
                    drawElement(
                        element = footer.copy(
                            yPt = paper.heightPoints - template.topMargin -
                                template.bottomReserved / 2 - template.footerSize
                        ),
                        originX = template.horizontalMargin,
                        originY = template.topMargin,
                        layout = layout
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawElement(
    element: CvPageElement,
    originX: Double,
    originY: Double,
    layout: CvPreviewLayout
) {
    when (element) {
        is CvPageElement.Rule -> drawLine(
            color = layout.theme.colorFor(element.color),
            start = Offset((originX + element.xPt).toFloat(), (originY + element.yPt).toFloat()),
            end = Offset(
                (originX + element.xPt + element.widthPt).toFloat(),
                (originY + element.yPt).toFloat()
            ),
            strokeWidth = element.thicknessPt.toFloat()
        )

        is CvPageElement.TextBlock -> {
            if (element.text.lines.isEmpty()) return

            // Re-requesting the measurement returns the cached TextLayoutResult the engine used,
            // so the glyphs drawn here are the ones that decided the page breaks.
            val result = layout.measurer.layoutFor(
                runs = element.text.sourceRuns,
                style = element.style,
                maxWidthPt = element.text.maxWidthPt
            )

            drawText(
                textLayoutResult = result,
                color = layout.theme.colorFor(element.style.color),
                topLeft = Offset((originX + element.xPt).toFloat(), (originY + element.yPt).toFloat())
            )
        }
    }
}

private fun CvPreviewTheme.colorFor(role: CvColorRole): Color = when (role) {
    CvColorRole.PrimaryText -> primaryText
    CvColorRole.SecondaryText -> secondaryText
    CvColorRole.Accent -> accent
    CvColorRole.Divider -> divider
    CvColorRole.Missing -> Color(0xFFB42318)
}

/** Print-preview controls — CV-FR-047. None of these change what export produces. */
@Composable
private fun PreviewControls(
    zoomPercent: Int,
    zoomFit: CvZoomFit,
    currentPage: Int,
    pageCount: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onZoomFitPage: () -> Unit,
    onZoomFitWidth: () -> Unit,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shadowElevation = 3.dp) {
        Column {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onZoomFitPage) {
                    Text("Fit page", fontSize = 11.sp, color = accentWhenActive(zoomFit == CvZoomFit.Page))
                }
                TextButton(onClick = onZoomFitWidth) {
                    Text("Fit width", fontSize = 11.sp, color = accentWhenActive(zoomFit == CvZoomFit.Width))
                }
                TextButton(onClick = onZoomReset) {
                    Text("Actual", fontSize = 11.sp, color = accentWhenActive(zoomFit == CvZoomFit.None && zoomPercent == CvZoomPolicy.DefaultPercent))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onPageSelected(currentPage - 1) },
                    enabled = currentPage > 1
                ) { Text("◀", fontSize = 11.sp) }
                Text("Page $currentPage of $pageCount", fontSize = 11.sp)
                TextButton(
                    onClick = { onPageSelected(currentPage + 1) },
                    enabled = currentPage < pageCount
                ) { Text("▶", fontSize = 11.sp) }
            }
        }
    }
}

private fun accentWhenActive(active: Boolean): Color =
    if (active) Color(0xFF0B5FFF) else Color(0xFF44546F)

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
