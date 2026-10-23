package com.alal.yft.detection.master

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Social plan task 14 (observation e): which words a social lookup's "needs playback" answer shows
 * ([SocialLadder.playbackWords]): the playback words only when the tab shows a video or the site's
 * answer names the post's video; a sign-in or age wall the sign-in words; else main's words.
 * Synthetic lines in each ladder's shape (words only; made-up keys), no lookup.
 */
class SocialPlaybackWordsTest {

    @Test
    fun anInstagramWallWithNoVideoAnywhereGetsTheSignInWords() {
        // Slot 9's shape: main's GraphQL 401, the pages without post data, the rung's answer
        // without the video.
        val details = listOf(
            "master: instagram ladder after LOGIN_REQUIRED; main's answers read, no new ask",
            "master: instagram I2 main's GraphQL: LOGIN_WALL (status 401)",
            "master: instagram I2 main's page: NEEDS_PLAYBACK (no post data in the page)",
            "master: instagram I2: LOGIN_WALL (GraphQL: status 401); next: BROWSER",
            "master: instagram probe: HTTP 200, 157 bytes; the post's video in the answer: no; " +
                "a wall on the page: no; rows 0 (NEEDS_PLAYBACK: no post data in the page)",
        )

        val pick = SocialLadder.playbackWords(INSTAGRAM, details, tabVideo = false)

        assertEquals(SocialLadder.WordsPick(SocialLadder.Words.SIGN_IN, "LOGIN_WALL; no video in the tab"), pick)
        assertEquals(
            "master: instagram words: sign-in (LOGIN_WALL; no video in the tab)",
            SocialLadder.wordsLine(INSTAGRAM, pick),
        )
    }

    @Test
    fun anInstagramVideoNamedByTheEmbedPageOrTheRungGetsThePlaybackWords() {
        // Slot 5's shape: the embed page names the video and lists no file (task 13, observation d).
        val embed = listOf(
            "master: instagram I2 main's GraphQL: LOGIN_WALL (status 401)",
            "master: instagram I2 main's embed page: VIDEO_WITHOUT_FILES (a video, no files read)",
            "master: instagram I2: LOGIN_WALL (GraphQL: status 401); next: BROWSER",
        )
        assertEquals(
            SocialLadder.WordsPick(SocialLadder.Words.PLAYBACK, "the site's answer names the video: VIDEO_WITHOUT_FILES"),
            SocialLadder.playbackWords(INSTAGRAM, embed, tabVideo = false),
        )
        // The rung read the post's video, and V kept none of its rows.
        val rung = listOf(
            "master: instagram I2: LOGIN_WALL (GraphQL: status 401); next: BROWSER",
            "master: instagram probe: HTTP 200, 9000 bytes; the post's video in the answer: yes; " +
                "a wall on the page: no; rows 0 (NEEDS_PLAYBACK: no post data in the page)",
        )
        assertEquals(
            SocialLadder.WordsPick(SocialLadder.Words.PLAYBACK, "the site's answer names the video: the post's video in the answer: yes"),
            SocialLadder.playbackWords(INSTAGRAM, rung, tabVideo = false),
        )
    }

    @Test
    fun theRungsWallAndInstagramsNoPostGetTheirOwnWords() {
        val wall = listOf(
            "master: instagram I2: NEEDS_PLAYBACK (page: no post data in the page); next: PLAYBACK",
            "master: instagram probe: HTTP 200, 4000 bytes; the post's video in the answer: no; " +
                "a wall on the page: yes; rows 0 (LOGIN_WALL: a wall on the page: never read)",
        )
        assertEquals(
            SocialLadder.Words.SIGN_IN,
            SocialLadder.playbackWords(INSTAGRAM, wall, tabVideo = false).words,
        )
        // No post in the answer (removed, private or gated alike; decision 20 (ii)): main's words.
        val noPost = listOf(
            "master: instagram I2 main's GraphQL: NEEDS_PLAYBACK (no post in the answer: removed, private or gated alike)",
            "master: instagram I2: NEEDS_PLAYBACK (GraphQL: no post in the answer: removed, private or gated alike); next: PLAYBACK",
        )
        assertEquals(
            SocialLadder.WordsPick(SocialLadder.Words.MAINS, "no video in the tab"),
            SocialLadder.playbackWords(INSTAGRAM, noPost, tabVideo = false),
        )
    }

