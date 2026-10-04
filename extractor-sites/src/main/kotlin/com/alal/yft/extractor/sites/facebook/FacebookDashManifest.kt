package com.alal.yft.extractor.sites.facebook

import java.io.StringReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

/** Whether a manifest track carries the picture or the sound. */
internal enum class FacebookTrackKind { VIDEO, AUDIO }

/**
 * One whole-file track of a Facebook DASH manifest: the complete MP4 at [url].
 *
 * Facebook's manifests use the on-demand profile, so every representation is one file at its
 * `BaseURL`; the `SegmentBase` index only serves players that seek and is not needed to
 * download the whole file (live check, 2026-10-05).
 */
internal data class FacebookDashTrack(
    val url: String,
    val kind: FacebookTrackKind,
    val mimeType: String,
    val codec: String,
    val width: Int?,
    val height: Int?,
    val framesPerSecond: Double?,
    val bandwidthBitsPerSecond: Long?,
) {
    /** The address carries signed parameters, so it never prints. */
    override fun toString(): String =
        "FacebookDashTrack(kind=$kind, codec=$codec, width=$width, height=$height, " +
            "bandwidth=$bandwidthBitsPerSecond)"
}

internal data class FacebookDashManifest(
    val durationMillis: Long?,
    val tracks: List<FacebookDashTrack>,
)

/**
 * Reads the DASH manifest Facebook inlines in its page (`dash_manifest_xml_string`, older
 * `dash_manifest`) into whole-file tracks.
 *
 * Only plain MP4 representations with an absolute HTTPS address qualify. A live or protected
 * manifest, segment templates and segment lists describe no single file and are skipped, and a
 * video track must state its picture size, so no height is ever guessed.
 */
internal object FacebookDashManifests {
    private const val MAX_MANIFEST_CHARS = 262_144
    private const val MAX_TRACKS = 64
    private const val MAX_FRAME_RATE = 300.0
    private const val VIDEO_MP4 = "video/mp4"
    private const val AUDIO_MP4 = "audio/mp4"
    private val SEGMENT_ADDRESSING = setOf("SegmentTemplate", "SegmentList")
    private val DURATION = Regex(
        """P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?""",
    )

    fun parse(manifest: String): FacebookDashManifest? {
        val document = document(manifest) ?: return null
        val root = document.documentElement
        if (root.localName != "MPD") return null
        if (root.getAttribute("type").equals("dynamic", ignoreCase = true)) return null
        if (document.getElementsByTagNameNS("*", "ContentProtection").length > 0) return null

        val tracks = LinkedHashMap<String, FacebookDashTrack>()
        children(root, "Period").filterNot(::addressesSegments).forEach { period ->
            children(period, "AdaptationSet").filterNot(::addressesSegments).forEach { set ->
                children(set, "Representation").forEach { representation ->
                    if (tracks.size >= MAX_TRACKS) return@forEach
                    val track = track(set, representation) ?: return@forEach
                    tracks.putIfAbsent(track.url, track)
                }
            }
        }
        return FacebookDashManifest(
            durationMillis = durationMillis(root.getAttribute("mediaPresentationDuration")),
            tracks = tracks.values.toList(),
        )
    }

    /**
     * A bounded, entity-free XML document, or null. Older pages URL-encode the manifest, so a
     * value that starts with an encoded `<` is decoded once first.
     */
    fun document(manifest: String): Document? {
        if (manifest.length > MAX_MANIFEST_CHARS) return null
        val xml = if (manifest.trimStart().startsWith("%3C", ignoreCase = true)) {
            runCatching { URLDecoder.decode(manifest, StandardCharsets.UTF_8.name()) }
                .getOrNull() ?: return null
        } else {
            manifest
        }
        return runCatching {
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
            }.parse(InputSource(StringReader(xml)))
        }.getOrNull()
    }

    private fun track(set: Element, representation: Element): FacebookDashTrack? {
        if (addressesSegments(representation)) return null
        val url = children(representation, "BaseURL").singleOrNull()
            ?.textContent?.trim()?.takeIf(::isHttpsUrl) ?: return null
        val mimeType = attribute(representation, set, "mimeType")?.lowercase(Locale.US)
        val kind = when (mimeType) {
            VIDEO_MP4 -> FacebookTrackKind.VIDEO
            AUDIO_MP4 -> FacebookTrackKind.AUDIO
            else -> return null
        }
        // One codec per file: a muxed or unnamed representation is not a separate track.
        val codec = attribute(representation, set, "codecs")?.takeUnless { ',' in it }
            ?: return null
        val video = kind == FacebookTrackKind.VIDEO
        val width = attribute(representation, set, "width")?.toIntOrNull()?.takeIf { it > 0 }
        val height = attribute(representation, set, "height")?.toIntOrNull()?.takeIf { it > 0 }
        if (video && (width == null || height == null)) return null
        return FacebookDashTrack(
            url = url,
            kind = kind,
            mimeType = mimeType,
            codec = codec,
            width = width.takeIf { video },
            height = height.takeIf { video },
            framesPerSecond = attribute(representation, set, "frameRate")
                ?.takeIf { video }?.let(::frameRate),
            bandwidthBitsPerSecond = representation.getAttribute("bandwidth").toLongOrNull()
                ?.takeIf { it > 0 },
        )
    }

    /** The representation's own value, else the one its adaptation set states for all. */
    private fun attribute(representation: Element, set: Element, name: String): String? =
        representation.getAttribute(name).trim().takeIf(String::isNotEmpty)
            ?: set.getAttribute(name).trim().takeIf(String::isNotEmpty)

    private fun addressesSegments(element: Element): Boolean =
        SEGMENT_ADDRESSING.any { children(element, it).isNotEmpty() }

    private fun children(parent: Element, localName: String): List<Element> = buildList {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && child.localName == localName) add(child)
            child = child.nextSibling
        }
    }

    private fun isHttpsUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) && value.length > "https://".length &&
            value.none { it.isWhitespace() || it.isISOControl() }

    /** `15360/512`, `30000/1001` or `25`. */
    private fun frameRate(value: String): Double? {
        val numerator = value.substringBefore('/').toDoubleOrNull() ?: return null
        val denominator = if ('/' in value) {
            value.substringAfter('/').toDoubleOrNull() ?: return null
        } else {
            1.0
        }
        if (denominator <= 0.0) return null
        return (numerator / denominator).takeIf { it > 0.0 && it <= MAX_FRAME_RATE }
    }

    /** An ISO 8601 duration such as `PT625.452698S` or `PT1H2M3.5S`. */
    private fun durationMillis(value: String): Long? {
        val match = DURATION.matchEntire(value.trim()) ?: return null
        val (days, hours, minutes, seconds) = match.destructured
        val total = (days.toLongOrNull() ?: 0) * 86_400_000L +
            (hours.toLongOrNull() ?: 0) * 3_600_000L +
            (minutes.toLongOrNull() ?: 0) * 60_000L +
            ((seconds.toDoubleOrNull() ?: 0.0) * 1_000).toLong()
        return total.takeIf { it > 0 }
    }
}
