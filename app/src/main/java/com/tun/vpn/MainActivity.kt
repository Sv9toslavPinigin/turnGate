package com.tun.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

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

    /** Android 13+ требует runtime-разрешение на показ нотификаций (issue #2). */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore — откажут, просто нотификаций не будет */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLink = intent?.data?.toString()?.takeIf { it.startsWith("turnbridge://") }
        handleNotificationAction(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val context = LocalContext.current
            val themeStore = remember { ThemeStore(context) }
            val langStore = remember { LanguageStore(context) }
            var themeKey by remember { mutableStateOf(themeStore.themeKey) }
            var langKey by remember { mutableStateOf(langStore.lang) }
            val theme = TgThemes.find { it.key == themeKey } ?: AuroraTheme
            val strings = stringsFor(langKey)

            // Синхронизируем глобальный Strings.current для ViewModel.
            LaunchedEffect(langKey) { Strings.setLang(langKey) }

            CompositionLocalProvider(
                LocalTgTheme provides theme,
                LocalStrings provides strings,
            ) {
                TurnGateTheme(theme) {
                    val vm: MainViewModel = viewModel()
                    viewModelRef = vm

                    LaunchedEffect(pendingDeepLink) {
                        pendingDeepLink?.let {
                            vm.importConfig(it)
                            pendingDeepLink = null
                        }
                    }
                    LaunchedEffect(Unit) { LogStore.init(this@MainActivity) }

                    MainApp(
                        vm = vm,
                        themeKey = themeKey,
                        onThemeChange = { key ->
                            themeKey = key
                            themeStore.themeKey = key
                        },
                        langKey = langKey,
                        onLangChange = { key ->
                            langKey = key
                            langStore.lang = key
                        },
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data?.toString()
        if (uri != null && uri.startsWith("turnbridge://")) {
            viewModelRef?.importConfig(uri)
        }
        handleNotificationAction(intent)
    }

    /** Ловим intent.action == ACTION_DISCONNECT от кнопки в notification. */
    private fun handleNotificationAction(intent: Intent?) {
        if (intent?.action == TunVpnService.ACTION_DISCONNECT) {
            viewModelRef?.disconnect()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

// ===== Navigation =====

enum class Screen { HOME, SETTINGS, LOGS, ABOUT, PROFILE_EDIT, ROUTING, TRAFFIC_RULES }

// ===== Theme wrapper =====

@Composable
fun TurnGateTheme(tg: TgTheme, content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val typography = androidx.compose.material3.Typography(
        displayLarge = base.displayLarge.copy(fontFamily = FontDisplay),
        displayMedium = base.displayMedium.copy(fontFamily = FontDisplay),
        displaySmall = base.displaySmall.copy(fontFamily = FontDisplay),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontDisplay),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontDisplay),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontDisplay),
        titleLarge = base.titleLarge.copy(fontFamily = FontDisplay),
        titleMedium = base.titleMedium.copy(fontFamily = FontDisplay),
        titleSmall = base.titleSmall.copy(fontFamily = FontDisplay),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontBody),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontBody),
        bodySmall = base.bodySmall.copy(fontFamily = FontBody),
        labelLarge = base.labelLarge.copy(fontFamily = FontDisplay),
        labelMedium = base.labelMedium.copy(fontFamily = FontDisplay),
        labelSmall = base.labelSmall.copy(fontFamily = FontDisplay),
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = tg.accent,
            secondary = tg.accent2,
            background = tg.bg0,
            surface = tg.surface,
            onPrimary = Color.White,
            onBackground = tg.textPrimary,
            onSurface = tg.textPrimary,
            onSurfaceVariant = tg.textSecondary,
            error = tg.error
        ),
        typography = typography,
        content = content
    )
}

// ===== Main App =====

