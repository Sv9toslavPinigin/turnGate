package com.tun.vpn

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Глобальные (не привязанные к профилю) правила маршрутизации.
 *
 * Phase 1: per-app whitelist / blacklist.
 * Phase 2 (future): списки IP/CIDR/доменов для routeThroughVpn / bypassVpn.
 */
enum class RoutingMode { EVERYTHING, WHITELIST, BLACKLIST }

class RoutingStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tg_routing", Context.MODE_PRIVATE)

    var mode: RoutingMode
        get() = runCatching { RoutingMode.valueOf(prefs.getString(KEY_MODE, RoutingMode.EVERYTHING.name)!!) }
            .getOrDefault(RoutingMode.EVERYTHING)
        set(v) { prefs.edit().putString(KEY_MODE, v.name).apply() }

    /** Пакеты, к которым применяется текущий [mode] (whitelist или blacklist). */
    var appList: Set<String>
        get() {
            val raw = prefs.getString(KEY_APP_LIST, "[]") ?: "[]"
            val arr = JSONArray(raw)
            return (0 until arr.length()).map { arr.getString(it) }.toSet()
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(it) }
            prefs.edit().putString(KEY_APP_LIST, arr.toString()).apply()
        }

    var showSystemApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM, false)
        set(v) { prefs.edit().putBoolean(KEY_SHOW_SYSTEM, v).apply() }

    fun togglePackage(pkg: String) {
        val cur = appList.toMutableSet()
        if (pkg in cur) cur.remove(pkg) else cur.add(pkg)
        appList = cur
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_APP_LIST = "app_list"
        private const val KEY_SHOW_SYSTEM = "show_system_apps"
    }
}
