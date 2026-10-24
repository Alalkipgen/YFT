// Provenance (Master R6 / YT-1): main 34a41890 extractor-sites/src/test/kotlin/com/alal/yft/
// extractor/sites/youtube/YouTubeSessionAuthTest.kt, run unchanged against the Master copy (passthrough parity).
package com.alal.yft.extractor.master.modules.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected hashes were computed independently with Python's `hashlib.sha1`. */
class YouTubeSessionAuthTest {
    @Test
    fun `the proof hashes the time, the session cookie and YouTube's origin`() {
        assertEquals(
            "1791000000_f06eff5f2684df83beb67c17102f72e3f0e1a971",
            YouTubeSessionAuth.proof(NOW, SAPISID, userSessionId = null),
        )
        assertEquals(
            "1791000000_2aa0705aa7a2fded7fce5c60e24abf4a62dd1fbf_u",
            YouTubeSessionAuth.proof(NOW, SAPISID, userSessionId = "Fixture0User"),
        )
    }

    @Test
    fun `a signed-in session gets every scheme YouTube's web player sends`() {
        val headers = YouTubeSessionAuth.headers(
            cookie = "PREF=f6=40000000; SAPISID=$SAPISID; __Secure-1PAPISID=$FIRST_PARTY; " +
                "__Secure-3PAPISID=$THIRD_PARTY; SID=fixture-session",
            signals = signals(loggedIn = true, dataSyncId = "Fixture0User||", sessionIndex = 0),
            nowEpochSeconds = NOW,
        )

        assertEquals(
            "SAPISIDHASH 1791000000_2aa0705aa7a2fded7fce5c60e24abf4a62dd1fbf_u " +
                "SAPISID1PHASH 1791000000_52266d7c5b19fdd8cf802d9f42442de838486e8b_u " +
                "SAPISID3PHASH 1791000000_c722c7e812ef50190cde1dca55a881a94759bc70_u",
            headers["Authorization"],
        )
        assertEquals("https://www.youtube.com", headers["X-Origin"])
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals("true", headers["X-Youtube-Bootstrap-Logged-In"])
        assertEquals(null, headers["X-Goog-PageId"])
    }

    @Test
    fun `a secondary channel names its page and its session index`() {
        val headers = YouTubeSessionAuth.headers(
            cookie = "SAPISID=$SAPISID",
            signals = signals(
                loggedIn = true,
                dataSyncId = "Fixture0Delegated||Fixture0User",
                sessionIndex = 1,
            ),
            nowEpochSeconds = NOW,
        )

        assertEquals(
            "SAPISIDHASH 1791000000_2aa0705aa7a2fded7fce5c60e24abf4a62dd1fbf_u",
            headers["Authorization"],
        )
        assertEquals("Fixture0Delegated", headers["X-Goog-PageId"])
        assertEquals("1", headers["X-Goog-AuthUser"])
    }

    @Test
    fun `a page that is not signed in hashes without a user session`() {
        val headers = YouTubeSessionAuth.headers(
            cookie = "__Secure-3PAPISID=$THIRD_PARTY",
            signals = signals(loggedIn = false, dataSyncId = "Fixture0Visitor||"),
            nowEpochSeconds = NOW,
        )

        assertEquals(
            "SAPISIDHASH 1791000000_4140150f240aac573cf933b99536d9b22ae1cf72 " +
                "SAPISID3PHASH 1791000000_4140150f240aac573cf933b99536d9b22ae1cf72",
            headers["Authorization"],
        )
        assertEquals(setOf("Authorization", "X-Origin"), headers.keys)
    }

    @Test
    fun `a cookie without a session cookie gets no authorization`() {
        listOf(
            null,
            "",
            "PREF=fixture; SID=fixture-session",
            "SAPISID=",
            "SAPISID=short",
            "SAPISID=has spaces in it",
            "SAPISID=\"quoted-value-0123\"",
        ).forEach { cookie ->
            assertTrue(
                cookie.toString(),
                YouTubeSessionAuth.headers(cookie, signals(loggedIn = true), NOW).isEmpty(),
            )
        }
    }

    private fun signals(
        loggedIn: Boolean? = null,
        dataSyncId: String? = null,
        sessionIndex: Int? = null,
    ) = YouTubePageSignals(
        playerResponseJson = null,
        playerId = "f1x7ure0",
        apiKey = null,
        clientName = "WEB",
        clientVersion = null,
        visitorData = null,
        signatureTimestamp = null,
        loggedIn = loggedIn,
        dataSyncId = dataSyncId,
        sessionIndex = sessionIndex,
    )

    private companion object {
        const val NOW = 1_791_000_000L
        const val SAPISID = "FixtureSapisid/0123456789"
        const val FIRST_PARTY = "Fixture1PSapisid/0123456789"
        const val THIRD_PARTY = "Fixture3PSapisid/0123456789"
    }
}
