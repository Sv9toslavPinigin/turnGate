package com.tun.vpn

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoutingScreen(onBack: () -> Unit) {
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    val ctx = LocalContext.current
    val store = remember { RoutingStore(ctx) }

    var mode by remember { mutableStateOf(store.mode) }
    var selected by remember { mutableStateOf(store.appList) }
    var showSystem by remember { mutableStateOf(store.showSystemApps) }
    var search by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppListStore.load(ctx) }
    }

    fun persist() {
        store.mode = mode
        store.appList = selected
        store.showSystemApps = showSystem
    }

    val filtered = remember(apps, search, showSystem) {
        val list = apps ?: return@remember emptyList()
        list.filter { app ->
            (showSystem || !app.isSystem) &&
                (search.isBlank() || app.label.contains(search, ignoreCase = true) ||
                    app.packageName.contains(search, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text(t.perApp, color = theme.textPrimary) },
            navigationIcon = {
                IconButton(onClick = {
                    persist()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.accent)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mode options
            RoutingModeRow(
                active = mode == RoutingMode.EVERYTHING,
                accent = theme.accent,
                surface = theme.surface,
                border = theme.surfaceBorder,
                textPrimary = theme.textPrimary,
                textSecondary = theme.textSecondary,
                title = t.routeEverything,
                subtitle = t.routeEverythingSub,
                onClick = { mode = RoutingMode.EVERYTHING; persist() }
            )
            RoutingModeRow(
                active = mode == RoutingMode.WHITELIST,
                accent = theme.accent,
                surface = theme.surface,
                border = theme.surfaceBorder,
                textPrimary = theme.textPrimary,
                textSecondary = theme.textSecondary,
                title = t.routeWhitelist,
                subtitle = t.routeWhitelistSub,
                onClick = { mode = RoutingMode.WHITELIST; persist() }
            )
            RoutingModeRow(
                active = mode == RoutingMode.BLACKLIST,
                accent = theme.accent,
                surface = theme.surface,
                border = theme.surfaceBorder,
                textPrimary = theme.textPrimary,
                textSecondary = theme.textSecondary,
                title = t.routeBlacklist,
                subtitle = t.routeBlacklistSub,
                onClick = { mode = RoutingMode.BLACKLIST; persist() }
            )
        }

        val needsAppList = mode != RoutingMode.EVERYTHING
        if (needsAppList) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.routeSelectedCount.format(selected.size),
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontDisplay,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected.isNotEmpty()) {
                        Text(
                            t.routeClearSelection,
                            color = theme.accent,
                            fontSize = 12.sp,
                            fontFamily = FontDisplay,
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                selected = emptySet()
                                persist()
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(t.routeSearchApps, color = theme.textSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.surfaceBorder,
                        focusedContainerColor = theme.surface,
                        unfocusedContainerColor = theme.surface,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.routeShowSystem,
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontBody,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showSystem,
                        onCheckedChange = { showSystem = it; persist() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.accent,
                            checkedTrackColor = theme.accent.copy(alpha = 0.35f),
                        )
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            theme = theme,
                            systemLabel = t.routeSystemAppBadge,
                            onToggle = {
                                selected = if (app.packageName in selected) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                                persist()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutingModeRow(
    active: Boolean,
    accent: Color,
    surface: Color,
    border: Color,
    textPrimary: Color,
    textSecondary: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) accent.copy(alpha = 0.14f) else surface)
            .border(
                1.dp,
                if (active) accent.copy(alpha = 0.6f) else border,
                RoundedCornerShape(14.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (active) accent else Color.Transparent)
                .border(1.5.dp, if (active) accent else border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                Text("✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (active) accent else textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontDisplay
            )
            Text(subtitle, color = textSecondary, fontSize = 11.sp, fontFamily = FontBody)
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    theme: TgTheme,
    systemLabel: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .border(1.dp, theme.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                color = theme.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontBody,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.packageName,
                    color = theme.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontMono,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystem) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        systemLabel,
                        color = theme.textTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontDisplay,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.surfaceBorder)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = theme.accent,
                uncheckedColor = theme.textSecondary,
                checkmarkColor = Color.Black,
            )
        )
    }
}

@Composable
private fun AppIcon(drawable: Drawable?) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageDrawable(drawable)
                    }
                },
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            )
        }
    }
}
