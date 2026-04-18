package com.tun.vpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/**
 * Экран редактирования профиля.
 * Сохранение при уходе со страницы (draft-based pattern).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profile: VpnProfile,
    onSave: (VpnProfile) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val theme = LocalTgTheme.current
    var name by remember { mutableStateOf(profile.name) }
    var vkLink by remember { mutableStateOf(profile.vkLink) }
    var peerAddr by remember { mutableStateOf(profile.peerAddr) }
    var listenAddr by remember { mutableStateOf(profile.listenAddr) }
    var nValue by remember { mutableIntStateOf(profile.nValue) }
    var wgConfig by remember { mutableStateOf(profile.wgQuickConfig) }
    var manualCaptcha by remember { mutableStateOf(profile.manualCaptcha) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Сохраняем при уходе
    DisposableEffect(Unit) {
        onDispose {
            onSave(
                profile.copy(
                    name = name,
                    vkLink = vkLink,
                    peerAddr = peerAddr,
                    listenAddr = listenAddr,
                    nValue = nValue,
                    wgQuickConfig = wgConfig,
                    manualCaptcha = manualCaptcha
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Edit Profile") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.accent)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("Profile")

            ProfileField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                placeholder = "My Server"
            )

            SectionLabel("Proxy Settings")

            ProfileField(
                value = vkLink,
                onValueChange = { vkLink = it },
                label = "TURN Server URL",
                placeholder = "https://vk.com/call/join/..."
            )

            ProfileField(
                value = peerAddr,
                onValueChange = { peerAddr = it },
                label = "Peer Address",
                placeholder = "IP:PORT"
            )

            ProfileField(
                value = listenAddr,
                onValueChange = { listenAddr = it },
                label = "Listen Address",
                placeholder = "127.0.0.1:9000"
            )

            ProfileField(
                value = nValue.toString(),
                onValueChange = { nValue = it.toIntOrNull() ?: 16 },
                label = "Connections (n)",
                placeholder = "16",
                keyboardType = KeyboardType.Number
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manual captcha",
                    color = Color(0xFFF1F5F9),
                    fontSize = 14.sp
                )
                Switch(
                    checked = manualCaptcha,
                    onCheckedChange = { manualCaptcha = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.accent,
                        checkedTrackColor = theme.accent.copy(alpha = 0.3f)
                    )
                )
            }

            SectionLabel("WireGuard Config")

            OutlinedTextField(
                value = wgConfig,
                onValueChange = { wgConfig = it },
                label = { Text("wg-quick config") },
                placeholder = {
                    Text(
                        "[Interface]\nPrivateKey = ...\nAddress = 10.0.0.2/32\n\n[Peer]\nPublicKey = ...\nEndpoint = 127.0.0.1:9000\nAllowedIPs = 0.0.0.0/0",
                        color = theme.textSecondary.copy(alpha = 0.3f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFF1F5F9)
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.3f),
                    focusedContainerColor = theme.surface,
                    unfocusedContainerColor = theme.surface
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Delete button
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.error.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = theme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Delete Profile", color = theme.error)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Profile") },
            text = { Text("Delete \"$name\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(profile.id)
                    onBack()
                }) {
                    Text("Delete", color = theme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val theme = LocalTgTheme.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = theme.textSecondary.copy(alpha = 0.5f)) }
        } else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = theme.accent,
            unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.3f),
            focusedContainerColor = theme.surface,
            unfocusedContainerColor = theme.surface
        )
    )
}

// Uses SectionLabel from MainActivity.kt
