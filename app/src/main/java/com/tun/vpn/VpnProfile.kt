package com.tun.vpn

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Профиль VPN-подключения.
 * Содержит все параметры для запуска proxy и WireGuard туннеля.
 */
data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Profile",
    var vkLink: String = "",
    var peerAddr: String = "",
    var listenAddr: String = "127.0.0.1:9000",
    var nValue: Int = 16,
    var wgQuickConfig: String = "",
    var manualCaptcha: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("vkLink", vkLink)
        put("peerAddr", peerAddr)
        put("listenAddr", listenAddr)
        put("nValue", nValue)
        put("wgQuickConfig", wgQuickConfig)
        put("manualCaptcha", manualCaptcha)
    }

    companion object {
        fun fromJson(json: JSONObject): VpnProfile = VpnProfile(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Profile"),
            vkLink = json.optString("vkLink", ""),
            peerAddr = json.optString("peerAddr", ""),
            listenAddr = json.optString("listenAddr", "127.0.0.1:9000"),
            nValue = json.optInt("nValue", 16),
            wgQuickConfig = json.optString("wgQuickConfig", ""),
            manualCaptcha = json.optBoolean("manualCaptcha", false)
        )

        fun listToJson(profiles: List<VpnProfile>): String {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String): List<VpnProfile> {
            if (jsonStr.isBlank()) return emptyList()
            val arr = JSONArray(jsonStr)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    }
}
