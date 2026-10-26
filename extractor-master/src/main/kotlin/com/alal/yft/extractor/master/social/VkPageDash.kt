package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.generic.manifest.ManifestReader
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.toolkit.InlineDashReader
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.GZIPInputStream

/**
 * VK's pack proof (V3's retest, 2026-10-26): the DASH manifest VK's player fetched on its page
 * (a capture row: [CandidateSource.REQUEST], `application/dash+xml`). VK's manifest names each
 * quality and each sound as one whole file (`BaseURL` + `SegmentBase`, the on-demand profile);
 * its player then asks for pieces of them (`&bytes=A-B`), which are never rows (F1). The app
 * downloads no `SegmentBase` manifest, so the manifest's row is its tallest picture file with its
 * best sound file as the sound to merge, both whole files on the manifest's own host, as
 * Facebook's inline manifests give theirs ([InlineDashReader]).
 *
 * Pure: [read] parses a manifest already read; [SocialRowCheck] reads it and both files' starts.
 */
internal object VkPageDash {
    /** The manifest's cap (as [InlineDashReader] reads, and the row read's own bound is 64 KiB). */
    const val MAX_MANIFEST_CHARS = 256 * 1024

    private const val KEY = "vk:page-manifest"
    private val GZIP = byteArrayOf(0x1f, 0x8b.toByte())

    /** What a page manifest gave. */
    sealed interface Read {
        /** Its tallest picture file and the sound for it (null when the manifest lists none). */
        data class Files(val picture: MediaCandidate, val sound: MediaCandidate?, val durationMillis: Long?) : Read {
            override fun toString(): String = "VkPageDash.Files(sound=${sound != null}, address=[omitted])"
        }

        /** No whole file to offer ([reason]: DRM, or nothing readable). */
        data class None(val reason: SiteExtractionFailure) : Read
    }

    /** Whether [row] is a capture's DASH manifest (its page's request), never a ladder's row. */
    fun pageManifest(row: MediaCandidate): Boolean {
        if (CandidateSource.REQUEST !in row.sources) return false
        val mime = row.mimeType?.substringBefore(';')?.trim()?.lowercase()
        val path = runCatching { URI(row.mediaUrl.trim()).rawPath }.getOrNull().orEmpty().lowercase()
        return row.kind == MediaKind.DASH || mime == "application/dash+xml" || path.endsWith(".mpd")
    }

    /**
     * The manifest's text from its answer's [bytes]: as is, or unpacked when a server sent it
     * packed although asked for `identity`; null when it is larger than [MAX_MANIFEST_CHARS] or
     * cannot be unpacked.
     */
    fun text(bytes: ByteArray): String? {
        val plain = if (bytes.size >= 2 && bytes[0] == GZIP[0] && bytes[1] == GZIP[1]) {
            runCatching {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                        if (out.size() > MAX_MANIFEST_CHARS) return null
                    }
                    out.toByteArray()
                }
            }.getOrNull() ?: return null
        } else {
            bytes
        }
        return plain.toString(Charsets.UTF_8).takeIf { it.length <= MAX_MANIFEST_CHARS }
    }

    /**
     * [text], the manifest at [manifestUrl]: its tallest picture file (MP4's when it lists any,
     * else WebM's) and the highest-bandwidth sound of the same container; [Read.None] with
     * `DRM_PROTECTED` for a protected one, `NO_MEDIA_FOUND` for a live one, one without a whole
     * file (`SegmentTemplate`, `SegmentList`) or one that is not a manifest. The files keep the
     * manifest's own fields; their addresses, types, codecs and sizes are the manifest's.
     */
    fun read(text: String, manifestUrl: String, nowEpochMs: Long): Read {
        val parsed = InlineDashReader.read(
            text,
            MasterRequest(manifestUrl, 0, nowEpochMs.coerceAtLeast(0), SiteExtractionFailure.NO_MEDIA_FOUND),
            contentId = null,
            key = KEY,
        )
        if (parsed.drm) return Read.None(SiteExtractionFailure.DRM_PROTECTED)
        val files = parsed.candidates.filter { it.mediaUrl.isNotBlank() }
        val pictures = files.filter { type(it) == "video/mp4" }.ifEmpty { files.filter { type(it) == "video/webm" } }
        val picture = SiteRowCheck.best(pictures) ?: return Read.None(SiteExtractionFailure.NO_MEDIA_FOUND)
        val soundType = "audio/" + type(picture)!!.substringAfter('/')
        val sound = files.filter { type(it) == soundType }
            .withIndex()
            .maxWithOrNull(compareBy<IndexedValue<MediaCandidate>> { parsed.bandwidths[it.value.mediaUrl] ?: 0L }
                .thenByDescending { it.index })
            ?.value
        return Read.Files(picture, sound, ManifestReader.dash(text)?.durationMillis)
    }

    private fun type(file: MediaCandidate): String? = file.mimeType?.substringBefore(';')?.trim()?.lowercase()
}
