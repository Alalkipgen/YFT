package com.alal.yft.extractor.master.modules

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI

/**
 * R6: a site reader Master owns. A page a module claims is answered by that module alone: no
 * layer read, no browser capture and no media probe follows, and a module never runs after the
 * site adapter already looked the same video up (P12: one lookup per video).
 */
interface MasterSiteModule {
    val siteId: String

    /** The video [pageUrl] shows, or null when this module does not read the page. */
    fun identify(pageUrl: String): SitePageIdentity?

    suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult
}

/**
 * Runs a Master copy of a site extractor and keeps only rows Master may offer: HTTPS media,
 * never a SABR stream, never a protected one. Kept rows are tagged with the video key the app's
 * site coordinator uses ("site:contentId"), so the browser hook focuses them the same way.
 */
class SiteExtractorModule(private val delegate: SiteExtractor) : MasterSiteModule {
    override val siteId: String get() = delegate.id

    override fun identify(pageUrl: String): SitePageIdentity? =
        delegate.identify(pageUrl)?.takeIf { it.siteId == delegate.id }

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        if (request.identity.siteId != delegate.id) {
            return SiteExtractionResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
        }
        val result = delegate.extract(request)
        if (result !is SiteExtractionResult.Success) return result
        val key = "${request.identity.siteId}:${request.identity.contentId}"
        val kept = result.candidates.filter(::admissible).map { it.copy(videoId = key) }
        val dropped = result.candidates.size - kept.size
        val details = if (dropped == 0) {
            result.details
        } else {
            result.details + "master module: $dropped rows failed the row check"
        }
        return if (kept.isEmpty()) {
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, details = details)
        } else {
            SiteExtractionResult.Success(kept, details)
        }
    }

    companion object {
        /** A row Master may offer: direct HTTPS addresses, no SABR and no protection. */
        fun admissible(candidate: MediaCandidate): Boolean =
            candidate.drmHint != true &&
                usable(candidate.mediaUrl) &&
                candidate.audioCompanion?.let { usable(it.mediaUrl) } != false

        private fun usable(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty()) {
                return false
            }
            // YT-4: SABR is never a row; its addresses carry the sabr parameter.
            return uri.rawQuery.orEmpty().split('&').none {
                it.substringBefore('=').equals("sabr", ignoreCase = true)
            }
        }
    }
}
