package com.skaldoria.canvas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.canvas.model.*
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.state.CanvasTool
import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.markdown.parser.MarkdownSlideParser
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.*

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** Breathing room inside the shape's own geometry, so glyphs never touch the outline. */
private val BASE_CONTENT_PADDING = 8.dp

/**
 * The node's content box at its unscaled size, anchored top-left and allowed to exceed the
 * zoom-sized box it sits in.
 *
 * A plain `size` is coerced into the parent's constraints, so below 100% zoom the content was
 * squeezed to the zoomed box first and then scaled down again by the graphics layer — text
 * shrank twice and the editing box came out smaller than the node.
 */
private fun Modifier.unscaledNodeSize(node: CanvasNode, density: androidx.compose.ui.unit.Density): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true)
        .requiredSize(
            width = with(density) { node.width.toDp() },
            height = with(density) { node.height.toDp() }
        )

private fun Modifier.nodeOutline(
    shape: androidx.compose.ui.graphics.Shape,
    fill: Color,
    border: BorderStroke,
    isSelected: Boolean,
    theme: PresentationTheme
): Modifier = shadow(
    elevation = if (isSelected) 12.dp else 4.dp,
    shape = shape,
    // The content clips itself to the outline. Clipping here as well cut the port handles in
    // half and cut the editing box back down to the shape.
    clip = false,
    spotColor = if (isSelected) theme.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.25f)
)
    .background(fill, shape)
    .border(border, shape)

/** The editing box laid over a shaped node while its source is being written. */
private val EDITOR_SHAPE = RoundedCornerShape(6.dp)

/**
 * Interactive spatial whiteboard card supporting rich Markdown, live code blocks,
 * LaTeX formulas, Mermaid diagrams, resizing, color customization, and edge port linking.
 */
