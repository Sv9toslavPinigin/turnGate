package com.tun.vpn

/**
 * Одно правило маршрутизации из UI: CIDR/IP/домен/wildcard.
 * Хранится в RoutingStore как строка, парсится через [parse].
 */
sealed class RouteRule {
    data class Cidr(val raw: String) : RouteRule()
    data class Domain(val host: String) : RouteRule()
    data class Wildcard(val suffix: String) : RouteRule()
    data class Invalid(val raw: String, val reason: String) : RouteRule()

    val displayValue: String get() = when (this) {
        is Cidr -> raw
        is Domain -> host
        is Wildcard -> "*.$suffix"
        is Invalid -> raw
    }

    val isValid: Boolean get() = this !is Invalid

    companion object {
        private val ipv4Regex = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(/\\d{1,2})?$")
        private val domainRegex = Regex(
            "^(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$",
            RegexOption.IGNORE_CASE
        )

        fun parse(input: String): RouteRule {
            val raw = input.trim().lowercase()
            if (raw.isEmpty()) return Invalid(input, "empty")

            // Wildcard: *.suffix
            if (raw.startsWith("*.")) {
                val suffix = raw.substring(2)
                if (!suffix.matches(domainRegex)) return Invalid(input, "invalid wildcard")
                return Wildcard(suffix)
            }

            // CIDR / IPv4
            if (raw.matches(ipv4Regex)) {
                val parts = raw.split("/")
                val octets = parts[0].split(".").map { it.toIntOrNull() ?: return Invalid(input, "bad octet") }
                if (octets.any { it !in 0..255 }) return Invalid(input, "octet out of range")
                val prefix = if (parts.size == 2) parts[1].toIntOrNull() ?: return Invalid(input, "bad prefix") else 32
                if (prefix !in 0..32) return Invalid(input, "prefix out of range")
                val normalized = if (parts.size == 2) raw else "$raw/32"
                return Cidr(normalized)
            }

            // Plain domain
            if (raw.matches(domainRegex)) return Domain(raw)

            return Invalid(input, "not IP/CIDR/domain")
        }
    }
}
