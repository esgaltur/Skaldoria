package com.skaldoria.cv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.cv.core.CvOutline
import com.skaldoria.cv.core.CvOutlineItem
import com.skaldoria.cv.core.CvOutlineLevel

/**
 * Keyboard-navigable document outline — CV-FR-024.
 *
 * Rows are `selectable`, which is what gives each one a role, a selected state and a focus stop
 * for free (CV-FR-080, CV-NFR-080): the outline is reachable by Tab and activated by Space or
 * Enter without any key handling of its own.
 *
 * A [LazyColumn] rather than a scrolling `Column` because a hundred-page CV has a hundred-odd
 * rows, and the panel should cost what is on screen — the same reasoning as [CvPageWindow].
 */
@Composable
internal fun CvOutlinePanel(
    outline: List<CvOutlineItem>,
    caretLine: Int,
    onItemSelected: (CvOutlineItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = CvOutline.activeAt(outline, caretLine)

    Column(
        modifier.width(240.dp).fillMaxHeight().background(Color(0xFFF8F9FA)).padding(12.dp)
    ) {
        Text(
            text = "Outline",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.semantics { heading() }
        )

        if (outline.isEmpty()) {
            Text(
                text = "Add a level-two heading to start a section.",
                fontSize = 12.sp,
                color = Color(0xFF6B778C),
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }

        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(outline, key = { it.level.name + it.source.startLine }) { item ->
                val isActive = item == active
                val isEntry = item.level == CvOutlineLevel.Entry
                Text(
                    text = item.title,
                    fontSize = if (isEntry) 12.sp else 13.sp,
                    fontWeight = if (isEntry) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isActive) Color(0xFF0B5FFF) else Color(0xFF172B4D),
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .selectable(selected = isActive, onClick = { onItemSelected(item) })
                        .background(if (isActive) Color(0xFFE6EEFF) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .padding(start = if (isEntry) 12.dp else 0.dp)
                )
            }
        }
    }
}
