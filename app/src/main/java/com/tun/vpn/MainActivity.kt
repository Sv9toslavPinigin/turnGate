package com.tun.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private var connectPending = false
    private var viewModelRef: MainViewModel? = null
    private var pendingDeepLink: String? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (VpnService.prepare(this) == null && connectPending) {
            connectPending = false
            viewModelRef?.connect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Deep link из intent
        pendingDeepLink = intent?.data?.toString()?.takeIf { it.startsWith("turnbridge://") }

        setContent {
            TurnGateTheme {
                val vm: MainViewModel = viewModel()
                viewModelRef = vm

                // Обработка deep link
                LaunchedEffect(pendingDeepLink) {
                    pendingDeepLink?.let {
                        vm.importConfig(it)
                        pendingDeepLink = null
                    }
                }

                // Инициализируем LogStore
                LaunchedEffect(Unit) {
                    LogStore.init(this@MainActivity)
                }

                MainApp(
                    vm = vm,
                    onConnect = { vmRef ->
                        viewModelRef = vmRef
                        val vpnIntent = VpnService.prepare(this)
                        if (vpnIntent != null) {
                            connectPending = true
                            vpnPermissionLauncher.launch(vpnIntent)
                        } else {
                            vmRef.connect()
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data?.toString()
        if (uri != null && uri.startsWith("turnbridge://")) {
            viewModelRef?.importConfig(uri)
        }
    }
}

// ===== Navigation =====

enum class Screen {
    HOME, SETTINGS, LOGS, ABOUT, PROFILE_EDIT
}

// ===== Theme =====

private val DarkBlue = Color(0xFF0D1B2A)
private val DeepBlue = Color(0xFF1B2838)
private val CardDark = Color(0xFF1E293B)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentCyan = Color(0xFF06B6D4)
private val AccentGreen = Color(0xFF10B981)
private val AccentRed = Color(0xFFEF4444)
private val AccentOrange = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun TurnGateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentBlue,
            secondary = AccentCyan,
            background = DarkBlue,
            surface = CardDark,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary,
            error = AccentRed
        ),
        content = content
    )
}

// ===== Main App with Navigation =====

@Composable
fun MainApp(
    vm: MainViewModel = viewModel(),
    onConnect: (MainViewModel) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val showCaptcha by vm.showCaptchaDialog.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBlue, DeepBlue, Color(0xFF0F172A))
                )
            )
    ) {
        when (currentScreen) {
            Screen.HOME -> HomeScreen(
                state = state,
                vm = vm,
                onConnect = { onConnect(vm) },
                onDisconnect = { vm.disconnect() },
                onSettingsClick = { currentScreen = Screen.SETTINGS },
                onEditProfile = { id ->
                    editingProfileId = id
                    currentScreen = Screen.PROFILE_EDIT
                }
            )
            Screen.SETTINGS -> GlobalSettingsScreen(
                onBack = { currentScreen = Screen.HOME },
                onLogsClick = { currentScreen = Screen.LOGS },
                onAboutClick = { currentScreen = Screen.ABOUT }
            )
            Screen.LOGS -> LogViewScreen(
                onBack = { currentScreen = Screen.SETTINGS }
            )
            Screen.ABOUT -> AboutScreen(
                onBack = { currentScreen = Screen.SETTINGS }
            )
            Screen.PROFILE_EDIT -> {
                val profile = editingProfileId?.let { id ->
                    state.profiles.find { it.id == id }
                }
                if (profile != null) {
                    ProfileEditScreen(
                        profile = profile,
                        onSave = { vm.updateProfile(it) },
                        onDelete = { id ->
                            vm.deleteProfile(id)
                            currentScreen = Screen.HOME
                        },
                        onBack = { currentScreen = Screen.HOME }
                    )
                } else {
                    currentScreen = Screen.HOME
                }
            }
        }

        // Captcha WebView overlay
        if (showCaptcha) {
            CaptchaWebViewDialog(onDismiss = { vm.dismissCaptcha() })
        }
    }
}

// ===== Home Screen =====

