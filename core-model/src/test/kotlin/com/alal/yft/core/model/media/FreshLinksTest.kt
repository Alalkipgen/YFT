package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P37 (R17, R18): on a site without an adapter the address the page's player asked for comes
 * before the link the page's script names; a dead script link has the player's video to try
 * before the page's next one; the Details tell where a link came from and whether it expired.
 */
class FreshLinksTest {
    private val page = "https://clips.example.test/watch/5"
    private val scriptLink = "https://media.example.test/v5/720.mp4?validto=1700000000&hash=old"
    private val playerLink = "https://media.example.test/v5/720.mp4?validto=1700009999&hash=new"

    private fun candidate(
        url: String,
        source: CandidateSource,
        observedAt: Long = 0,
        height: Int? = null,
        durationMillis: Long? = null,
        title: String? = null,
        role: PageMediaRole? = null,
        videoId: String? = null,
    ) = MediaCandidate(
        pageUrl = page,
        mediaUrl = url,
        sources = setOf(source),
        kind = MediaKind.DIRECT,
        title = title,
        durationMillis = durationMillis,
        height = height,
        observedAtEpochMs = observedAt,
        pageRole = role,
        videoId = videoId,
    )

    private fun group(vararg candidates: MediaCandidate) =
        MediaGroup(key = "v5", title = null, candidates = candidates.toList())

    @Test
    fun thePlayersRequestOfTheSameFileReplacesTheScriptsLink() {
        val script = candidate(scriptLink, CandidateSource.DOM, height = 720, title = "Harbour")
        val older = candidate(playerLink.replace("new", "older"), CandidateSource.REQUEST, 5)
        val player = candidate(playerLink, CandidateSource.REQUEST, observedAt = 9)

        val shown = FreshLinks.playerFirst(group(script), listOf(older, player, script))

        assertEquals(listOf(playerLink), shown.candidates.map { it.mediaUrl })
        assertEquals("Harbour", shown.candidates.single().title)
        assertEquals(720, shown.candidates.single().height)
        assertEquals(LinkOrigin.PLAYER_REQUEST, FreshLinks.origin(shown.candidates.single()))
    }

    @Test
    fun aScriptLinkOfAHeightThePlayerAskedForGivesWayAndThePlayerComesFirst() {
        val player = candidate("https://cdn2.test/a/720.mp4", CandidateSource.REQUEST, 3, 720)
        val script720 = candidate("https://media.test/b/720.mp4", CandidateSource.DOM, 0, 720)
        val script480 = candidate("https://media.test/b/480.mp4", CandidateSource.DOM, 0, 480)

        val shown = FreshLinks.playerFirst(group(script720, script480, player), emptyList())

        assertEquals(
            listOf(player.mediaUrl, script480.mediaUrl),
            shown.candidates.map { it.mediaUrl },
        )
    }

    @Test
    fun aGroupWithoutPlayerRequestsAndAnAdaptersVideoStayAsTheyAre() {
        val script = group(candidate(scriptLink, CandidateSource.DOM))
        assertSame(script, FreshLinks.playerFirst(script, listOf(script.candidates.single())))

        val adapter = group(candidate(scriptLink, CandidateSource.DOM, videoId = "abc"))
        val player = candidate(playerLink, CandidateSource.REQUEST, 9)
        assertSame(adapter, FreshLinks.playerFirst(adapter, listOf(player)))
    }

