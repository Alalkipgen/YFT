package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.toolkit.CandidateFactory

/**
 * The owner's retest of 7e101aa4 (2026-10-26): VK's page asks for its video's data once a
 * document (`video.getByIds`, read by the capture as any JSON body: every quality's whole file
 * with its sound inside, `files.mp4_144…mp4_1080`, the title and the length), so a lookup that
 * found no page manifest had the player's pieces only, and the first Try again offered 480p
 * titled by the page ("ВКонтакте | VK Видео"). The capture now carries that body to later binds
 * of the same page (`yft-master-capture.js`), and this object reads it.
 *
 * [of] reads a capture's page data ([PageSnapshot.apiResponses]) for the link's video
 * ([VkReader.api]: bound to the video by its `owner_id` and `id`, the trailer and every picture
 * left out, VK's video servers only) and gives its MP4 files as rows, the tallest first: each
 * with the data's title and length, its height and its address's expiry. The selection checks
 * them as any row (V and the init check, [SocialRowCheck]) and admits the tallest that passes,
 * before the pieces' fallback ([VkPieceFiles]). The link is the request's identity
 * ([MasterRequest.expectedContentId], VK's `{owner}_{id}`), else the page address's
 * (`video{owner}_{id}`, `clip…`, or the overlay's `z=video…`). Pure: never fetches, plays or
 * logs; an address stays in memory.
 */
object VkPageData {
    /** The files whose starts the selection reads at most (the tallest first). */
    const val MAX_FILES = 3

    /** The page data's rows for [request]'s video in [snapshot]; empty without such data. */
    fun of(request: MasterRequest, snapshot: PageSnapshot): List<MediaCandidate> {
        val link = linkOf(request) ?: return emptyList()
        val factory = CandidateFactory(request)
        for (json in snapshot.apiResponses) {
            if (!json.contains(MP4_KEY)) continue
            val read = runCatching { VkReader.api(json, link) }.getOrNull() ?: continue
            val files = read.files.filter { it.type == VkReader.MP4 && VkClient.mediaFile(it.url) }
            if (files.isEmpty()) continue
            val rows = files.sortedByDescending { it.height ?: 0 }.mapNotNull { file ->
                factory.candidate(
                    url = file.url,
                    mime = VkReader.MP4,
                    id = link.contentId,
                    role = PageMediaRole.MAIN,
                    source = CandidateSource.REQUEST,
                )?.copy(
                    title = read.title,
                    thumbnailUrl = read.thumbnail,
                    durationMillis = read.durationSeconds?.takeIf { it > 0 }?.times(1_000),
                    height = file.height,
                    expiresAtEpochMs = VkClient.expiresAtOf(file.url),
                )
            }
            if (rows.isNotEmpty()) return rows
        }
        return emptyList()
    }

    /** [request]'s video: its identity's content ID, else its page address's. */
    internal fun linkOf(request: MasterRequest): VkClient.Link? {
        request.expectedContentId?.let { id ->
            VkClient.Link.of(SitePageIdentity(VkClient.SITE_ID, id, request.pageUrl))?.let { return it }
        }
        val match = PAGE_VIDEO.find(request.pageUrl) ?: return null
        val owner = match.groupValues[1].toLongOrNull() ?: return null
        val id = match.groupValues[2].toLongOrNull() ?: return null
        if (owner == 0L || id <= 0L) return null
        return VkClient.Link(owner, id)
    }

    private const val MP4_KEY = "\"mp4_"
    private val PAGE_VIDEO = Regex("(?:video|clip)(-?[0-9]{1,19})_([0-9]{1,19})")
}
