package com.tun.vpn

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class TgTheme(
    val key: String,
    val name: String,
    val themeSub: String,
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val accent: Color,
    val accent2: Color,
    val connected: Color,
    val connecting: Color,
    val error: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val logSurface: Color,
    val logText: Color,
)

val AuroraTheme = TgTheme(
    key = "aurora", name = "Aurora", themeSub = "Violet · magenta",
    bg0 = Color(0xFF0A0714), bg1 = Color(0xFF1A0F2E), bg2 = Color(0xFF120826),
    surface = Color(0xFF18102A), surfaceBorder = Color(0x14FFFFFF),
    accent = Color(0xFFB57BFF), accent2 = Color(0xFFFF6EC7),
    connected = Color(0xFF5EEAD4), connecting = Color(0xFFFFB547), error = Color(0xFFFF6B8A),
    textPrimary = Color(0xFFF5F0FF), textSecondary = Color(0xFF9B8FB8), textTertiary = Color(0xFF5E5478),
    logSurface = Color(0xFF0A0514), logText = Color(0xFFC8A8FF),
)

val BloomTheme = TgTheme(
    key = "bloom", name = "Bloom", themeSub = "Coral · lime",
    bg0 = Color(0xFF131513), bg1 = Color(0xFF1C1F1B), bg2 = Color(0xFF0F110F),
    surface = Color(0xFF191C19), surfaceBorder = Color(0x12FFFFFF),
    accent = Color(0xFFFF7A4D), accent2 = Color(0xFFD4FF4D),
    connected = Color(0xFFB5F24D), connecting = Color(0xFFFFB547), error = Color(0xFFFF5A6C),
    textPrimary = Color(0xFFF2F0E8), textSecondary = Color(0xFF9B9A8C), textTertiary = Color(0xFF5E5D52),
    logSurface = Color(0xFF0C0E0C), logText = Color(0xFFD4FF4D),
)

val PrismTheme = TgTheme(
    key = "prism", name = "Prism", themeSub = "Pink · electric",
    bg0 = Color(0xFF0B0B0F), bg1 = Color(0xFF15151C), bg2 = Color(0xFF08080C),
    surface = Color(0xFF121218), surfaceBorder = Color(0x17FFFFFF),
    accent = Color(0xFFFF3D8C), accent2 = Color(0xFFF4FF47),
    connected = Color(0xFF4DFFB3), connecting = Color(0xFFFFB547), error = Color(0xFFFF3D5C),
    textPrimary = Color(0xFFF5F5F7), textSecondary = Color(0xFF8B8B96), textTertiary = Color(0xFF4E4E58),
    logSurface = Color(0xFF07070B), logText = Color(0xFFF4FF47),
)

val TgThemes = listOf(AuroraTheme, BloomTheme, PrismTheme)

val LocalTgTheme = compositionLocalOf<TgTheme> { AuroraTheme }

class ThemeStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tg_theme", Context.MODE_PRIVATE)

    var themeKey: String
        get() = prefs.getString("key", "aurora") ?: "aurora"
        set(v) { prefs.edit().putString("key", v).apply() }
}

class LanguageStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tg_lang", Context.MODE_PRIVATE)

    var lang: String
        get() = prefs.getString("lang", defaultLang()) ?: defaultLang()
        set(v) { prefs.edit().putString("lang", v).apply() }

    private fun defaultLang(): String {
        val sys = java.util.Locale.getDefault().language
        return if (sys == "ru") "ru" else "en"
    }
}
