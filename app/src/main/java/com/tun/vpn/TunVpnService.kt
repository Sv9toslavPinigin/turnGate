package com.tun.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wireguard.android.backend.GoBackend

/**
 * VPN-сервис, наследующий GoBackend.VpnService.
 *
 * Поддерживает stateful notification: иконка TG + заголовок/текст меняются
 * в зависимости от статуса подключения. Обновление приходит из ViewModel
 * через companion static-reference (см. [updateState]).
 */
class TunVpnService : GoBackend.VpnService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // Первичный foreground — "Connecting...", потом VM перебьёт.
        startForegroundCompat(buildNotification(ConnectionState.CONNECTING, null))
        Log.i(TAG, "VpnService created")
    }

    override fun onDestroy() {
        Log.i(TAG, "VpnService destroyed")
        connectionState = ConnectionState.DISCONNECTED
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Пересобрать notification под текущий статус (из ViewModel).
     * Если state == DISCONNECTED — убираем foreground и даём сервису умереть.
     */
    fun refreshNotification(state: ConnectionState, profileName: String?) {
        connectionState = state
        if (state == ConnectionState.DISCONNECTED) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        val n = buildNotification(state, profileName)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, n)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows ongoing VPN status and a Disconnect action"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState, profileName: String?): Notification {
        val s = Strings.current

        // Tap → open MainActivity (foreground).
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Disconnect action → open MainActivity with special action.
        val disconnectIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_DISCONNECT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val disconnectPi = PendingIntent.getActivity(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title, text, accent) = when (state) {
            ConnectionState.CONNECTED -> Triple(
                "${s.app} · ${s.stConnected}",
                profileName ?: s.app,
                ACCENT_GREEN
            )
            ConnectionState.CONNECTING -> Triple(
                "${s.app} · ${s.stConnecting}",
                profileName ?: s.app,
                ACCENT_ORANGE
            )
            ConnectionState.DISCONNECTING -> Triple(
                "${s.app} · ${s.stDisconnecting}",
                profileName ?: s.app,
                ACCENT_ORANGE
            )
            ConnectionState.ERROR -> Triple(
                "${s.app} · ${s.stError}",
                profileName ?: s.app,
                ACCENT_RED
            )
            ConnectionState.DISCONNECTED -> Triple(
                s.app,
                profileName ?: s.app,
                ACCENT_GREY
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_status_tg)
            .setColor(accent)
            .setColorized(false)
            .setContentIntent(openPi)
            .addAction(
                NotificationCompat.Action(
                    0,
                    s.btnDisconnect,
                    disconnectPi
                )
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "TunVpnService"
        private const val CHANNEL_ID = "tun_vpn_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_DISCONNECT = "com.tun.vpn.ACTION_DISCONNECT"

        // Цвета берём не из темы (тема compose), а константные чтобы не таскать context.
        private val ACCENT_GREEN = Color.parseColor("#10B981")
        private val ACCENT_ORANGE = Color.parseColor("#F59E0B")
        private val ACCENT_RED = Color.parseColor("#EF4444")
        private val ACCENT_GREY = Color.parseColor("#64748B")

        @Volatile
        var connectionState: ConnectionState = ConnectionState.DISCONNECTED

        @Volatile
        private var instance: TunVpnService? = null

        /**
         * Вызывается из MainViewModel при каждом изменении статуса/активного
         * профиля. No-op если сервис ещё не запущен.
         */
        fun updateState(state: ConnectionState, profileName: String?) {
            connectionState = state
            instance?.refreshNotification(state, profileName)
        }
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}
