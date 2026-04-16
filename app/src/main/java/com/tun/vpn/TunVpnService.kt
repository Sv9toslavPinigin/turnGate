package com.tun.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wireguard.android.backend.GoBackend

/**
 * VPN-сервис, наследующий GoBackend.VpnService.
 * GoBackend сам управляет VPN-интерфейсом, нам нужно только
 * обеспечить foreground notification.
 */
class TunVpnService : GoBackend.VpnService() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.i(TAG, "VpnService created")
    }

    override fun onDestroy() {
        Log.i(TAG, "VpnService destroyed")
        connectionState = ConnectionState.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows VPN connection status"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TurnGate")
            .setContentText("VPN connection active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "TunVpnService"
        private const val CHANNEL_ID = "tun_vpn_channel"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var connectionState: ConnectionState = ConnectionState.DISCONNECTED
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}
