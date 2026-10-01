package com.alal.yft.core.download

import java.security.MessageDigest
import java.util.Locale
import okhttp3.HttpUrl

internal object HlsDownloadManifestParser {
    fun parse(
        manifest: String,
        manifestUrl: HttpUrl,
        maxChunks: Int,
    ): Result {
        require(maxChunks > 0)
        val lines = manifest.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.firstOrNull() != EXTM3U) return Result.Malformed
        if (lines.any(::isProtectedKey)) return Result.DrmProtected
        if (lines.any { it.startsWith(STREAM_INF, ignoreCase = true) }) {
            return Result.Unsupported
        }
        if (lines.none { it.equals(END_LIST, ignoreCase = true) }) {
            return Result.Unsupported
        }

        val chunks = mutableListOf<HlsDownloadChunk>()
        var pendingSegment = false
        var pendingRange: RawByteRange? = null
        var pendingGap = false
        var previousRange: ResolvedByteRange? = null
        var previousRangeUrl: HttpUrl? = null
        var lastMapIdentity: String? = null

        lines.drop(1).forEach { line ->
            when {
                line.startsWith(MAP, ignoreCase = true) -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    val uri = attributes["URI"] ?: return Result.Malformed
                    val target = resolve(manifestUrl, uri) ?: return Result.Malformed
                    val byteRange = attributes["BYTERANGE"]
                        ?.let(::parseRawByteRange)
                        ?.let { raw ->
                            val offset = raw.offset ?: 0
                            raw.resolve(offset) ?: return Result.Malformed
                        }
                    val identity = buildString {
                        append(target)
                        append('|')
                        append(byteRange?.offset)
                        append('|')
                        append(byteRange?.length)
                    }
                    if (identity != lastMapIdentity) {
                        chunks += HlsDownloadChunk(
                            index = chunks.size,
                            url = target,
                            byteRange = byteRange,
                            type = HlsChunkType.INITIALIZATION,
                        )
                        if (chunks.size > maxChunks) return Result.TooManyChunks
                        lastMapIdentity = identity
                    }
                }
                line.startsWith(EXTINF, ignoreCase = true) -> {
                    if (pendingSegment) return Result.Malformed
                    pendingSegment = true
                }
                line.startsWith(BYTE_RANGE, ignoreCase = true) -> {
                    if (pendingRange != null) return Result.Malformed
                    pendingRange = parseRawByteRange(line.substringAfter(':'))
                        ?: return Result.Malformed
                }
                line.equals(GAP, ignoreCase = true) -> {
                    pendingGap = true
                }
                !line.startsWith('#') -> {
                    if (!pendingSegment || pendingGap) return Result.Unsupported
                    val target = resolve(manifestUrl, line) ?: return Result.Malformed
                    val byteRange = pendingRange?.let { raw ->
                        val offset = raw.offset ?: run {
                            if (previousRangeUrl != target || previousRange == null) {
                                return Result.Malformed
                            }
                            val previous = checkNotNull(previousRange)
                            previous.offset.safeAdd(previous.length) ?: return Result.Malformed
                        }
                        raw.resolve(offset) ?: return Result.Malformed
                    }
                    chunks += HlsDownloadChunk(
                        index = chunks.size,
                        url = target,
                        byteRange = byteRange,
                        type = HlsChunkType.MEDIA,
                    )
                    if (chunks.size > maxChunks) return Result.TooManyChunks
                    previousRange = byteRange
                    previousRangeUrl = if (byteRange == null) null else target
                    pendingSegment = false
                    pendingRange = null
                    pendingGap = false
                }
            }
        }
        if (pendingSegment || pendingRange != null || pendingGap) return Result.Malformed
        if (chunks.none { it.type == HlsChunkType.MEDIA }) return Result.Malformed
        return Result.Parsed(
            chunks = chunks,
            fingerprint = fingerprint(chunks),
        )
    }

    private fun resolve(base: HttpUrl, child: String): HttpUrl? {
        val target = base.resolve(child)?.takeIf(HttpUrl::isSafeDownloadUrl) ?: return null
        if (base.isHttps && !target.isHttps) return null
        return target
    }

    private fun isProtectedKey(line: String): Boolean {
        if (
            !line.startsWith(KEY, ignoreCase = true) &&
            !line.startsWith(SESSION_KEY, ignoreCase = true)
        ) {
            return false
        }
        val method = parseAttributes(line.substringAfter(':'))["METHOD"]
        return !method.equals("NONE", ignoreCase = true)
    }

    private fun parseRawByteRange(value: String): RawByteRange? {
        val normalized = value.trim().removeSurrounding("\"")
        val length = normalized.substringBefore('@').toLongOrNull()?.takeIf { it > 0 }
            ?: return null
        val hasOffset = '@' in normalized
        val offset = if (hasOffset) {
            normalized.substringAfter('@').toLongOrNull()?.takeIf { it >= 0 }
                ?: return null
        } else {
            null
        }
        return RawByteRange(length, offset)
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val output = linkedMapOf<String, String>()
        val pieces = mutableListOf<String>()
        var start = 0
        var inQuotes = false
        raw.forEachIndexed { index, character ->
            when (character) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    pieces += raw.substring(start, index)
                    start = index + 1
                }
            }
        }
        if (inQuotes) return emptyMap()
        pieces += raw.substring(start)
        pieces.forEach { piece ->
            val key = piece.substringBefore('=', missingDelimiterValue = "")
                .trim()
                .uppercase(Locale.US)
            val value = piece.substringAfter('=', missingDelimiterValue = "")
                .trim()
                .removeSurrounding("\"")
            if (key.isNotBlank() && value.isNotBlank()) output[key] = value
        }
        return output
    }

    private fun fingerprint(chunks: List<HlsDownloadChunk>): String {
        val digest = MessageDigest.getInstance("SHA-256")
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

    private fun Long.safeAdd(other: Long): Long? =
        if (this <= Long.MAX_VALUE - other) this + other else null

    private data class RawByteRange(
        val length: Long,
        val offset: Long?,
    ) {
        fun resolve(resolvedOffset: Long): ResolvedByteRange? {
            if (resolvedOffset < 0 || resolvedOffset > Long.MAX_VALUE - length) return null
            return ResolvedByteRange(resolvedOffset, length)
        }
    }

    sealed interface Result {
        data class Parsed(
            val chunks: List<HlsDownloadChunk>,
            val fingerprint: String,
        ) : Result

        data object DrmProtected : Result
        data object Unsupported : Result
        data object TooManyChunks : Result
        data object Malformed : Result
    }

    private const val EXTM3U = "#EXTM3U"
    private const val EXTINF = "#EXTINF:"
    private const val STREAM_INF = "#EXT-X-STREAM-INF:"
    private const val MAP = "#EXT-X-MAP:"
    private const val BYTE_RANGE = "#EXT-X-BYTERANGE:"
    private const val KEY = "#EXT-X-KEY:"
    private const val SESSION_KEY = "#EXT-X-SESSION-KEY:"
    private const val END_LIST = "#EXT-X-ENDLIST"
    private const val GAP = "#EXT-X-GAP"
}

internal data class HlsDownloadChunk(
    val index: Int,
    val url: HttpUrl,
    val byteRange: ResolvedByteRange?,
    val type: HlsChunkType,
) {
    override fun toString(): String = buildString {
        append("HlsDownloadChunk(index=")
        append(index)
        append(", url=[REDACTED], byteRange=")
        append(byteRange)
        append(", type=")
        append(type)
        append(')')
    }
}

internal data class ResolvedByteRange(
    val offset: Long,
    val length: Long,
) {
    init {
        require(offset >= 0)
        require(length > 0)
        require(offset <= Long.MAX_VALUE - length)
    }

    val endInclusive: Long
        get() = offset + length - 1
}

internal enum class HlsChunkType {
    INITIALIZATION,
    MEDIA,
}
