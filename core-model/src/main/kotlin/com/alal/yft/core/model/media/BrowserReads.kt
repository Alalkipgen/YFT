package com.alal.yft.core.model.media

/**
 * P45: a small answer (a list of qualities, the start of a file, a page) asked through the
 * browser's own engine: Android's WebView, so Chromium's network stack with its TLS handshake,
 * the browser's cookie store and the tab's agent, from a blank page of the link's page origin.
 *
 * Used only after a site refused YFT's own request of a link a browser page names (HTTP 403,
 * 410, 412 or a code no standard defines, such as 474): on the owner's phone the CDN of an adult
 * site answered 410 and 474 to fresh links the page's player played, and public reports
 * (yt-dlp #16729, #17642, 2026) show that site refusing requests that are not a browser's.
 * Nothing here taps, solves or skips anything; it asks what the page's player asks.
 */
fun interface BrowserReads {
    /** The browser's answer to [request]; null when no browser engine could ask at all. */
    suspend fun read(request: BrowserReadRequest): BrowserReadAnswer?

    companion object {
        /** No browser engine (tests, a phone without WebView): YFT's own answer stands. */
        val None = BrowserReads { null }

        /**
         * The answers worth asking the browser again: refusals a browser's own request may not
         * get (403, 410, 412, and the codes no standard defines, 452–499, such as 474).
         */
        fun isRefusal(status: Int?): Boolean =
            status == HTTP_FORBIDDEN || status == HTTP_GONE || status == HTTP_PRECONDITION ||
                status != null && status in NON_STANDARD_CLIENT_ERRORS

        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_GONE = 410
        private const val HTTP_PRECONDITION = 412
        private val NON_STANDARD_CLIENT_ERRORS = 452..499
    }
}

/**
 * What [BrowserReads] asks: [url] from a blank page of [pageUrl]'s origin with the browser's
 * [userAgent]. [wantsText]: the answer's text is read, at most [maxBytes] characters (a manifest,
 * a page); otherwise only its status and headers (a file, whose body is never downloaded).
 */
data class BrowserReadRequest(
    val url: String,
    val pageUrl: String,
    val userAgent: String?,
    val wantsText: Boolean,
    val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    override fun toString(): String = "BrowserReadRequest(wantsText=$wantsText, maxBytes=$maxBytes)"

    companion object {
        const val DEFAULT_MAX_BYTES = 1_048_576
    }
}

/**
 * The browser's answer: its HTTP [status] (0 when the browser could not ask, [error] says why by
 * a class name only), the address after redirects, its type and length, and its [text] when it
 * was asked for and fit. Never logged with its text or address.
 */
data class BrowserReadAnswer(
    val status: Int,
    val finalUrl: String? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val text: String? = null,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = status in SUCCESS

    override fun toString(): String =
        "BrowserReadAnswer(status=$status, contentType=$contentType, " +
            "contentLength=$contentLength, textChars=${text?.length}, error=$error)"

    private companion object {
        val SUCCESS = 200..299
    }
}