    @Test
    fun xsTombstonesAndItsPlaylistGetTheirOwnWords() {
        val notice = listOf(
            "master: x X1 main's answer: NEEDS_PLAYBACK (a tombstone with X's notice: removed, protected or suspended alike); next: PLAYBACK",
        )
        assertEquals(SocialLadder.Words.MAINS, SocialLadder.playbackWords(X, notice, tabVideo = false).words)
        val age = listOf("master: x X1 main's answer: AGE_LIMITED (an empty tombstone); next: BROWSER")
        assertEquals(
            SocialLadder.WordsPick(SocialLadder.Words.SIGN_IN, "AGE_LIMITED; no video in the tab"),
            SocialLadder.playbackWords(X, age, tabVideo = false),
        )
        val playlist = listOf("master: x X1 main's answer: NEEDS_PLAYBACK (the item's playlist only, no length); next: PLAYBACK")
        assertEquals(
            SocialLadder.WordsPick(SocialLadder.Words.PLAYBACK, "the site's answer names the video: the item's playlist only"),
            SocialLadder.playbackWords(X, playlist, tabVideo = false),
        )
    }

    @Test
    fun facebookAndTikTokAnswersThatNameTheVideoGetThePlaybackWords() {
        val plugin = listOf("master: facebook F1 plugin player: NEEDS_PLAYBACK (the plugin player: needs playback); next: PLAYBACK")
        assertEquals(SocialLadder.Words.PLAYBACK, SocialLadder.playbackWords(FACEBOOK, plugin, tabVideo = false).words)
        val named = listOf("master: tiktok T1 embed player: NEEDS_PLAYBACK (names the video, no file); next: PLAYBACK")
        assertEquals(SocialLadder.Words.PLAYBACK, SocialLadder.playbackWords(TIKTOK, named, tabVideo = false).words)
        val status = listOf(
            "master: tiktok T1 embed player: NEEDS_PLAYBACK (TikTok status: private or unavailable; one anonymous answer); next: PLAYBACK",
        )
        assertEquals(
            SocialLadder.WordsPick(SocialLadder.Words.MAINS, "no video in the tab"),
            SocialLadder.playbackWords(TIKTOK, status, tabVideo = false),
        )
    }

    @Test
    fun aVideoInTheTabWinsAndAnotherSitesLinesAreNotRead() {
        val wall = listOf("master: x X1 main's answer: AGE_LIMITED (an empty tombstone); next: BROWSER")
        assertEquals(
            SocialLadder.WordsPick(SocialLadder.Words.PLAYBACK, "the tab shows a video"),
            SocialLadder.playbackWords(X, wall, tabVideo = true),
        )
        assertEquals(
            "master: x words: playback (the tab shows a video)",
            SocialLadder.wordsLine(X, SocialLadder.playbackWords(X, wall, tabVideo = true)),
        )
        // Another site's lines (a wall, a named video) do not pick this site's words.
        val other = listOf(
            "master: facebook F1 plugin player: NEEDS_PLAYBACK (the plugin player: needs playback); next: PLAYBACK",
            "master: instagram I2: LOGIN_WALL (GraphQL: status 401); next: BROWSER",
        )
        assertEquals(SocialLadder.Words.MAINS, SocialLadder.playbackWords(X, other, tabVideo = false).words)
        assertEquals(SocialLadder.Words.MAINS, SocialLadder.playbackWords(X, emptyList(), tabVideo = false).words)
    }

    @Test
    fun theSignInWordsAreTheAppsOwnForEachSite() {
        assertEquals("Sign in to X on this page first, then try again.", SocialLadder.signInMessage(X))
        assertEquals("Sign in to Instagram on this page first, then try again.", SocialLadder.signInMessage(INSTAGRAM))
        assertEquals("Sign in to TikTok on this page first, then try again.", SocialLadder.signInMessage(TIKTOK))
        assertEquals("Sign in to Facebook on this page first, then try again.", SocialLadder.signInMessage(FACEBOOK))
    }

    private companion object {
        const val INSTAGRAM = "instagram:C9fixtREEL1"
        const val X = "x:1000000000000000001"
        const val FACEBOOK = "facebook:1000000000000001"
        const val TIKTOK = "tiktok:7000000000000000001"
    }
}
