package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.UrlPolicy

/** L1: GET requests the browser made for this page, each with its own origin's context. */
internal class CaptureLayer : MasterLayer {
    override val id = LayerId.L1_CAPTURE

    override fun collect(request: MasterRequest, snapshot: PageSnapshot): Evidence {
        val factory = CandidateFactory(request)
        val found = mutableListOf<MediaCandidate>()
        val focusedUrl = snapshot.playingMediaUrl?.let(UrlPolicy::secure)?.let(UrlPolicy::whole)
        snapshot.requests.forEach { observed ->
            if (!observed.method.equals("GET", true)) return@forEach
            val url = UrlPolicy.secure(observed.url) ?: return@forEach
            val focused = focusedUrl == UrlPolicy.whole(url)
            if (
                request.expectedContentId != null && observed.contentId != null &&
                observed.contentId != request.expectedContentId
            ) {
                return@forEach
            }
            val candidate = factory.candidate(
                url = url,
                mime = observed.mimeType,
                id = observed.contentId ?: request.expectedContentId.takeIf { focused },
                role = observed.pageRole ?: PageMediaRole.MAIN.takeIf { focused },
                source = CandidateSource.REQUEST,
            ) ?: return@forEach
            found += candidate.copy(
                requestContext = UrlPolicy.context(
                    observed.context, url, url, request.pageUrl,
                    captured = true,
                ),
                observedAtEpochMs = observed.observedAtEpochMs,
            )
        }
        return Evidence(found)
    }
}
