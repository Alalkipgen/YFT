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
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** Opt-in policy. Captured source identities and the observed playing URL are never changed. */
class MasterMainSelection(
    private val session: MasterBrowserSession,
    private val enabled: Boolean = true,
    private val metadata: suspend (MediaCandidate) -> MediaCandidate = { it },
) {
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
        var rejection: SiteExtractionFailure? = null
        for (candidate in candidates) {
            coroutineContext.ensureActive()
            if (candidate.mimeType?.startsWith("audio/") == true) continue
            if (ad(candidate, facts)) continue
            when (val verdict = probe(candidate)) {
                is ValidationResult.Rejected -> {
                    if (verdict.reason == SiteExtractionFailure.DRM_PROTECTED) {
                        return failure(verdict.reason)
                    }
                    rejection = verdict.reason
                }
                is ValidationResult.Valid -> {
                    var media = metadata(verdict.candidate)
                    if (media.drmHint == true) return failure(SiteExtractionFailure.DRM_PROTECTED)
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
                        when (val sound = probe(input)) {
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
        last = SelectedCapture(request.pageUrl, request.generation, ranked)
        return MasterResult.Success(
            SiteExtractionResult.Success(ranked, listOf(
                "master: automatic main selected; more=${ranked.size - 1}",
                "master: independently checked duration; tolerance=2000ms; dimensions=ranking-only",
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
            media.copy(videoId = null, pageVideoKey = MasterMainPresentation.key(media))
        }.also { record.presented = it }
    }

    fun presentation(candidates: List<MediaCandidate>): MasterMainPresentation? {
        if (!enabled) return null
        val record = last ?: return null
        if (record.presented.isEmpty()) return null
        val presentedUrls = record.presented.map { it.mediaUrl }.toSet()
        val owned = candidates.filter {
            it.videoId == null && it.mediaUrl in presentedUrls &&
                it.pageVideoKey == MasterMainPresentation.key(it)
        }.distinctBy { it.mediaUrl }
        if (active(owned) == null) return null
        val order = record.candidates.mapIndexed { index, media -> media.mediaUrl to index }.toMap()
        return MasterMainPresentation.from(owned.sortedBy { order[it.mediaUrl] })
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
        var presented: List<MediaCandidate> = emptyList(),
    )

    private companion object {
        const val DURATION_SLACK_MS = 2_000L
    }
}