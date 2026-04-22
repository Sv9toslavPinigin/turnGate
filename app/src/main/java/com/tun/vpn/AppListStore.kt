package com.tun.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
)

/**
 * Перечисление установленных приложений.
 * Вычисляется лениво, кешируется по всему процессу (список apps меняется редко).
 */
object AppListStore {

    @Volatile
    private var cache: List<AppInfo>? = null

    fun load(context: Context, forceRefresh: Boolean = false): List<AppInfo> {
        cache?.let { if (!forceRefresh) return it }

        val pm: PackageManager = context.packageManager
        val pkgs = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = ArrayList<AppInfo>(pkgs.size)
        for (ai in pkgs) {
            // Только приложения с launcher intent-ом — чтобы не показывать
            // системные сервисы без иконки. Но флаг isSystem сохраняем,
            // чтобы пользователь мог включить "Show system apps".
            val hasLauncher = pm.getLaunchIntentForPackage(ai.packageName) != null
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!hasLauncher && isSystem) continue

            val label = runCatching { pm.getApplicationLabel(ai).toString() }
                .getOrDefault(ai.packageName)
            val icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull()
            result.add(AppInfo(ai.packageName, label, icon, isSystem))
        }
        val sorted = result.sortedBy { it.label.lowercase() }
        cache = sorted
        return sorted
    }

    fun invalidate() { cache = null }
}
