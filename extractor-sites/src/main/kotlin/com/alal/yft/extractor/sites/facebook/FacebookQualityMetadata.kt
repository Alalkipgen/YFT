package com.alal.yft.extractor.sites.facebook

import java.net.URI
import java.util.Locale
import org.w3c.dom.Element

/** Heights are used only for the exact rendition URL; no HD/SD-to-height guess is made. */
internal object FacebookQualityMetadata {
    fun heightsByUrl(manifests: List<String>): Map<String, Int> {
        val heights = mutableMapOf<String, MutableSet<Int>>()
        manifests.distinct().forEach { manifest ->
            val document = FacebookDashManifests.document(manifest) ?: return@forEach
            if (document.documentElement.localName != "MPD") return@forEach
            if (document.getElementsByTagNameNS("*", "ContentProtection").length > 0) {
                return@forEach
            }
            val representations = document.getElementsByTagNameNS("*", "Representation")
            repeat(representations.length) { index ->
                val representation = representations.item(index) as? Element ?: return@repeat
                val height = representation.getAttribute("height").toIntOrNull()
                    ?.takeIf { it > 0 } ?: return@repeat
                val urls = representation.getElementsByTagNameNS("*", "BaseURL")
                repeat(urls.length) urlLoop@{ urlIndex ->
                    val key = key(urls.item(urlIndex).textContent.trim()) ?: return@urlLoop
                    heights.getOrPut(key, ::mutableSetOf) += height
                }
            }
        }
        // Conflicting heights for a shared path are ambiguous, never pick the highest one.
        return heights.mapNotNull { (url, values) -> values.singleOrNull()?.let { url to it } }
            .toMap()
    }

    fun label(
        quality: String?,
        url: String,
        heights: Map<String, Int>,
        explicitHeight: Long? = null,
    ): String? {
        val height = explicitHeight?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
            ?: key(url)?.let(heights::get)
            ?: return quality
        return if (quality.isNullOrBlank()) "${height}p" else "${height}p · $quality"
    }

    private fun key(value: String): String? {
        val uri = runCatching { URI(value).normalize() }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val port = uri.port.takeIf { it != -1 } ?: 443
        return "$host:$port${uri.rawPath}"
    }
}
