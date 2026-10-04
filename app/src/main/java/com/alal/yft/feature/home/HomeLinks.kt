package com.alal.yft.feature.home

/** Turns what the user typed or pasted on Home into the link handed to the browser. */
internal object HomeLinks {
    /** Matches the browser's address limit so a pasted link is never cut differently there. */
    const val MAX_LENGTH: Int = 2_048

    private val webLink = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?"

    /**
     * The first web link in copied text, so "Watch this: https://…" pastes only the link. Text
     * without one is kept as its first line, for the browser to validate like a typed address.
     */
    fun fromClipboard(text: CharSequence?): String? {
        val trimmed = text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val link = webLink.find(trimmed)?.value?.let(::trimTrailingPunctuation)
        return (link ?: trimmed.lineSequence().first().trim())
            .take(MAX_LENGTH)
            .takeIf { it.isNotEmpty() }
    }

    /** Only the first http(s) link in [text], or null when it has none (copied-link check). */
    fun firstWebLink(text: CharSequence?): String? =
        text?.let { webLink.find(it) }?.value
            ?.let(::trimTrailingPunctuation)
            ?.take(MAX_LENGTH)
            ?.takeIf { it.isNotEmpty() }

    private fun trimTrailingPunctuation(link: String): String {
        var end = link.length
        while (end > 0) {
            val last = link[end - 1]
            val body = link.substring(0, end)
            val unbalancedClose = (last == ')' && body.unbalanced('(', ')')) ||
                (last == ']' && body.unbalanced('[', ']'))
            if (last in TRAILING_PUNCTUATION || unbalancedClose) end-- else break
        }
        return link.substring(0, end)
    }

    private fun String.unbalanced(open: Char, close: Char): Boolean =
        count { it == close } > count { it == open }
}
