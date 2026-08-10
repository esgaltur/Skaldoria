package com.skaldoria.writer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.shared.ui.components.EditorTooltip
import com.skaldoria.shared.ui.theme.SkaldoriaTheme
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.CodeBlockView
import com.skaldoria.ui.components.MathFormulaRenderer
import com.skaldoria.ui.components.MermaidDiagramCanvas
import com.skaldoria.ui.components.inlineMarkdown
import com.skaldoria.writer.parser.Blockquote
import com.skaldoria.writer.parser.Bold
import com.skaldoria.writer.parser.BulletList
import com.skaldoria.writer.parser.Code
import com.skaldoria.writer.parser.CodeBlock
import com.skaldoria.writer.parser.Document
import com.skaldoria.writer.parser.DocumentParser
import com.skaldoria.writer.parser.Heading
import com.skaldoria.writer.parser.InlineNode
import com.skaldoria.writer.parser.Italic
import com.skaldoria.writer.parser.MathBlock
import com.skaldoria.writer.parser.Paragraph
import com.skaldoria.writer.parser.Strikethrough
import com.skaldoria.writer.parser.Text as AstText
import com.skaldoria.writer.parser.ThematicBreak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun WriterEditor(
    state: WriterState = remember { WriterState() },
    onOpenRequest: () -> Unit = {},
    onSaveRequest: () -> Unit = {}
) {
    val theme = state.theme
    val editorFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.text) {
        val sourceText = state.text
        delay(PARSE_DEBOUNCE_MILLIS)
        val parsed = withContext(Dispatchers.Default) { DocumentParser().parse(sourceText) }
        state.acceptParsedDocument(sourceText, parsed)
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        state.clearError()
    }

    val colorScheme = if (theme.bg.luminance() < 0.5f) {
        darkColorScheme(
            primary = theme.accent,
            background = theme.bg,
            surface = theme.surface,
            onBackground = theme.text,
            onSurface = theme.text
        )
    } else {
        lightColorScheme(
            primary = theme.accent,
            background = theme.bg,
            surface = theme.surface,
            onBackground = theme.text,
            onSurface = theme.text
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Box(Modifier.fillMaxSize().background(theme.bg).testTag(WriterTestTags.Root)) {
            Row(Modifier.fillMaxSize()) {
                WriterOutline(
                    state = state,
                    theme = theme,
                    onHeadingSelected = { index ->
                        if (state.navigateToHeading(index)) editorFocusRequester.requestFocus()
                    }
                )

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!state.isFocusMode) {
                        WriterTopBar(
                            state = state,
                            theme = theme,
                            onOpenRequest = onOpenRequest,
                            onSaveRequest = onSaveRequest
                        )
                    }

                    if (state.viewMode != ViewMode.Preview && !state.isFocusMode) {
                        FormattingToolbar(state, theme)
                    }

                    WriterWorkspace(
                        state = state,
                        theme = theme,
                        editorFocusRequester = editorFocusRequester,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )

                    if (!state.isFocusMode) WriterFooter(state, theme)
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

@Composable
private fun WriterOutline(
    state: WriterState,
    theme: SkaldoriaTheme,
    onHeadingSelected: (Int) -> Unit
) {
    AnimatedVisibility(
        visible = state.isSidebarOpen && !state.isFocusMode,
        enter = slideInHorizontally() + fadeIn(),
        exit = slideOutHorizontally() + fadeOut()
    ) {
        Surface(
            modifier = Modifier.width(240.dp).fillMaxHeight().testTag(WriterTestTags.Outline),
            color = theme.surface,
            shadowElevation = 3.dp
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "DOCUMENT OUTLINE",
                    color = theme.subtext,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(12.dp))
                if (state.headings.isEmpty()) {
                    Text("No headings found.", color = theme.subtext.copy(alpha = 0.55f), fontSize = 14.sp)
                } else {
                    state.headings.forEachIndexed { index, heading ->
                        Text(
                            text = heading.text,
                            color = theme.text,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(WriterTestTags.heading(index))
                                .clickable { onHeadingSelected(index) }
                                .padding(
                                    start = ((heading.level - 1) * 14).dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WriterTopBar(
    state: WriterState,
    theme: SkaldoriaTheme,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit
) {
    Surface(color = theme.bg, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = state::toggleSidebar) {
                        Icon(
                            if (state.isSidebarOpen) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = theme.accent
                        )
                    }
                    EditorTooltip("Open File (Ctrl+O)", theme) {
                        IconButton(onClick = onOpenRequest) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open", tint = theme.accent)
                        }
                    }
                    EditorTooltip("Save File (Ctrl+S)", theme) {
                        IconButton(onClick = onSaveRequest) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = theme.accent)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = (state.currentFile?.name ?: "Untitled Document") + if (state.isDirty) " *" else "",
                        color = theme.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = state::cycleTheme) {
                        Text(theme.name, color = theme.accent, fontWeight = FontWeight.Bold)
                    }
                    Text("Focus", color = theme.subtext, fontSize = 13.sp)
                    Switch(
                        checked = state.isFocusMode,
                        onCheckedChange = state::updateFocusMode,
                        enabled = state.viewMode != ViewMode.Preview,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.accent,
                            checkedTrackColor = theme.surface
                        ),
                        modifier = Modifier.testTag(WriterTestTags.FocusToggle)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center
            ) {
                SegmentedButton("Edit", state.viewMode == ViewMode.Edit, theme, WriterTestTags.viewMode(ViewMode.Edit)) {
                    state.selectViewMode(ViewMode.Edit)
                }
                SegmentedButton("Split", state.viewMode == ViewMode.Split, theme, WriterTestTags.viewMode(ViewMode.Split)) {
                    state.selectViewMode(ViewMode.Split)
                }
                SegmentedButton("Preview", state.viewMode == ViewMode.Preview, theme, WriterTestTags.viewMode(ViewMode.Preview)) {
                    state.selectViewMode(ViewMode.Preview)
                }
                Spacer(Modifier.width(16.dp))
                SegmentedButton("Visual", state.editingMode == EditingMode.Visual, theme, WriterTestTags.editingMode(EditingMode.Visual)) {
                    state.selectEditingMode(EditingMode.Visual)
                }
                SegmentedButton("Source", state.editingMode == EditingMode.Source, theme, WriterTestTags.editingMode(EditingMode.Source)) {
                    state.selectEditingMode(EditingMode.Source)
                }
            }
        }
    }
}

@Composable
private fun SegmentedButton(
    label: String,
    selected: Boolean,
    theme: SkaldoriaTheme,
    testTag: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) theme.accent.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) theme.accent else theme.subtext
        )
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FormattingToolbar(state: WriterState, theme: SkaldoriaTheme) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormatIcon(Icons.Default.FormatBold, "Bold", WriterFormat.Bold, state, theme)
        FormatIcon(Icons.Default.FormatItalic, "Italic", WriterFormat.Italic, state, theme)
        FormatIcon(Icons.Default.FormatStrikethrough, "Strikethrough", WriterFormat.Strikethrough, state, theme)
        FormatIcon(Icons.Default.Code, "Code", WriterFormat.Code, state, theme)
        FormatText("H1", WriterFormat.Heading1, state, theme)
        FormatText("H2", WriterFormat.Heading2, state, theme)
        FormatIcon(Icons.Default.FormatQuote, "Quote", WriterFormat.Quote, state, theme)
        FormatIcon(Icons.AutoMirrored.Filled.FormatListBulleted, "List", WriterFormat.List, state, theme)
        FormatIcon(Icons.Default.Check, "Checklist", WriterFormat.Checklist, state, theme)
    }
}

