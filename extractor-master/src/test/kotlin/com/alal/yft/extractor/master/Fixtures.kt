package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure

internal const val PAGE = "https://example.test/watch/fixture"
internal const val MEDIA = "https://cdn.example.test/main.mp4"
internal const val NOW = 1_800_000_000_000L

internal fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/fixtures/$name")).readText()

internal fun request(
    snapshot: PageSnapshot? = null,
    failure: SiteExtractionFailure = SiteExtractionFailure.RESPONSE_CHANGED,
    id: String? = null,
) = MasterRequest(PAGE, 1, NOW, failure, id, snapshot = snapshot)

internal fun snapshot(
    body: String? = null,
    playing: String? = null,
    requests: List<CapturedRequest> = emptyList(),
    authorized: Boolean = false,
) = PageSnapshot(
    PAGE, 1, apiResponses = listOfNotNull(body), requests = requests,
    playingMediaUrl = playing, authorizedPlayback = authorized,
)

internal fun candidate(url: String = MEDIA) = MediaCandidate(
    pageUrl = PAGE,
    mediaUrl = url,
    sources = setOf(CandidateSource.REQUEST),
    kind = MediaKind.DIRECT,
    mimeType = "video/mp4",
    observedAtEpochMs = NOW,
    pageRole = PageMediaRole.MAIN,
)

internal class RecordingValidator(
    private val result: (MediaCandidate) -> ValidationResult = {
        ValidationResult.Valid(it.copy(contentLengthBytes = 32_768))
    },
) : MasterMediaValidator {
    val seen = mutableListOf<MediaCandidate>()

    override suspend fun validate(
        candidate: MediaCandidate,
        nowEpochMs: Long,
    ): ValidationResult {
        seen += candidate
        return result(candidate)
    }
}