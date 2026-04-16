package com.tun.vpn

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Хранилище VPN-профилей в SharedPreferences.
 * Поддерживает CRUD операции и миграцию из старого формата SettingsManager.
 */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tun_vpn_profiles", Context.MODE_PRIVATE)

    private val legacySettings = SettingsManager(context)

    var profiles: List<VpnProfile>
        get() {
            val json = prefs.getString(KEY_PROFILES, "") ?: ""
            return VpnProfile.listFromJson(json)
        }
        private set(value) {
            prefs.edit().putString(KEY_PROFILES, VpnProfile.listToJson(value)).apply()
        }

    var selectedProfileId: String?
        get() = prefs.getString(KEY_SELECTED_ID, null)
        set(value) {
            prefs.edit().putString(KEY_SELECTED_ID, value).apply()
        }

    val selectedProfile: VpnProfile?
        get() {
            val id = selectedProfileId ?: return profiles.firstOrNull()
            return profiles.find { it.id == id } ?: profiles.firstOrNull()
        }

    init {
        migrateFromLegacy()
    }

    fun addProfile(profile: VpnProfile): VpnProfile {
        val named = profile.copy(name = uniqueName(profile.name))
        val list = profiles.toMutableList()
        list.add(named)
        profiles = list
        if (selectedProfileId == null) {
            selectedProfileId = named.id
        }
        return named
    }

    fun updateProfile(profile: VpnProfile) {
        val list = profiles.toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) {
            list[idx] = profile
            profiles = list
        }
    }

    fun deleteProfile(id: String) {
        val list = profiles.toMutableList()
        list.removeAll { it.id == id }
        profiles = list
        if (selectedProfileId == id) {
            selectedProfileId = list.firstOrNull()?.id
        }
    }

    private fun uniqueName(base: String): String {
        val names = profiles.map { it.name }.toSet()
        if (base !in names) return base
        var i = 2
        while ("$base $i" in names) i++
        return "$base $i"
    }

    /**
     * Миграция из старого формата SettingsManager.
     * Создаёт один профиль из существующих настроек, если профили пустые.
     */
    private fun migrateFromLegacy() {
        if (profiles.isNotEmpty()) return

        val vkLink = legacySettings.vkLink
        val server = legacySettings.serverAddress
        val privateKey = legacySettings.privateKey
        val publicKey = legacySettings.publicKey
        val threads = legacySettings.threads

        // Если старые настройки пустые — ничего не мигрируем
        if (vkLink.isBlank() && server.isBlank() && privateKey.isBlank()) return

        // Собираем wg-quick конфиг из отдельных полей
        val wgConfig = buildString {
            appendLine("[Interface]")
            if (privateKey.isNotBlank()) appendLine("PrivateKey = $privateKey")
            appendLine("Address = 10.0.0.2/32")
            appendLine("DNS = 8.8.8.8")
            appendLine("MTU = 1280")
            appendLine()
            appendLine("[Peer]")
            if (publicKey.isNotBlank()) appendLine("PublicKey = $publicKey")
            appendLine("Endpoint = 127.0.0.1:9000")
            appendLine("AllowedIPs = 0.0.0.0/0")
            appendLine("PersistentKeepalive = 25")
        }

        val profile = VpnProfile(
            name = "Migrated",
            vkLink = vkLink,
            peerAddr = server,
            nValue = threads,
            wgQuickConfig = wgConfig.trim()
        )

        val list = mutableListOf(profile)
        profiles = list
        selectedProfileId = profile.id

        legacySettings.clearLegacy()
        Log.i(TAG, "Migrated legacy settings to profile: ${profile.name}")
    }

    companion object {
        private const val TAG = "ProfileStore"
        private const val KEY_PROFILES = "vpn_profiles"
        private const val KEY_SELECTED_ID = "selected_profile_id"
    }
}
