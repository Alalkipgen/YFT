package com.alal.yft.extractor.generic.normalizer

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateNormalizerTest {
    private val pageUrl = "https://example.test/watch?id=1"

    @Test
    fun deduplicatesSignedUrlsAndMergesRichestMetadataAndContext() {
        val first = candidate(
            mediaUrl = "https://cdn.test/movie.mp4?token=old&quality=720",
            sources = setOf(CandidateSource.REQUEST),
            confidence = CandidateConfidence.MEDIUM,
            observedAt = 10,
            context = BrowserRequestContext(pageUrl, "UA", null, mapOf("Accept" to "video/*")),
        )
        val second = candidate(
            mediaUrl = "https://cdn.test/movie.mp4?token=new&quality=720",
            sources = setOf(CandidateSource.DOM),
            title = "Fixture movie",
            durationMillis = 60_000,
            confidence = CandidateConfidence.HIGH,
            observedAt = 20,
            context = BrowserRequestContext(pageUrl, null, "session=fake", emptyMap()),
        )

        val result = CandidateNormalizer().normalize(pageUrl, listOf(first, second))

        assertEquals(1, result.size)
        // P37: the address the page's player asked for stays over the script's later copy.
        assertTrue(result.single().mediaUrl.contains("token=old"))
        assertEquals(setOf(CandidateSource.REQUEST, CandidateSource.DOM), result.single().sources)
        assertEquals("Fixture movie", result.single().title)
        assertEquals(60_000L, result.single().durationMillis)
        assertEquals("session=fake", result.single().requestContext.cookie)
        assertEquals("video/*", result.single().requestContext.observedHeaders["Accept"])
    }

    @Test
    fun mergeKeepsTheAudioCompanionAndCodecsOfAnOlderObservation() {
        val audio = CompanionAudio(
            mediaUrl = "https://media.test/audio.m4a?token=fake",
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"),
            requestContext = BrowserRequestContext(pageUrl, "UA", null),
        )
        val fromAdapter = candidate(
            mediaUrl = "https://media.test/video.mp4?token=old",
            sources = setOf(CandidateSource.PASTED_URL),
            observedAt = 10,
        ).copy(codecs = listOf("avc1.4d401f"), audioCompanion = audio)
        val fromBrowser = candidate(
            mediaUrl = "https://media.test/video.mp4?token=new",
            sources = setOf(CandidateSource.REQUEST),
            observedAt = 20,
        )

        val merged = CandidateNormalizer().normalize(pageUrl, listOf(fromAdapter, fromBrowser))

        assertEquals(1, merged.size)
        assertEquals(audio, merged.single().audioCompanion)
        assertEquals(listOf("avc1.4d401f"), merged.single().codecs)
    }

    @Test
    fun preservesDistinctQualityParameters() {
        val candidates = listOf(
            candidate("https://cdn.test/movie.mp4?quality=720&token=a"),
            candidate("https://cdn.test/movie.mp4?quality=1080&token=b"),
        )

        assertEquals(2, CandidateNormalizer().normalize(pageUrl, candidates).size)
    }

    @Test
    fun byteRangePiecesOfOneFileAreTheWholeFileOnce() {
        // P3-FIX regression: Facebook's player fetched each track in byte ranges, and every
        // piece was listed as another "Video file" (26 rows in pairs).
        val track = "https://video.cdn.test/v/t42/abc_n.mp4?_nc_cat=1&oh=fake"
        val audio = "https://video.cdn.test/v/t42/def_n.mp4?_nc_cat=1&oh=fake"
        val pieces = (0 until 13).flatMap { index ->
            val range = "bytestart=${index * 1000}&byteend=${index * 1000 + 999}"
            listOf(
                candidate("$track&$range", contentLengthBytes = 1_000, observedAt = index * 2L),
                candidate("$audio&$range", contentLengthBytes = 1_000, observedAt = index * 2L + 1),
            )
        } + candidate("https://cdn.test/movie.mp4?range=0-4095", contentLengthBytes = 4_096)

        val result = CandidateNormalizer().normalize(pageUrl, pieces)

        assertEquals(
            setOf(track, audio, "https://cdn.test/movie.mp4"),
            result.map(MediaCandidate::mediaUrl).toSet(),
        )
        // A piece's length is not the file's.
        assertTrue(result.all { it.contentLengthBytes == null })
    }

    @Test
    fun requestsForOneOpaqueCdnFileWithOtherPerRequestValuesAreOneFile() {
        val file = "https://video.cdn.test/o1/v/t2/AQOfixtureOpaqueName0123456789_abc.mp4"
        val result = CandidateNormalizer().normalize(
            pageUrl,
            listOf(
                candidate("$file?_nc_gid=first&_nc_zt=1", observedAt = 1),
                candidate("$file?_nc_gid=second&_nc_zt=2", observedAt = 2),
            ),
        )

        assertEquals(listOf("$file?_nc_gid=second&_nc_zt=2"), result.map(MediaCandidate::mediaUrl))
    }

    @Test
    fun hlsPiecesAreOneStreamAndDistinctVideosStayApart() {
        val pieces = (1..5).map { candidate("https://cdn.test/hls/720/seg-$it.ts?token=t$it") }
        val manifest = candidate(
            "https://cdn.test/hls/master.m3u8",
            kind = MediaKind.HLS,
            confidence = CandidateConfidence.HIGH,
        )
        val clips = (1..3).map { candidate("https://cdn.test/clips/clip-$it.mp4") }
        val normalizer = CandidateNormalizer()

        // Next to the manifest the pieces drop out; the numbered clips are three videos.
        assertEquals(
            listOf("https://cdn.test/hls/master.m3u8") + clips.map(MediaCandidate::mediaUrl),
            normalizer.normalize(pageUrl, pieces + manifest + clips)
                .map(MediaCandidate::mediaUrl)
                .sortedBy { !it.endsWith(".m3u8") },
        )
        // Without it the series keeps its first piece.
        assertEquals(
            listOf("https://cdn.test/hls/720/seg-1.ts?token=t1"),
            normalizer.normalize(pageUrl, pieces).map(MediaCandidate::mediaUrl),
        )
    }

    @Test
    fun rejectsBlobTrackingTinyAndWrongPageObservations() {
        val candidates = listOf(
            candidate("blob:https://example.test/id", kind = MediaKind.UNKNOWN, sources = setOf(CandidateSource.DOM)),
            candidate("https://cdn.test/pixel.mp4", contentLengthBytes = 20_000),
            candidate("https://cdn.test/tiny.mp4", contentLengthBytes = 512),
            candidate("https://cdn.test/valid.mp4", candidatePageUrl = "https://example.test/other"),
        )

        assertTrue(CandidateNormalizer().normalize(pageUrl, candidates).isEmpty())
    }

    @Test
    fun acceptsUnknownDomCandidateButNotUnknownRequestCandidate() {
        val dom = candidate(
            mediaUrl = "https://cdn.test/generated?id=media",
            kind = MediaKind.UNKNOWN,
            sources = setOf(CandidateSource.DOM),
        )
        val request = candidate(
            mediaUrl = "https://cdn.test/generated?id=request",
            kind = MediaKind.UNKNOWN,
            sources = setOf(CandidateSource.REQUEST),
        )

        val result = CandidateNormalizer().normalize(pageUrl, listOf(dom, request))

        assertEquals(1, result.size)
        assertTrue(result.single().mediaUrl.contains("id=media"))
    }

    @Test
    fun boundsCandidatesAndOrdersByConfidenceThenRecency() {
        val candidates = (1..6).map { index ->
            candidate(
                mediaUrl = "https://cdn.test/$index.mp4",
                confidence = if (index == 2) CandidateConfidence.HIGH else CandidateConfidence.LOW,
                observedAt = index.toLong(),
            )
        }

        val result = CandidateNormalizer(CandidateNormalizer.Policy(maxCandidates = 3))
            .normalize(pageUrl, candidates)

        assertEquals(3, result.size)
        assertTrue(result.first().mediaUrl.endsWith("/2.mp4"))
        assertFalse(result.any { it.mediaUrl.endsWith("/1.mp4") })
    }

    @Test
    fun aFileThePageNamesAsItsVideoStaysItsVideoWhereverElseItShows() {
        // P24: the page's JSON-LD names the stream; the same stream played muted is no preview.
        val stream = "https://cdn.test/v/master.m3u8"
        val named = candidate(stream, kind = MediaKind.HLS).copy(pageRole = PageMediaRole.MAIN)
        val played = candidate(stream, kind = MediaKind.HLS, observedAt = 9)
            .copy(pageRole = PageMediaRole.PREVIEW)
        val clip = candidate("https://cdn.test/clip.mp4").copy(pageRole = PageMediaRole.PREVIEW)
        val plain = candidate("https://cdn.test/clip.mp4", observedAt = 9)

        val result = CandidateNormalizer().normalize(pageUrl, listOf(named, played, clip, plain))

        assertEquals(
            listOf(PageMediaRole.MAIN, PageMediaRole.PREVIEW),
            result.map { it.pageRole },
        )
    }

    @Test
    fun thePlayersRequestKeepsItsAddressAndOfTwoRequestsTheNewestWins() {
        val script = candidate(
            mediaUrl = "https://cdn.test/clip.mp4?token=script",
            sources = setOf(CandidateSource.DOM),
            observedAt = 30,
        )
        val player = candidate(
            mediaUrl = "https://cdn.test/clip.mp4?token=player",
            sources = setOf(CandidateSource.REQUEST),
            observedAt = 20,
        )
        val later = candidate(
            mediaUrl = "https://cdn.test/clip.mp4?token=later",
            sources = setOf(CandidateSource.REQUEST),
            observedAt = 40,
        )

        val first = CandidateNormalizer().normalize(pageUrl, listOf(player, script)).single()
        val both = CandidateNormalizer().normalize(pageUrl, listOf(player, script, later)).single()

        assertTrue(first.mediaUrl.endsWith("token=player"))
        assertTrue(both.mediaUrl.endsWith("token=later"))
    }

    private fun candidate(
        mediaUrl: String,
        candidatePageUrl: String = pageUrl,
        sources: Set<CandidateSource> = setOf(CandidateSource.REQUEST),
        kind: MediaKind = MediaKind.DIRECT,
        title: String? = null,
        durationMillis: Long? = null,
        contentLengthBytes: Long? = null,
        confidence: CandidateConfidence = CandidateConfidence.MEDIUM,
        observedAt: Long = 1,
        context: BrowserRequestContext = BrowserRequestContext(candidatePageUrl, null, null),
    ) = MediaCandidate(
        pageUrl = candidatePageUrl,
        mediaUrl = mediaUrl,
        sources = sources,
        kind = kind,
        title = title,
        durationMillis = durationMillis,
        contentLengthBytes = contentLengthBytes,
        confidence = confidence,
        observedAtEpochMs = observedAt,
        requestContext = context,
    )
}
