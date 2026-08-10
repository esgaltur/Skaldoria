package com.skaldoria.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.media.ImageResolver
import com.skaldoria.core.media.ImageSource
import com.skaldoria.theme.PresentationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Directory that relative image paths resolve against — the project root, or the folder
 * holding the `.md`. Provided once near the window root so no layout has to thread it down.
 */
val LocalDeckBaseDir = compositionLocalOf<File?> { null }

/** Convenience for providing [LocalDeckBaseDir] around slide content. */
@Composable
fun ProvideDeckBaseDir(baseDir: File?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDeckBaseDir provides baseDir, content = content)
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val bitmap: ImageBitmap) : LoadState
    data class Failed(val reason: String) : LoadState
}

/** Decoded images keyed by resolved source, so paging back to a slide does not re-decode. */
private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

/** Remote fetch limits — a slide image should never be able to hang or exhaust memory. */
private const val REMOTE_CONNECT_TIMEOUT_MS = 5_000
private const val REMOTE_READ_TIMEOUT_MS = 8_000
private const val MAX_IMAGE_BYTES = 24 * 1024 * 1024

/**
 * Renders a slide image, or an explicit failure state.
 *
 * COR-10. Loading happens off the UI thread and results are cached; a failure shows what
 * went wrong rather than a blank box, because a silently missing image on a projector is
 * the worst outcome for an author.
 */
@Composable
fun SlideImage(
    url: String,
    altText: String,
    theme: PresentationTheme,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val baseDir = LocalDeckBaseDir.current
    val source = remember(url, baseDir) { ImageResolver.resolve(url, baseDir) }

    var state by remember(source) {
        mutableStateOf(
            when (source) {
                is ImageSource.Unsupported -> LoadState.Failed(source.reason)
                else -> imageCache[source.cacheKey()]
                    ?.let { LoadState.Loaded(it) }
                    ?: LoadState.Loading
            }
        )
    }

    LaunchedEffect(source) {
        if (state !is LoadState.Loading) return@LaunchedEffect
        state = withContext(Dispatchers.IO) { loadImage(source) }
    }

    when (val current = state) {
        is LoadState.Loaded -> Image(
            bitmap = current.bitmap,
            contentDescription = altText.ifBlank { "Slide image" },
            contentScale = contentScale,
            modifier = modifier
        )

        is LoadState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = theme.primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }

        is LoadState.Failed -> ImageFailure(altText, current.reason, theme, modifier)
    }
}

@Composable
private fun ImageFailure(
    altText: String,
    reason: String,
    theme: PresentationTheme,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = theme.textMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(10.dp))
            if (altText.isNotBlank()) {
                Text(
                    text = altText,
                    color = theme.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = reason,
                color = theme.textMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun ImageSource.cacheKey(): String = when (this) {
    is ImageSource.LocalFile -> "file:${file.absolutePath}:${file.lastModified()}"
    is ImageSource.Remote -> "url:$url"
    is ImageSource.Unsupported -> "unsupported:$reason"
}

private fun loadImage(source: ImageSource): LoadState {
    val key = source.cacheKey()
    imageCache[key]?.let { return LoadState.Loaded(it) }

    return try {
        val bytes = when (source) {
            is ImageSource.LocalFile -> {
                if (source.file.length() > MAX_IMAGE_BYTES) {
                    return LoadState.Failed("Image too large (${source.file.length() / 1024 / 1024} MB)")
                }
                source.file.readBytes()
            }

            is ImageSource.Remote -> readRemote(source.url)
                ?: return LoadState.Failed("Could not fetch image")

            is ImageSource.Unsupported -> return LoadState.Failed(source.reason)
        }

        val bitmap = org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        // Bounded so a long deck of large images cannot grow without limit.
        if (imageCache.size > 64) imageCache.clear()
        imageCache[key] = bitmap
        LoadState.Loaded(bitmap)
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        // Must propagate. This runs inside withContext(Dispatchers.IO), so swallowing it
        // would break structured concurrency — leaving a slide mid-load would be reported
        // as "could not decode" rather than simply being abandoned.
        throw cancellation
    } catch (e: Exception) {
        // Deliberately Exception, not Throwable: an OutOfMemoryError from decoding an
        // oversized image must not be caught and turned into a tidy placeholder.
        LoadState.Failed(e.message?.take(120) ?: "Could not decode image")
    }
}

/**
 * Fetches a remote image with explicit timeouts and a size ceiling.
 *
 * Deliberately plain `java.net` rather than an HTTP client dependency: this is a single GET
 * of bytes, and the project keeps its runtime dependency-free.
 */
private fun readRemote(url: String): ByteArray? {
    return try {
        val connection = (java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
            readTimeout = REMOTE_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Skaldoria")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(chunk)
                    if (read == -1) break
                    buffer.write(chunk, 0, read)
                    // Stop mid-stream rather than after the fact, so an oversized or
                    // endless response cannot be buffered into memory first.
                    if (buffer.size() > MAX_IMAGE_BYTES) return null
                }
                buffer.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }
}