@Composable
fun MainApp(
    vm: MainViewModel = viewModel(),
    themeKey: String,
    onThemeChange: (String) -> Unit,
    langKey: String,
    onLangChange: (String) -> Unit,
    onConnect: (MainViewModel) -> Unit
) {
    val theme = LocalTgTheme.current
    val state by vm.state.collectAsStateWithLifecycle()
    val showCaptcha by vm.showCaptchaDialog.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(theme.bg1, theme.bg0, theme.bg2))
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
                onAboutClick = { currentScreen = Screen.ABOUT },
                onRoutingClick = { currentScreen = Screen.ROUTING },
                onTrafficRulesClick = { currentScreen = Screen.TRAFFIC_RULES },
                themeKey = themeKey,
                onThemeChange = onThemeChange,
                langKey = langKey,
                onLangChange = onLangChange
            )
            Screen.ROUTING -> AppRoutingScreen(onBack = { currentScreen = Screen.SETTINGS })
            Screen.TRAFFIC_RULES -> TrafficRulesScreen(onBack = { currentScreen = Screen.SETTINGS })
            Screen.LOGS -> LogViewScreen(onBack = { currentScreen = Screen.SETTINGS })
            Screen.ABOUT -> AboutScreen(onBack = { currentScreen = Screen.SETTINGS })
            Screen.PROFILE_EDIT -> {
                val profile = editingProfileId?.let { id -> state.profiles.find { it.id == id } }
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

        if (showCaptcha) {
            val captchaIdx by vm.captchaCount.collectAsStateWithLifecycle()
            val activeN = state.profiles.find { it.id == state.selectedProfileId }?.nValue ?: 1
            CaptchaWebViewDialog(
                captchaIndex = captchaIdx,
                totalEstimate = activeN,
                onDismiss = { vm.dismissCaptcha() },
                onCancel = { vm.disconnect() }
            )
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
    val theme = LocalTgTheme.current
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
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showImportModal = true }, enabled = !isConnected && !isBusy) {
                Icon(Icons.Filled.Add, "Import", tint = if (!isConnected && !isBusy) theme.accent else theme.textTertiary)
            }
            // TurnGate title with tagline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TurnGate",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontDisplay,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(colors = listOf(theme.accent, theme.accent2))
                    )
                )
                Text(
                    text = LocalStrings.current.tagline,
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontBody,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, "Settings", tint = theme.textSecondary)
            }
        }

        // Update banner
        update?.let { info ->
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = theme.connected.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(14.dp),
                color = theme.connected.copy(alpha = 0.12f),
                onClick = { vm.downloadUpdate() }
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val ts = LocalStrings.current
                        Text(ts.updateAvailable, color = theme.connected, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("v${info.versionName} · ${ts.updateTapToInstall}", color = theme.connected.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                    Text("↓", color = theme.connected, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Profile carousel
        if (state.profiles.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            ProfileCarousel(
                profiles = state.profiles,
                selectedId = state.selectedProfileId,
                enabled = !isConnected && !isBusy,
                onSelect = { vm.selectProfile(it) },
                onEdit = onEditProfile
            )
        }

        Spacer(Modifier.weight(1f))

        // Tunnel hero
        TunnelHero(status = state.status, sizeDp = 240.dp)

        Spacer(Modifier.height(16.dp))

        // Status text
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = theme.connecting
                )
                Spacer(Modifier.width(10.dp))
            }
            val t = LocalStrings.current
            val statusLine = when {
                state.statusText.isNotBlank() -> state.statusText
                state.status == ConnectionState.DISCONNECTED -> t.stDisconnected
                state.status == ConnectionState.CONNECTED -> t.stConnected
                state.status == ConnectionState.CONNECTING -> t.stConnecting
                state.status == ConnectionState.DISCONNECTING -> t.stDisconnecting
                state.status == ConnectionState.ERROR -> t.stError
                else -> t.stDisconnected
            }
            Text(
                text = statusLine,
                color = when (state.status) {
                    ConnectionState.CONNECTED -> theme.connected
                    ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> theme.connecting
                    ConnectionState.ERROR -> theme.error
                    else -> theme.textSecondary
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Proxy log panel (raw, small, during connecting)
        if (state.proxyLogs.isNotEmpty() && (isBusy || isConnected)) {
            Spacer(Modifier.height(12.dp))
            ProxyLogPanel(logs = state.proxyLogs, theme = theme)
        }

        Spacer(Modifier.weight(1f))

        // Manual captcha toggle — привязан к выбранному профилю.
        val activeProfile = state.profiles.find { it.id == state.selectedProfileId }
        if (activeProfile != null && !isConnected && !isBusy) {
            ManualCaptchaToggle(
                checked = activeProfile.manualCaptcha,
                onChange = { vm.updateProfile(activeProfile.copy(manualCaptcha = it)) }
            )
            Spacer(Modifier.height(12.dp))
        }

        // Connect button
        ConnectButton(
            status = state.status,
            enabled = state.profiles.isNotEmpty(),
            onClick = { if (isConnected) onDisconnect() else onConnect() }
        )

        Spacer(Modifier.height(40.dp))
    }

    if (showImportModal) {
        ImportModal(
            onPasteFromClipboard = {
                showImportModal = false
                val clip = clipboardManager.getText()?.text ?: ""
                if (clip.startsWith("turnbridge://")) vm.importConfig(clip)
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

// ===== Profile Carousel =====

@Composable
fun ProfileCarousel(
    profiles: List<VpnProfile>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit
) {
    val theme = LocalTgTheme.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(profiles, key = { it.id }) { profile ->
            val isSelected = profile.id == selectedId
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) theme.accent.copy(alpha = 0.12f) else theme.surface
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) theme.accent.copy(alpha = 0.7f) else theme.surfaceBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .then(
                        if (enabled) Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSelect(profile.id) } else Modifier
                    )
                    .padding(12.dp)
            ) {
                Column {
                    // First letter avatar
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) theme.accent.copy(alpha = 0.2f)
                                else theme.surfaceBorder
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            color = if (isSelected) theme.accent else theme.textSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = profile.name,
                        color = if (isSelected) theme.textPrimary else theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Edit",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { if (enabled) onEdit(profile.id) }
                    )
                }
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
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.addTitle, color = theme.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPasteFromClipboard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) { Text(t.pasteClip, color = Color.White) }
                OutlinedButton(
                    onClick = onAddManually,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(t.addManual, color = theme.textPrimary) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel, color = theme.textSecondary) } },
        containerColor = theme.bg1
    )
}

