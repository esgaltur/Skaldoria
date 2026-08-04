package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
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
 * as wireless slide clickers and speaker note monitors.
 */
@Composable
fun RemotePairingDialog(
    state: PresentationState,
    onDismiss: () -> Unit
) {
    val theme = state.currentTheme
    var serverUrl by remember {
        mutableStateOf(
            if (state.isRemoteServerRunning) state.remoteServerUrl ?: "http://${RemoteCompanionServer.getLocalIpAddress()}:8888"
            else "http://${RemoteCompanionServer.getLocalIpAddress()}:8888"
        )
    }
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(460.dp)
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
                                text = "Mobile Remote Control",
                                color = theme.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Wireless phone clicker & notes",
                                color = theme.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = theme.textMuted)
                    }
                }

                Spacer(Modifier.height(20.dp))

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
                                onClick = {
                                    state.toggleRemoteServer()
                                    if (state.isRemoteServerRunning) {
                                        serverUrl = state.remoteServerUrl ?: "http://${RemoteCompanionServer.getLocalIpAddress()}:8888"
                                    }
                                },
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

                        if (state.isRemoteServerRunning) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Open this URL on your phone's browser (same Wi-Fi):",
                                color = theme.textMuted,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surface)
                                    .border(1.dp, theme.cardBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serverUrl,
                                    color = theme.primary,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(serverUrl), null)
                                        copied = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        "Copy",
                                        tint = if (copied) theme.success else theme.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Wifi, null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Both devices must be connected to the same local Wi-Fi network.",
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
