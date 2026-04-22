package com.tun.vpn

/**
 * IPv4 subnet. Хранится как беззнаковый 32-бит адрес + длина префикса.
 */
data class Subnet(val addr: Long, val prefix: Int) {
    init {
        require(prefix in 0..32)
        require(addr and (0xFFFFFFFFL shr prefix) == 0L || prefix == 0) {
            "network bits below prefix must be zero"
        }
    }

    private val mask: Long get() = if (prefix == 0) 0L else 0xFFFFFFFFL shl (32 - prefix) and 0xFFFFFFFFL

    fun contains(other: Subnet): Boolean =
        other.prefix >= prefix && (other.addr and mask) == addr

    fun toCidr(): String {
        val a = (addr shr 24) and 0xFF
        val b = (addr shr 16) and 0xFF
        val c = (addr shr 8) and 0xFF
        val d = addr and 0xFF
        return "$a.$b.$c.$d/$prefix"
    }

    /** Делит сеть пополам (prefix+1). Бросает если уже /32. */
    fun split(): Pair<Subnet, Subnet> {
        require(prefix < 32) { "cannot split /32" }
        val newPrefix = prefix + 1
        val half = 1L shl (32 - newPrefix)
        return Subnet(addr, newPrefix) to Subnet(addr or half, newPrefix)
    }

    companion object {
        fun parse(cidr: String): Subnet? {
            val parts = cidr.trim().split("/")
            if (parts.size !in 1..2) return null
            val octets = parts[0].split(".")
            if (octets.size != 4) return null
            var addr = 0L
            for (o in octets) {
                val i = o.toIntOrNull() ?: return null
                if (i !in 0..255) return null
                addr = (addr shl 8) or i.toLong()
            }
            val prefix = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 32
            if (prefix !in 0..32) return null
            // Нормализуем: обнуляем биты ниже префикса.
            val mask = if (prefix == 0) 0L else 0xFFFFFFFFL shl (32 - prefix) and 0xFFFFFFFFL
            return Subnet(addr and mask, prefix)
        }
    }
}

/**
 * Возвращает [from] \ exclude (все подсети, покрывающие разницу).
 * Если они не пересекаются — возвращает сам [from]. Если exclude ⊇ from — empty.
 */
fun subtract(from: Subnet, exclude: Subnet): List<Subnet> {
    val overlap = from.contains(exclude) || exclude.contains(from)
    if (!overlap) return listOf(from)
    if (exclude.contains(from)) return emptyList()
    // from строго содержит exclude → режем пополам
    val (a, b) = from.split()
    return subtract(a, exclude) + subtract(b, exclude)
}

/** Применяет исключения по-очереди. */
fun subtractMany(from: Subnet, excludes: List<Subnet>): List<Subnet> {
    var list = listOf(from)
    for (e in excludes) {
        list = list.flatMap { subtract(it, e) }
    }
    return list
}
