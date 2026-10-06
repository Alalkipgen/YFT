package com.alal.yft.core.model.download

/**
 * The short detail of a download failure that is stored with the task, shown under Details and
 * copied into a bug report (P21): the exception's class and message, with links, IP addresses,
 * host names, secrets and token-like values removed, on one line of at most [MAX_CHARS]
 * characters.
 */
object DownloadFailureDetails {
    const val MAX_CHARS = 120
    private const val MAX_INPUT_CHARS = 2_000
    private const val MAX_COPIES = 4

    /** "SocketTimeoutException: timeout", with its first cause when it has one. */
    fun of(thrown: Throwable): String {
        val error = thrown.withoutCopies()
        val cause = error.cause?.takeIf { it !== error }
        val text = buildString {
            if (cause != null && error.message == cause.toString()) {
                append(error.className())
            } else {
                append(error.describe())
            }
            if (cause != null) {
                append("; cause ")
                append(cause.describe())
            }
        }
        return sanitize(text) ?: error.className()
    }

    /** [text] without addresses or secrets, on one line and at most [MAX_CHARS] long. */
    fun sanitize(text: String?): String? {
        if (text == null) return null
        // Bounds the work for a very long message; the end is cut to MAX_CHARS anyway.
        var clean = CONTROL.replace(text.take(MAX_INPUT_CHARS), " ")
        clean = URI.replace(clean) { match -> uriPlaceholder(match.groupValues[1]) }
        clean = BEARER.replace(clean, "Bearer [hidden]")
        clean = SECRET_PAIR.replace(clean) { match -> "${match.groupValues[1]}=[hidden]" }
        clean = IPV4.replace(clean, "[ip]")
        clean = IPV6.replace(clean, "[ip]")
        clean = HOST.replace(clean) { match ->
            val lastLabel = match.value.substringAfterLast('.')
            if (lastLabel in FILE_EXTENSIONS) match.value else "[host]"
        }
        clean = TOKEN.replace(clean) { match ->
            val value = match.value
            if (value.any(Char::isDigit) && value.any(Char::isLetter)) "[token]" else value
        }
        clean = WHITESPACE.replace(clean, " ").trim()
        if (clean.isEmpty()) return null
        return if (clean.length <= MAX_CHARS) {
            clean
        } else {
            clean.take(MAX_CHARS - 1).trimEnd() + "\u2026"
        }
    }

    /**
     * The first error that is not a copy of its cause. Coroutines may rethrow a copy of an error
     * with the original as its cause (stack trace recovery); naming both says the same twice.
     */
    private fun Throwable.withoutCopies(): Throwable {
        var current = this
        repeat(MAX_COPIES) {
            val cause = current.cause
            if (
                cause == null ||
                cause === current ||
                cause.javaClass != current.javaClass ||
                (cause.message != current.message && cause.toString() != current.message)
            ) {
                return current
            }
            current = cause
        }
        return current
    }

    private fun Throwable.describe(): String {
        val message = message?.takeIf(String::isNotBlank) ?: return className()
        return "${className()}: $message"
    }

    private fun Throwable.className(): String =
        javaClass.simpleName.ifBlank { javaClass.name.substringAfterLast('.') }

    private fun uriPlaceholder(scheme: String): String =
        when (val normalized = scheme.lowercase()) {
            "http", "https", "ws", "wss" -> "[link]"
            else -> "[$normalized uri]"
        }

    private val CONTROL = Regex("""\p{Cntrl}""")
    private val URI = Regex("""\b([A-Za-z][A-Za-z0-9+.-]{1,15})://[^\s"'<>]*""")
    private val BEARER = Regex("""(?i)\bbearer\s+[^\s&;,]+""")
    private val SECRET_PAIR = Regex(
        "(?i)\\b((?:access_|refresh_|id_|po_)?token|api[_-]?key|key|sig|signature|" +
            "set-cookie|cookie|authorization|auth|session(?:id)?|sid|pot|" +
            "visitor(?:_data)?|password|passwd|secret|credential)s?\\s*[=:]\\s*[^\\s&;,]+",
    )
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val IPV6 = Regex(
        """(?<![\w:])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?![\w:])""",
    )
    private val HOST = Regex(
        """(?<![\w.@/-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}(?![\w.-])""",
    )
    private val TOKEN = Regex("""[A-Za-z0-9_+=-]{24,}""")
    private val WHITESPACE = Regex("""\s+""")

    /** Endings of file names that would otherwise read as host names. */
    private val FILE_EXTENSIONS = setOf(
        "aac", "avi", "flac", "gif", "html", "jpeg", "jpg", "json", "mkv", "mov", "mpd", "ogg",
        "opus", "part", "pending", "png", "ready", "downloading", "srt", "tmp", "ts", "txt",
        "vtt", "webm", "webp", "zip",
    )
}
