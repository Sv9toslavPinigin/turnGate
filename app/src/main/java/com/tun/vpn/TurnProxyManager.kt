package com.tun.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * События капчи, детектируемые из stdout proxy-бинарника.
 */
sealed class CaptchaEvent {
    object ManualCaptchaRequired : CaptchaEvent()
    object CaptchaSolved : CaptchaEvent()
    object CaptchaFailed : CaptchaEvent()
}

/**
 * Управление процессом vk-turn-proxy-client.
 * Бинарник упакован как .so в jniLibs/arm64-v8a и автоматически распаковывается
 * Android-ом в nativeLibraryDir с правами на исполнение.
 */
class TurnProxyManager(private val context: Context) {

    private var process: Process? = null

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _captchaEvent = MutableSharedFlow<CaptchaEvent>(extraBufferCapacity = 10)
    val captchaEvent: SharedFlow<CaptchaEvent> = _captchaEvent.asSharedFlow()

    private fun addLog(line: String) {
        val current = _logs.value
        _logs.value = (if (current.size >= 200) current.drop(1) else current) + line
    }

    /**
     * Проверить строку лога на события капчи и эмитить соответствующее событие.
     */
    private fun detectCaptchaEvent(line: String) {
        when {
            line.contains("ACTION REQUIRED: MANUAL CAPTCHA SOLVING NEEDED") -> {
                _captchaEvent.tryEmit(CaptchaEvent.ManualCaptchaRequired)
                Log.i(TAG, "Captcha event: manual captcha required")
            }
            line.contains("[Captcha] Success! Got success_token") -> {
                _captchaEvent.tryEmit(CaptchaEvent.CaptchaSolved)
                Log.i(TAG, "Captcha event: solved")
            }
            line.contains("manual captcha timed out") || line.contains("FATAL_CAPTCHA") -> {
                _captchaEvent.tryEmit(CaptchaEvent.CaptchaFailed)
                Log.i(TAG, "Captcha event: failed")
            }
        }
    }

    /** Путь к бинарнику в нативной директории (exec разрешён) */
    private val binaryFile: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libvkturnproxy.so")

    /**
     * Запустить vk-turn-proxy-client.
     *
     * @param serverAddress IP:PORT VPS сервера
     * @param vkLink ссылка на VK звонок
     * @param listenAddress локальный адрес для прослушивания (по умолчанию 127.0.0.1:9000)
     * @param threads количество потоков
     * @return true если процесс успешно запущен
     */
    fun start(
        serverAddress: String,
        vkLink: String,
        listenAddress: String = "127.0.0.1:9000",
        threads: Int = 4,
        manualCaptcha: Boolean = false
    ): Boolean {
        if (process != null) {
            Log.w(TAG, "Process already running")
            return true
        }

        if (!binaryFile.exists()) {
            Log.e(TAG, "Binary not found at ${binaryFile.absolutePath}")
            return false
        }

        return try {
            _logs.value = emptyList()
            val cmd = mutableListOf(
                binaryFile.absolutePath,
                "-peer", serverAddress,
                "-vk-link", vkLink,
                "-listen", listenAddress,
                "-n", threads.toString()
            )
            if (manualCaptcha) {
                cmd.add("-manual-captcha")
            }
            val cmdStr = cmd.joinToString(" ")
            Log.i(TAG, "Starting: $cmdStr")
            addLog("CMD: $cmdStr")

            val pb = ProcessBuilder(cmd)
                .directory(context.filesDir)
                .redirectErrorStream(true)

            process = pb.start()
            addLog("PID started, waiting 500ms...")

            // Логируем stdout/stderr бинарника в logcat и в StateFlow
            Thread({
                try {
                    process?.inputStream?.bufferedReader()?.forEachLine { line ->
                        Log.d(TAG, "[proxy] $line")
                        addLog(line)
                        LogStore.addProxyLine(line)
                        detectCaptchaEvent(line)
                    }
                } catch (_: IOException) {
                    // процесс завершён
                }
            }, "proxy-log").apply { isDaemon = true }.start()

            // Даём процессу 500ms на старт и проверяем, не упал ли сразу
            Thread.sleep(500)
            val alive = process?.isAlive == true
            if (!alive) {
                val exitCode = process?.exitValue()
                val msg = "CRASHED: exit code $exitCode"
                Log.e(TAG, msg)
                addLog(msg)
                process = null
            } else {
                addLog("OK: proxy alive on $listenAddress")
            }
            alive
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy", e)
            process = null
            false
        }
    }

    /**
     * Остановить процесс.
     */
    fun stop() {
        process?.let { p ->
            p.destroy()
            try {
                p.waitFor()
            } catch (_: InterruptedException) {
                p.destroyForcibly()
            }
            Log.i(TAG, "Proxy process stopped")
        }
        process = null
    }

    /**
     * Проверить, жив ли процесс.
     */
    fun isRunning(): Boolean = process?.isAlive == true

    companion object {
        private const val TAG = "TurnProxyManager"
    }
}