@Composable
private fun FormatIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    format: WriterFormat,
    state: WriterState,
    theme: SkaldoriaTheme
) {
    EditorTooltip(label, theme) {
        IconButton(
            onClick = { state.applyFormat(format) },
            modifier = Modifier.testTag(WriterTestTags.format(format))
        ) {
            Icon(icon, contentDescription = label, tint = theme.accent)
        }
    }
}

@Composable
private fun FormatText(label: String, format: WriterFormat, state: WriterState, theme: SkaldoriaTheme) {
    TextButton(
        onClick = { state.applyFormat(format) },
        modifier = Modifier.testTag(WriterTestTags.format(format))
    ) {
        Text(label, color = theme.accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WriterWorkspace(
    state: WriterState,
    theme: SkaldoriaTheme,
    editorFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        if (state.viewMode == ViewMode.Edit || state.viewMode == ViewMode.Split) {
            WriterTextPane(
                state = state,
                theme = theme,
                focusRequester = editorFocusRequester,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        if (state.viewMode == ViewMode.Split) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(theme.subtext.copy(alpha = 0.2f)))
        }
        if (state.viewMode == ViewMode.Preview || state.viewMode == ViewMode.Split) {
            Box(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .testTag(WriterTestTags.Preview),
                contentAlignment = Alignment.TopCenter
            ) {
                PreviewPanel(state.document, theme)
            }
        }
    }
}

@Composable
private fun WriterTextPane(
    state: WriterState,
    theme: SkaldoriaTheme,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val visualTransformation = remember(
        theme,
        state.textValue.selection,
        state.editingMode,
        state.isFocusMode
    ) {
        WysiwygVisualTransformation(
            theme = theme,
            cursorIndex = state.textValue.selection.start,
            isVisualMode = state.editingMode == EditingMode.Visual,
            isFocusMode = state.isFocusMode
        )
    }

    Box(modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxWidth().widthIn(max = 850.dp).padding(horizontal = 40.dp, vertical = 28.dp)) {
            if (state.text.isEmpty()) {
                Text(
                    "Start writing your masterpiece…",
                    color = theme.subtext.copy(alpha = 0.5f),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            BasicTextField(
                value = state.textValue,
                onValueChange = state::updateText,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 520.dp)
                    .focusRequester(focusRequester)
                    .testTag(WriterTestTags.Editor),
                textStyle = TextStyle(color = theme.text, fontSize = 17.sp, lineHeight = 27.sp),
                cursorBrush = SolidColor(theme.accent),
                visualTransformation = visualTransformation
            )
        }
    }
}

@Composable
private fun WriterFooter(state: WriterState, theme: SkaldoriaTheme) {
    Surface(modifier = Modifier.fillMaxWidth().height(36.dp), color = theme.surface) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "${state.wordCount} words • ${state.readTimeMinutes} min read",
                color = theme.subtext,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PreviewPanel(document: Document, theme: SkaldoriaTheme) {
    val presentationTheme = remember(theme) { theme.toPresentationTheme() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Text("LIVE PREVIEW", color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        document.blocks.forEach { block ->
            when (block) {
                is Heading -> Text(
                    text = inlineMarkdown(flattenInline(block.children), presentationTheme),
                    color = theme.text,
                    fontSize = (32 - block.level * 3).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                is Paragraph -> Text(
                    text = inlineMarkdown(flattenInline(block.children), presentationTheme),
                    color = theme.text.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                is CodeBlock -> PreviewCodeBlock(block, theme, presentationTheme)
                is ThematicBreak -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = theme.subtext.copy(alpha = 0.25f)
                )
                is Blockquote -> PreviewBlockquote(block, theme, presentationTheme)
                is MathBlock -> MathFormulaRenderer(
                    formula = block.content,
                    theme = presentationTheme,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                is BulletList -> PreviewList(block, theme, presentationTheme)
            }
        }
    }
}

@Composable
private fun PreviewCodeBlock(block: CodeBlock, theme: SkaldoriaTheme, presentationTheme: PresentationTheme) {
    val language = block.language.lowercase()
    if (language in DIAGRAM_LANGUAGES) {
        val diagramSource = when (language) {
            "gantt" -> "gantt\n${block.code}"
            "class" -> "classDiagram\n${block.code}"
            else -> block.code
        }
        Box(
            Modifier.fillMaxWidth().height(360.dp).padding(vertical = 12.dp)
                .clip(RoundedCornerShape(8.dp)).background(theme.surface)
        ) {
            MermaidDiagramCanvas(diagramSource, presentationTheme)
        }
    } else {
        CodeBlockView(
            code = block.code,
            language = block.language,
            highlightedLines = emptySet(),
            theme = presentationTheme,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun PreviewBlockquote(block: Blockquote, theme: SkaldoriaTheme, presentationTheme: PresentationTheme) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(theme.accent, RoundedCornerShape(2.dp)))
        Column(Modifier.padding(start = 16.dp)) {
            block.blocks.filterIsInstance<Paragraph>().forEach { paragraph ->
                Text(
                    text = inlineMarkdown(flattenInline(paragraph.children), presentationTheme),
                    color = theme.subtext,
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
private fun PreviewList(block: BulletList, theme: SkaldoriaTheme, presentationTheme: PresentationTheme) {
    Column(Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(
                    if (block.isOrdered) "${index + 1}." else "•",
                    color = theme.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    text = inlineMarkdown(item, presentationTheme),
                    color = theme.text.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

fun SkaldoriaTheme.toPresentationTheme(): PresentationTheme = PresentationTheme(
    id = "writer-preview",
    name = name,
    isDark = bg.luminance() < 0.5f,
    background = bg,
    surface = surface,
    surfaceVariant = surface,
    cardBorder = subtext.copy(alpha = 0.2f),
    primary = accent,
    accent = accent,
    success = Color(0xFFA3BE8C),
    warning = Color(0xFFEBCB8B),
    textPrimary = text,
    textSecondary = subtext,
    textMuted = subtext.copy(alpha = 0.7f),
    codeBackground = bg,
    codeText = text,
    codeKeyword = accent,
    codeString = Color(0xFFA3BE8C),
    codeComment = subtext,
    codeNumber = accent,
    codeHighlightLine = accent.copy(alpha = 0.2f),
    badgeBackground = surface,
    badgeText = accent
)

private fun flattenInline(nodes: List<InlineNode>): String = nodes.joinToString("") { node ->
    when (node) {
        is AstText -> node.content
        is Bold -> "**${flattenInline(node.children)}**"
        is Italic -> "*${flattenInline(node.children)}*"
        is Code -> "`${node.content}`"
        is Strikethrough -> "~~${flattenInline(node.children)}~~"
    }
}

private const val PARSE_DEBOUNCE_MILLIS = 250L
private val DIAGRAM_LANGUAGES = setOf("mermaid", "gantt", "class")
