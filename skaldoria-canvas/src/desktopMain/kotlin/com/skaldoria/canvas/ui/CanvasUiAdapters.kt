package com.skaldoria.canvas.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.skaldoria.canvas.model.CanvasGeometry
import com.skaldoria.canvas.model.CanvasPoint
import com.skaldoria.canvas.model.CanvasRect
import com.skaldoria.canvas.model.NodeColor

internal fun Offset.toCanvasPoint() = CanvasPoint(x, y)
internal fun CanvasPoint.toOffset() = Offset(x, y)
internal fun Rect.toCanvasRect() = CanvasRect(left, top, right, bottom)

internal fun CanvasGeometry.CubicBezier.toPath(): Path = Path().apply {
    moveTo(start.x, start.y)
    cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
}

internal fun NodeColor.surface(isDark: Boolean): Color =
    Color(if (isDark) hexDark else hexLight)

internal fun NodeColor.accent(isDark: Boolean): Color = when (this) {
    NodeColor.Default -> if (isDark) Color(0xFF88C0D0) else Color(0xFF4F46E5)
    NodeColor.Indigo -> if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
    NodeColor.Emerald -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    NodeColor.Amber -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    NodeColor.Rose -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
    NodeColor.Purple -> if (isDark) Color(0xFFC084FC) else Color(0xFF9333EA)
    NodeColor.Cyan -> if (isDark) Color(0xFF22D3EE) else Color(0xFF0891B2)
}
