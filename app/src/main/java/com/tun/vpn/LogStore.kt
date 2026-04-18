package com.tun.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Хранилище логов — in-memory StateFlow + файл vpn_tunnel.log.
 * Ротация: при превышении 500KB оставляет последние 60% строк.
 */
object LogStore {

    private const val TAG = "LogStore"
    private const val LOG_FILE_NAME = "vpn_tunnel.log"
    private const val MAX_FILE_SIZE = 500 * 1024L // 500KB
    private const val KEEP_RATIO = 0.6
    private const val MAX_IN_MEMORY = 2000

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
    }

    fun addEntry(message: String, level: LogLevel, source: LogSource) {
        val entry = LogEntry(level = level, source = source, message = message)
        val current = _entries.value
        _entries.value = if (current.size >= MAX_IN_MEMORY) {
            current.drop(current.size - MAX_IN_MEMORY + 1) + entry
        } else {
            current + entry
        }

        // Записать в файл
        appendToFile(entry.formatted())
    }

    fun addProxyLine(line: String) {
        addEntry(line, LogEntry.inferLevel(line), LogSource.PROXY)
    }

    fun addAppLog(message: String, level: LogLevel = LogLevel.INFO) {
        addEntry(message, level, LogSource.APP)
    }

    fun addWgLog(message: String, level: LogLevel = LogLevel.INFO) {
        addEntry(message, level, LogSource.WIREGUARD)
    }

    fun addFriendlyLog(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(level = level, source = LogSource.APP, message = message, isFriendly = true)
        val current = _entries.value
        _entries.value = if (current.size >= MAX_IN_MEMORY) {
            current.drop(current.size - MAX_IN_MEMORY + 1) + entry
        } else {
            current + entry
        }
    }

    fun clear() {
        _entries.value = emptyList()
        logFile?.let { if (it.exists()) it.writeText("") }
    }

    fun getLogText(): String {
        return _entries.value.joinToString("\n") { it.formatted() }
    }

    private fun appendToFile(line: String) {
        try {
            val file = logFile ?: return
            file.appendText("$line\n")
            rotateIfNeeded(file)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write log", e)
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_FILE_SIZE) return
        try {
            val lines = file.readLines()
            val keepCount = (lines.size * KEEP_RATIO).toInt()
            val kept = lines.takeLast(keepCount)
            file.writeText(kept.joinToString("\n") + "\n")
            Log.d(TAG, "Log rotated: kept $keepCount of ${lines.size} lines")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rotate log", e)
        }
    }
}
