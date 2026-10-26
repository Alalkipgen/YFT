package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.UrlPolicy

/**
 * VK's pack proof (V3's retest 2 of f22c2217, 2026-10-26: the 18+ video, 18 observations, none a
 * row): a VK lookup's capture may hold no page manifest (VK's player fetches it once a document,
 * as it starts), only the pieces the player asked for. Each piece is its file's address with
 * `&bytes=A-B` added (the manifest's `BaseURL`, F1), so that address without its range is the
 * whole file: one per quality and per sound, the player asks for the start of each.
 *
 * [of] groups a capture's VK pieces (GET, on VK's media hosts ([VkSite.SITE]), a closed `bytes=`
 * range, the page's answer typed `video/…` or `audio/…`) into those whole files: the picture files
 * (MP4's when there is any, else WebM's) and the sound files of the same container, each list in
 * VK's order of quality (the address's `type`, [QUALITY]: the fullest first), then by how many
 * pieces the player asked for. A piece is still never a row (F1): only its whole file is, and only
 * here, where the selection reads the files' starts ([SocialRowCheck]: V and the init check, each
 * file once, in the lookup's reads) and offers the tallest picture with the fullest sound to merge
 * ([row]), as VK's page manifest's row is ([VkPageDash]).
 *
 * Pure: never fetches, plays or logs; a file's address stays in memory.
 */
object VkPieceFiles {
    /** The picture files whose starts the selection reads at most (the fullest first). */
    const val MAX_PICTURES = 3

    /** The sound files whose starts the selection reads at most (the fullest first). */
    const val MAX_SOUNDS = 2

    /** A capture's VK files: its pictures and the sounds of their container, the fullest first. */
    data class Files(val pictures: List<MediaCandidate>, val sounds: List<MediaCandidate>) {
        override fun toString(): String =
            "VkPieceFiles.Files(pictures=${pictures.size}, sounds=${sounds.size}, addresses=[omitted])"
    }

    /**
     * VK's `type` values from the thinnest file to the fullest, as its page manifest lists them
     * (V3's retest 2: 144p is 4, then 240p 0, 360p 1, 480p 2, 720p 3, 1080p 5; its sounds too, 4
     * the thinnest and 3 the fullest).
     */
    private val QUALITY = listOf("4", "0", "1", "2", "3", "5", "6", "7")
    private val CLOSED_RANGE = Regex("^[0-9]{1,15}-[0-9]{1,15}$")
    private val TYPES = setOf("video/mp4", "audio/mp4", "video/webm", "audio/webm")

    /** [snapshot]'s VK files (see the object's notes); null when it holds no VK picture piece. */
    fun of(request: MasterRequest, snapshot: PageSnapshot): Files? {
        val site = VkSite.SITE
        val factory = CandidateFactory(request)
        val files = LinkedHashMap<String, Found>()
        snapshot.requests.forEach { observed ->
            if (!observed.method.equals("GET", true)) return@forEach
            val url = UrlPolicy.secure(observed.url) ?: return@forEach
            if (!site.mediaHost(url)) return@forEach
            val whole = whole(url) ?: return@forEach
            val type = observed.mimeType?.substringBefore(';')?.trim()?.lowercase()
                ?.takeIf { it in TYPES } ?: return@forEach
            if (request.expectedContentId != null && observed.contentId != null &&
                observed.contentId != request.expectedContentId
            ) {
                return@forEach
            }
            files[whole]?.let { found ->
                found.pieces++
                return@forEach
            }
            val row = factory.candidate(
                url = whole,
                mime = type,
                id = observed.contentId ?: request.expectedContentId,
                role = observed.pageRole ?: PageMediaRole.MAIN,
                source = CandidateSource.REQUEST,
            ) ?: return@forEach
            files[whole] = Found(
                row.copy(
                    requestContext = UrlPolicy.context(observed.context, url, whole, request.pageUrl, captured = true),
                    observedAtEpochMs = observed.observedAtEpochMs,
                ),
                type,
                quality(whole),
                files.size,
            )
        }
        val ordered = files.values.sortedWith(
            compareByDescending<Found> { it.quality }.thenByDescending { it.pieces }.thenBy { it.order },
        )
        val pictures = ordered.filter { it.type == "video/mp4" }.ifEmpty { ordered.filter { it.type == "video/webm" } }
        if (pictures.isEmpty()) return null
        val sound = "audio/" + pictures.first().type.substringAfter('/')
        return Files(pictures.map { it.row }, ordered.filter { it.type == sound }.map { it.row })
    }

    /** [picture]'s row with [sound] (when there is one) as the sound to merge. */
    fun row(picture: MediaCandidate, sound: MediaCandidate?): MediaCandidate =
        picture.copy(audioCompanion = sound?.let(CandidateFactory::companion))

    /**
     * [url] without its closed `bytes=A-B` range (its whole file); null when it names none (an
     * open range asks for the file from its start, so it is no piece).
     */
    internal fun whole(url: String): String? {
        val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
        val queryStart = url.indexOf('?').takeIf { it in 0 until fragmentStart } ?: return null
        val parameters = url.substring(queryStart + 1, fragmentStart).split('&')
        val kept = parameters.filterNot { parameter ->
            parameter.substringBefore('=').lowercase() == "bytes" &&
                CLOSED_RANGE.matches(parameter.substringAfter('=', missingDelimiterValue = ""))
        }
        if (kept.size == parameters.size) return null
        val query = kept.filter(String::isNotEmpty).joinToString("&")
        return buildString {
            append(url, 0, queryStart)
            if (query.isNotEmpty()) append('?').append(query)
        }
    }

    /** The rank of [url]'s `type` in [QUALITY] (higher is fuller); -1 without a known one. */
    private fun quality(url: String): Int {
        val query = url.substringAfter('?', "")
        val type = query.split('&').firstOrNull { it.substringBefore('=') == "type" }?.substringAfter('=', "")
        return type?.let(QUALITY::indexOf) ?: -1
    }

    private class Found(val row: MediaCandidate, val type: String, val quality: Int, val order: Int) {
        var pieces = 1
    }
}