// ===== Global Settings Screen =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    onLogsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onRoutingClick: () -> Unit,
    onTrafficRulesClick: () -> Unit,
    themeKey: String,
    onThemeChange: (String) -> Unit,
    langKey: String,
    onLangChange: (String) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    val update by vm.updateInfo.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text(t.settings, color = theme.textPrimary) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SectionLabel(t.appearance)
            ThemePicker(themeKey = themeKey, onThemeChange = onThemeChange)

            Spacer(Modifier.height(24.dp))
            SectionLabel(t.language)
            LanguagePicker(langKey = langKey, onLangChange = onLangChange)

            Spacer(Modifier.height(24.dp))
            SectionLabel(t.navigation)

            SettingsNavRow(
                icon = { Icon(Icons.Filled.ArrowDropDown, null, tint = theme.accent, modifier = Modifier.size(20.dp)) },
                title = t.perApp,
                subtitle = t.perAppSub,
                onClick = onRoutingClick
            )
            Spacer(Modifier.height(8.dp))
            SettingsNavRow(
                icon = { Icon(Icons.Filled.ArrowDropDown, null, tint = theme.accent, modifier = Modifier.size(20.dp)) },
                title = t.trafficRules,
                subtitle = t.trafficRulesSub,
                onClick = onTrafficRulesClick
            )
            Spacer(Modifier.height(8.dp))
            SettingsNavRow(
                icon = { Icon(Icons.Filled.ArrowDropDown, null, tint = theme.accent, modifier = Modifier.size(20.dp)) },
                title = t.viewLogs,
                subtitle = t.viewLogsSub,
                onClick = onLogsClick
            )
            Spacer(Modifier.height(8.dp))
            SettingsNavRow(
                icon = { Icon(Icons.Filled.Settings, null, tint = theme.accent, modifier = Modifier.size(20.dp)) },
                title = t.about,
                subtitle = t.aboutSub,
                onClick = onAboutClick
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel(t.updatesSection)
            UpdateBlock(vm = vm)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun UpdateBlock(vm: MainViewModel) {
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    val update by vm.updateInfo.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var lastCheckOk by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Manual check row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(theme.surface)
                .border(1.dp, theme.surfaceBorder, RoundedCornerShape(14.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (!checking) {
                        checking = true
                        lastCheckOk = false
                        vm.viewModelScope.launch(Dispatchers.IO) {
                            val result = UpdateChecker.check()
                            vm.setUpdateInfo(result)
                            checking = false
                            lastCheckOk = result == null
                            if (result == null) {
                                android.widget.Toast.makeText(ctx, t.upToDate, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = theme.accent
                    )
                } else {
                    Text("↻", color = theme.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(t.checkForUpdates, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "v${BuildConfig.VERSION_NAME} · " + t.checkForUpdatesSub,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontMono
                )
            }
        }

        // Install prompt if available
        update?.let { info ->
            Button(
                onClick = { vm.downloadUpdate() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.connected)
            ) {
                Text(
                    "${t.downloadUpdate} v${info.versionName}",
                    color = Color.Black,
                    fontFamily = FontDisplay,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun LanguagePicker(langKey: String, onLangChange: (String) -> Unit) {
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("en" to t.langEn, "ru" to t.langRu).forEach { (code, label) ->
            val active = langKey == code
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) theme.accent.copy(alpha = 0.18f) else theme.surface)
                    .border(1.dp, if (active) theme.accent.copy(alpha = 0.6f) else theme.surfaceBorder, RoundedCornerShape(14.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onLangChange(code) }
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) theme.accent else theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SettingsNavRow(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    val theme = LocalTgTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.surface)
            .border(1.dp, theme.surfaceBorder, RoundedCornerShape(14.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = theme.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ArrowDropDown, null, tint = theme.textTertiary,
             modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = -90f })
    }
}

// ===== Theme Picker =====

@Composable
fun ThemePicker(themeKey: String, onThemeChange: (String) -> Unit) {
    val currentTheme = LocalTgTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TgThemes.forEach { th ->
            val active = themeKey == th.key
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (active) Brush.linearGradient(listOf(th.accent.copy(alpha = 0.12f), th.accent2.copy(alpha = 0.08f)))
                        else Brush.linearGradient(listOf(currentTheme.surface, currentTheme.surface))
                    )
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) th.accent.copy(alpha = 0.7f) else currentTheme.surfaceBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onThemeChange(th.key) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mini preview tile
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(th.bg0)
                        .border(1.dp, th.surfaceBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(54.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        listOf(22f, 15f, 7f).forEachIndexed { i, r ->
                            drawCircle(color = th.accent.copy(alpha = 0.5f - i * 0.1f), radius = r, center = Offset(cx, cy), style = Stroke(1.5f))
                        }
                        drawCircle(
                            brush = Brush.radialGradient(listOf(th.accent, th.accent2), center = Offset(cx, cy), radius = 7f),
                            radius = 7f, center = Offset(cx, cy)
                        )
                    }
                }
                // Name and subtitle
                Column(Modifier.weight(1f)) {
                    Text(th.name, color = if (active) th.accent else currentTheme.textPrimary,
                         fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(th.themeSub, color = currentTheme.textSecondary, fontSize = 11.sp)
                    // Color swatches
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(th.accent, th.accent2, th.connected).forEach { c ->
                            Box(Modifier.size(width = 16.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                        }
                    }
                }
                // Radio indicator
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (active) th.accent else Color.Transparent)
                        .border(1.5.dp, if (active) th.accent else currentTheme.surfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (active) {
                        Canvas(Modifier.size(12.dp)) {
                            val s = size.width
                            drawLine(Color.Black, Offset(s * 0.2f, s * 0.55f), Offset(s * 0.42f, s * 0.78f), 2f, StrokeCap.Round)
                            drawLine(Color.Black, Offset(s * 0.42f, s * 0.78f), Offset(s * 0.8f, s * 0.28f), 2f, StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}

// ===== Proxy Log Panel =====

@Composable
fun ProxyLogPanel(logs: List<String>, theme: TgTheme) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.logSurface)
            .padding(8.dp)
    ) {
        LazyColumn(state = listState) {
            items(logs) { line ->
                Text(line, color = theme.logText, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 14.sp)
            }
        }
    }
}

// ===== TG Monogram (Canvas) =====

@Composable
fun TGMonogram(sizeDp: Dp) {
    val theme = LocalTgTheme.current
    Canvas(modifier = Modifier.size(sizeDp)) {
        val s = size.width
        val strokeW = s * 0.104f
        val cx = s / 2f
        val cy = s / 2f
        val gRadius = s * 0.333f
        // G arc — nearly full circle from east, 306° clockwise
        drawArc(
            color = theme.accent,
            startAngle = 0f,
            sweepAngle = 306f,
            useCenter = false,
            topLeft = Offset(cx - gRadius, cy - gRadius),
            size = Size(gRadius * 2, gRadius * 2),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        // G bar — horizontal line from east toward center
        drawLine(
            color = theme.accent,
            start = Offset(cx + gRadius, cy),
            end = Offset(cx + gRadius * 0.375f, cy),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        // T crossbar
        drawLine(
            color = theme.textPrimary.copy(alpha = 0.92f),
            start = Offset(s * 0.292f, s * 0.292f),
            end = Offset(s * 0.708f, s * 0.292f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        // T stem
        drawLine(
            color = theme.textPrimary.copy(alpha = 0.92f),
            start = Offset(cx, s * 0.292f),
            end = Offset(cx, s * 0.708f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

// ===== Tunnel Hero =====

@Composable
fun TunnelHero(status: ConnectionState, sizeDp: Dp = 240.dp) {
    val theme = LocalTgTheme.current
    val isConnected = status == ConnectionState.CONNECTED
    val isConnecting = status == ConnectionState.CONNECTING || status == ConnectionState.DISCONNECTING
    val isError = status == ConnectionState.ERROR

    val coreColor by animateColorAsState(
        targetValue = when {
            isError -> theme.error
            isConnected -> theme.connected
            isConnecting -> theme.connecting
            else -> theme.textTertiary
        },
        animationSpec = tween(500), label = "core"
    )
    val ringColor by animateColorAsState(
        targetValue = when {
            isConnected -> theme.connected
            isError -> theme.error
            isConnecting -> theme.connecting
            else -> Color.White
        },
        animationSpec = tween(500), label = "ring"
    )
    val ringAlpha = when {
        isConnected -> 0.85f; isConnecting -> 0.7f; isError -> 0.7f; else -> 0.35f
    }
    val glowAlpha by animateFloatAsState(
        targetValue = if (isConnected || isConnecting || isError) 1f else 0f,
        animationSpec = tween(600), label = "glow"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "tunnel")
    val pulseScale by infiniteTransition.animateFloat(1f, 1.08f,
        infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse), "pulse")
    val breatheScale by infiniteTransition.animateFloat(1f, 1.025f,
        infiniteRepeatable(tween(3500, easing = EaseInOut), RepeatMode.Reverse), "breathe")
    val spinAngle by infiniteTransition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)), "spin")
    val orbitA by infiniteTransition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(5000, easing = LinearEasing)), "oA")
    val orbitB by infiniteTransition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(6700, easing = LinearEasing)), "oB")
    val pAlpha by infiniteTransition.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse), "pa")

    val ringScale = when {
        isConnecting -> pulseScale; isConnected -> breatheScale; else -> 1f
    }
    val discSize = sizeDp * 0.38f

    Box(modifier = Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        // Radial glow backdrop
        if (glowAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(sizeDp * 0.7f)
                    .blur(32.dp)
                    .clip(CircleShape)
                    .background(coreColor.copy(alpha = 0.28f * glowAlpha))
            )
        }

        // Concentric rings
        Canvas(modifier = Modifier.size(sizeDp)) {
            listOf(0.5f, 0.72f, 0.92f).forEachIndexed { i, fraction ->
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha * (1f - i * 0.2f)),
                    radius = size.width / 2f * fraction * ringScale,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Orbiting particles (when connecting or connected)
        if (isConnected || isConnecting) {
            val particleColor = if (isConnected) theme.connected else theme.connecting
            // Track 1 — inner
            Canvas(modifier = Modifier.size(sizeDp)) {
                val r = size.width / 2f * 0.5f
                for (i in 0..2) {
                    val rad = Math.toRadians((orbitA + i * 120.0))
                    val cx = center.x + r * cos(rad).toFloat()
                    val cy = center.y + r * sin(rad).toFloat()
                    drawCircle(color = particleColor.copy(alpha = pAlpha * (1f - i * 0.15f)), radius = 3f, center = Offset(cx, cy))
                }
            }
            // Track 2 — mid, reverse
            Canvas(modifier = Modifier.size(sizeDp)) {
                val r = size.width / 2f * 0.72f
                for (i in 0..2) {
                    val rad = Math.toRadians((-orbitB + i * 120.0))
                    val cx = center.x + r * cos(rad).toFloat()
                    val cy = center.y + r * sin(rad).toFloat()
                    drawCircle(color = particleColor.copy(alpha = pAlpha * 0.7f * (1f - i * 0.15f)), radius = 2.5f, center = Offset(cx, cy))
                }
            }
        }

        // Center glass disc
        Box(
            modifier = Modifier
                .size(discSize)
                .shadow(
                    elevation = if (isConnected) 20.dp else if (isError) 14.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = coreColor.copy(alpha = 0.5f),
                    spotColor = coreColor.copy(alpha = 0.5f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isConnected)
                            listOf(theme.connected.copy(alpha = 0.18f), theme.accent.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.45f))
                        else
                            listOf(theme.accent.copy(alpha = 0.13f), Color.Black.copy(alpha = 0.45f))
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isConnected) theme.connected.copy(alpha = 0.5f) else theme.surfaceBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            TGMonogram(sizeDp = discSize * 0.58f)
        }

        // Spinning arc (connecting) / full ring (connected)
        if (isConnecting || isConnected) {
            val arcSize = discSize * 1.15f
            Canvas(
                modifier = Modifier
                    .size(arcSize)
                    .then(if (isConnecting) Modifier.graphicsLayer { rotationZ = spinAngle } else Modifier)
            ) {
                drawArc(
                    color = coreColor.copy(alpha = if (isConnected) 0.85f else 0.9f),
                    startAngle = 0f,
                    sweepAngle = if (isConnected) 360f else 180f,
                    useCenter = false,
                    style = Stroke(
                        width = if (isConnected) 2.dp.toPx() else 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

// ===== Connect Button =====

@Composable
fun ConnectButton(status: ConnectionState, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalTgTheme.current
    val isConnected = status == ConnectionState.CONNECTED
    val isConnecting = status == ConnectionState.CONNECTING
    val isBusy = isConnecting || status == ConnectionState.DISCONNECTING

    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> theme.error
            isBusy -> theme.connecting
            else -> theme.accent
        },
        animationSpec = tween(400), label = "btn"
    )

    Button(
        onClick = onClick,
        enabled = !isBusy && enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = buttonColor.copy(alpha = 0.3f), spotColor = buttonColor.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = buttonColor.copy(alpha = 0.6f)
        )
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(12.dp))
        }
        val tt = LocalStrings.current
        Text(
            text = when {
                status == ConnectionState.ERROR -> tt.btnRetry
                isConnecting -> tt.btnConnecting
                isConnected -> tt.btnDisconnect
                else -> tt.btnConnect
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

// ===== Manual Captcha Toggle (above Connect) =====

@Composable
fun ManualCaptchaToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) theme.accent.copy(alpha = 0.12f) else theme.surface)
            .border(
                1.dp,
                if (checked) theme.accent.copy(alpha = 0.5f) else theme.surfaceBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = t.manualCaptchaToggle,
            color = if (checked) theme.accent else theme.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontBody,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.accent,
                checkedTrackColor = theme.accent.copy(alpha = 0.35f),
                uncheckedThumbColor = theme.textSecondary,
                uncheckedTrackColor = theme.surface,
                uncheckedBorderColor = theme.surfaceBorder
            )
        )
    }
}

// ===== Section Label =====

@Composable
fun SectionLabel(text: String) {
    val theme = LocalTgTheme.current
    Text(
        text = text.uppercase(),
        color = theme.textTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
    )
}
