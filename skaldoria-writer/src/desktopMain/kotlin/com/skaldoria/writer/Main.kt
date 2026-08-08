package com.skaldoria.writer

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.skaldoria.shared.ui.util.loadClasspathPainter

fun main() = application {
    val appIcon = remember { loadClasspathPainter("icons/writer.png") }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Skaldoria Writer",
        icon = appIcon,
        state = rememberWindowState(width = 900.dp, height = 700.dp)
    ) {
        WriterEditor()
    }
}