    @Test
    fun thePlayersVideoOfTheSameLengthIsTriedAndAnAdOrATriedLinkNever() {
        val failed = group(candidate(scriptLink, CandidateSource.DOM, durationMillis = 600_000))
        val ad = MediaGroup(
            "ad",
            null,
            listOf(
                candidate(
                    "https://ads.example.test/spot.mp4",
                    CandidateSource.REQUEST,
                    observedAt = 20,
                    durationMillis = 15_000,
                ),
            ),
        )
        val preview = MediaGroup(
            "preview",
            null,
            listOf(
                candidate(
                    "https://media.example.test/v5/preview.mp4",
                    CandidateSource.REQUEST,
                    observedAt = 30,
                    role = PageMediaRole.PREVIEW,
                ),
            ),
        )
        val tried = MediaGroup(
            "tried",
            null,
            listOf(candidate("https://cdn.example.test/dead.mp4", CandidateSource.REQUEST, 40)),
        )
        val video = MediaGroup(
            "video",
            null,
            listOf(
                candidate(
                    "https://cdn.example.test/v5/full.mp4",
                    CandidateSource.REQUEST,
                    observedAt = 10,
                    durationMillis = 601_000,
                ),
                candidate("https://cdn.example.test/v5/full-dom.mp4", CandidateSource.DOM),
            ),
        )

        val chosen = FreshLinks.playerVideo(
            failed,
            listOf(ad, preview, tried, video),
            tried = setOf("https://cdn.example.test/dead.mp4"),
        )

        assertEquals(
            listOf("https://cdn.example.test/v5/full.mp4"),
            chosen?.candidates?.map { it.mediaUrl },
        )
    }

    @Test
    fun withoutAPlayerRequestThereIsNoPlayersVideo() {
        val failed = group(candidate(scriptLink, CandidateSource.DOM))
        val other = MediaGroup(
            "other",
            null,
            listOf(candidate("https://cdn.example.test/o.mp4", CandidateSource.DOM)),
        )
        assertNull(FreshLinks.playerVideo(failed, listOf(other), emptySet()))
    }

    @Test
    fun theLinksOriginNamesWhoGaveIt() {
        val script = candidate(scriptLink, CandidateSource.DOM)
        assertEquals(LinkOrigin.PAGE_SCRIPT, FreshLinks.origin(script))
        val pasted = candidate(scriptLink, CandidateSource.PASTED_URL)
        assertEquals(LinkOrigin.PASTED_LINK, FreshLinks.origin(pasted))
        val reread = candidate(scriptLink, CandidateSource.DOM).let {
            it.copy(sources = it.sources + CandidateSource.PAGE_REREAD)
        }
        assertEquals(LinkOrigin.PAGE_READ_AGAIN, FreshLinks.origin(reread))
    }

    @Test
    fun theExpiryComesFromTheSourceOrAnExpiryLikeQueryValue() {
        val stated = candidate(scriptLink, CandidateSource.DOM).copy(expiresAtEpochMs = 5_000)
        assertEquals(LinkExpiry.PASSED, FreshLinks.expiry(stated, nowMillis = 6_000))
        assertEquals(LinkExpiry.NOT_PASSED, FreshLinks.expiry(stated, nowMillis = 4_000))

        val signed = candidate(scriptLink, CandidateSource.DOM)
        assertEquals(1_700_000_000_000L, FreshLinks.expiryOf(scriptLink))
        assertEquals(LinkExpiry.PASSED, FreshLinks.expiry(signed, 1_700_000_000_001L))
        assertEquals(LinkExpiry.NOT_PASSED, FreshLinks.expiry(signed, 1_699_999_999_000L))
        val millis = "https://a.test/v.mp4?Expires=1700000000123"
        assertEquals(1_700_000_000_123L, FreshLinks.expiryOf(millis))
        val seconds = "https://a.test/v.mp4?x-expires=1700000000"
        assertEquals(1_700_000_000_000L, FreshLinks.expiryOf(seconds))
    }

    @Test
    fun aSmallNumberOrNoExpiryValueStatesNoExpiry() {
        assertNull(FreshLinks.expiryOf("https://a.test/v.mp4?e=3600"))
        assertNull(FreshLinks.expiryOf("https://a.test/v.mp4?token=1700000000"))
        assertNull(FreshLinks.expiryOf("https://a.test/v.mp4"))
        val plain = candidate("https://a.test/v.mp4", CandidateSource.DOM)
        assertEquals(LinkExpiry.NONE, FreshLinks.expiry(plain, 0))
        assertTrue(FreshLinks.unsigned(scriptLink) == "https://media.example.test/v5/720.mp4")
    }
}
