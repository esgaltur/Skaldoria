package com.skaldoria.shared.ui.util

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * Loads a [Painter] icon directly from Java classpath resources without using deprecated
 * `androidx.compose.ui.res.painterResource(String)`.
 */
fun loadClasspathPainter(resourcePath: String): Painter? = try {
    val cleanPath = resourcePath.removePrefix("/")
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(cleanPath)
        ?: ResourceLoaderAnchor::class.java.getResourceAsStream("/$cleanPath")
        ?: ResourceLoaderAnchor::class.java.classLoader?.getResourceAsStream(cleanPath)

    stream?.use { input ->
        BitmapPainter(Image.makeFromEncoded(input.readBytes()).toComposeImageBitmap())
    }
} catch (_: Exception) {
    null
}

private object ResourceLoaderAnchor
