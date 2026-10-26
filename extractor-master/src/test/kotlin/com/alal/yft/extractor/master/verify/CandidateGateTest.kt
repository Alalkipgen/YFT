package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.candidate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What may be offered at all, whichever layer or capture found it. */
class CandidateGateTest {
    @Test
    fun `a site's build resource is never offered, a video's file is (task 18, O1)`() {
        assertTrue(CandidateGate.eligible(candidate("https://video.example-cdn.test/v/main-720.mp4?oe=7A000000"), NOW))
        listOf(
            "https://static.example-cdn.test/rsrc.php/v/stand-in.mp4",
            "https://static.example-cdn.test/RSRC.PHP/v/stand-in.mp4",
            "https://static.example-cdn.test/rsrc.php/yY/r/fixture.mp4?_nc_x=1",
        ).forEach { assertFalse(it, CandidateGate.eligible(candidate(it), NOW)) }
        // The path's own segment only: not a file named so, nor a query saying it.
        assertTrue(CandidateGate.eligible(candidate("https://video.example-cdn.test/v/rsrc.php.mp4"), NOW))
        assertTrue(CandidateGate.eligible(candidate("https://video.example-cdn.test/v/main.mp4?from=/rsrc.php/"), NOW))
    }

    @Test
    fun `plain HTTP, DRM, ads, previews and an expired file are still never offered`() {
        val file = candidate("https://video.example-cdn.test/v/main-720.mp4")
        assertFalse(CandidateGate.eligible(candidate("http://video.example-cdn.test/v/main-720.mp4"), NOW))
        assertFalse(CandidateGate.eligible(file.copy(drmHint = true), NOW))
        assertFalse(CandidateGate.eligible(candidate("https://ad.doubleclick.net/v/main.mp4"), NOW))
        assertFalse(CandidateGate.eligible(candidate("https://video.example-cdn.test/ads/main.mp4"), NOW))
        assertFalse(CandidateGate.eligible(file.copy(pageRole = PageMediaRole.PREVIEW), NOW))
        assertFalse(CandidateGate.eligible(file.copy(expiresAtEpochMs = NOW), NOW))
        assertTrue(CandidateGate.eligible(file.copy(expiresAtEpochMs = NOW + 1), NOW))
    }

    /** F1 (VK's pack proof, V3's retest 2026-10-26): a piece named by its bytes is never offered. */
    @Test
    fun `a piece of a file named by its bytes is never offered, its whole file is`() {
        assertFalse(CandidateGate.eligible(candidate("https://vkvd1.media.test/?type=2&id=7&bytes=0-1427"), NOW))
        assertTrue(CandidateGate.eligible(candidate("https://vkvd1.media.test/?type=2&id=7"), NOW))
        assertTrue(CandidateGate.eligible(candidate("https://video.example-cdn.test/v/main.mp4?bytes=0-"), NOW))
    }
}
