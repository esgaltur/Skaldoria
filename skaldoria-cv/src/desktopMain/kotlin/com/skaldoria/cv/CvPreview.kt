package com.skaldoria.cv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.skaldoria.cv.core.*
import com.skaldoria.cv.core.layout.*
import kotlin.math.roundToInt

private val PAGE_GAP = 24.dp

/**
 * Resolves the document once and hands back both halves of the result.abc
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
    document: CvDocument,
    templateId: CvTemplateId,
    themeId: CvThemeId,
    fontId: CvFontId,
    zoomPercent: Int = CvZoomPolicy.DefaultPercent,
    showZoomControls: Boolean = false,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onZoomReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val layout = remember(document, templateId, themeId, fontId) {
        resolveCvLayout(document, templateId, themeId, fontId)
    }
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
            Column(
                modifier = Modifier.previewZoom(zoomScale)
                    .width(layout.resolved.paper.widthPoints.dp),
                verticalArrangement = Arrangement.spacedBy(PAGE_GAP)
            ) {
                layout.resolved.pages.forEach { page ->
                    CvPageSheet(page, layout)
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
            ZoomControls(
                zoomPercent = zoomPercent,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onZoomReset = onZoomReset,
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