@Composable
fun HomeScreen(
    state: UiState,
    vm: MainViewModel,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditProfile: (String) -> Unit
) {
    val update by vm.updateInfo.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val isConnected = state.status == ConnectionState.CONNECTED
    val isConnecting = state.status == ConnectionState.CONNECTING
    val isDisconnecting = state.status == ConnectionState.DISCONNECTING
    val isBusy = isConnecting || isDisconnecting
    var showImportModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Import button
            IconButton(
                onClick = { showImportModal = true },
                enabled = !isConnected && !isBusy
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Import", tint = TextSecondary)
            }

            // Settings
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
        }

        // Title
        Text(
            text = "TurnGate",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.headlineLarge.copy(
                brush = Brush.linearGradient(colors = listOf(AccentBlue, AccentCyan))
            )
        )

        // Update banner
        update?.let { info ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AccentGreen.copy(alpha = 0.12f),
                onClick = { vm.downloadUpdate() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Update v${info.versionName} available",
                        color = AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Download",
                        color = AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Profile picker
        if (state.profiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            ProfilePicker(
                profiles = state.profiles,
                selectedId = state.selectedProfileId,
                enabled = !isConnected && !isBusy,
                onSelect = { vm.selectProfile(it) },
                onEdit = onEditProfile
            )

            // Manual captcha toggle
            val selectedProfile = state.profiles.find { it.id == state.selectedProfileId }
            if (selectedProfile != null && !isConnected && !isBusy) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedProfile.manualCaptcha,
                        onCheckedChange = {
                            vm.updateProfile(selectedProfile.copy(manualCaptcha = it))
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentBlue,
                            uncheckedColor = TextSecondary
                        )
                    )
                    Text(
                        text = "Manual captcha",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Shield icon
        ShieldIcon(state.status)

        Spacer(modifier = Modifier.height(16.dp))

        // Status text
        Text(
            text = state.statusText,
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        // Proxy logs panel
        if (state.proxyLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            ProxyLogPanel(state.proxyLogs)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Connect button
        ConnectButton(
            isConnected = isConnected,
            isConnecting = isConnecting,
            isBusy = isBusy,
            enabled = state.profiles.isNotEmpty(),
            onClick = {
                if (isConnected) onDisconnect() else onConnect()
            }
        )

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Import modal
    if (showImportModal) {
        ImportModal(
            onPasteFromClipboard = {
                showImportModal = false
                val clip = clipboardManager.getText()?.text ?: ""
                if (clip.startsWith("turnbridge://")) {
                    vm.importConfig(clip)
                }
            },
            onAddManually = {
                showImportModal = false
                val profile = vm.addEmptyProfile()
                onEditProfile(profile.id)
            },
            onDismiss = { showImportModal = false }
        )
    }
}

// ===== Profile Picker =====

@Composable
fun ProfilePicker(
    profiles: List<VpnProfile>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.find { it.id == selectedId } ?: profiles.firstOrNull()

    Box {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            Text(
                text = selected?.name ?: "No profiles",
                fontSize = 14.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            profile.name,
                            fontWeight = if (profile.id == selectedId) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(profile.id)
                        expanded = false
                    },
                    trailingIcon = {
                        TextButton(onClick = {
                            expanded = false
                            onEdit(profile.id)
                        }) {
                            Text("Edit", fontSize = 12.sp, color = AccentBlue)
                        }
                    }
                )
            }
        }
    }
}

// ===== Import Modal =====

@Composable
fun ImportModal(
    onPasteFromClipboard: () -> Unit,
    onAddManually: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPasteFromClipboard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Paste from Clipboard")
                }
                OutlinedButton(
                    onClick = onAddManually,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add Manually")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ===== Global Settings Screen =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    onLogsClick: () -> Unit,
    onAboutClick: () -> Unit,
    vm: MainViewModel = viewModel()
) {
    val update by vm.updateInfo.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AccentBlue
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel("Navigation")

            OutlinedButton(
                onClick = onLogsClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Logs")
            }

            OutlinedButton(
                onClick = onAboutClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("About")
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Updates")

            if (update != null) {
                Button(
                    onClick = { vm.downloadUpdate() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Download v${update!!.versionName}")
                }
            } else {
                OutlinedButton(
                    onClick = { vm.checkForUpdates() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Check for Updates")
                }
            }
        }
    }
}

// ===== Proxy Log Panel =====

@Composable
fun ProxyLogPanel(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A0F1A))
            .padding(8.dp)
    ) {
        LazyColumn(state = listState) {
            items(logs) { line ->
                Text(
                    text = line,
                    color = Color(0xFF7DD3FC),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ===== Shield Icon with glow =====

@Composable
fun ShieldIcon(status: ConnectionState) {
    val iconColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionState.CONNECTED -> AccentGreen
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> AccentOrange
            ConnectionState.ERROR -> AccentRed
            ConnectionState.DISCONNECTED -> TextSecondary
        },
        animationSpec = tween(500),
        label = "iconColor"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (status == ConnectionState.CONNECTED) 0.4f else 0f,
        animationSpec = tween(800),
        label = "glowAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val scale = if (status == ConnectionState.CONNECTING) pulseScale else 1f

    Box(contentAlignment = Alignment.Center) {
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .blur(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = glowAlpha))
            )
        }
        Icon(
            Icons.Filled.Shield,
            contentDescription = "VPN Status",
            tint = iconColor,
            modifier = Modifier.size((120 * scale).dp)
        )
    }
}

// ===== Connect Button =====

@Composable
fun ConnectButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    isBusy: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> AccentRed
            isConnecting -> AccentOrange
            else -> AccentBlue
        },
        animationSpec = tween(400),
        label = "buttonColor"
    )

    Button(
        onClick = onClick,
        enabled = !isBusy && enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = buttonColor.copy(alpha = 0.3f),
                spotColor = buttonColor.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = buttonColor.copy(alpha = 0.6f)
        )
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = when {
                isConnecting -> "Connecting..."
                isConnected -> "Disconnect"
                else -> "Connect"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ===== Section Label =====

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}
