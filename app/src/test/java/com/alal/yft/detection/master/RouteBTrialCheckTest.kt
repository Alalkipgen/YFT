package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Phase 1.4 item 7 (YouTube routing B) F7: the route-B trial's comparison of Master's rows with
 * main's set-aside rows of the same lookup — the files' one-byte check now, whether the links and
 * the download headers are main's, and which names differ. Names, statuses and counts only.
 */
class RouteBTrialCheckTest {
    @Test
    fun mainsOwnLinksAreSaidSameAndEachDistinctFileIsCheckedOnce() = runTest {
        val checked = mutableListOf<String>()
        val rows = listOf(row(VIDEO_134, AUDIO_140), row(VIDEO_133, AUDIO_140))

        val segment = RouteBTrialCheck.segment(
            rows,
            rows.map { it.copy() },
            RouteBTrialCheck.FileCheck { url, _ ->
                checked += url
                403
            },
        )

        assertEquals("; check: 403×3; same as main's: 2 of 2 rows, headers same", segment)
        assertEquals(listOf(VIDEO_134, AUDIO_140, VIDEO_133), checked)
    }

    @Test
    fun anotherLinkNamesWhatDiffersAndChecksMainsLinkToo() = runTest {
        val master = row("$VIDEO_134&n=AAAA", AUDIO_140, agent = "Agent-One")
        val main = row("$VIDEO_134&n=BBBB&pot=SECRETPOT", AUDIO_140, agent = "Agent-Two")

        val segment = RouteBTrialCheck.segment(
            listOf(master),
            listOf(main),
            RouteBTrialCheck.FileCheck { url, _ -> if ("pot=" in url) 206 else 403 },
        )

        assertEquals(
            "; check: 403×2; same as main's: 0 of 1 rows, headers differ (User-Agent); differ: n; " +
                "main's only: pot; main's check: 206×1",
            segment,
        )
        for (value in listOf("AAAA", "BBBB", "SECRETPOT", "Agent-One", "Agent-Two", "googlevideo", "SIG1")) {
            assertFalse(value, value in segment)
        }
    }

    @Test
    fun aStreamMainNeverHadIsCountedAndNoCheckRunsWithoutOne() = runTest {
        val segment = RouteBTrialCheck.segment(listOf(row(VIDEO_133)), listOf(row(VIDEO_134)), check = null)

        assertEquals("; same as main's: 0 of 1 rows, no main's stream to compare; streams not in main's: 1", segment)
    }

    @Test
    fun oddOrManyNamesStayShort() = runTest {
        val master = row("$VIDEO_134&a=1&b=1&c2=1&d=1&e=1&f=1&g=1&%3Cx%3E=1")
        val main = row(VIDEO_134)

        val segment = RouteBTrialCheck.segment(listOf(master), listOf(main), check = null)

        assertEquals(
            "; same as main's: 0 of 1 rows, headers same; Master's only: a, b, c2, d, e, f, …",
            segment,
        )
        val odd = RouteBTrialCheck.segment(listOf(row("$VIDEO_134&%3Cx%3E=1")), listOf(main), check = null)
        assertEquals("; same as main's: 0 of 1 rows, headers same; Master's only: ?", odd)
    }

    private fun row(video: String, audio: String? = null, agent: String = "Agent") = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = video,
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        requestContext = BrowserRequestContext(PAGE, agent, null),
        audioCompanion = audio?.let {
            CompanionAudio(it, "audio/mp4", listOf("mp4a.40.2"), BrowserRequestContext(PAGE, agent, null))
        },
    )

    private companion object {
        const val PAGE = "https://m.youtube.com/watch?v=Yft0Fixture"
        const val MEDIA = "https://rr1---sn-test.googlevideo.com/videoplayback?expire=1900000000&c=VISIONOS&sig=SIG1"
        const val VIDEO_134 = "$MEDIA&itag=134"
        const val VIDEO_133 = "$MEDIA&itag=133"
        const val AUDIO_140 = "$MEDIA&itag=140"
    }
}
