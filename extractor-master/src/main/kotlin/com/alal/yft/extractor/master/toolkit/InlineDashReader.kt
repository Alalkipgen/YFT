package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.master.MasterRequest
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * The Facebook-style inline MPD case: whole-file BaseURL + SegmentBase tracks only.
 * SegmentTemplate/SegmentList need a real manifest URL and stay with the existing resolver.
 */
internal object InlineDashReader {
    /**
     * [bandwidths]: each track's MPD `bandwidth` by its address (VK's pack proof: a page
     * manifest's sound is chosen by it, never shown; the tracks themselves state no bitrate).
     */
    data class Result(
        val candidates: List<MediaCandidate>,
        val drm: Boolean = false,
        val bandwidths: Map<String, Long> = emptyMap(),
    )

    fun read(
        xml: String,
        request: MasterRequest,
        contentId: String?,
        key: String,
    ): Result {
        if (xml.length > 256 * 1024) return Result(emptyList())
        // v1.0.2 R9: Android's own parser (`org.apache.harmony.xml.parsers`) throws for
        // `isXIncludeAware`, for every feature but namespaces and validation and for every
        // attribute, so asking them all at once left every inline manifest unread on a phone (the
        // JVM's parser knows them all), as F1's DASH reader did. Each one is asked on its own and
        // one the parser does not know is skipped; a document type is refused before parsing.
        if (DOCUMENT_TYPE.containsMatchIn(xml)) return Result(emptyList())
        val root = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                ifKnown { isXIncludeAware = false }
                ifKnown { setExpandEntityReferences(false) }
                ifKnown { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                ifKnown { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                ifKnown {
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                }
                ifKnown {
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                }
                ifKnown { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
                ifKnown { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
            }
            factory.newDocumentBuilder().apply {
                setErrorHandler(DefaultHandler())
            }.parse(InputSource(StringReader(xml))).documentElement
        }.getOrNull() ?: return Result(emptyList())
        if (root.localName != "MPD" || root.getAttribute("type") == "dynamic") {
            return Result(emptyList())
        }
        if (root.getElementsByTagNameNS("*", "ContentProtection").length > 0) {
            return Result(emptyList(), drm = true)
        }
        val tracks = mutableListOf<MediaCandidate>()
        val bandwidths = mutableMapOf<String, Long>()
        val representations = root.getElementsByTagNameNS("*", "Representation")
        for (index in 0 until minOf(representations.length, 100)) {
            val representation = representations.item(index) as? Element ?: continue
            val ancestry = generateSequence(representation) { it.parentNode as? Element }
                .toList().asReversed()
            if (ancestry.any { parent ->
                    parent.children("SegmentTemplate").isNotEmpty() ||
                        parent.children("SegmentList").isNotEmpty()
                }
            ) {
                continue
            }
            var address = request.pageUrl
            var hasBase = false
            ancestry.forEach { parent ->
                parent.children("BaseURL").firstOrNull()?.textContent?.trim()?.let {
                    address = UrlPolicy.resolve(address, it) ?: return@let
                    hasBase = true
                }
            }
            if (!hasBase) continue
            val mime = representation.getAttribute("mimeType").ifBlank {
                ancestry.asReversed().firstNotNullOfOrNull {
                    it.getAttribute("mimeType").takeIf(String::isNotBlank)
                }.orEmpty()
            }
            val codec = ancestry.asReversed().firstNotNullOfOrNull {
                it.getAttribute("codecs").takeIf(String::isNotBlank)
            }
            val candidate = MediaCandidate(
                pageUrl = request.pageUrl,
                mediaUrl = address,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = mime.takeIf(String::isNotBlank),
                codecs = codec?.split(',')?.map(String::trim).orEmpty(),
                width = representation.getAttribute("width").toIntOrNull()?.takeIf { it > 0 },
                height = representation.getAttribute("height").toIntOrNull()?.takeIf { it > 0 },
                // MPD bandwidth can be peak bandwidth, not an honest whole-file average.
                bitrateBitsPerSecond = null,
                requestContext = UrlPolicy.context(
                    request.requestContext, request.requestContext.pageUrl.orEmpty(),
                    address, request.pageUrl,
                ),
                expiresAtEpochMs = UrlPolicy.expiry(address),
                observedAtEpochMs = request.nowEpochMs,
                drmHint = false,
                confidence = CandidateConfidence.HIGH,
                pageRole = PageMediaRole.MAIN,
                videoId = contentId?.let { UrlPolicy.videoKey(request.pageUrl, it) },
                pageVideoKey = key,
            )
            tracks += candidate
            representation.getAttribute("bandwidth").toLongOrNull()?.takeIf { it > 0 }
                ?.let { bandwidths.putIfAbsent(address, it) }
        }
        val audio = tracks.filter { it.mimeType == "audio/mp4" }
            .filter { it.codecs.any { codec -> codec.startsWith("mp4a") } }
            .maxByOrNull { it.bitrateBitsPerSecond ?: 0 }
        return Result(
            tracks.map { track ->
                if (
                    audio != null && track.mimeType == "video/mp4" &&
                    track.codecs.any { it.startsWith("avc1") }
                ) {
                    track.copy(audioCompanion = CandidateFactory.companion(audio))
                } else {
                    track
                }
            },
            bandwidths = bandwidths,
        )
    }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).mapNotNull { index ->
            (childNodes.item(index) as? Element)?.takeIf { it.localName == name }
        }

    /** One hardening setting; a parser that does not know it throws, and it is skipped (R9). */
    private inline fun ifKnown(setting: () -> Unit) {
        try {
            setting()
        } catch (_: ParserConfigurationException) {
        } catch (_: IllegalArgumentException) {
        } catch (_: UnsupportedOperationException) {
        }
    }

    /** A document type or an entity declaration (R9): refused before any parser reads it. */
    private val DOCUMENT_TYPE = Regex("""<!\s*(?:DOCTYPE|ENTITY)""", RegexOption.IGNORE_CASE)
}