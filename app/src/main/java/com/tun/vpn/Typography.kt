package com.tun.vpn

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/**
 * Типографика из дизайн-системы:
 * - Display: Space Grotesk  (заголовки, кнопки, метки статуса)
 * - Body:    Inter          (основной текст, формы)
 * - Mono:    JetBrains Mono (адреса, моноширинные логи)
 *
 * Загружаются из Google Fonts через GMS font provider. Если provider недоступен,
 * Compose автоматически откатывается на FontFamily.Default.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val spaceGrotesk = GoogleFont("Space Grotesk")
private val inter = GoogleFont("Inter")
private val jetBrainsMono = GoogleFont("JetBrains Mono")

val FontDisplay = FontFamily(
    Font(googleFont = spaceGrotesk, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = spaceGrotesk, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = spaceGrotesk, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = spaceGrotesk, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = spaceGrotesk, fontProvider = provider, weight = FontWeight.Black),
)

val FontBody = FontFamily(
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = inter, fontProvider = provider, weight = FontWeight.Bold),
)

val FontMono = FontFamily(
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Bold),
)
