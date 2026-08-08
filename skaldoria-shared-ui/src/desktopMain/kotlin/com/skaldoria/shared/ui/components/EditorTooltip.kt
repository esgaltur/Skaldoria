package com.skaldoria.shared.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.shared.ui.theme.SkaldoriaTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTooltip(text: String, theme: SkaldoriaTheme, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                color = theme.surface,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 8.dp
            ) {
                Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        },
        delayMillis = 400
    ) {
        content()
    }
}
