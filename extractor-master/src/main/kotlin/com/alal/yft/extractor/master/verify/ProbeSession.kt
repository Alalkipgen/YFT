package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.UrlPolicy

/**
 * One invocation's shared media-check budget. Each address/context pair is checked at most
 * once, and a valid answer must still belong to the requested page.
 */
internal class ProbeSession(
    private val validator: MasterMediaValidator,
    private val request: MasterRequest,
    budget: Int,
) {
    var remaining: Int = budget
        private set
    val exhausted: Boolean get() = remaining == 0
    private val checked =
        mutableMapOf<Pair<String, BrowserRequestContext>, ValidationResult.Valid>()

    suspend fun probe(candidate: MediaCandidate): ValidationResult {
        val key = candidate.mediaUrl to candidate.requestContext
        checked[key]?.let { return it }
        if (remaining == 0) return ValidationResult.Rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        remaining -= 1
        val result = validator.validate(candidate, request.nowEpochMs)
        if (result is ValidationResult.Valid) {
            if (
                !UrlPolicy.samePage(result.candidate.pageUrl, request.pageUrl) ||
                !CandidateGate.eligible(result.candidate, request.nowEpochMs)
            ) {
                return ValidationResult.Rejected(SiteExtractionFailure.RESPONSE_CHANGED)
            }
            checked[key] = result
        }
        return result
    }

    sealed interface Checked {
        data class Accepted(val media: MediaCandidate) : Checked {
            override fun toString(): String = "Accepted(kind=${media.kind}, address=[omitted])"
        }
        data class Refused(val reason: SiteExtractionFailure, val detail: String) : Checked
    }

    /** Checks a candidate and, when it names one, its separate audio track. */
    suspend fun check(candidate: MediaCandidate): Checked {
        val verdict = probe(candidate)
        if (verdict is ValidationResult.Rejected) {
            return Checked.Refused(verdict.reason, "master: media check ${verdict.reason.name}")
        }
        val media = (verdict as ValidationResult.Valid).candidate
        val companion = media.audioCompanion ?: return Checked.Accepted(media)
        val audio = media.copy(
            mediaUrl = companion.mediaUrl,
            mimeType = companion.mimeType,
            kind = MediaKind.DIRECT,
            codecs = companion.codecs,
            requestContext = companion.requestContext,
            expiresAtEpochMs = companion.expiresAtEpochMs,
            contentLengthBytes = companion.contentLengthBytes,
            width = null, height = null, framesPerSecond = null,
            audioCompanion = null,
        )
        val audioCheck = if (CandidateGate.eligible(audio, request.nowEpochMs)) {
            probe(audio)
        } else {
            ValidationResult.Rejected(SiteExtractionFailure.EXPIRED_LINK)
        }
        if (audioCheck is ValidationResult.Rejected) {
            return Checked.Refused(
                audioCheck.reason, "master: companion check ${audioCheck.reason.name}",
            )
        }
        val verified = (audioCheck as ValidationResult.Valid).candidate
        return Checked.Accepted(media.copy(audioCompanion = CandidateFactory.companion(verified)))
    }
}
