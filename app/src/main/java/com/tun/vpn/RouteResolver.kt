package com.tun.vpn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Резолвит набор [RouteRule] → набор IPv4 CIDR-строк для AllowedIPs / subtraction.
 *
 * CIDR/IP идут as-is; домены резолвятся параллельно через InetAddress (таймаут на рул),
 * wildcard (`*.vk.com`) пока резолвим как просто апекс-домен (vk.com) — это минимально
 * полезно (не покрывает subdomains у всех CDN), но точный wildcard-резолв требует
 * DNS-zone enumeration, что в рамках клиента не делаем.
 */
object RouteResolver {

    private const val TAG = "RouteResolver"
    private const val DNS_TIMEOUT_MS = 2_500L
    private const val MAX_IPS_PER_DOMAIN = 8

    suspend fun resolveAll(rules: List<RouteRule>): List<String> = coroutineScope {
        val tasks = rules.map { rule ->
            async(Dispatchers.IO) { resolveOne(rule) }
        }
        tasks.awaitAll().flatten().distinct()
    }

    private suspend fun resolveOne(rule: RouteRule): List<String> = when (rule) {
        is RouteRule.Cidr -> listOf(rule.raw)
        is RouteRule.Invalid -> emptyList()
        is RouteRule.Domain -> lookup(rule.host)
        is RouteRule.Wildcard -> lookup(rule.suffix)
    }

    private suspend fun lookup(host: String): List<String> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(DNS_TIMEOUT_MS) {
            runCatching {
                InetAddress.getAllByName(host)
                    .filterIsInstance<Inet4Address>()
                    .take(MAX_IPS_PER_DOMAIN)
                    .map { it.hostAddress!! + "/32" }
            }.onFailure { Log.d(TAG, "DNS fail for $host: ${it.message}") }
                .getOrNull()
        }
        if (result.isNullOrEmpty()) {
            Log.d(TAG, "No addresses for $host (timeout or dns fail)")
            emptyList()
        } else {
            result
        }
    }
}
