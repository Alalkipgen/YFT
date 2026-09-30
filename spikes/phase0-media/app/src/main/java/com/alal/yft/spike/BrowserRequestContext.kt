package com.alal.yft.spike

/** Minimal request context required to replay browser-authorized media requests. */
data class BrowserRequestContext(
    val pageUrl: String?,
    val userAgent: String?,
    val cookie: String?,
    val observedHeaders: Map<String, String> = emptyMap(),
) {
    fun replayHeaders(): Map<String, String> {
        val blocked = setOf(
            "connection", "content-length", "host", "if-range", "proxy-connection",
            "range", "te", "transfer-encoding", "upgrade",
        )
        val output = linkedMapOf<String, String>()
        observedHeaders.forEach { (name, value) ->
            if (name.lowercase() !in blocked && value.isNotBlank()) output[name] = value
        }

        fun replace(name: String, value: String?) {
            if (value.isNullOrBlank()) return
            output.keys.filter { it.equals(name, ignoreCase = true) }
                .toList()
                .forEach(output::remove)
            output[name] = value
        }

        replace("User-Agent", userAgent)
        replace("Cookie", cookie)
        replace("Referer", pageUrl?.takeIf { it.startsWith("https://") })
        if (output.keys.none { it.equals("Accept", ignoreCase = true) }) output["Accept"] = "*/*"
        return output
    }
}
