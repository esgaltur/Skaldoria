package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.skaldoria.remote.RemoteCompanionServer
import com.skaldoria.state.PresentationState
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Interactive pairing modal for connecting smartphones and tablets
 * as wireless slide clickers, speaker notes monitors, and audience live interaction.
 */
@Composable
fun RemotePairingDialog(
    state: PresentationState,
    onDismiss: () -> Unit
) {
    val theme = state.currentTheme
    val baseUrl = if (state.isRemoteServerRunning) {
        state.remoteServerUrl ?: "http://${RemoteCompanionServer.getLocalIpAddress()}:${RemoteCompanionServer.currentPort}"
    } else {
        "http://${RemoteCompanionServer.getLocalIpAddress()}:${RemoteCompanionServer.currentPort}"
    }
    val presenterUrl = "$baseUrl/remote"
    val audienceUrl = "$baseUrl/audience"

    var copiedPresenter by remember { mutableStateOf(false) }
    var copiedAudience by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = theme.primary.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(20.dp))
                .background(theme.surface)
                .border(1.dp, theme.cardBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = theme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = theme.primary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "Wireless Remote & Audience Portal",
                                color = theme.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Live clicker, notes & interactive audience",
                                color = theme.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = theme.textMuted)
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Server Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.background)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (state.isRemoteServerRunning) theme.success else theme.textMuted)
                                )
                                Text(
                                    text = if (state.isRemoteServerRunning) "SERVER ACTIVE" else "SERVER STOPPED",
                                    color = if (state.isRemoteServerRunning) theme.success else theme.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = { state.toggleRemoteServer() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isRemoteServerRunning) Color(0xFFDC2626) else theme.primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (state.isRemoteServerRunning) "Stop Server" else "Start Server",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (state.remoteServerError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "⚠️ Server Notice: ${state.remoteServerError}",
                                color = Color(0xFFF87171),
                                fontSize = 11.sp
                            )
                        }

                        if (state.isRemoteServerRunning) {
                            Spacer(Modifier.height(14.dp))

                            // 1. Presenter Clicker Link
                            Text(
                                text = "📱 Speaker Clicker & Notes URL:",
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surface)
                                    .border(1.dp, theme.cardBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = presenterUrl,
                                    color = theme.primary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(presenterUrl), null)
                                        copiedPresenter = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        "Copy",
                                        tint = if (copiedPresenter) theme.success else theme.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // 2. Audience Interaction Link
                            Text(
                                text = "👥 Audience Live Polls & Q&A URL:",
                                color = theme.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surface)
                                    .border(1.dp, theme.cardBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = audienceUrl,
                                    color = theme.accent,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(audienceUrl), null)
                                        copiedAudience = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        "Copy",
                                        tint = if (copiedAudience) theme.success else theme.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Wifi, null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Devices must be connected to the same local network.",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done", color = theme.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
