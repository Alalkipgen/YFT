package com.alal.yft.extractor.sites.youtube

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeClientProfileTest {
    @Test
    fun `the embedded player is asked without the session and names YFT as the embedder`() {
        val profile = YouTubeClientProfiles.embedded(signals(clientVersion = "2.20261002.01.00"))

        assertEquals("WEB_EMBEDDED_PLAYER", profile.clientName)
        assertEquals(56, profile.clientNameId)
        assertEquals("2.20261002.01.00", profile.clientVersion)
        assertFalse(profile.replaysSession)
        assertEquals("https://github.com/Alalkipgen/YFT", profile.thirdPartyEmbedUrl)
    }

    @Test
    fun `a client version that does not look like one falls back to the pinned version`() {
        listOf(null, "2.2026", "99.20261002.01.00", "2.20261002.01.00\"").forEach { version ->
            assertEquals(
                version.toString(),
                YouTubeClientProfiles.FALLBACK_CLIENT_VERSION,
                YouTubeClientProfiles.embedded(signals(clientVersion = version)).clientVersion,
            )
        }
    }

    @Test
    fun `the page client is the browser client the page names, with the session`() {
        val mobile = YouTubeClientProfiles.page(signals(clientName = "MWEB"))
        assertEquals("MWEB", mobile.clientName)
        assertEquals(2, mobile.clientNameId)
        assertTrue(mobile.replaysSession)
        assertNull(mobile.thirdPartyEmbedUrl)

        listOf(null, "WEB", "ANDROID", "TVHTML5").forEach { name ->
            val profile = YouTubeClientProfiles.page(signals(clientName = name))
            assertEquals(name.toString(), "WEB", profile.clientName)
            assertEquals(name.toString(), 1, profile.clientNameId)
        }
    }

    @Test
    fun `the request body is valid JSON and never acknowledges a content gate`() {
        val profile = YouTubeClientProfiles.embedded(signals(clientVersion = "2.20261002.01.00"))

        val body = profile.playerRequestBody(
            videoId = "Yft0Fixture",
            visitorData = "fixture\"visitor\\data\n",
            signatureTimestamp = 20_726,
        )

        val json = BoundedJsonParser.parse(body, maxNodes = 1_000)
        assertEquals(
            "fixture\"visitor\\data\n",
            json.path("context", "client", "visitorData").asStringOrNull,
        )
        assertEquals("Yft0Fixture", json.path("videoId").asStringOrNull)
        assertEquals(
            "HTML5_PREF_WANTS",
            json.path("playbackContext", "contentPlaybackContext", "html5Preference")
                .asStringOrNull,
        )
        assertEquals(
            20_726L,
            json.path("playbackContext", "contentPlaybackContext", "signatureTimestamp")
                .asLongOrNull,
        )
        assertFalse(body.contains("contentCheckOk"))
        assertFalse(body.contains("racyCheckOk"))
        assertNull(json.path("context", "client", "gl"))
    }

    @Test
    fun `optional values are left out rather than sent empty`() {
        val body = YouTubeClientProfiles.page(signals()).playerRequestBody(
            videoId = "Yft0Fixture",
            visitorData = null,
            signatureTimestamp = null,
        )

        val json = BoundedJsonParser.parse(body, maxNodes = 1_000)
        assertNull(json.path("context", "client", "visitorData"))
        assertNull(json.path("context", "thirdParty"))
        assertNull(json.path("playbackContext", "contentPlaybackContext", "signatureTimestamp"))
    }

    private fun signals(
        clientName: String? = null,
        clientVersion: String? = null,
    ) = YouTubePageSignals(
        playerResponseJson = null,
        playerId = "f1x7ure0",
        apiKey = null,
        clientName = clientName,
        clientVersion = clientVersion,
        visitorData = null,
        signatureTimestamp = null,
    )
}
