package com.tun.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val AccentBlue = Color(0xFF3B82F6)
private val TextSecondary = Color(0xFF94A3B8)
private val CardDark = Color(0xFF1E293B)

/**
 * Экран просмотра логов с поиском, фильтрацией и экспортом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewScreen(
    onBack: () -> Unit
) {
    val allEntries by LogStore.entries.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedSource by remember { mutableStateOf<LogSource?>(null) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val filteredEntries = remember(allEntries, searchQuery, selectedLevel, selectedSource) {
        allEntries.filter { entry ->
            (searchQuery.isBlank() || entry.message.contains(searchQuery, ignoreCase = true)) &&
            (selectedLevel == null || entry.level == selectedLevel) &&
            (selectedSource == null || entry.source == selectedSource)
        }
    }

    // Автоскролл
    LaunchedEffect(filteredEntries.size, autoScroll) {
        if (autoScroll && filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text("Logs (${filteredEntries.size}/${allEntries.size})")
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AccentBlue)
                }
            },
            actions = {
                // Auto-scroll toggle
                IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(
                        Icons.Filled.VerticalAlignBottom,
                        "Auto-scroll",
                        tint = if (autoScroll) AccentBlue else TextSecondary
                    )
                }
                // Copy all
                IconButton(onClick = {
                    val text = filteredEntries.joinToString("\n") { it.formatted() }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("logs", text))
                    Toast.makeText(context, "Copied ${filteredEntries.size} lines", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, "Copy", tint = TextSecondary)
                }
                // Share
                IconButton(onClick = {
                    val text = filteredEntries.joinToString("\n") { it.formatted() }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Logs"))
                }) {
                    Icon(Icons.Filled.Share, "Share", tint = TextSecondary)
                }
                // Clear
                IconButton(onClick = { LogStore.clear() }) {
                    Icon(Icons.Filled.Clear, "Clear", tint = Color(0xFFEF4444))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search logs...", color = TextSecondary.copy(alpha = 0.5f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips — источники
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedSource == null,
                onClick = { selectedSource = null },
                label = { Text("All", fontSize = 12.sp) },
                colors = chipColors(selectedSource == null)
            )
            LogSource.entries.forEach { source ->
                FilterChip(
                    selected = selectedSource == source,
                    onClick = { selectedSource = if (selectedSource == source) null else source },
                    label = { Text(source.displayName, fontSize = 12.sp) },
                    colors = chipColors(selectedSource == source)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Filter chips — уровни
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = if (selectedLevel == level) null else level },
                    label = { Text(level.label, fontSize = 12.sp) },
                    colors = chipColors(selectedLevel == level)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log list
        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (allEntries.isEmpty()) "No logs yet" else "No matching logs",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(filteredEntries, key = { it.id }) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFEF4444)
        LogLevel.WARNING -> Color(0xFFF59E0B)
        LogLevel.INFO -> Color(0xFF3B82F6)
        LogLevel.DEBUG -> Color(0xFF6B7280)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Level indicator
        Text(
            text = entry.level.label,
            color = levelColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp)
        )

        // Source tag
        Text(
            text = entry.source.tag,
            color = TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(22.dp)
        )

        // Message
        Text(
            text = entry.message,
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun chipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
    selectedLabelColor = AccentBlue,
    containerColor = CardDark,
    labelColor = TextSecondary
)
