package com.alal.yft.extractor.sites.facebook

import java.io.StringReader
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

/** Heights are used only for the exact rendition URL; no HD/SD-to-height guess is made. */
internal object FacebookQualityMetadata {
    private const val MAX_MANIFEST_CHARS = 262_144

    fun heightsByUrl(manifests: List<String>): Map<String, Int> {
        val heights = mutableMapOf<String, MutableSet<Int>>()
        manifests.distinct().forEach { manifest ->
            if (manifest.length > MAX_MANIFEST_CHARS) return@forEach
            val document = runCatching {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isXIncludeAware = false
                    isExpandEntityReferences = false
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    setFeature(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd", false,
                    )
                    setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                    setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
                }
                factory.newDocumentBuilder().apply {
                    setEntityResolver { _, _ -> InputSource(StringReader("")) }
                    setErrorHandler(object : DefaultHandler() {
                        override fun error(error: SAXParseException) = throw error
                        override fun fatalError(error: SAXParseException) = throw error
                    })
                }.parse(InputSource(StringReader(manifest)))
            }.getOrNull() ?: return@forEach
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
