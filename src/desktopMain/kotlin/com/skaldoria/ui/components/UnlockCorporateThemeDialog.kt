package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.skaldoria.state.PresentationState

@Composable
fun UnlockCorporateThemeDialog(
    state: PresentationState,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val theme = state.currentTheme

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, if (isSuccess) theme.success else theme.cardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSuccess) theme.success.copy(alpha = 0.15f) else theme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Business,
                        contentDescription = null,
                        tint = if (isSuccess) theme.success else theme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (isSuccess) "Enterprise Theme Unlocked!" else "Unlock Corporate Theme",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (isSuccess) {
                        "Deutsche Börse Executive theme has been activated for institutional presentations."
                    } else {
                        "Enter your enterprise access code to unlock the restricted 'Deutsche Börse Executive' corporate design system."
                    },
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(20.dp))

                if (!isSuccess) {
                    // Code Input
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = {
                            codeInput = it
                            errorMessage = null
                        },
                        label = { Text("Corporate Access Code") },
                        placeholder = { Text("******") },
                        leadingIcon = {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = theme.primary)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.cardBorder,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            cursorColor = theme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = theme.warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = theme.textMuted)
                        ) {
                            Text("Cancel")
                        }

                        Spacer(Modifier.width(10.dp))

                        Button(
                            onClick = {
                                if (codeInput.isBlank()) {
                                    errorMessage = "Please enter an access code."
                                } else {
                                    val success = state.unlockCorporateTheme(codeInput)
                                    if (success) {
                                        isSuccess = true
                                    } else {
                                        errorMessage = "Invalid corporate code. Access denied."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Unlock Theme", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // Success View
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.success,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Apply & Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
