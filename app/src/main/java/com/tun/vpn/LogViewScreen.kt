package com.tun.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewScreen(onBack: () -> Unit) {
    val theme = LocalTgTheme.current
    val allEntries by LogStore.entries.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf("friendly") } // "friendly" | "raw"
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedSource by remember { mutableStateOf<LogSource?>(null) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val displayEntries = remember(allEntries, mode, searchQuery, selectedLevel, selectedSource) {
        allEntries.filter { entry ->
            val modeOk = if (mode == "friendly") entry.isFriendly else !entry.isFriendly
            val searchOk = searchQuery.isBlank() || entry.message.contains(searchQuery, ignoreCase = true)
            val levelOk = selectedLevel == null || entry.level == selectedLevel
            val srcOk = selectedSource == null || entry.source == selectedSource
            modeOk && searchOk && levelOk && srcOk
        }
    }

    LaunchedEffect(displayEntries.size, autoScroll) {
        if (autoScroll && displayEntries.isNotEmpty()) {
            listState.animateScrollToItem(displayEntries.size - 1)
        }
    }

    val totalForMode = if (mode == "friendly")
        allEntries.count { it.isFriendly }
    else
        allEntries.count { !it.isFriendly }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val t = LocalStrings.current
        TopAppBar(
            title = { Text("${t.activity} ${displayEntries.size}/$totalForMode", color = theme.textPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.accent)
                }
            },
            actions = {
                IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(
                        Icons.Filled.VerticalAlignBottom,
                        "Auto-scroll",
                        tint = if (autoScroll) theme.accent else theme.textSecondary
                    )
                }
                IconButton(onClick = {
                    val text = displayEntries.joinToString("\n") { it.formatted() }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("logs", text))
                    Toast.makeText(context, t.copiedLines.format(displayEntries.size), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, "Copy", tint = theme.textSecondary)
                }
                IconButton(onClick = {
                    val text = displayEntries.joinToString("\n") { it.formatted() }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Logs"))
                }) {
                    Icon(Icons.Filled.Share, "Share", tint = theme.textSecondary)
                }
                IconButton(onClick = { LogStore.clear() }) {
                    Icon(Icons.Filled.Clear, "Clear", tint = theme.error)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // Friendly / Raw mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surface)
        ) {
            listOf("friendly" to t.friendly, "raw" to t.raw).forEach { (key, label) ->
                val active = mode == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) theme.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { mode = key }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) theme.accent else theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(t.searchLogs, color = theme.textSecondary.copy(alpha = 0.5f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = theme.accent,
                unfocusedBorderColor = theme.surfaceBorder,
                focusedContainerColor = theme.surface,
                unfocusedContainerColor = theme.surface,
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary
            )
        )

        Spacer(Modifier.height(8.dp))

        // Filter chips — only show in raw mode
        if (mode == "raw") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TgFilterChip(
                    selected = selectedSource == null,
                    onClick = { selectedSource = null },
                    label = t.logsAll,
                    theme = theme
                )
                LogSource.entries.forEach { source ->
                    val label = when (source) {
                        LogSource.APP -> t.logsApp
                        LogSource.PROXY -> t.logsTp
                        LogSource.WIREGUARD -> t.logsWg
                    }
                    TgFilterChip(
                        selected = selectedSource == source,
                        onClick = { selectedSource = if (selectedSource == source) null else source },
                        label = label,
                        theme = theme
                    )
                }
                Spacer(Modifier.width(12.dp))
                LogLevel.entries.forEach { level ->
                    TgFilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = level.label,
                        theme = theme
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Log list
        if (displayEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (allEntries.isEmpty()) t.noLogs else t.noMatchingLogs,
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            if (mode == "friendly") {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayEntries, key = { it.id }) { entry ->
                        FriendlyLogRow(entry, theme)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    items(displayEntries, key = { it.id }) { entry ->
                        RawLogRow(entry, theme)
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendlyLogRow(entry: LogEntry, theme: TgTheme) {
    val dotColor = when (entry.level) {
        LogLevel.ERROR -> theme.error
        LogLevel.WARNING -> theme.connecting
        LogLevel.INFO -> theme.accent
        LogLevel.DEBUG -> theme.textTertiary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dotColor)
        )
        Text(
            text = entry.message,
            color = theme.textPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RawLogRow(entry: LogEntry, theme: TgTheme) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> theme.error
        LogLevel.WARNING -> theme.connecting
        LogLevel.INFO -> theme.accent
        LogLevel.DEBUG -> theme.textTertiary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.level.label,
            color = levelColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = entry.source.tag,
            color = theme.textSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(22.dp)
        )
        Text(
            text = entry.message,
            color = theme.logText,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TgFilterChip(selected: Boolean, onClick: () -> Unit, label: String, theme: TgTheme) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = theme.accent.copy(alpha = 0.2f),
            selectedLabelColor = theme.accent,
            containerColor = theme.surface,
            labelColor = theme.textSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = theme.accent.copy(alpha = 0.4f),
            borderColor = theme.surfaceBorder
        )
    )
}
