package com.tun.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Standalone foreground service отображающий статус VPN в шторке.
 *
 * NB: не расширяем GoBackend.VpnService и не объявляем intent-filter
 * android.net.VpnService — такой сервис WireGuardKit запускает сам (его inner
 * class GoBackend$VpnService), и наш сабкласс никогда не стартовал. Вместо
 * этого — обычный service, который стартуется нами параллельно с wg-backend.
 *
 * Используется только для UI-нотификации, сетевое соединение держит GoBackend.
 */
class TunVpnService : Service() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Log.i(TAG, "TunVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = when (intent?.action) {
            ACTION_UPDATE -> (intent.getSerializableExtra(EXTRA_STATE) as? ConnectionState)
                ?: ConnectionState.CONNECTING
            else -> ConnectionState.CONNECTING
        }
        val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME)

        // Всегда удерживаем foreground status, пока сервис активен.
        val n = buildNotification(state, profileName)
        startForegroundCompat(n)

        connectionState = state
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Когда task swiped из recents — НЕ останавливаем сервис. На стоковом
     * Android foreground service этим live через процесс; на MIUI это кладёт
     * болт на kill-policy, но хотя бы не мы его инициируем.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved — keeping VPN alive")
    }

    override fun onDestroy() {
        Log.i(TAG, "TunVpnService destroyed")
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    fun refreshNotification(state: ConnectionState, profileName: String?) {
        connectionState = state
        if (state == ConnectionState.DISCONNECTED) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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
        val nm = getSystemService(NotificationManager::class.java)
        // Удаляем старый LOW-канал, чтобы обновлённый IMPORTANCE_DEFAULT подхватился.
        runCatching { nm.deleteNotificationChannel("tun_vpn_channel") }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN status",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows ongoing VPN status and a Disconnect action"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState, profileName: String?): Notification {
        val s = Strings.current

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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
        private const val CHANNEL_ID = "tun_vpn_status_v2"
        private const val NOTIFICATION_ID = 1

        const val ACTION_DISCONNECT = "com.tun.vpn.ACTION_DISCONNECT"
        const val ACTION_UPDATE = "com.tun.vpn.ACTION_UPDATE"
        const val EXTRA_STATE = "state"
        const val EXTRA_PROFILE_NAME = "profile_name"

        private val ACCENT_GREEN = Color.parseColor("#10B981")
        private val ACCENT_ORANGE = Color.parseColor("#F59E0B")
        private val ACCENT_RED = Color.parseColor("#EF4444")
        private val ACCENT_GREY = Color.parseColor("#64748B")

        @Volatile
        var connectionState: ConnectionState = ConnectionState.DISCONNECTED

        @Volatile
        private var instance: TunVpnService? = null

        /**
         * Стартует сервис (если ещё не запущен) и обновляет notification
         * под новое состояние. Вызывается из MainViewModel при каждом изменении
         * status / активного профиля.
         */
        fun updateState(context: android.content.Context, state: ConnectionState, profileName: String?) {
            connectionState = state
            val running = instance
            if (state == ConnectionState.DISCONNECTED) {
                running?.refreshNotification(state, profileName)
                return
            }
            if (running != null) {
                running.refreshNotification(state, profileName)
            } else {
                val intent = Intent(context, TunVpnService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_STATE, state)
                    putExtra(EXTRA_PROFILE_NAME, profileName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
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
