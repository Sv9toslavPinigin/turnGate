package com.tun.vpn

import android.content.Context
import android.content.SharedPreferences

/**
 * Сохранение и загрузка настроек подключения в SharedPreferences.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tun_vpn_prefs", Context.MODE_PRIVATE)

    var vkLink: String
        get() = prefs.getString(KEY_VK_LINK, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VK_LINK, value).apply()

    var serverAddress: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var privateKey: String
        get() = prefs.getString(KEY_PRIVATE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRIVATE_KEY, value).apply()

    var publicKey: String
        get() = prefs.getString(KEY_PUBLIC_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUBLIC_KEY, value).apply()

    var threads: Int
        get() = prefs.getInt(KEY_THREADS, 16)
        set(value) = prefs.edit().putInt(KEY_THREADS, value).apply()

    /**
     * Очистить старые ключи после миграции в ProfileStore.
     */
    fun clearLegacy() {
        prefs.edit()
            .remove(KEY_VK_LINK)
            .remove(KEY_SERVER)
            .remove(KEY_PRIVATE_KEY)
            .remove(KEY_PUBLIC_KEY)
            .remove(KEY_THREADS)
            .apply()
    }

    companion object {
        private const val KEY_VK_LINK = "vk_link"
        private const val KEY_SERVER = "server_address"
        private const val KEY_PRIVATE_KEY = "wg_private_key"
        private const val KEY_PUBLIC_KEY = "wg_public_key"
        private const val KEY_THREADS = "threads"
    }
}
