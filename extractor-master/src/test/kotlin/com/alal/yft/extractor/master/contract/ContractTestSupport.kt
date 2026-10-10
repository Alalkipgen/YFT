package com.alal.yft.extractor.master.contract

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** R8 test support: one capture that counts its calls, and the engine with contracts. */
internal class CountingCapture(
    private val result: CaptureResult = CaptureResult.Unavailable,
) : PlaybackCaptureProvider {
    var calls = 0
        private set

    override suspend fun capture(request: MasterRequest): CaptureResult {
        calls += 1
        return result
    }
}

internal const val AGENT = "fixture-agent"

internal fun answering(
    vararg answers: Pair<String, String>,
    contentType: String = "application/json",
) = FakeExtractorHttpClient(
    responses = answers.associate { (url, body) ->
        url to ExtractorHttpResult.Success(200, body, url, contentType)
    },
)

internal fun contractEngine(
    http: FakeExtractorHttpClient,
    validator: RecordingValidator = RecordingValidator(),
    capture: PlaybackCaptureProvider = CountingCapture(),
    policy: MasterPolicy = MasterPolicy(enabled = true),
) = MasterFallbackEngine(validator, capture, policy, contracts = SiteContracts(http))

internal fun contractRequest(
    pageUrl: String,
    identity: SitePageIdentity,
    failure: SiteExtractionFailure = SiteExtractionFailure.RESPONSE_CHANGED,
    cookie: String? = null,
) = MasterRequest(
    pageUrl, 1, NOW, failure, identity.contentId,
    BrowserRequestContext(pageUrl, AGENT, cookie), identity = identity,
)

/** What main's adapter would be asked with for the same page and session. */
internal fun mainRequest(identity: SitePageIdentity, cookie: String? = null) =
    SiteExtractionRequest(
        identity, BrowserRequestContext(identity.canonicalPageUrl, AGENT, cookie), NOW,
    )

/**
 * Parity: every main row is a Master row (same file), Master states every size main states,
 * every Master row was probed and carries the app's video key.
 */
internal fun assertCovers(
    name: String,
    main: List<MediaCandidate>,
    result: MasterResult,
    validator: RecordingValidator,
    key: String,
): List<MediaCandidate> {
    assertTrue("$name: Master result $result", result is MasterResult.Success)
    val rows = (result as MasterResult.Success).result.candidates
    val urls = rows.map { it.mediaUrl }.toSet()
    main.forEach { assertTrue("$name: Master lacks main's ${it.kind} row", it.mediaUrl in urls) }
    assertTrue(
        "$name: sizes ${rows.mapNotNull { it.height }} lack main's",
        rows.mapNotNull { it.height }.containsAll(main.mapNotNull { it.height }),
    )
    val probed = validator.seen.map { it.mediaUrl }.toSet()
    rows.forEach { assertTrue("$name: unprobed row", it.mediaUrl in probed) }
    rows.forEach { assertEquals("$name: row key", key, it.videoId) }
    assertTrue("$name: rows >= main", rows.size >= main.size)
    return rows
}