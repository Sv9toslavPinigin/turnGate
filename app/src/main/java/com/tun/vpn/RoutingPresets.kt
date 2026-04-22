package com.tun.vpn

/**
 * Готовые наборы правил маршрутизации.
 */
data class RoutingPreset(
    val key: String,
    val nameKeyEn: String,
    val nameKeyRu: String,
    val rules: List<String>,
)

object RoutingPresets {

    val RU = RoutingPreset(
        key = "ru",
        nameKeyEn = "Russian services",
        nameKeyRu = "Российские сервисы",
        rules = listOf(
            "*.vk.com", "*.vk.ru", "*.vkontakte.ru", "*.vkuser.net", "*.vkuseraudio.com",
            "*.vkplay.ru", "*.vkplay.live", "*.vkvideo.ru",
            "*.yandex.ru", "*.yandex.net", "*.yandex.com", "*.ya.ru", "*.yastatic.net", "*.ycdn.ru",
            "*.mail.ru", "*.my.mail.ru", "*.ok.ru", "*.okcdn.ru",
            "*.dzen.ru", "*.zen.yandex.ru",
            "*.sberbank.ru", "*.sber.ru", "*.sberdevices.ru",
            "*.gosuslugi.ru", "*.nalog.gov.ru",
            "*.avito.ru", "*.avito.st",
            "*.ozon.ru", "*.wildberries.ru", "*.wbbasket.ru",
            "*.kinopoisk.ru", "*.rutube.ru",
        ),
    )

    val GOOGLE = RoutingPreset(
        key = "google",
        nameKeyEn = "Google services",
        nameKeyRu = "Сервисы Google",
        rules = listOf(
            "*.google.com", "*.googleapis.com", "*.gstatic.com", "*.googleusercontent.com",
            "*.youtube.com", "*.youtu.be", "*.ytimg.com", "*.ggpht.com",
            "*.doubleclick.net", "*.googlevideo.com", "*.youtubei.googleapis.com",
            "*.gmail.com", "*.google-analytics.com",
        ),
    )

    // Всегда в обход VPN: приватные сети RFC1918 + link-local.
    val LAN = RoutingPreset(
        key = "lan",
        nameKeyEn = "Local networks",
        nameKeyRu = "Локальные сети",
        rules = listOf(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "169.254.0.0/16",
            "127.0.0.0/8",
        ),
    )

    val ALL = listOf(RU, GOOGLE, LAN)

    fun byKey(key: String): RoutingPreset? = ALL.find { it.key == key }
}
