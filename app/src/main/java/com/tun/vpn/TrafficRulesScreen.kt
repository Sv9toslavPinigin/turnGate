package com.tun.vpn

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficRulesScreen(onBack: () -> Unit) {
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    val ctx = LocalContext.current
    val store = remember { RoutingStore(ctx) }

    var tab by remember { mutableStateOf(0) } // 0 = route through, 1 = bypass
    var throughList by remember { mutableStateOf(store.routeThroughVpn) }
    var bypassList by remember { mutableStateOf(store.bypassVpn) }
    var draft by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    fun persist() {
        store.routeThroughVpn = throughList
        store.bypassVpn = bypassList
    }

    val activeList = if (tab == 0) throughList else bypassList
    fun setActiveList(new: List<String>) {
        if (tab == 0) throughList = new else bypassList = new
        persist()
    }

    fun addRule() {
        val parsed = RouteRule.parse(draft)
        if (!parsed.isValid) {
            invalid = true
            return
        }
        val v = parsed.displayValue
        if (v !in activeList) setActiveList(activeList + v)
        draft = ""
        invalid = false
    }

    fun addPreset(preset: RoutingPreset) {
        val merged = (activeList + preset.rules).distinct()
        setActiveList(merged)
        val label = when (preset.key) {
            "ru" -> t.presetRu
            "google" -> t.presetGoogle
            "lan" -> t.presetLan
            else -> preset.key
        }
        Toast.makeText(ctx, t.addedPresetToast.format(label), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text(t.trafficRules, color = theme.textPrimary) },
            navigationIcon = {
                IconButton(onClick = { persist(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.accent)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // Tab switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surface)
        ) {
            listOf(0 to t.tabRouteThrough, 1 to t.tabBypass).forEach { (idx, label) ->
                val active = tab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) theme.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            tab = idx
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (active) theme.accent else theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = FontDisplay
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Input row
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; invalid = false },
                placeholder = { Text(t.addRulePlaceholder, color = theme.textSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = invalid,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.surfaceBorder,
                    focusedContainerColor = theme.surface,
                    unfocusedContainerColor = theme.surface,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary,
                    errorBorderColor = theme.error,
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { addRule() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(t.addBtn, color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
        if (invalid) {
            Text(
                t.ruleInvalid,
                color = theme.error,
                fontSize = 11.sp,
                fontFamily = FontBody,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Preset chips
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                t.presetsLabel.uppercase(),
                color = theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontDisplay,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoutingPresets.ALL.forEach { preset ->
                    val label = when (preset.key) {
                        "ru" -> t.presetRu
                        "google" -> t.presetGoogle
                        "lan" -> t.presetLan
                        else -> preset.key
                    }
                    OutlinedButton(
                        onClick = { addPreset(preset) },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("+ $label", color = theme.textPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Rules list
        if (activeList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(t.noRulesYet, color = theme.textSecondary, fontFamily = FontBody)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(activeList, key = { it }) { rule ->
                    RuleRow(
                        rule = rule,
                        theme = theme,
                        onRemove = {
                            setActiveList(activeList.filter { it != rule })
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: String, theme: TgTheme, onRemove: () -> Unit) {
    val parsed = remember(rule) { RouteRule.parse(rule) }
    val isIp = parsed is RouteRule.Cidr
    val icon = if (isIp) "⌁" else "⌘"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .border(1.dp, theme.surfaceBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = theme.accent, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            rule,
            color = theme.textPrimary,
            fontSize = 13.sp,
            fontFamily = FontMono,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Clear, "Remove", tint = theme.textTertiary)
        }
    }
}
