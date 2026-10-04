package com.alal.yft.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P4: AV1 merges are offered only where Android 14's muxer writes them and a decoder plays. */
class MergeSupportTest {
    private val file = candidate("file", listOf("avc1.42001e", "mp4a.40.2"), merged = false)
    private val avc = candidate("avc", listOf("avc1.64001f"))
    private val av1 = candidate("av1", listOf("av01.0.08M.08"))
    private val vp9 = candidate("vp9", listOf("vp09.00.31.08"))

    @Test
    fun avcMergesEverywhereAndAv1OnlyFromAndroid14WithADecoder() {
        val android13 = DeviceMergeSupport(sdkInt = 33, hasDecoder = { true })
        val android14NoDecoder = DeviceMergeSupport(sdkInt = 34, hasDecoder = { false })
        val android14 = DeviceMergeSupport(sdkInt = 34, hasDecoder = { it == "video/av01" })

        assertTrue(android13.canMerge(file))
        assertTrue(android13.canMerge(avc))
        assertFalse(android13.canMerge(av1))
        assertFalse(android14NoDecoder.canMerge(av1))
        assertTrue(android14NoDecoder.canMerge(avc))
        assertTrue(android14.canMerge(av1))
        assertFalse(android14.canMerge(vp9))
    }

    @Test
    fun theDecoderListIsReadOnceAndOnlyForAv1() {
        var lookups = 0
        val support = DeviceMergeSupport(sdkInt = 35, hasDecoder = { lookups += 1; true })

        support.canMerge(avc)
        assertEquals(0, lookups)
        repeat(3) { support.canMerge(av1) }
        assertEquals(1, lookups)
    }

    @Test
    fun theCoordinatorLeavesOutMergesThisPhoneCannotMake() = runTest {
        val coordinator = SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(Extractor(listOf(av1, avc, file)))),
            DeviceMergeSupport(sdkInt = 33, hasDecoder = { true }),
        )

        val outcome = coordinator.inspect(PAGE, BrowserRequestContext(PAGE, null, null), 10)
            as SiteAdapterOutcome.Detected

        assertEquals(
            listOf("https://cdn.fixture.test/avc.mp4", "https://cdn.fixture.test/file.mp4"),
            outcome.candidates.map(MediaCandidate::mediaUrl),
        )
    }

    @Test
    fun onlyMergesThisPhoneCannotMakeIsNoUsableMedia() = runTest {
        val coordinator = SiteAdapterCoordinator(
            SiteExtractorRegistry(listOf(Extractor(listOf(av1)))),
            DeviceMergeSupport(sdkInt = 33, hasDecoder = { true }),
        )

        val outcome = coordinator.inspect(PAGE, BrowserRequestContext(PAGE, null, null), 10)
            as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, outcome.reason)
        assertTrue(outcome.details.any { it.contains("merges this phone cannot make: 1") })
    }

    private class Extractor(private val candidates: List<MediaCandidate>) : SiteExtractor {
        override val id: String = "fixture"
        override val displayName: String = "Fixture Site"

        override fun identify(pageUrl: String): SitePageIdentity? =
            SitePageIdentity("fixture", "42", PAGE).takeIf { pageUrl == PAGE }

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Success(candidates)
    }

    private companion object {
        const val PAGE = "https://fixture.test/video/42"

        fun candidate(name: String, codecs: List<String>, merged: Boolean = true) =
            MediaCandidate(
                pageUrl = PAGE,
                mediaUrl = "https://cdn.fixture.test/$name.mp4",
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = "video/mp4",
                codecs = codecs,
                audioCompanion = if (merged) {
                    CompanionAudio(
                        mediaUrl = "https://cdn.fixture.test/audio.mp4",
                        mimeType = "audio/mp4",
                        codecs = listOf("mp4a.40.5"),
                        requestContext = BrowserRequestContext(PAGE, null, null),
                    )
                } else {
                    null
                },
            )
    }
}
