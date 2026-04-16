package com.tun.vpn

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Уровень лога.
 */
enum class LogLevel(val label: String) {
    DEBUG("DBG"),
    INFO("INF"),
    WARNING("WRN"),
    ERROR("ERR");
}

/**
 * Источник лога.
 */
enum class LogSource(val tag: String, val displayName: String) {
    APP("APP", "App"),
    PROXY("TP", "Turn Proxy"),
    WIREGUARD("WG", "WireGuard");
}

/**
 * Одна запись лога.
 */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val source: LogSource = LogSource.APP,
    val message: String = ""
) {
    fun formatted(): String {
        val time = TIME_FORMAT.format(Date(timestamp))
        return "[$time] ${level.label}|${source.tag}| $message"
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

        /**
         * Определить уровень лога из содержимого строки proxy.
         */
        fun inferLevel(line: String): LogLevel = when {
            line.contains("[FATAL]", ignoreCase = true) ||
            line.contains("FATAL_CAPTCHA") ||
            line.contains("CRASHED") -> LogLevel.ERROR

            line.contains("error", ignoreCase = true) ||
            line.contains("failed", ignoreCase = true) -> LogLevel.ERROR

            line.contains("[Captcha]") ||
            line.contains("WARNING", ignoreCase = true) ||
            line.contains("captcha", ignoreCase = true) -> LogLevel.WARNING

            line.contains("[STREAM") ||
            line.contains("connected") ||
            line.contains("OK:") -> LogLevel.INFO

            else -> LogLevel.DEBUG
        }
    }
}
