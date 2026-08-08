package com.skaldoria.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import com.skaldoria.shared.ui.theme.SkaldoriaTheme
import com.skaldoria.shared.ui.theme.Themes
import com.skaldoria.shared.ui.components.EditorTooltip
import com.skaldoria.shared.ui.formatting.MarkdownFormatter
import com.skaldoria.writer.parser.DocumentParser
import com.skaldoria.writer.parser.Heading
import com.skaldoria.writer.parser.Document
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily

import com.skaldoria.theme.PresentationTheme
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.font.FontStyle
import com.skaldoria.ui.components.CodeBlockView
import com.skaldoria.ui.components.MathFormulaRenderer
import com.skaldoria.ui.components.MermaidParser
import com.skaldoria.ui.components.MermaidDiagramCanvas
import com.skaldoria.ui.components.inlineMarkdown
import com.skaldoria.writer.parser.CodeBlock
import com.skaldoria.writer.parser.Paragraph
import com.skaldoria.writer.parser.ThematicBreak
import com.skaldoria.writer.parser.Blockquote
import com.skaldoria.writer.parser.MathBlock
import com.skaldoria.writer.parser.BulletList
import com.skaldoria.writer.parser.Text as AstText

enum class ViewMode { Edit, Split, Preview }

@Composable
fun WriterEditor() {
    var viewMode by remember { mutableStateOf(ViewMode.Edit) }
    var isFocusMode by remember { mutableStateOf(false) }
    var isSidebarOpen by remember { mutableStateOf(true) }
    var currentThemeIndex by remember { mutableStateOf(0) }
    val theme = Themes.all[currentThemeIndex]
    
    var textState by remember {
        mutableStateOf(
            TextFieldValue(
                """
# Welcome to Skaldoria Writer

This is a demonstration of the **WysiwygVisualTransformation**. 
When your cursor is not on this line, you will see a large, bold header above, and the asterisks here will disappear.

## Try it out!

Use the arrow keys to move your cursor up to the header. Watch how the markdown syntax magically reveals itself only when you need to edit it!
                """.trimIndent()
            )
        )
    }

    var currentFile by remember { mutableStateOf<File?>(null) }
    var document by remember { mutableStateOf(Document(emptyList())) }
    val scrollState = rememberScrollState()

    // Phase D Lite: Debounced Background AST Parsing
    // Massively improves typing performance on large files by moving parsing off the main thread.
    LaunchedEffect(textState.text) {
        delay(300) // Debounce rapid typing
        val parsed = withContext(Dispatchers.Default) {
            DocumentParser().parse(textState.text)
        }
        document = parsed
    }

    val headings = document.blocks.filterIsInstance<Heading>()

    fun openFileDialog() {
        val dialog = FileDialog(null as Frame?, "Open Markdown", FileDialog.LOAD)
        dialog.file = "*.md"
        dialog.isVisible = true
        if (dialog.directory != null && dialog.file != null) {
            val file = File(dialog.directory, dialog.file)
            currentFile = file
            textState = TextFieldValue(file.readText())
        }
    }

    fun saveFileDialog() {
        if (currentFile != null) {
            currentFile!!.writeText(textState.text)
        } else {
            val dialog = FileDialog(null as Frame?, "Save Markdown", FileDialog.SAVE)
            dialog.file = "*.md"
            dialog.isVisible = true
            if (dialog.directory != null && dialog.file != null) {
                var file = File(dialog.directory, dialog.file)
                if (!file.name.endsWith(".md")) {
                    file = File(dialog.directory, "${dialog.file}.md")
                }
                currentFile = file
                file.writeText(textState.text)
            }
        }
    }

    Row(Modifier.fillMaxSize().background(theme.bg)) {
        // Document Outline Sidebar (Animated)
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally(tween(300)) + fadeIn(),
            exit = slideOutHorizontally(tween(300)) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                color = theme.surface,
                shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("DOCUMENT OUTLINE", color = theme.subtext, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp))
                    Spacer(Modifier.height(16.dp))
                    
                    if (headings.isEmpty()) {
                         Text("No headings found.", color = theme.subtext.copy(alpha=0.5f), fontSize = 14.sp)
                    } else {
                        headings.forEach { heading ->
                            val indent = (heading.level - 1) * 16
                            Text(
                                text = heading.text,
                                color = theme.text,
                                style = TextStyle(fontSize = 14.sp),
                                modifier = Modifier.padding(start = indent.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Main Content Area
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Top Toolbar
            Surface(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                color = theme.bg,
                shadowElevation = if (isSidebarOpen) 0.dp else 4.dp // Only shadow if sidebar is closed to separate
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSidebarOpen = !isSidebarOpen }) {
                            Icon(if (isSidebarOpen) Icons.Default.Close else Icons.Default.Menu, contentDescription = "Toggle Sidebar", tint = theme.accent)
                        }
                        Spacer(Modifier.width(8.dp))
                        EditorTooltip("Open File (Ctrl+O)", theme) {
                            IconButton(onClick = { openFileDialog() }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Open", tint = theme.accent)
                            }
                        }
                        EditorTooltip("Save File (Ctrl+S)", theme) {
                            IconButton(onClick = { saveFileDialog() }) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = theme.accent)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = currentFile?.name ?: "Untitled Document",
                            color = theme.text,
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EditorTooltip("Cycle Themes", theme) {
                            TextButton(onClick = { currentThemeIndex = (currentThemeIndex + 1) % Themes.all.size }) {
                                Text(theme.name, color = theme.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        
                        // View Mode Selector
                        Surface(shape = RoundedCornerShape(50), color = theme.surface) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                                ViewModeButton("Edit", viewMode == ViewMode.Edit, theme) { viewMode = ViewMode.Edit }
                                ViewModeButton("Split", viewMode == ViewMode.Split, theme) { viewMode = ViewMode.Split }
                                ViewModeButton("Preview", viewMode == ViewMode.Preview, theme) { viewMode = ViewMode.Preview }
                            }
                        }
                        
                        Spacer(Modifier.width(16.dp))

                        Text("Focus", color = theme.subtext, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(end = 8.dp))
                        Switch(
                            checked = isFocusMode,
                            onCheckedChange = { isFocusMode = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surface),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }

            // Floating Toolbar Component (Only show if editing)
            if (viewMode != ViewMode.Preview) {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 12.dp,
                    modifier = Modifier.padding(end = 24.dp).shadow(2.dp, RoundedCornerShape(50))
                ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                            EditorTooltip("Bold (Ctrl+B)", theme) {
                                IconButton(onClick = { textState = MarkdownFormatter.toggleBold(textState) }) { 
                                    Icon(Icons.Default.FormatBold, contentDescription = "Bold", tint = theme.accent) 
                                }
                            }
                            EditorTooltip("Italic (Ctrl+I)", theme) {
                                IconButton(onClick = { textState = MarkdownFormatter.toggleItalic(textState) }) { 
                                    Icon(Icons.Default.FormatItalic, contentDescription = "Italic", tint = theme.accent) 
                                }
                            }
                            EditorTooltip("Strikethrough", theme) {
                                IconButton(onClick = { textState = MarkdownFormatter.toggleStrikethrough(textState) }) { 
                                    Icon(Icons.Default.FormatStrikethrough, contentDescription = "Strikethrough", tint = theme.accent) 
                                }
                            }
                            EditorTooltip("Code Block", theme) {
                                IconButton(onClick = { textState = MarkdownFormatter.toggleCode(textState) }) { 
                                    Icon(Icons.Default.Code, contentDescription = "Code", tint = theme.accent) 
                                }
                            }
                            
                            // Divider
                            Box(Modifier.width(1.dp).height(24.dp).background(theme.subtext.copy(alpha=0.3f)).padding(horizontal = 4.dp))
                            
                            EditorTooltip("Heading 1", theme) {
                                TextButton(onClick = { textState = MarkdownFormatter.toggleHeader1(textState) }) { Text("H1", color = theme.accent, fontWeight = FontWeight.Bold) }
                            }
                            EditorTooltip("Heading 2", theme) {
                                TextButton(onClick = { textState = MarkdownFormatter.toggleHeader2(textState) }) { Text("H2", color = theme.accent, fontWeight = FontWeight.Bold) }
                            }
                            
                            // Divider
                            Box(Modifier.width(1.dp).height(24.dp).background(theme.subtext.copy(alpha=0.3f)).padding(horizontal = 4.dp))
                            
                            EditorTooltip("Blockquote", theme) {
                                IconButton(onClick = { textState = MarkdownFormatter.toggleQuote(textState) }) { 
                                    Icon(Icons.Default.FormatQuote, contentDescription = "Quote", tint = theme.accent) 
                                }
                            }
                            EditorTooltip("Bulleted List", theme) {
                                IconButton(onClick = { textState = MarkdownFormatter.toggleList(textState) }) { 
                                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "List", tint = theme.accent) 
                                }
                            }
                            EditorTooltip("Checklist", theme) {
                                IconButton(onClick = { textState = MarkdownFormatter.toggleChecklist(textState) }) { 
                                    Icon(Icons.Default.Check, contentDescription = "Checklist", tint = theme.accent) 
                                }
                            }
                        }
                    }
            }
            }

            // Editor & Preview Workspace
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // Editor Pane
                if (viewMode == ViewMode.Edit || viewMode == ViewMode.Split) {
                    Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(scrollState), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 850.dp).padding(horizontal = 40.dp, vertical = 24.dp)) {
                            val visualTransformation = remember(theme, textState.selection, isFocusMode, viewMode) {
                                // Always show source code (no WYSIWYG hiding) in the editor pane
                                WysiwygVisualTransformation(theme, textState.selection.start, false, isFocusMode)
                            }

                        Box(Modifier.fillMaxSize()) {
                            if (textState.text.isEmpty()) {
                                Text(
                                    text = "Start writing your masterpiece...",
                                    style = TextStyle(
                                        color = theme.subtext.copy(alpha = 0.5f),
                                        fontSize = 18.sp,
                                        lineHeight = 32.sp,
                                        fontWeight = FontWeight.Light
                                    ),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            
                            BasicTextField(
                                value = textState,
                                onValueChange = { textState = it },
                                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                                    if (event.isCtrlPressed && event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.B -> { textState = MarkdownFormatter.toggleBold(textState); true }
                                            Key.I -> { textState = MarkdownFormatter.toggleItalic(textState); true }
                                            Key.S -> { saveFileDialog(); true }
                                            Key.O -> { openFileDialog(); true }
                                            else -> false
                                        }
                                    } else false
                                },
                                textStyle = TextStyle(
                                    color = theme.text,
                                    fontSize = 17.sp,
                                    lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                                visualTransformation = visualTransformation
                            )
                        }
                        }
                    }
                }

                // Vertical Divider for Split Mode
                if (viewMode == ViewMode.Split) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(theme.subtext.copy(alpha=0.2f)))
                }

                // Preview Pane
                if (viewMode == ViewMode.Preview || viewMode == ViewMode.Split) {
                    Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                        PreviewPanel(textState.text, theme)
                    }
                }
            }

            // Beautiful Analytics Footer
            val wordCount = remember(textState.text) { textState.text.split("\\s+".toRegex()).count { it.isNotBlank() } }
            val readTime = remember(wordCount) { maxOf(1, wordCount / 200) } // Avg 200 wpm
            Surface(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                color = theme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "$wordCount words • $readTime min read",
                        color = theme.subtext,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

@Composable
fun ViewModeButton(text: String, isSelected: Boolean, theme: SkaldoriaTheme, onClick: () -> Unit) {
    val bgColor = if (isSelected) theme.accent.copy(alpha = 0.2f) else Color.Transparent
    val textColor = if (isSelected) theme.accent else theme.subtext
    
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

fun SkaldoriaTheme.toPresentationTheme(): PresentationTheme {
    return PresentationTheme(
        id = "writer-preview",
        name = this.name,
        isDark = this.bg.luminance() < 0.5f,
        background = this.bg,
        surface = this.surface,
        surfaceVariant = this.surface,
        cardBorder = this.subtext.copy(alpha = 0.2f),
        primary = this.accent,
        accent = this.accent,
        success = Color(0xFFA3BE8C), // Hardcoded nice green
        warning = Color(0xFFEBCB8B), // Hardcoded nice yellow
        textPrimary = this.text,
        textSecondary = this.subtext,
        textMuted = this.subtext.copy(alpha = 0.7f),
        codeBackground = this.bg,
        codeText = this.text,
        codeKeyword = this.accent,
        codeString = Color(0xFFA3BE8C),
        codeComment = this.subtext,
        codeNumber = this.accent,
        codeHighlightLine = this.accent.copy(alpha = 0.2f),
        badgeBackground = this.surface,
        badgeText = this.accent
    )
}

@Composable
fun PreviewPanel(text: String, theme: SkaldoriaTheme) {
    val document = remember(text) { DocumentParser().parse(text) }
    val presentationTheme = remember(theme) { theme.toPresentationTheme() }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Text("LIVE PREVIEW", color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(24.dp))
        
        for (block in document.blocks) {
            when (block) {
                is Heading -> {
                    Text(
                        text = block.text,
                        color = theme.text,
                        fontSize = (32 - block.level * 3).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                is Paragraph -> {
                    Text(
                        text = inlineMarkdown(flattenInline(block.children), presentationTheme),
                        color = theme.text.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        lineHeight = 26.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                is CodeBlock -> {
                    if (block.language.lowercase() == "mermaid") {
                        Box(Modifier.fillMaxWidth().height(400.dp).padding(vertical = 12.dp).clip(RoundedCornerShape(8.dp)).background(theme.surface)) {
                            MermaidDiagramCanvas(block.code, presentationTheme)
                        }
                    } else {
                        CodeBlockView(
                            code = block.code,
                            language = block.language,
                            highlightedLines = emptySet(),
                            theme = presentationTheme,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                is ThematicBreak -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp).height(1.dp).background(theme.subtext.copy(alpha = 0.2f)))
                }
                is Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(theme.accent.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            for (innerBlock in block.blocks) {
                                if (innerBlock is Paragraph) {
                                    Text(
                                        text = inlineMarkdown(flattenInline(innerBlock.children), presentationTheme),
                                        color = theme.subtext,
                                        fontSize = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
                is MathBlock -> {
                    MathFormulaRenderer(
                        formula = block.content,
                        theme = presentationTheme,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
                is BulletList -> {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = if (block.isOrdered) "${index + 1}." else "•",
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
            }
        }
    }
}

private fun flattenInline(nodes: List<com.skaldoria.writer.parser.InlineNode>): String {
    return nodes.joinToString("") { node ->
        when (node) {
            is AstText -> node.content
            is com.skaldoria.writer.parser.Bold -> "**${flattenInline(node.children)}**"
            is com.skaldoria.writer.parser.Italic -> "*${flattenInline(node.children)}*"
            is com.skaldoria.writer.parser.Code -> "`${node.content}`"
            is com.skaldoria.writer.parser.Strikethrough -> "~~${flattenInline(node.children)}~~"
        }
    }
}
