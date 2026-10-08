package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * The Facebook-style inline MPD case: whole-file BaseURL + SegmentBase tracks only.
 * SegmentTemplate/SegmentList need a real manifest URL and stay with the existing resolver.
 */
internal object InlineDashReader {
    data class Result(val candidates: List<MediaCandidate>, val drm: Boolean = false)

    fun read(
        xml: String,
        request: MasterRequest,
        contentId: String?,
        key: String,
    ): Result {
        if (xml.length > 256 * 1024) return Result(emptyList())
        val root = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
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
                    request.requestContext, request.pageUrl, address, request.pageUrl,
                ),
                expiresAtEpochMs = UrlPolicy.expiry(address),
                observedAtEpochMs = request.nowEpochMs,
                drmHint = false,
                confidence = CandidateConfidence.HIGH,
                pageRole = PageMediaRole.MAIN,
                videoId = contentId?.let {
                    "master:${UrlPolicy.origin(request.pageUrl)?.substringAfter("https://")}:$it"
                },
                pageVideoKey = key,
            )
            tracks += candidate
        }
        val audio = tracks.filter { it.mimeType == "audio/mp4" }
            .filter { it.codecs.any { codec -> codec.startsWith("mp4a") } }
            .maxByOrNull { it.bitrateBitsPerSecond ?: 0 }
        return Result(tracks.map { track ->
            if (
                audio != null && track.mimeType == "video/mp4" &&
                track.codecs.any { it.startsWith("avc1") }
            ) {
                track.copy(audioCompanion = PayloadMediaReader.companion(audio))
            } else {
                track
            }
        })
    }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).mapNotNull { index ->
            (childNodes.item(index) as? Element)?.takeIf { it.localName == name }
        }
}