package com.alal.yft.feature.browser

import java.net.URLEncoder

/**
 * "Search to download" (T13): words typed in the browser's address field become a YouTube or a
 * web search, while anything that looks like an address still opens as a page.
 */
internal object BrowserSearch {
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val whitespace = Regex("""\s""")

    /** The trimmed words when [input] reads as a search, or `null` for an address or nothing. */
    fun wordsOrNull(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (whitespace.containsMatchIn(trimmed)) return trimmed
        if (scheme.containsMatchIn(trimmed)) return null
        val host = trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
        val looksLikeHost = '.' in host && !host.startsWith('.') && !host.endsWith('.')
        return if (looksLikeHost) null else trimmed
    }

    fun youTubeUrl(words: String): String =
        "https://m.youtube.com/results?search_query=${encode(words)}"

    fun webUrl(words: String): String = "https://duckduckgo.com/?q=${encode(words)}"

    private fun encode(words: String): String = URLEncoder.encode(words.trim(), "UTF-8")
}
