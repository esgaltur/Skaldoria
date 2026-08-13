package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.theme.PresentationTheme

/**
 * A text input that can be made short without clipping its own content.
 *
 * Material 3's `OutlinedTextField` enforces `MinHeight = 56.dp` and reserves 16.dp of
 * content padding above and below the text line. Pinning one to a smaller height does not
 * compress it — it crops the placeholder and the input, which is what produced the clipped
 * fields in the slide-overview search box and the parking lot.
 *
 * `BasicTextField` has no decoration box, so it fits whatever height it is given. This wraps
 * it with the border, background, optional leading icon and placeholder that the Material
 * component would otherwise provide, so compact fields across the app share one
 * implementation instead of each re-deriving it.
 *
 * @param minHeight a floor, not a pin — a multi-line field grows past it rather than cropping.
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    theme: PresentationTheme,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 3,
    fontSize: TextUnit = 13.sp,
    minHeight: Dp = 44.dp,
    cornerRadius: Dp = 8.dp,
    borderColor: Color? = null,
    containerColor: Color? = null
) {
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor ?: theme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, borderColor ?: theme.cardBorder, RoundedCornerShape(cornerRadius))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            // Placeholder is a sibling rather than a decoration-box slot — the whole point of
            // dropping the Material field is that there is no decoration box.
            if (value.isEmpty()) {
                Text(text = placeholder, color = theme.textMuted, fontSize = fontSize)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = fontSize),
                cursorBrush = SolidColor(theme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
