package com.markdownpres.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdownpres.theme.PresentationTheme

@Composable
fun CodeBlockView(
    code: String,
    language: String,
    highlightedLines: Set<Int>,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val lines = code.lines()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.codeBackground)
            .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp))
    ) {
        // Window Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Traffic light dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(theme.accent.copy(alpha = 0.8f)))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(theme.warning.copy(alpha = 0.8f)))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(theme.success.copy(alpha = 0.8f)))
            }

            // Language Badge
            Text(
                text = language.uppercase(),
                color = theme.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Code Body with Line Numbers & Highlights
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            lines.forEachIndexed { index, lineText ->
                val lineNumber = index + 1
                val isHighlighted = lineNumber in highlightedLines

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHighlighted) theme.codeHighlightLine else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Line Number
                    Text(
                        text = lineNumber.toString().padStart(2, ' '),
                        color = if (isHighlighted) theme.primary else theme.textMuted.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(28.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    // Syntax Highlighted Text
                    Text(
                        text = highlightSyntax(lineText, theme),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

private val KEYWORDS = setOf(
    "val", "var", "fun", "class", "data", "object", "interface", "when", "if", "else",
    "return", "import", "package", "for", "while", "is", "in", "null", "true", "false",
    "public", "private", "protected", "override", "sealed", "enum", "async", "await",
    "let", "const", "fn", "struct", "impl", "pub", "match", "type", "def"
)

private fun highlightSyntax(line: String, theme: PresentationTheme): AnnotatedString {
    return buildAnnotatedString {
        val trimmed = line.trimStart()

        // Handle full line comments
        if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
            append(line)
            addStyle(SpanStyle(color = theme.codeComment), 0, line.length)
            return@buildAnnotatedString
        }

        // Tokenize line by words, strings, comments
        var i = 0
        while (i < line.length) {
            val char = line[i]

            // Check for string literal "..."
            if (char == '"' || char == '\'') {
                val quoteChar = char
                val start = i
                i++
                while (i < line.length && line[i] != quoteChar) {
                    if (line[i] == '\\' && i + 1 < line.length) i++
                    i++
                }
                if (i < line.length) i++ // include closing quote
                val str = line.substring(start, i)
                append(str)
                addStyle(SpanStyle(color = theme.codeString), length - str.length, length)
                continue
            }

            // Check for inline comment //
            if (char == '/' && i + 1 < line.length && line[i + 1] == '/') {
                val comment = line.substring(i)
                append(comment)
                addStyle(SpanStyle(color = theme.codeComment), length - comment.length, length)
                break
            }

            // Check for word token (identifier or keyword)
            if (char.isLetter() || char == '_') {
                val start = i
                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
                    i++
                }
                val word = line.substring(start, i)
                append(word)

                if (word in KEYWORDS) {
                    addStyle(SpanStyle(color = theme.codeKeyword, fontWeight = FontWeight.Bold), length - word.length, length)
                } else if (word.first().isUpperCase()) {
                    addStyle(SpanStyle(color = theme.primary), length - word.length, length)
                } else {
                    addStyle(SpanStyle(color = theme.codeText), length - word.length, length)
                }
                continue
            }

            // Check for number literal
            if (char.isDigit()) {
                val start = i
                while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'f' || line[i] == 'L')) {
                    i++
                }
                val num = line.substring(start, i)
                append(num)
                addStyle(SpanStyle(color = theme.codeNumber), length - num.length, length)
                continue
            }

            // Other punctuation / symbols
            append(char)
            addStyle(SpanStyle(color = theme.codeText.copy(alpha = 0.8f)), length - 1, length)
            i++
        }
    }
}
