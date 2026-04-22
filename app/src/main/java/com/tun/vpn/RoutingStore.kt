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

    /** Правила, которые прогоняются через VPN (CIDR, IP, домен, `*.wildcard`). */
    var routeThroughVpn: List<String>
        get() = readList(KEY_ROUTE_VPN)
        set(v) = writeList(KEY_ROUTE_VPN, v)

    /** Правила, которые идут в обход VPN (LAN по умолчанию). */
    var bypassVpn: List<String>
        get() = readList(KEY_BYPASS_VPN)
        set(v) = writeList(KEY_BYPASS_VPN, v)

    fun togglePackage(pkg: String) {
        val cur = appList.toMutableSet()
        if (pkg in cur) cur.remove(pkg) else cur.add(pkg)
        appList = cur
    }

    private fun readList(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun writeList(key: String, v: List<String>) {
        val arr = JSONArray()
        v.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_APP_LIST = "app_list"
        private const val KEY_SHOW_SYSTEM = "show_system_apps"
        private const val KEY_ROUTE_VPN = "route_through_vpn"
        private const val KEY_BYPASS_VPN = "bypass_vpn"
    }
}
