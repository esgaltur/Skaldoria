package com.markdownpres.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdownpres.state.PresentationState
import com.markdownpres.theme.PresentationTheme

/**
 * Landing screen shown on launch (before any deck is opened). Offers the
 * primary entry points — create a new deck, open an existing file/project, or
 * load the built-in sample — instead of dropping the user straight into the
 * demo deck.
 */
@Composable
fun WelcomeScreen(state: PresentationState) {
    val theme = state.currentTheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Skaldoria",
                color = theme.textPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Markdown Presentation Studio",
                color = theme.textMuted,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(40.dp))

            WelcomeAction(
                icon = Icons.Filled.Add,
                title = "New Presentation",
                subtitle = "Start from a clean, minimal deck",
                theme = theme,
                primary = true,
                onClick = { state.startBlankPresentation() }
            )
            Spacer(Modifier.height(14.dp))
            WelcomeAction(
                icon = Icons.Filled.FolderOpen,
                title = "Open File or Project…",
                subtitle = "Load a .md file or a .mdpres deck project",
                theme = theme,
                primary = false,
                onClick = { state.openFile() }
            )
            Spacer(Modifier.height(14.dp))
            WelcomeAction(
                icon = Icons.Filled.AutoAwesome,
                title = "Open Sample Deck",
                subtitle = "Explore the features with the demo presentation",
                theme = theme,
                primary = false,
                onClick = { state.openSampleDeck() }
            )
        }
    }
}

@Composable
private fun WelcomeAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    theme: PresentationTheme,
    primary: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val container = if (primary) theme.primary.copy(alpha = 0.14f) else theme.surface
    val baseBorder = if (primary) theme.primary else theme.cardBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(
                width = if (hovered || primary) 1.5.dp else 1.dp,
                color = if (hovered) theme.primary else baseBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (primary) theme.primary else theme.textSecondary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = theme.textMuted,
                fontSize = 13.sp
            )
        }
    }
}
