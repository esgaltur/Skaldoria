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
 *
 * Features instant scannable QR Code generation and one-click URL copying.
 */
@Composable
fun RemotePairingDialog(
    state: PresentationState,
    onDismiss: () -> Unit
) {
    val theme = state.currentTheme
    // SEC-2: the presenter URL embeds the per-session token, so it must come from the
    // server rather than being assembled here. It is a credential — do not log or share it.
    // The audience URL is deliberately token-free.
    val presenterUrl = RemoteCompanionServer.presenterUrl()
    val audienceUrl = RemoteCompanionServer.audienceUrl()

    var selectedQrTab by remember { mutableStateOf(0) } // 0: Speaker Clicker, 1: Audience Portal
    var copiedUrl by remember { mutableStateOf(false) }

    val currentUrl = if (selectedQrTab == 0) presenterUrl else audienceUrl

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(520.dp)
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
                            Icon(Icons.Default.QrCode, null, tint = theme.primary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "Wireless Remote & Audience Portal",
                                color = theme.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scan QR code to connect mobile devices",
                                color = theme.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = theme.textMuted)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Server Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.background)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                    text = if (state.isRemoteServerRunning) "SERVER RUNNING" else "SERVER STOPPED",
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
                                text = "⚠️ Notice: ${state.remoteServerError}",
                                color = Color(0xFFF87171),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (state.isRemoteServerRunning) {
                    Spacer(Modifier.height(16.dp))

                    // QR Mode Selector Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.background)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedQrTab = 0
                                copiedUrl = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedQrTab == 0) theme.surfaceVariant else Color.Transparent,
                                contentColor = if (selectedQrTab == 0) theme.primary else theme.textMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(15.dp))
                                Text("Speaker Remote", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                selectedQrTab = 1
                                copiedUrl = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedQrTab == 1) theme.surfaceVariant else Color.Transparent,
                                contentColor = if (selectedQrTab == 1) theme.accent else theme.textMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.People, null, modifier = Modifier.size(15.dp))
                                Text("Audience Portal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Scannable QR Code Canvas Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .size(190.dp)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            QrCodeView(
                                content = currentUrl,
                                modifier = Modifier.size(170.dp),
                                darkColor = if (selectedQrTab == 0) Color(0xFF0F172A) else Color(0xFF0F172A),
                                lightColor = Color.White,
                                quietZoneModules = 2,
                                cornerRadius = 12.dp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = if (selectedQrTab == 0)
                            "Point phone camera to open Speaker Clicker & Notes"
                        else
                            "Audience scans this to participate in Live Polls & Q&A",
                        color = theme.textMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    // URL Copy Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.background)
                            .border(1.dp, theme.cardBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentUrl,
                            color = if (selectedQrTab == 0) theme.primary else theme.accent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(currentUrl), null)
                                copiedUrl = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (copiedUrl) Icons.Default.Check else Icons.Default.ContentCopy,
                                "Copy URL",
                                tint = if (copiedUrl) theme.success else theme.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
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
                        text = "Phone & laptop must be connected to the same Wi-Fi network.",
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
