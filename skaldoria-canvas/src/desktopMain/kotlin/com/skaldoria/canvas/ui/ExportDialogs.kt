package com.skaldoria.canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.skaldoria.theme.PresentationTheme
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Modal dialog for inspecting and exporting compiled Skaldoria Decks or Documents.
 */
@Composable
fun ExportDialog(
    title: String,
    content: String,
    theme: PresentationTheme,
    isPresentationDeck: Boolean,
    onDismiss: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    var savedFilePath by remember { mutableStateOf<String?>(null) }

    val slideCount = remember(content) {
        if (isPresentationDeck) content.split("\n---\n").size else 1
    }

    Dialog(onDismissRequest = onDismiss) {
        val dialogShape = RoundedCornerShape(14.dp)
        Surface(
            modifier = Modifier
                .width(680.dp)
                .height(520.dp)
                .shadow(24.dp, dialogShape)
                .clip(dialogShape)
                .border(1.dp, theme.cardBorder, dialogShape),
            color = theme.surface,
            shape = dialogShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = title,
                            style = TextStyle(
                                color = theme.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isPresentationDeck) "Compiled into $slideCount presentation slides" else "Compiled into unified Markdown document",
                            style = TextStyle(color = theme.textMuted, fontSize = 12.sp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textMuted
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Content View Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.codeBackground)
                        .border(1.dp, theme.cardBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        style = TextStyle(
                            color = theme.codeText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (savedFilePath != null) {
                        Text(
                            text = "Saved to: ${File(savedFilePath!!).name}",
                            style = TextStyle(color = theme.success, fontSize = 12.sp)
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Copy Button
                        OutlinedButton(
                            onClick = {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(StringSelection(content), null)
                                copied = true
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(16.dp),
                                tint = if (copied) theme.success else theme.textPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "Copied!" else "Copy to Clipboard", color = theme.textPrimary, fontSize = 12.sp)
                        }

                        // Save As Button
                        Button(
                            onClick = {
                                val chooser = JFileChooser().apply {
                                    dialogTitle = "Save Exported Markdown"
                                    fileFilter = FileNameExtensionFilter("Markdown File (*.md)", "md")
                                    selectedFile = File(if (isPresentationDeck) "presentation.md" else "document.md")
                                }
                                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    var file = chooser.selectedFile
                                    if (!file.name.endsWith(".md", ignoreCase = true)) {
                                        file = File(file.parentFile, "${file.name}.md")
                                    }
                                    file.writeText(content, Charsets.UTF_8)
                                    savedFilePath = file.absolutePath
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Save File (.md)", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
