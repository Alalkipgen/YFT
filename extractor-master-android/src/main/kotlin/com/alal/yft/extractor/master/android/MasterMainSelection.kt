package com.alal.yft.extractor.master.android

import com.alal.yft.core.browser.detection.BrowserObservationMapper
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.present.MasterMainPresentation
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
        // Leave headroom inside the unchanged engine timeout for verified main/More routing.
        val deadline = System.nanoTime() + VALIDATION_WINDOW_MS * 1_000_000
        suspend fun <T> bounded(block: suspend () -> T): T? {
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) return null
            return withTimeoutOrNull(remaining) { block() }
        }
        for (candidate in candidates) {
            coroutineContext.ensureActive()
            if (candidate.mimeType?.startsWith("audio/") == true) continue
            if (ad(candidate, facts)) continue
            val verdict = bounded { probe(candidate) }
            if (verdict == null) { rejection = SiteExtractionFailure.NETWORK; break }
            when (verdict) {
                is ValidationResult.Rejected -> {
                    if (verdict.reason == SiteExtractionFailure.DRM_PROTECTED) {
                        return failure(verdict.reason)
                    }
                    rejection = verdict.reason
                }
                is ValidationResult.Valid -> {
                    val inspected = bounded { inspect(verdict.candidate) }
                    if (inspected == null) { rejection = SiteExtractionFailure.NETWORK; break }
                    var media = inspected.candidate
                    if (media.drmHint == true) return failure(SiteExtractionFailure.DRM_PROTECTED)
                    if (media.mimeType?.startsWith("audio/") == true) continue
                    if (ad(media, facts)) continue
                    if (target != null && media.durationMillis?.let {
                            abs(it - target) <= DURATION_SLACK_MS
                        } != true
                    ) continue
                    val audio = media.audioCompanion
                    if (audio != null) {
                        val input = media.copy(
                            mediaUrl = audio.mediaUrl, mimeType = audio.mimeType,
                            kind = MediaKind.DIRECT, codecs = audio.codecs,
                            requestContext = audio.requestContext,
                            expiresAtEpochMs = audio.expiresAtEpochMs,
                            width = null, height = null, framesPerSecond = null,
                            audioCompanion = null,
                        )
                        val sound = bounded { probe(input) }
                        if (sound == null) { rejection = SiteExtractionFailure.NETWORK; break }
                        when (sound) {
                            is ValidationResult.Rejected -> {
                                if (sound.reason == SiteExtractionFailure.DRM_PROTECTED) {
                                    return failure(sound.reason)
                                }
                                rejection = sound.reason
                                continue
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
                }
            }
        }
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
            return failure(rejection ?: SiteExtractionFailure.NO_MEDIA_FOUND)
        }
        val groups = FingerprintGroups.of(ranked) { prints[it.mediaUrl] }
        val selected = groups.flatten()
        val keys = groups.flatMap { group ->
            val key = MasterMainPresentation.key(group.first())
            group.map { it.mediaUrl to key }
        }.toMap()
        last = SelectedCapture(request.pageUrl, request.generation, selected, keys)
        return MasterResult.Success(
            SiteExtractionResult.Success(selected, listOf(
                "master: automatic main selected; more=${groups.size - 1}",
                "master: independently checked duration; tolerance=2000ms; dimensions=ranking-only",
                "master: fingerprint qualities=${groups.first().size}; " +
                    "dropped=${ranked.size - selected.size}",
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

    private fun failure(reason: SiteExtractionFailure) =
        MasterResult.Failure(reason, listOf("master: automatic selection ${reason.name}"))

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