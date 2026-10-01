package com.alal.yft.core.download

import com.alal.yft.core.model.media.MediaTrackType
import java.io.StringReader
import java.security.MessageDigest
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.HttpUrl
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

internal object DashDownloadManifestParser {
    fun parse(
        manifest: String,
        manifestUrl: HttpUrl,
        representationId: String,
        expectedTrackType: MediaTrackType,
        maxChunks: Int,
    ): Result {
        require(representationId.isNotBlank())
        require(maxChunks > 0)
        val document = runCatching {
            secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(manifest)))
        }.getOrNull() ?: return Result.Malformed
        val root = document.documentElement
        if (!root.localOrNodeName().equals(MPD, ignoreCase = true)) {
            return Result.Malformed
        }
        if (root.attribute("type")?.equals("dynamic", ignoreCase = true) == true) {
            return Result.Unsupported
        }
        if (root.descendantElements(CONTENT_PROTECTION).isNotEmpty()) {
            return Result.DrmProtected
        }

        val representations = root.descendantElements(REPRESENTATION)
            .filter { it.attribute("id") == representationId }
        if (representations.size != 1) return Result.Unsupported
        val representation = representations.single()
        val adaptation = representation.ancestor(ADAPTATION_SET) ?: return Result.Malformed
        val period = adaptation.ancestor(PERIOD) ?: return Result.Malformed
        val inferredTrackType = inferTrackType(representation, adaptation)
        val trackType = inferredTrackType ?: expectedTrackType
        if (
            expectedTrackType != MediaTrackType.AUDIO_VIDEO &&
            trackType != expectedTrackType
        ) {
            return Result.Unsupported
        }

        val hierarchy = listOf(root, period, adaptation, representation)
        val resolvedBase = resolveBaseUrl(manifestUrl, hierarchy)
            ?: return Result.Malformed
        val mimeType = representation.attribute("mimeType")
            ?: adaptation.attribute("mimeType")
        val codecs = (
            representation.attribute("codecs")
                ?: adaptation.attribute("codecs")
            ).toCodecs()
        val durationMillis = period.attribute("duration").toDurationMillis()
            ?: root.attribute("mediaPresentationDuration").toDurationMillis()

        val chunks = try {
            when {
                hierarchy.hasChild(SEGMENT_BASE) -> return Result.Unsupported
                hierarchy.hasChild(SEGMENT_LIST) -> parseSegmentList(
                    hierarchy = hierarchy,
                    baseUrl = resolvedBase.url,
                    manifestUrl = manifestUrl,
                    maxChunks = maxChunks,
                )
                hierarchy.hasChild(SEGMENT_TEMPLATE) -> parseSegmentTemplate(
                    hierarchy = hierarchy,
                    baseUrl = resolvedBase.url,
                    manifestUrl = manifestUrl,
                    representationId = representationId,
                    bandwidth = representation.positiveLong("bandwidth"),
                    durationMillis = durationMillis,
                    maxChunks = maxChunks,
                )
                resolvedBase.representationSpecific -> listOf(
                    DashDownloadChunk(
                        index = 0,
                        url = resolvedBase.url,
                        byteRange = null,
                        type = DashChunkType.MEDIA,
                    ),
                )
                else -> return Result.Unsupported
            }
        } catch (_: TooManyChunksException) {
            return Result.TooManyChunks
        } ?: return Result.Malformed

        if (chunks.size > maxChunks) return Result.TooManyChunks
        if (chunks.none { it.type == DashChunkType.MEDIA }) return Result.Malformed
        return Result.Parsed(
            chunks = chunks,
            fingerprint = fingerprint(
                representationId = representationId,
                trackType = trackType,
                chunks = chunks,
            ),
            trackType = trackType,
            mimeType = mimeType,
            codecs = codecs,
        )
    }

    private fun parseSegmentList(
        hierarchy: List<Element>,
        baseUrl: HttpUrl,
        manifestUrl: HttpUrl,
        maxChunks: Int,
    ): List<DashDownloadChunk>? {
        val segmentList = hierarchy.nearestChild(SEGMENT_LIST) ?: return null
        val chunks = mutableListOf<DashDownloadChunk>()
        segmentList.singleOptionalChild(INITIALIZATION)?.let { initialization ->
            val source = initialization.attribute("sourceURL")
            val target = if (source == null) {
                baseUrl
            } else {
                resolve(baseUrl, manifestUrl, source) ?: return null
            }
            chunks += DashDownloadChunk(
                index = chunks.size,
                url = target,
                byteRange = initialization.attribute("range")?.toByteRange()
                    ?: if (initialization.attribute("range") == null) null else return null,
                type = DashChunkType.INITIALIZATION,
            )
        }
        val segmentUrls = segmentList.childElements(SEGMENT_URL)
        if (segmentUrls.isEmpty()) return null
        segmentUrls.forEach { segment ->
            val media = segment.attribute("media")
            val rangeValue = segment.attribute("mediaRange")
            if (media == null && rangeValue == null) return null
            val target = if (media == null) {
                baseUrl
            } else {
                resolve(baseUrl, manifestUrl, media) ?: return null
            }
            chunks += DashDownloadChunk(
                index = chunks.size,
                url = target,
                byteRange = rangeValue?.toByteRange()
                    ?: if (rangeValue == null) null else return null,
                type = DashChunkType.MEDIA,
            )
            if (chunks.size > maxChunks) throw TooManyChunksException
        }
        return chunks
    }

    private fun parseSegmentTemplate(
        hierarchy: List<Element>,
        baseUrl: HttpUrl,
        manifestUrl: HttpUrl,
        representationId: String,
        bandwidth: Long?,
        durationMillis: Long?,
        maxChunks: Int,
    ): List<DashDownloadChunk>? {
        val templates = hierarchy.mapNotNull { element ->
            element.singleOptionalChild(SEGMENT_TEMPLATE)
        }
        if (templates.isEmpty()) return null
        val attributes = linkedMapOf<String, String>()
        templates.forEach { template ->
            TEMPLATE_ATTRIBUTES.forEach { name ->
                template.attribute(name)?.let { attributes[name] = it }
            }
        }
        val mediaTemplate = attributes["media"] ?: return null
        val initializationTemplate = attributes["initialization"]
        val timescale = attributes["timescale"]?.toLongOrNull()?.takeIf { it > 0 } ?: 1
        val startNumber = attributes["startNumber"]?.toLongOrNull()?.takeIf { it >= 0 } ?: 1
        val presentationTimeOffset = attributes["presentationTimeOffset"]
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?: 0
        val timeline = templates.asReversed()
            .firstNotNullOfOrNull { it.singleOptionalChild(SEGMENT_TIMELINE) }
        val points = if (timeline != null) {
            expandTimeline(
                timeline = timeline,
                startNumber = startNumber,
                durationMillis = durationMillis,
                timescale = timescale,
                maxChunks = maxChunks,
            )
        } else {
            expandFixedDuration(
                mediaTemplate = mediaTemplate,
                duration = attributes["duration"]?.toLongOrNull()?.takeIf { it > 0 },
                endNumber = attributes["endNumber"]?.toLongOrNull()?.takeIf { it >= 0 },
                startNumber = startNumber,
                presentationTimeOffset = presentationTimeOffset,
                durationMillis = durationMillis,
                timescale = timescale,
                maxChunks = maxChunks,
            )
        } ?: return null

        val chunks = mutableListOf<DashDownloadChunk>()
        if (initializationTemplate != null) {
            val path = expandTemplate(
                template = initializationTemplate,
                representationId = representationId,
                bandwidth = bandwidth,
                number = startNumber,
                time = points.firstOrNull()?.time ?: presentationTimeOffset,
            ) ?: return null
            chunks += DashDownloadChunk(
                index = chunks.size,
                url = resolve(baseUrl, manifestUrl, path) ?: return null,
                byteRange = null,
                type = DashChunkType.INITIALIZATION,
            )
        }
        points.forEach { point ->
            val path = expandTemplate(
                template = mediaTemplate,
                representationId = representationId,
                bandwidth = bandwidth,
                number = point.number,
                time = point.time,
            ) ?: return null
            chunks += DashDownloadChunk(
                index = chunks.size,
                url = resolve(baseUrl, manifestUrl, path) ?: return null,
                byteRange = null,
                type = DashChunkType.MEDIA,
            )
            if (chunks.size > maxChunks) throw TooManyChunksException
        }
        return chunks
    }

    private fun expandTimeline(
        timeline: Element,
        startNumber: Long,
        durationMillis: Long?,
        timescale: Long,
        maxChunks: Int,
    ): List<SegmentPoint>? {
        val entries = timeline.childElements(TIMELINE_ENTRY)
        if (entries.isEmpty()) return null
        val output = mutableListOf<SegmentPoint>()
        var currentTime = 0L
        var currentNumber = startNumber
        entries.forEachIndexed { index, entry ->
            val duration = entry.attribute("d")?.toLongOrNull()?.takeIf { it > 0 }
                ?: return null
            val explicitTime = entry.attribute("t")?.toLongOrNull()?.takeIf { it >= 0 }
            val segmentStart = explicitTime ?: currentTime
            val rawRepeat = entry.attribute("r")?.toIntOrNull() ?: 0
            if (rawRepeat < -1) return null
            val repeat = if (rawRepeat == -1) {
                val nextTime = entries.getOrNull(index + 1)
                    ?.attribute("t")
                    ?.toLongOrNull()
                    ?.takeIf { it >= 0 }
                val boundary = nextTime ?: durationMillis
                    ?.toTimescaleUnits(timescale)
                    ?: return null
                if (boundary <= segmentStart) return null
                val segmentCount = ceilDivision(boundary - segmentStart, duration)
                if (segmentCount <= 0 || segmentCount > Int.MAX_VALUE) return null
                segmentCount.toInt() - 1
            } else {
                rawRepeat
            }
            if (repeat >= maxChunks || output.size + repeat + 1 > maxChunks) {
                throw TooManyChunksException
            }
            repeat(repeat + 1) { offset ->
                val time = segmentStart.safeAdd(duration.safeMultiply(offset.toLong()) ?: return null)
                    ?: return null
                output += SegmentPoint(number = currentNumber, time = time)
                currentNumber = currentNumber.safeAdd(1) ?: return null
            }
            currentTime = segmentStart.safeAdd(
                duration.safeMultiply(repeat.toLong() + 1) ?: return null,
            ) ?: return null
        }
        return output.takeIf(List<SegmentPoint>::isNotEmpty)
    }

    private fun expandFixedDuration(
        mediaTemplate: String,
        duration: Long?,
        endNumber: Long?,
        startNumber: Long,
        presentationTimeOffset: Long,
        durationMillis: Long?,
        timescale: Long,
        maxChunks: Int,
    ): List<SegmentPoint>? {
        if (!mediaTemplate.contains("\$Number") && !mediaTemplate.contains("\$Time")) {
            return listOf(SegmentPoint(startNumber, presentationTimeOffset))
        }
        val segmentDuration = duration ?: return null
        val count = if (endNumber != null) {
            if (endNumber < startNumber) return null
            endNumber - startNumber + 1
        } else {
            val totalUnits = durationMillis?.toTimescaleUnits(timescale) ?: return null
            ceilDivision(totalUnits, segmentDuration)
        }
        if (count <= 0) return null
        if (count > maxChunks) throw TooManyChunksException
        return buildList {
            repeat(count.toInt()) { offset ->
                val offsetLong = offset.toLong()
                val number = startNumber.safeAdd(offsetLong) ?: return null
                val time = presentationTimeOffset.safeAdd(
                    segmentDuration.safeMultiply(offsetLong) ?: return null,
                ) ?: return null
                add(SegmentPoint(number, time))
            }
        }
    }

    private fun expandTemplate(
        template: String,
        representationId: String,
        bandwidth: Long?,
        number: Long,
        time: Long,
    ): String? {
        val escapedMarker = "\u0000"
        var invalid = false
        val expanded = TEMPLATE_TOKEN.replace(template.replace("\$\$", escapedMarker)) { match ->
            val name = match.groupValues[1]
            val width = match.groupValues[2].toIntOrNull()
            val value = when (name) {
                "RepresentationID" -> representationId
                "Bandwidth" -> bandwidth?.toString()
                "Number" -> formatTemplateNumber(number, width)
                "Time" -> formatTemplateNumber(time, width)
                else -> null
            }
            if (value == null) {
                invalid = true
                ""
            } else {
                value
            }
        }.replace(escapedMarker, "$")
        if (invalid || '$' in expanded || expanded.isBlank()) return null
        return expanded
    }

    private fun formatTemplateNumber(value: Long, width: Int?): String? {
        if (width == null) return value.toString()
        if (width !in 1..18) return null
        return value.toString().padStart(width, '0')
    }

    private fun resolveBaseUrl(
        manifestUrl: HttpUrl,
        hierarchy: List<Element>,
    ): ResolvedBase? {
        var current = manifestUrl
        var representationSpecific = false
        hierarchy.forEachIndexed { index, element ->
            val children = element.childElements(BASE_URL)
            if (children.size > 1) return null
            val raw = children.singleOrNull()?.textContent?.trim()?.takeIf(String::isNotEmpty)
                ?: return@forEachIndexed
            current = resolve(current, manifestUrl, raw) ?: return null
            if (index == hierarchy.lastIndex) representationSpecific = true
        }
        return ResolvedBase(current, representationSpecific)
    }

    private fun resolve(base: HttpUrl, manifestUrl: HttpUrl, child: String): HttpUrl? {
        val target = base.resolve(child)?.takeIf(HttpUrl::isSafeDownloadUrl) ?: return null
        if (manifestUrl.isHttps && !target.isHttps) return null
        return target
    }

    private fun inferTrackType(
        representation: Element,
        adaptation: Element,
    ): MediaTrackType? {
        val contentType = representation.attribute("contentType")
            ?: adaptation.attribute("contentType")
        val mimeType = representation.attribute("mimeType")
            ?: adaptation.attribute("mimeType")
        return when {
            contentType.equals("video", ignoreCase = true) -> MediaTrackType.VIDEO
            contentType.equals("audio", ignoreCase = true) -> MediaTrackType.AUDIO
            mimeType?.startsWith("video/", ignoreCase = true) == true -> MediaTrackType.VIDEO
            mimeType?.startsWith("audio/", ignoreCase = true) == true -> MediaTrackType.AUDIO
            representation.attribute("width") != null -> MediaTrackType.VIDEO
            representation.attribute("audioSamplingRate") != null -> MediaTrackType.AUDIO
            adaptation.attribute("audioSamplingRate") != null -> MediaTrackType.AUDIO
            else -> null
        }
    }

    private fun fingerprint(
        representationId: String,
        trackType: MediaTrackType,
        chunks: List<DashDownloadChunk>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(representationId.toByteArray(Charsets.UTF_8))
        digest.update(trackType.name.toByteArray(Charsets.UTF_8))
        chunks.forEach { chunk ->
            val stableUrl = chunk.url.newBuilder()
                .query(null)
                .fragment(null)
                .build()
            val line = buildString {
                append(chunk.index)
                append('|')
                append(chunk.type.name)
                append('|')
                append(stableUrl)
                append('|')
                append(chunk.byteRange?.offset)
                append('|')
                append(chunk.byteRange?.length)
                append('\n')
            }
            digest.update(line.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }
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

    private fun List<Element>.hasChild(localName: String): Boolean =
        any { it.childElements(localName).isNotEmpty() }

    private fun List<Element>.nearestChild(localName: String): Element? =
        asReversed().firstNotNullOfOrNull { it.singleOptionalChild(localName) }

    private fun Element.singleOptionalChild(localName: String): Element? {
        val matching = childElements(localName)
        return if (matching.size <= 1) matching.singleOrNull() else null
    }

    private fun Element.ancestor(localName: String): Element? {
        var current = parentNode
        while (current != null) {
            if (current.nodeType == Node.ELEMENT_NODE && current.localOrNodeName() == localName) {
                return current as Element
            }
            current = current.parentNode
        }
        return null
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

    private fun Element.positiveLong(name: String): Long? =
        attribute(name)?.toLongOrNull()?.takeIf { it > 0 }

    private fun String?.toCodecs(): List<String> = this
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()

    private fun String.toByteRange(): ResolvedByteRange? {
        val start = substringBefore('-', missingDelimiterValue = "").trim()
            .toLongOrNull()
            ?.takeIf { it >= 0 }
            ?: return null
        val end = substringAfter('-', missingDelimiterValue = "").trim()
            .toLongOrNull()
            ?.takeIf { it >= start }
            ?: return null
        if (end == Long.MAX_VALUE) return null
        return ResolvedByteRange(offset = start, length = end - start + 1)
    }

    private fun String?.toDurationMillis(): Long? = this?.let { value ->
        val match = ISO_8601_DURATION.matchEntire(value) ?: return null
        if (match.groupValues.drop(1).none(String::isNotEmpty)) return null
        val days = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val hours = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[3].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[4].toDoubleOrNull() ?: 0.0
        val totalMillis = (
            days * MILLIS_PER_DAY +
                hours * MILLIS_PER_HOUR +
                minutes * MILLIS_PER_MINUTE +
                seconds * MILLIS_PER_SECOND
            )
        totalMillis
            .takeIf { it.isFinite() && it >= 0 && it <= Long.MAX_VALUE }
            ?.toLong()
    }

    private fun Long.toTimescaleUnits(timescale: Long): Long? {
        if (this < 0 || timescale <= 0) return null
        val product = safeMultiply(timescale) ?: return null
        return ceilDivision(product, MILLIS_PER_SECOND.toLong())
    }

    private fun Long.safeAdd(other: Long): Long? =
        if (other >= 0 && this <= Long.MAX_VALUE - other) this + other else null

    private fun Long.safeMultiply(other: Long): Long? =
        if (this >= 0 && other >= 0 && (this == 0L || other <= Long.MAX_VALUE / this)) {
            this * other
        } else {
            null
        }

    private fun ceilDivision(numerator: Long, denominator: Long): Long {
        if (numerator <= 0 || denominator <= 0) return 0
        return 1 + (numerator - 1) / denominator
    }

    sealed interface Result {
        data class Parsed(
            val chunks: List<DashDownloadChunk>,
            val fingerprint: String,
            val trackType: MediaTrackType,
            val mimeType: String?,
            val codecs: List<String>,
        ) : Result

        data object DrmProtected : Result
        data object Unsupported : Result
        data object TooManyChunks : Result
        data object Malformed : Result
    }

    private data class ResolvedBase(
        val url: HttpUrl,
        val representationSpecific: Boolean,
    )

    private data class SegmentPoint(
        val number: Long,
        val time: Long,
    )

    private object TooManyChunksException : RuntimeException(null, null, false, false)

    private const val MPD = "MPD"
    private const val PERIOD = "Period"
    private const val ADAPTATION_SET = "AdaptationSet"
    private const val REPRESENTATION = "Representation"
    private const val CONTENT_PROTECTION = "ContentProtection"
    private const val BASE_URL = "BaseURL"
    private const val SEGMENT_BASE = "SegmentBase"
    private const val SEGMENT_LIST = "SegmentList"
    private const val SEGMENT_URL = "SegmentURL"
    private const val SEGMENT_TEMPLATE = "SegmentTemplate"
    private const val SEGMENT_TIMELINE = "SegmentTimeline"
    private const val TIMELINE_ENTRY = "S"
    private const val INITIALIZATION = "Initialization"
    private const val MILLIS_PER_SECOND = 1_000.0
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
    private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
    private val TEMPLATE_ATTRIBUTES = setOf(
        "media",
        "initialization",
        "timescale",
        "duration",
        "startNumber",
        "endNumber",
        "presentationTimeOffset",
    )
    private val TEMPLATE_TOKEN = Regex(
        """\$(RepresentationID|Bandwidth|Number|Time)(?:%0(\d{1,2})d)?\$""",
    )
    private val ISO_8601_DURATION = Regex(
        """^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$""",
    )
}

internal data class DashDownloadChunk(
    val index: Int,
    val url: HttpUrl,
    val byteRange: ResolvedByteRange?,
    val type: DashChunkType,
) {
    override fun toString(): String = buildString {
        append("DashDownloadChunk(index=")
        append(index)
        append(", url=[REDACTED], byteRange=")
        append(byteRange)
        append(", type=")
        append(type)
        append(')')
    }
}

internal enum class DashChunkType {
    INITIALIZATION,
    MEDIA,
}
