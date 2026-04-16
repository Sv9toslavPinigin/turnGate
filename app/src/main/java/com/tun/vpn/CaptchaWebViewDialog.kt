package com.tun.vpn

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Диалог с WebView для ручного решения капчи VK.
 * Загружает страницу с localhost:8765 (proxy reverse-proxy).
 *
 * Закрывается автоматически:
 * - когда пользователь решил капчу (фраза "Done!" в body);
 * - когда proxy прислал событие CaptchaSolved (через ViewModel → onDismiss).
 *
 * @param onDismiss  — капча успешно решена либо её больше не ждут (скрыть диалог).
 * @param onCancel   — пользователь явно отменил, нужен полный disconnect.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebViewDialog(
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    fun dismissOnce() {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }

    // Polling body.innerText каждые 500ms — надёжно ловит success страницу.
    LaunchedEffect(Unit) {
        while (!dismissed) {
            delay(500)
            webViewRef?.evaluateJavascript(
                "(document.body && document.body.innerText) || ''"
            ) { result ->
                if (result?.contains("Done!") == true ||
                    result?.contains("close the page") == true
                ) {
                    dismissOnce()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { /* не даём закрыть тапом вне */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .padding(12.dp)
        ) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Solve Captcha",
                        color = Color(0xFFF1F5F9),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Cancel aborts connection",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = onCancel,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444)
                    )
                ) {
                    Text("Cancel")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // WebView — во всю высоту
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = DESKTOP_UA

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(
                                        "(document.body && document.body.innerText) || ''"
                                    ) { result ->
                                        if (result?.contains("Done!") == true ||
                                            result?.contains("close the page") == true
                                        ) {
                                            dismissOnce()
                                        }
                                    }
                                }
                            }

                            webChromeClient = WebChromeClient()
                            loadUrl(CAPTCHA_URL)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }
}

private const val CAPTCHA_URL = "http://localhost:8765"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
