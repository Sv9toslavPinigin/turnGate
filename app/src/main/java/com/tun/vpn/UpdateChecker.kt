package com.tun.vpn

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.GITHUB_REPO
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")

            if (conn.responseCode != 200) {
                Log.d(TAG, "GitHub API returned ${conn.responseCode}")
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().readText()
            val json = org.json.JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val releaseNotes = json.optString("body", "").take(500)
            val assets = json.optJSONArray("assets") ?: JSONArray()

            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }

            if (apkUrl.isNullOrBlank()) {
                Log.d(TAG, "No APK asset in release")
                return@withContext null
            }

            // Parse version code from tag: "v3" -> 3, "v3.0" -> 3, "3" -> 3
            val versionCode = tagName
                .removePrefix("v")
                .split(".")
                .firstOrNull()
                ?.toIntOrNull() ?: 0

            if (versionCode <= BuildConfig.VERSION_CODE) {
                Log.d(TAG, "No update: remote=$versionCode, local=${BuildConfig.VERSION_CODE}")
                return@withContext null
            }

            UpdateInfo(
                versionName = tagName.removePrefix("v"),
                versionCode = versionCode,
                downloadUrl = apkUrl,
                releaseNotes = releaseNotes
            )
        } catch (e: Exception) {
            Log.d(TAG, "Update check failed (silent): ${e.message}")
            null
        }
    }

    fun downloadAndInstall(context: Context, update: UpdateInfo) {
        val fileName = "TurnGate-${update.versionName}.apk"

        // Clean up old downloads
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }

        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("TurnGate ${update.versionName}")
            .setDescription("Downloading update...")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    val file = File(downloadsDir, fileName)
                    if (file.exists()) {
                        installApk(ctx, file)
                    }
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