@Composable
fun CanvasNodeCard(
    node: CanvasNode,
    state: CanvasState,
    theme: PresentationTheme,
    modifier: Modifier = Modifier,
    /**
     * Applied *after* the card is positioned. Passing `Modifier.alpha` through [modifier] puts the
     * fade layer ahead of the offset, so the card renders into a layer at the origin and is
     * clipped away.
     */
    alpha: Float = 1f
) {
    val isSelected = state.selectedNodeIds.contains(node.id)
    val isEditing = state.editingNodeId == node.id
    val zoom = state.viewport.zoom
    val density = LocalDensity.current

    // Screen-space position
    val screenPos = state.viewport.canvasToScreen(CanvasPoint(node.x, node.y))

    var showColorMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    val nodeShape = when (node.shape) {
        NodeShape.Card -> RoundedCornerShape(10.dp)
        NodeShape.Rectangle -> RectangleShape
        NodeShape.Circle -> CircleShape
        NodeShape.Diamond -> GenericShape { size, _ ->
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        NodeShape.Cylinder -> GenericShape { size, _ ->
            val ry = size.height * CYLINDER_RADIUS_FRACTION
            // The whole lid, so its near rim shows as the top of the body…
            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, ry * 2f))
            // …then the sides and the front half of the base. The old
            // outline added the body as a rectangle and the base as a full ellipse, so the border
            // drew straight lines across the lid and the base, and the base's hidden back rim.
            moveTo(0f, ry)
            lineTo(0f, size.height - ry)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, size.height - ry * 2f, size.width, size.height),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(size.width, ry)
            // Closed along the lid's front rim — a line that is drawn anyway — rather than
            // straight across, which the border would draw through the middle of the lid.
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, ry * 2f),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }
    }
    
    // Shapes with nothing eaten by the outline edit inside themselves; the rest get a full box.
    val editsInPlace = node.shape.contentInset() == NodeContentInset(0f, 0f, 0f, 0f)

    val cardBg = node.color.surface(theme.isDark)
    val accentColor = node.color.accent(theme.isDark)

    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, theme.primary)
        else -> BorderStroke(1.dp, theme.cardBorder.copy(alpha = 0.6f))
    }

    Box(
        modifier = modifier
            .offset { IntOffset(screenPos.x.roundToInt(), screenPos.y.roundToInt()) }
            .alpha(alpha)
            // The outer box's real layout size is the actual on-screen footprint (already
            // zoom-scaled). Previously this box was laid out at the *unscaled* node size and
            // only made to look bigger/smaller via a graphicsLayer scale — which meant pointer
            // input on this box (and its drag/resize math, which separately divides deltas by
            // `zoom`) received coordinates in an ambiguous, doubly-transformed space. That made
            // node dragging/resizing desync from the mouse at any zoom level other than 1.0.
            // Baking zoom directly into the layout size keeps this box's pointer coordinate
            // space equal to real screen pixels, so the single `/ zoom` conversions below are
            // correct and unambiguous.
            .size(
                width = with(density) { (node.width * zoom).toDp() },
                height = with(density) { (node.height * zoom).toDp() }
            )
            .then(if (isEditing && !editsInPlace) Modifier else Modifier.nodeOutline(nodeShape, cardBg, borderStroke, isSelected, theme))
            .testTag(CanvasTestTags.node(node.id))
            .pointerInput(node.id, zoom, isEditing, state.activeTool) {
                if (!isEditing) {
                    detectCanvasGestures(
                        onMiddleDrag = { delta ->
                            state.panBy(delta.toCanvasPoint())
                        },
                        onTap = {
                            if (state.activeTool != CanvasTool.Pan) {
                                state.selectNode(node.id, multiSelect = false)
                            }
                        },
                        onDoubleTap = {
                            if (state.activeTool == CanvasTool.Select) {
                                state.editingNodeId = node.id
                            }
                        },
                        onDragStart = { localPointerPosition ->
                            when (state.activeTool) {
                                CanvasTool.Select -> {
                                    if (node.id !in state.selectedNodeIds) {
                                        state.selectNode(node.id, multiSelect = false)
                                    }
                                    state.beginNodeTransform()
                                }
                                CanvasTool.Connect -> state.beginConnection(
                                    sourceNodeId = node.id,
                                    sourcePort = EdgePort.Auto,
                                    pointerScreenPosition = screenPos + localPointerPosition.toCanvasPoint()
                                )
                                CanvasTool.Pan -> Unit
                            }
                        },
                        onDrag = { _, dragAmount ->
                            when (state.activeTool) {
                                CanvasTool.Select -> state.moveSelectedNodes((dragAmount / zoom).toCanvasPoint())
                                CanvasTool.Connect -> state.moveConnectionPointerBy(dragAmount.toCanvasPoint())
                                CanvasTool.Pan -> state.panBy(dragAmount.toCanvasPoint())
                            }
                        },
                        onDragEnd = {
                            when (state.activeTool) {
                                CanvasTool.Select -> state.endNodeTransform()
                                CanvasTool.Connect -> state.finishConnection()
                                CanvasTool.Pan -> Unit
                            }
                        },
                        onDragCancel = {
                            when (state.activeTool) {
                                CanvasTool.Select -> state.endNodeTransform()
                                CanvasTool.Connect -> state.cancelConnection()
                                CanvasTool.Pan -> Unit
                            }
                        },
                        consumeDown = true
                    )
                }
            }
    ) {
        // Content is laid out at the node's true (unscaled) size and then visually scaled to
        // fill the already zoom-sized outer box. This keeps the visual result identical to
        // before while confining the zoom transform to rendering only — pointer input stays on
        // the outer box, entirely in real screen-pixel space.
        Box(
            modifier = Modifier
                .unscaledNodeSize(node, density)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                // After the scale, so the outline is clipped in the content's own coordinates.
                // Clipping first cut at the *unscaled* size in screen space: past 100% zoom the
                // card showed only its top-left corner — half a title and no body text.
                .clip(nodeShape)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (node.shape == NodeShape.Card) {
                    // Header Bar
                    CardHeader(
                        node = node,
                        theme = theme,
                        accentColor = accentColor,
                        isEditing = isEditing,
                        onToggleEdit = {
                            state.editingNodeId = if (isEditing) null else node.id
                        },
                        onOpenColorMenu = { showColorMenu = true },
                        onDelete = {
                            state.selectNode(node.id)
                            state.deleteSelected()
                        }
                    )

                    HorizontalDivider(
                        color = theme.cardBorder.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )
                }

                // Content Area.
                //
                // The inset comes from the shape's own geometry rather than a flat padding: a
                // diamond's usable rectangle is a quarter of its bounding box by area, so laying
                // content across the full width and clipping to the outline sliced the corners
                // off. See NodeShapeGeometry.contentInset.
                val inset = node.shape.contentInset()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = with(density) { (node.width * inset.left).toDp() } + BASE_CONTENT_PADDING,
                            top = with(density) { (node.height * inset.top).toDp() } + BASE_CONTENT_PADDING,
                            end = with(density) { (node.width * inset.right).toDp() } + BASE_CONTENT_PADDING,
                            bottom = with(density) { (node.height * inset.bottom).toDp() } + BASE_CONTENT_PADDING
                        ),
                    contentAlignment = if (node.shape == NodeShape.Card) Alignment.TopStart else Alignment.Center
                ) {
                    if (!isEditing) {
                        CardPreviewMode(
                            markdown = node.markdown,
                            theme = theme
                        )
                    } else if (editsInPlace) {
                        CardEditMode(
                            markdown = node.markdown,
                            onMarkdownChange = { state.updateNodeMarkdown(node.id, it) },
                            theme = theme,
                            focusRequester = focusRequester
                        )
                    }
                }
            }
        }

        // A shape whose outline eats into its box — circle, diamond, cylinder — leaves too little
        // room to type in: the source was confined to the inscribed rectangle and cut off after a
        // line or two. While editing, the source gets the whole bounding box instead, unclipped,
        // and the shape returns as soon as editing ends.
        if (isEditing && !editsInPlace) {
            Box(
                modifier = Modifier
                    .unscaledNodeSize(node, density)
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .background(cardBg, EDITOR_SHAPE)
                    .border(1.5.dp, theme.primary, EDITOR_SHAPE)
                    .testTag(CanvasTestTags.nodeEditor(node.id))
                    .padding(BASE_CONTENT_PADDING)
            ) {
                CardEditMode(
                    markdown = node.markdown,
                    onMarkdownChange = { state.updateNodeMarkdown(node.id, it) },
                    theme = theme,
                    focusRequester = focusRequester
                )
            }
        }

        // Color Picker Dropdown Menu
        DropdownMenu(
            expanded = showColorMenu,
            onDismissRequest = { showColorMenu = false }
        ) {
            NodeColor.entries.forEach { colorOption ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(colorOption.accent(theme.isDark))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(colorOption.label)
                        }
                    },
                    onClick = {
                        state.updateNodeColor(node.id, colorOption)
                        showColorMenu = false
                    }
                )
            }
        }

        // Connection Port Handles (Top, Right, Bottom, Left)
        PortHandle(
            port = EdgePort.Top,
            alignment = Alignment.TopCenter,
            node = node,
            state = state,
            theme = theme
        )
        PortHandle(
            port = EdgePort.Right,
            alignment = Alignment.CenterEnd,
            node = node,
            state = state,
            theme = theme
        )
        PortHandle(
            port = EdgePort.Bottom,
            alignment = Alignment.BottomCenter,
            node = node,
            state = state,
            theme = theme
        )
        PortHandle(
            port = EdgePort.Left,
            alignment = Alignment.CenterStart,
            node = node,
            state = state,
            theme = theme
        )

        // Bottom-Right Corner Resize Handle
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(16.dp)
                .pointerInput(node.id, zoom, state.activeTool) {
                    if (state.activeTool == CanvasTool.Select) {
                        detectCanvasGestures(
                            onDragStart = { state.beginNodeTransform() },
                            onDragEnd = state::endNodeTransform,
                            onDragCancel = state::endNodeTransform,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                state.resizeNode(node.id, dragAmount.x / zoom, dragAmount.y / zoom)
                            },
                            consumeDown = true
                        )
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Resize",
                tint = theme.textMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(12.dp).align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun CardHeader(
    node: CanvasNode,
    theme: PresentationTheme,
    accentColor: Color,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onOpenColorMenu: () -> Unit,
    onDelete: () -> Unit
) {
    val title = remember(node.markdown) {
        val firstLine = node.markdown.lines().firstOrNull { it.isNotBlank() } ?: "Card"
        firstLine.removePrefix("#").removePrefix("#").removePrefix("#").trim().take(24)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(theme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .clickable { onOpenColorMenu() }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = TextStyle(
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleEdit,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (isEditing) "Done" else "Edit",
                    tint = if (isEditing) theme.primary else theme.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = theme.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun CardEditMode(
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    theme: PresentationTheme,
    focusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BasicTextField(
            value = markdown,
            onValueChange = onMarkdownChange,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = theme.textPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            ),
            cursorBrush = SolidColor(theme.primary)
        )
    }
}

/**
 * Renders the node's markdown, shrinking it to fit rather than letting it run past the outline.
 *
 * [FitToCanvas] is the same overflow policy slides use, and [FitMode.Height] is the right measure
 * here because the content reflows: only genuine vertical overflow drives the scale down. The
 * content column must size itself naturally — a `fillMaxSize` wrapper collapses to nothing under
 * the unbounded height the fit pipeline measures with, which is why the old outer `Box` is gone.
 */
@Composable
private fun CardPreviewMode(
    markdown: String,
    theme: PresentationTheme
) {
    val slides = remember(markdown) {
        MarkdownSlideParser.parse(markdown)
    }

    if (markdown.isBlank()) {
        Text(
            text = "Double-click to write Markdown...",
            style = TextStyle(color = theme.textMuted, fontSize = 12.sp)
        )
        return
    }

    FitToCanvas(
        modifier = Modifier.fillMaxSize(),
        fitMode = FitMode.Height
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slides.forEach { slide ->
                if (slide.title.isNotBlank()) {
                    Text(
                        text = slide.title,
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                if (!slide.subtitle.isNullOrBlank()) {
                    Text(
                        text = slide.subtitle!!,
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                slide.elements.forEach { elem ->
                    RenderSlideElement(elem = elem, theme = theme)
                }
            }
        }
    }
}

@Composable
private fun RenderSlideElement(elem: SlideElement, theme: PresentationTheme) {
    when (elem) {
        is SlideElement.Text -> {
            Text(
                text = inlineMarkdown(elem.content, theme),
                style = TextStyle(
                    color = theme.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
        is SlideElement.BulletList -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                elem.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (elem.isOrdered) "${index + 1}. " else "• ",
                            style = TextStyle(color = theme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = inlineMarkdown(item, theme),
                            style = TextStyle(
                                color = theme.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }
        }
        is SlideElement.Quote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        border = BorderStroke(2.dp, theme.accent.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .background(theme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(4.dp)
            ) {
                Column {
                    Text(
                        text = inlineMarkdown(elem.quote, theme),
                        style = TextStyle(color = theme.textPrimary, fontSize = 11.sp)
                    )
                    elem.author?.takeIf { it.isNotBlank() }?.let { author ->
                        Text(
                            text = "— $author",
                            style = TextStyle(color = theme.textSecondary, fontSize = 10.sp)
                        )
                    }
                }
            }
        }
        is SlideElement.CodeBlock -> {
            CodeBlockView(
                code = elem.code,
                language = elem.language,
                highlightedLines = elem.highlightedLines,
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            )
        }
        is SlideElement.MermaidDiagram -> {
            MermaidDiagramCanvas(
                code = elem.code,
                theme = theme,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
        }
        is SlideElement.MathFormula -> {
            MathFormulaRenderer(
                formula = elem.formula,
                theme = theme,
                isBlock = elem.isBlock,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
        is SlideElement.Table -> CardTable(elem, theme)
        is SlideElement.Image -> {
            // The canvas has no image loader of its own; the card says what is there rather
            // than silently dropping it, and the deck export renders the real image.
            Text(
                text = "🖼 " + (elem.caption ?: elem.altText.ifBlank { elem.url }),
                style = TextStyle(color = theme.textSecondary, fontSize = 11.sp)
            )
        }
        is SlideElement.Metric -> {
            Column {
                Text(
                    text = elem.value,
                    style = TextStyle(color = theme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = inlineMarkdown(elem.label, theme),
                    style = TextStyle(color = theme.textSecondary, fontSize = 11.sp)
                )
            }
        }
        is SlideElement.Poll -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = inlineMarkdown(elem.question, theme),
                    style = TextStyle(color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                )
                elem.options.forEach { option ->
                    Text(
                        text = "○ $option",
                        style = TextStyle(color = theme.textPrimary, fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardTable(table: SlideElement.Table, theme: PresentationTheme) {
    val columns = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, theme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
    ) {
        @Composable
        fun TableRow(cells: List<String>, header: Boolean) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (header) Modifier.background(theme.surfaceVariant.copy(alpha = 0.5f)) else Modifier)
            ) {
                for (column in 0 until columns) {
                    Text(
                        text = inlineMarkdown(cells.getOrElse(column) { "" }, theme),
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (table.headers.isNotEmpty()) TableRow(table.headers, header = true)
        table.rows.forEach { row ->
            HorizontalDivider(color = theme.cardBorder.copy(alpha = 0.4f), thickness = 1.dp)
            TableRow(row, header = false)
        }
    }
}

@Composable
private fun BoxScope.PortHandle(
    port: EdgePort,
    alignment: Alignment,
    node: CanvasNode,
    state: CanvasState,
    theme: PresentationTheme
) {
    val isConnectingThis = state.connectingSourceNodeId == node.id && state.connectingSourcePort == port

    Box(
        modifier = Modifier
            .align(alignment)
            .size(14.dp)
            .offset(
                x = when (port) {
                    EdgePort.Right -> 7.dp
                    EdgePort.Left -> (-7).dp
                    else -> 0.dp
                },
                y = when (port) {
                    EdgePort.Bottom -> 7.dp
                    EdgePort.Top -> (-7).dp
                    else -> 0.dp
                }
            )
            .clip(CircleShape)
            .background(if (isConnectingThis) theme.primary else theme.cardBorder)
            .border(1.5.dp, if (isConnectingThis) Color.White else theme.surface, CircleShape)
            .testTag(CanvasTestTags.port(node.id, port.name))
            .pointerInput(node.id, port) {
                detectCanvasGestures(
                    onDragStart = {
                        val portCanvas = node.portPosition(port)
                        state.beginConnection(
                            sourceNodeId = node.id,
                            sourcePort = port,
                            pointerScreenPosition = state.viewport.canvasToScreen(portCanvas)
                        )
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.moveConnectionPointerBy(dragAmount.toCanvasPoint())
                    },
                    onDragEnd = { state.finishConnection() },
                    onDragCancel = state::cancelConnection,
                    consumeDown = true
                )
            }
    )
}
