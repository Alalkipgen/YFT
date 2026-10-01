package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantSupport
import java.io.StringReader
import java.time.Duration
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

internal object DashManifestParser {
    fun parse(
        manifest: String,
        manifestUrl: String,
        requestContext: BrowserRequestContext,
        expiresAtEpochMs: Long?,
    ): ManifestParseResult {
        val document = runCatching {
            secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(manifest)))
        }.getOrNull() ?: return ManifestParseResult.Malformed
        val root = document.documentElement
        if (!root.localOrNodeName().equals("MPD", ignoreCase = true)) {
            return ManifestParseResult.Malformed
        }
        if (document.getElementsByTagNameNS("*", "ContentProtection").length > 0) {
            return ManifestParseResult.DrmProtected
        }

        val durationMillis = root.attribute("mediaPresentationDuration")
            .toDurationMillis()
        val variants = mutableListOf<MediaVariant>()
        var representationIndex = 0
        root.descendantElements("AdaptationSet").forEach { adaptation ->
            val adaptationMime = adaptation.attribute("mimeType")
            val adaptationContentType = adaptation.attribute("contentType")
            val adaptationCodecs = adaptation.attribute("codecs").toCodecs()
            val adaptationLanguage = adaptation.attribute("lang")
            adaptation.childElements("Representation").forEach { representation ->
                val mimeType = representation.attribute("mimeType") ?: adaptationMime
                val contentType = representation.attribute("contentType") ?: adaptationContentType
                val width = representation.positiveInt("width") ?: adaptation.positiveInt("width")
                val height = representation.positiveInt("height") ?: adaptation.positiveInt("height")
                val trackType = trackType(
                    contentType = contentType,
                    mimeType = mimeType,
                    width = width,
                    representation = representation,
                    adaptation = adaptation,
                ) ?: return@forEach
                val codecs = representation.attribute("codecs").toCodecs()
                    .ifEmpty { adaptationCodecs }
                val bitrate = representation.positiveLong("bandwidth")
                val estimate = estimateSize(bitrate, durationMillis)
                val representationId = representation.attribute("id")
                variants += MediaVariant(
                    id = "dash-${trackType.name.lowercase(Locale.US)}-$representationIndex",
                    playbackUrl = manifestUrl,
                    kind = MediaKind.DASH,
                    trackType = trackType,
                    requestContext = requestContext,
                    manifestVariantId = representationId,
                    label = representationId ?: qualityLabel(
                        height = height,
                        bitrate = bitrate,
                        fallback = "DASH representation ${representationIndex + 1}",
                    ),
                    mimeType = mimeType ?: DASH_MIME_TYPE,
                    container = "DASH",
                    codecs = codecs,
                    width = width,
                    height = height,
                    framesPerSecond = (
                        representation.attribute("frameRate")
                            ?: adaptation.attribute("frameRate")
                        ).toFrameRate(),
                    bitrateBitsPerSecond = bitrate,
                    durationMillis = durationMillis,
                    sizeBytes = estimate.bytes,
                    sizeAccuracy = estimate.accuracy,
                    language = representation.attribute("lang") ?: adaptationLanguage,
                    support = if (CodecSupport.isSupported(codecs)) {
                        VariantSupport.SUPPORTED
                    } else {
                        VariantSupport.UNSUPPORTED_CODEC
                    },
                    expiresAtEpochMs = expiresAtEpochMs,
                )
                representationIndex += 1
            }
        }

        if (variants.isEmpty()) return ManifestParseResult.Malformed
        return ManifestParseResult.Parsed(variants, durationMillis)
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }

    private fun trackType(
        contentType: String?,
        mimeType: String?,
        width: Int?,
        representation: Element,
        adaptation: Element,
    ): MediaTrackType? = when {
        contentType.equals("video", ignoreCase = true) -> MediaTrackType.VIDEO
        contentType.equals("audio", ignoreCase = true) -> MediaTrackType.AUDIO
        mimeType?.startsWith("video/", ignoreCase = true) == true -> MediaTrackType.VIDEO
        mimeType?.startsWith("audio/", ignoreCase = true) == true -> MediaTrackType.AUDIO
        width != null -> MediaTrackType.VIDEO
        representation.attribute("audioSamplingRate") != null -> MediaTrackType.AUDIO
        adaptation.attribute("audioSamplingRate") != null -> MediaTrackType.AUDIO
        else -> null
    }

    private fun Element.descendantElements(localName: String): List<Element> =
        getElementsByTagNameNS("*", localName).let { nodes ->
            buildList {
                repeat(nodes.length) { index ->
                    (nodes.item(index) as? Element)?.let(::add)
                }
            }
        }

    private fun Element.childElements(localName: String): List<Element> = buildList {
        val children = childNodes
        repeat(children.length) { index ->
            val node = children.item(index)
            if (node.nodeType == Node.ELEMENT_NODE && node.localOrNodeName() == localName) {
                add(node as Element)
            }
        }
    }

    private fun Node.localOrNodeName(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.attribute(name: String): String? =
        getAttribute(name).trim().takeIf(String::isNotEmpty)

    private fun Element.positiveInt(name: String): Int? =
        attribute(name)?.toIntOrNull()?.takeIf { it > 0 }

    private fun Element.positiveLong(name: String): Long? =
        attribute(name)?.toLongOrNull()?.takeIf { it > 0 }

    private fun String?.toCodecs(): List<String> = this
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()

    private fun String?.toDurationMillis(): Long? = this?.let { value ->
        runCatching { Duration.parse(value).toMillis() }
            .getOrNull()
            ?.takeIf { it >= 0 }
    }

    private fun String?.toFrameRate(): Double? {
        val value = this ?: return null
        val numerator = value.substringBefore('/').toDoubleOrNull() ?: return null
        val denominator = value.substringAfter('/', missingDelimiterValue = "1")
            .toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        return (numerator / denominator).takeIf { it > 0 }
    }

    private fun qualityLabel(height: Int?, bitrate: Long?, fallback: String): String = when {
        height != null -> "${height}p"
        bitrate != null -> "${bitrate / 1_000} kbps"
        else -> fallback
    }

    private const val DASH_MIME_TYPE = "application/dash+xml"
}