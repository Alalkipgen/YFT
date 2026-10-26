package com.alal.yft.extractor.master.android

import com.alal.yft.core.browser.detection.BrowserObservationMapper
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.generic.classifier.MediaFileUrls
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.present.MasterMainPresentation
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.VkClient
import com.alal.yft.extractor.master.social.VkPieceFiles
import com.alal.yft.extractor.master.verify.FingerprintGroups
import com.alal.yft.extractor.master.verify.InspectedMedia
import com.alal.yft.extractor.master.verify.MediaFingerprint
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Opt-in policy. Captured source identities and the observed playing URL are never changed.
 *
 * R4: [inspect] adds each checked file's fingerprint (length + keyframe cues). Files that are one
 * video's qualities form one group (main's qualities instead of extra "More" videos); a lone file
 * whose cues disagree with a main confirmed by two agreeing files is an ad, preview or related
 * clip and is dropped. Without cues nothing is grouped or dropped (behaviour before R4).
 *
 * F1 (VK's pack proof, V3's retest 2026-10-26): a piece of a file is never a row, whatever its
 * length: an address naming a closed `bytes=A-B` range ([MediaFileUrls.isPiece]) or one whose
 * page answer was a piece to a request that named no range ([MasterBrowserSession.piece]). It is
 * never checked and never offered (a piece's own start may state the whole video's length: VK's
 * 1.4 KB init piece did); it still counts as the player's request. The page's manifest is the row.
 *
 * VK's pack proof (V3's retest 2 of f22c2217, 2026-10-27: 18 observations, all pieces, no row):
 * in a VK lookup ([MainPages.site]) whose captured rows gave nothing, the pieces' whole files
 * ([VkPieceFiles]): at most [VkPieceFiles.MAX_PICTURES] pictures and [VkPieceFiles.MAX_SOUNDS]
 * sounds are checked (V, in the lookup's reads), and the tallest mergeable picture with the
 * fullest sound is admitted as any passed row is (inspected, its length held to the player's, its
 * sound checked). A failure names, in counts only, what the capture held and what the checks said.
 */
class MasterMainSelection(
    private val session: MasterBrowserSession,
    private val enabled: Boolean = true,
    private val metadata: suspend (MediaCandidate) -> MediaCandidate = { it },
    inspect: (suspend (MediaCandidate) -> InspectedMedia)? = null,
) {
    private val inspect: suspend (MediaCandidate) -> InspectedMedia =
        inspect ?: { InspectedMedia.of(metadata(it)) }

    @Volatile private var last: SelectedCapture? = null

    suspend fun select(
        request: MasterRequest,
        snapshot: PageSnapshot,
        candidates: List<MediaCandidate>,
        probe: suspend (MediaCandidate) -> ValidationResult,
    ): MasterResult? {
        if (!enabled) return null
        last = null
        snapshot.accessFailure?.let { return failure(it) }
        val player = session.focusedPlayer(request)
        if (!snapshot.authorizedPlayback || player == null) {
            return MasterResult.NeedsPlayback(listOf("master: fresh natural playback required"))
        }
        val target = player.duration?.let { (it * 1_000).toLong() }
        val facts = target?.let { PageVideoFacts(durationMillis = it) }
        val checked = mutableListOf<MediaCandidate>()
        val prints = mutableMapOf<String, MediaFingerprint>()
        var rejection: SiteExtractionFailure? = null
        val tally = Tally()
        // Leave headroom inside the unchanged engine timeout for verified main/More routing.
        val deadline = System.nanoTime() + VALIDATION_WINDOW_MS * 1_000_000
        suspend fun <T> bounded(block: suspend () -> T): T? {
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) return null
            return withTimeoutOrNull(remaining) { block() }
        }

        /** A row V passed: inspected, its length held to the player's, its sound checked. */
        suspend fun admit(row: MediaCandidate): Step {
            if (piece(row.mediaUrl)) return Step.NEXT
            val inspected = bounded { inspect(row) } ?: return Step.OUT
            var media = inspected.candidate
            if (media.drmHint == true) return Step.DRM
            if (media.mimeType?.startsWith("audio/") == true) return Step.NEXT
            if (ad(media, facts)) return Step.NEXT
            if (target != null && media.durationMillis?.let {
                    abs(it - target) <= DURATION_SLACK_MS
                } != true
            ) {
                tally.offLength++
                return Step.NEXT
            }
            val audio = media.audioCompanion
            if (audio != null) {
                // The sound file's own length (as the engine's check asks it), never the picture's.
                val input = media.copy(
                    mediaUrl = audio.mediaUrl, mimeType = audio.mimeType,
                    kind = MediaKind.DIRECT, codecs = audio.codecs,
                    requestContext = audio.requestContext,
                    expiresAtEpochMs = audio.expiresAtEpochMs,
                    contentLengthBytes = audio.contentLengthBytes,
                    width = null, height = null, framesPerSecond = null,
                    audioCompanion = null,
                )
                when (val sound = bounded { probe(input) } ?: return Step.OUT) {
                    is ValidationResult.Rejected -> {
                        if (sound.reason == SiteExtractionFailure.DRM_PROTECTED) return Step.DRM
                        rejection = sound.reason
                        tally.refused(sound.reason)
                        return Step.NEXT
                    }
                    is ValidationResult.Valid -> {
                        media = media.copy(audioCompanion = audio.copy(
                            mediaUrl = sound.candidate.mediaUrl,
                            requestContext = sound.candidate.requestContext,
                        ))
                    }
                }
            }
            checked += media
            prints[media.mediaUrl] = inspected.fingerprint.copy(
                durationMillis = media.durationMillis,
            )
            return Step.NEXT
        }

        var out = false
        for (candidate in candidates) {
            coroutineContext.ensureActive()
            if (candidate.mimeType?.startsWith("audio/") == true) continue
            if (piece(candidate.mediaUrl)) continue
            if (ad(candidate, facts)) continue
            val verdict = bounded { probe(candidate) }
            if (verdict == null) { out = true; break }
            tally.checked++
            when (verdict) {
                is ValidationResult.Rejected -> {
                    if (verdict.reason == SiteExtractionFailure.DRM_PROTECTED) {
                        return failure(verdict.reason)
                    }
                    rejection = verdict.reason
                    tally.refused(verdict.reason)
                }
                is ValidationResult.Valid -> {
                    tally.passed++
                    when (admit(verdict.candidate)) {
                        Step.NEXT -> Unit
                        Step.OUT -> { out = true; break }
                        Step.DRM -> return failure(SiteExtractionFailure.DRM_PROTECTED)
                    }
                }
            }
        }
        // VK's pack proof (V3's retest 2): nothing captured passed in a VK lookup (its capture holds
        // no page manifest that did): the whole files of the pieces its player asked for, the
        // tallest picture with the fullest sound (see [VkPieceFiles]).
        var pieceLine: String? = null
        if (!out && checked.isEmpty() && coroutineContext[MainPages]?.site == VkClient.SITE_ID) {
            VkPieceFiles.of(request, snapshot)?.let { files ->
                val pictures = mutableListOf<MediaCandidate>()
                val sounds = mutableListOf<MediaCandidate>()
                var stop: Step? = null
                suspend fun read(file: MediaCandidate, into: MutableList<MediaCandidate>): Boolean {
                    coroutineContext.ensureActive()
                    val verdict = bounded { probe(file) }
                    if (verdict == null) { stop = Step.OUT; return false }
                    when (verdict) {
                        is ValidationResult.Rejected -> {
                            if (verdict.reason == SiteExtractionFailure.DRM_PROTECTED) {
                                stop = Step.DRM
                                return false
                            }
                            rejection = verdict.reason
                            tally.refused(verdict.reason)
                        }
                        is ValidationResult.Valid -> if (!piece(verdict.candidate.mediaUrl)) into += verdict.candidate
                    }
                    return true
                }
                for (file in files.pictures.take(VkPieceFiles.MAX_PICTURES)) if (!read(file, pictures)) break
                if (stop == null) for (file in files.sounds.take(VkPieceFiles.MAX_SOUNDS)) if (!read(file, sounds)) break
                pieceLine = "master: VK pieces' files: pictures passed ${pictures.size} of ${files.pictures.size}; " +
                    "sounds passed ${sounds.size} of ${files.sounds.size}"
                // A mergeable picture first (AVC, as the phone's merge gate takes), then the tallest
                // and the fullest; the fullest sound (the largest file of one length).
                val picture = pictures.maxWithOrNull(
                    compareBy<MediaCandidate> { mergeable(it) }
                        .thenBy { it.height ?: 0 }
                        .thenBy { it.contentLengthBytes ?: 0L },
                )
                val sound = sounds.maxWithOrNull(compareBy { it.contentLengthBytes ?: 0L })
                when (stop) {
                    Step.DRM -> return failure(SiteExtractionFailure.DRM_PROTECTED)
                    Step.OUT -> rejection = SiteExtractionFailure.NETWORK
                    else -> if (picture != null && (sound != null || files.sounds.isEmpty())) {
                        when (admit(VkPieceFiles.row(picture, sound))) {
                            Step.NEXT -> Unit
                            Step.OUT -> rejection = SiteExtractionFailure.NETWORK
                            Step.DRM -> return failure(SiteExtractionFailure.DRM_PROTECTED)
                        }
                    }
                }
            }
        }
        if (out) rejection = SiteExtractionFailure.NETWORK
        if (session.currentScope() != BrowserCaptureScope(request.pageUrl, request.generation)) {
            return failure(SiteExtractionFailure.RESPONSE_CHANGED)
        }
        val ranked = if (target == null) {
            checked.sortedWith(compareByDescending<MediaCandidate> { it.durationMillis ?: -1 }
                .thenBy { sizeGap(it, player) })
        } else {
            checked.sortedWith(compareBy<MediaCandidate> { abs(it.durationMillis!! - target) }
                .thenBy { sizeGap(it, player) })
        }
        if (ranked.isEmpty()) {
            // Counts only (never an address): what the capture held and what the checks said.
            return failure(
                rejection ?: SiteExtractionFailure.NO_MEDIA_FOUND,
                listOfNotNull(census(snapshot, target != null), tally.line(), pieceLine),
            )
        }
        val groups = FingerprintGroups.of(ranked) { prints[it.mediaUrl] }
        val selected = groups.flatten()
        val keys = groups.flatMap { group ->
            val key = MasterMainPresentation.key(group.first())
            group.map { it.mediaUrl to key }
        }.toMap()
        last = SelectedCapture(request.pageUrl, request.generation, selected, keys)
        return MasterResult.Success(
            SiteExtractionResult.Success(selected, listOfNotNull(
                "master: automatic main selected; more=${groups.size - 1}",
                "master: independently checked duration; tolerance=2000ms; dimensions=ranking-only",
                "master: fingerprint qualities=${groups.first().size}; " +
                    "dropped=${ranked.size - selected.size}",
                pieceLine,
            )),
            MasterStage.PLAYBACK_CAPTURE,
        )
    }

    /** UI-only packing after identity checks; no app model or source identity is invented. */
    fun appCandidates(
        candidates: List<MediaCandidate>,
        expectedKey: String?,
    ): List<MediaCandidate>? {
        val record = active(candidates) ?: return null
        val kept = candidates.filter {
            expectedKey == null || it.videoId == null || it.videoId == expectedKey
        }
        if (kept.isEmpty()) return emptyList()
        // Explicit source IDs remain in record. UI copies use presentation keys, not fake post IDs.
        return kept.map { media ->
            media.copy(videoId = null, pageVideoKey = record.keyOf(media))
        }.also { record.presented = it }
    }

    fun presentation(candidates: List<MediaCandidate>): MasterMainPresentation? {
        if (!enabled) return null
        val record = last ?: return null
        if (record.presented.isEmpty()) return null
        val presentedUrls = record.presented.map { it.mediaUrl }.toSet()
        val owned = candidates.filter {
            it.videoId == null && it.mediaUrl in presentedUrls &&
                it.pageVideoKey == record.keyOf(it)
        }.distinctBy { it.mediaUrl }
        if (active(owned) == null) return null
        val order = record.candidates.mapIndexed { index, media -> media.mediaUrl to index }.toMap()
        return MasterMainPresentation.from(owned.sortedBy { order[it.mediaUrl] }, record::keyOf)
    }

    fun clear() { last = null }

    private fun active(candidates: List<MediaCandidate>): SelectedCapture? {
        if (!enabled || candidates.isEmpty()) return null
        val record = last ?: return null
        if (session.currentScope() != BrowserCaptureScope(record.page, record.generation)) {
            return null
        }
        val urls = record.candidates.map { it.mediaUrl }.toSet()
        return record.takeIf {
            candidates.all { c -> c.pageUrl == record.page && c.mediaUrl in urls }
        }
    }

    /** F1: [url] names a piece of a file, by its address or by the page's answer to it. */
    private fun piece(url: String): Boolean = MediaFileUrls.isPiece(url) || session.piece(url)

    private fun ad(media: MediaCandidate, facts: PageVideoFacts?): Boolean =
        media.pageRole == PageMediaRole.PREVIEW ||
            BrowserObservationMapper.adRole(media.mediaUrl, media.requestContext.pageUrl) != null ||
            MediaGroups.isPreviewAddress(media.mediaUrl) ||
            facts?.isFarShorter(media.durationMillis) == true

    private fun sizeGap(media: MediaCandidate, player: FramePlayer): Long {
        val w = player.width ?: return Long.MAX_VALUE
        val h = player.height ?: return Long.MAX_VALUE
        val mw = media.width ?: return Long.MAX_VALUE
        val mh = media.height ?: return Long.MAX_VALUE
        return abs(mw.toLong() - w) + abs(mh.toLong() - h)
    }

    private fun failure(reason: SiteExtractionFailure, lines: List<String> = emptyList()) =
        MasterResult.Failure(reason, listOf("master: automatic selection ${reason.name}") + lines)

    /** An AVC picture (or one whose codecs are unknown) merges on every phone (the merge gate's rule). */
    private fun mergeable(media: MediaCandidate): Int =
        if (media.codecs.all { it.trim().lowercase().startsWith("avc") }) 1 else 0

    /**
     * VK's pack proof (V3's retest 2): what the capture held, counts only (never an address): its
     * requests, the manifests among them (DASH, HLS), the pieces, the player's own, its page data.
     */
    private fun census(snapshot: PageSnapshot, length: Boolean): String {
        var dash = 0
        var hls = 0
        var pieces = 0
        var fed = 0
        snapshot.requests.forEach { observed ->
            val mime = observed.mimeType?.lowercase().orEmpty()
            val path = observed.url.substringBefore('#').substringBefore('?').lowercase()
            when {
                "dash+xml" in mime || path.endsWith(".mpd") -> dash++
                "mpegurl" in mime || path.endsWith(".m3u8") -> hls++
                piece(observed.url) -> pieces++
            }
            if (observed.fedPlayer) fed++
        }
        return "master: capture held requests=${snapshot.requests.size} (dash=$dash, hls=$hls, " +
            "pieces=$pieces, player's=$fed); page data=${snapshot.apiResponses.size}; " +
            "player length ${if (length) "known" else "unknown"}"
    }

    /** One step of a row's admission: on to the next row, out of time, or protected (final). */
    private enum class Step { NEXT, OUT, DRM }

    /** The checks' counts for the failure's line (counts and reasons only). */
    private class Tally {
        var checked = 0
        var passed = 0
        var offLength = 0
        private val reasons = linkedMapOf<SiteExtractionFailure, Int>()

        fun refused(reason: SiteExtractionFailure) {
            reasons[reason] = (reasons[reason] ?: 0) + 1
        }

        fun line(): String = "master: rows checked=$checked; passed=$passed; " +
            "off the player's length=$offLength; refused=" +
            (reasons.entries.joinToString(", ") { "${it.key.name}=${it.value}" }.ifEmpty { "none" })
    }

    private class SelectedCapture(
        val page: String,
        val generation: Long,
        val candidates: List<MediaCandidate>,
        /** Presentation key per file: one key per fingerprint group (main's qualities share). */
        private val keys: Map<String, String>,
        var presented: List<MediaCandidate> = emptyList(),
    ) {
        fun keyOf(media: MediaCandidate): String =
            keys[media.mediaUrl] ?: MasterMainPresentation.key(media)
    }

    private companion object {
        const val DURATION_SLACK_MS = 2_000L
        const val VALIDATION_WINDOW_MS = 15_000L
    }
}