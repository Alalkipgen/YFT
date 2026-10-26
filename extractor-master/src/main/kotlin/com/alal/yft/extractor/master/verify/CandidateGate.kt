package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.generic.classifier.MediaFileUrls
import com.alal.yft.extractor.master.toolkit.UrlPolicy

/**
 * What may be offered at all: HTTPS, unexpired, not DRM, not an ad, not a preview, not a site's
 * build resource ([UrlPolicy.buildResource], social plan task 18: whichever layer or capture found
 * it) and not a piece of a file (F1, VK's pack proof: an address naming a closed `bytes=A-B`
 * range, [MediaFileUrls.isPiece], gives that piece only).
 */
internal object CandidateGate {
    fun eligible(candidate: MediaCandidate, now: Long): Boolean =
        UrlPolicy.secure(candidate.mediaUrl) != null && candidate.drmHint != true &&
            candidate.pageRole != PageMediaRole.PREVIEW &&
            !UrlPolicy.looksLikeAd(candidate.mediaUrl) &&
            !UrlPolicy.buildResource(candidate.mediaUrl) &&
            !MediaFileUrls.isPiece(candidate.mediaUrl) &&
            candidate.expiresAtEpochMs?.let { it > now } != false

    /**
     * Keeps eligible normalized candidates. Explicit preview evidence in [raw] vetoes a weaker
     * page/JSON claim for the same file.
     */
    fun admit(
        raw: List<MediaCandidate>,
        normalized: List<MediaCandidate>,
        now: Long,
    ): List<MediaCandidate> {
        val previews = raw.filter { it.pageRole == PageMediaRole.PREVIEW }
            .map { UrlPolicy.whole(it.mediaUrl) }.toSet()
        return normalized.filter { eligible(it, now) && UrlPolicy.whole(it.mediaUrl) !in previews }
    }
}
