package com.skaldoria.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skaldoria.core.qr.QrCodeGenerator

/**
 * High-performance, GPU-rendered QR Code Composable.
 * Renders standard ISO/IEC 18004 scannable QR matrices with customizable quiet zones and colors.
 */
@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
    quietZoneModules: Int = 2,
    cornerRadius: Dp = 12.dp
) {
    if (content.isBlank()) return

    val qrMatrix = remember(content) {
        try {
            QrCodeGenerator.encode(content)
        } catch (_: Exception) {
            null
        }
    } ?: return

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(lightColor)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val matrixSize = qrMatrix.size
            val totalModules = matrixSize + (quietZoneModules * 2)
            val modulePixelSize = size.minDimension / totalModules

            val offsetX = (size.width - (matrixSize * modulePixelSize)) / 2f
            val offsetY = (size.height - (matrixSize * modulePixelSize)) / 2f

            for (r in 0 until matrixSize) {
                for (c in 0 until matrixSize) {
                    if (qrMatrix.isDark(r, c)) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(offsetX + c * modulePixelSize, offsetY + r * modulePixelSize),
                            size = Size(modulePixelSize + 0.5f, modulePixelSize + 0.5f) // Slight overlap to prevent sub-pixel gaps
                        )
                    }
                }
            }
        }
    }
}
