package com.alal.yft.detection

import java.util.Locale

/**
 * Approximates whether two hosts belong to one site, the scope a browser keeps a session in.
 *
 * A redirect from `www.youtube.com` to `m.youtube.com` keeps the session a browser would keep,
 * while a redirect to another site drops it. Without a public-suffix list the registrable domain
 * is taken as the last two labels, or the last three under common second-level domains of
 * two-letter country codes such as `co.uk`. IP addresses match only themselves.
 */
internal object SiteScope {
    private val COUNTRY_SECOND_LEVELS = setOf(
        "ac", "co", "com", "edu", "go", "gov", "ne", "net", "or", "org",
    )

    private val CREDENTIAL_HEADERS = setOf("cookie", "authorization")

    fun sameSite(firstHost: String, secondHost: String): Boolean {
        val first = normalize(firstHost)
        val second = normalize(secondHost)
        if (first == second) return true
        if (isIpAddress(first) || isIpAddress(second)) return false
        val site = registrableDomain(first) ?: return false
        return site == registrableDomain(second)
    }

    fun isCredentialHeader(name: String): Boolean = name.lowercase(Locale.US) in CREDENTIAL_HEADERS

    private fun registrableDomain(host: String): String? {
        val labels = host.split('.')
        if (labels.size < 2 || labels.any(String::isEmpty)) return null
        val topLevel = labels.last()
        val secondLevel = labels[labels.size - 2]
        val underCountryCode = labels.size >= 3 && topLevel.length == 2 &&
            secondLevel in COUNTRY_SECOND_LEVELS
        return labels.takeLast(if (underCountryCode) 3 else 2).joinToString(".")
    }

    private fun normalize(host: String): String = host.lowercase(Locale.US).trimEnd('.')

    private fun isIpAddress(host: String): Boolean =
        host.contains(':') || host.all { it.isDigit() || it == '.' }
}
