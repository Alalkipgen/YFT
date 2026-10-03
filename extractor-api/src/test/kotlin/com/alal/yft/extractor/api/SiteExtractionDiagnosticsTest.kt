package com.alal.yft.extractor.api

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SiteExtractionDiagnosticsTest {
    private val candidates = listOf(
        MediaCandidate("https://a.test/page", "https://a.test/clip.mp4",
            setOf(CandidateSource.MANIFEST), MediaKind.DIRECT),
    )

    @Test
    fun successfulResultsKeepTheOriginalSingleArgumentConstructor() {
        assertEquals(emptyList<String>(), SiteExtractionResult.Success(candidates).details)
    }

    @Test
    fun successfulWarningStepsCannotLeakThroughToString() {
        val result = SiteExtractionResult.Success(
            candidates,
            listOf("page GET 200", "Cookie: fixture-only", "https://a.test/?signature=fixture"),
        ).toString()

        assertFalse(result.contains("Cookie"))
        assertFalse(result.contains("signature="))
        assertFalse(result.contains("?"))
        assertFalse(result.contains("fixture"))
    }
}
