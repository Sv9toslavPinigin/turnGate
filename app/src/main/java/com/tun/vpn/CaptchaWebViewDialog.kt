package com.tun.vpn

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Диалог с WebView для ручного решения капчи VK.
 *
 * @param captchaIndex 1-based номер текущей капчи в сессии.
 * @param onDismiss    скрыть диалог — капча решена.
 * @param onCancel     пользователь явно отменил, нужен полный disconnect.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebViewDialog(
    captchaIndex: Int,
    totalEstimate: Int,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val theme = LocalTgTheme.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    fun dismissOnce() {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }

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
                .fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(theme.bg1)
                .padding(12.dp)
        ) {
            val t = LocalStrings.current

            // Compact step header (label + progress + dots, all in one block).
            val total = totalEstimate.coerceAtLeast(1)
            val shown = captchaIndex.coerceAtLeast(1)
            CaptchaStepHeader(theme = theme, current = shown, total = total, label = t.captchaStepOf.format(shown, total))

            Spacer(Modifier.height(8.dp))

            // Title row with refresh/cancel — hint goes INTO a horizontal
            // scroll marquee below so it never breaks layout vertically.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t.captchaTitle,
                    color = theme.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontDisplay,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { webViewRef?.reload() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textSecondary)
                ) {
                    Text(t.refresh, fontSize = 11.sp)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.error)
                ) {
                    Text(t.cancel, fontSize = 11.sp, color = Color.White)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Hint: small single-line marquee (horizontally scrolling).
            HintMarquee(text = t.captchaHint, color = theme.textTertiary)

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
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

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        dismissOnce()
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
        onDispose { webViewRef?.destroy() }
    }
}

/**
 * Шапка с прогрессом «Шаг N из ~M» + точки-чекбоксы для каждого шага.
 * Оценка total — из profile.nValue. Клэмпится до 12 точек чтобы не превращать
 * в неконтейнер.
 */
@Composable
private fun CaptchaStepHeader(theme: TgTheme, current: Int, total: Int, label: String) {
    val capped = total.coerceAtMost(12)
    val progress = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Label row: small uppercase
        Text(
            text = label.uppercase(),
            color = theme.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontDisplay,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(8.dp))

        // Progress track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(theme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(theme.accent, theme.accent2)
                        )
                    )
            )
        }

        Spacer(Modifier.height(8.dp))

        // Step dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..capped) {
                val done = i < current
                val active = i == current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                when {
                                    done -> theme.accent
                                    active -> theme.connecting
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    done -> theme.accent
                                    active -> theme.connecting
                                    else -> theme.textTertiary
                                },
                                shape = RoundedCornerShape(7.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) {
                            Text("✓", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    if (capped <= 8) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "#$i",
                            color = if (active || done) theme.textSecondary else theme.textTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontDisplay,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Маленькая строка-бегущая подсказка: одна строка, горизонтальная прокрутка
 * через LinearEasing. Разделяет текст точкой-пунктиром и зациклена.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HintMarquee(text: String, color: Color) {
    // basicMarquee — из foundation 1.7+; компактнее ручного цикла.
    Text(
        text = "  •  $text  •  $text",
        color = color,
        fontSize = 10.sp,
        fontFamily = FontBody,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                velocity = 28.dp
            )
    )
}

private const val CAPTCHA_URL = "http://localhost:8765"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
