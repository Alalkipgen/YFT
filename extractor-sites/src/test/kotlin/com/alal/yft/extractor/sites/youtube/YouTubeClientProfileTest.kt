package com.alal.yft.extractor.sites.youtube

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
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
        assertTrue(profile.usesPlayerScript)
        assertFalse(profile.usesPoToken)
        assertNull(profile.userAgent)
        assertNull(profile.device)
        assertEquals("https://github.com/Alalkipgen/YFT", profile.thirdPartyEmbedUrl)
    }

    @Test
    fun `device clients present themselves as their own apps, without the session`() {
        assertEquals("yt-dlp 2026.08.19", YouTubeClientProfiles.DEVICE_VALUES_SOURCE)
        assertEquals(
            listOf("VISIONOS", "ANDROID"),
            YouTubeClientProfiles.DEVICE_CLIENTS.map(YouTubeClientProfile::clientName),
        )
        YouTubeClientProfiles.DEVICE_CLIENTS.forEach { profile ->
            assertFalse(profile.id, profile.replaysSession)
            assertFalse(profile.id, profile.usesPlayerScript)
            assertFalse(profile.id, profile.usesPoToken)
            assertNull(profile.id, profile.thirdPartyEmbedUrl)

            val body = profile.playerRequestBody(
                videoId = "Yft0Fixture",
                visitorData = "fixture-visitor",
                signatureTimestamp = 20_726,
                poToken = null,
            )
            val json = BoundedJsonParser.parse(body, maxNodes = 1_000)
            val client = json.path("context", "client")
            assertEquals(profile.clientName, client["clientName"].text)
            assertEquals(profile.clientVersion, client["clientVersion"].text)
            assertEquals(profile.userAgent, client["userAgent"].text)
            assertEquals("fixture-visitor", client["visitorData"].text)
            // The signature timestamp names a web player version, which an app does not run.
            assertNull(
                json.path("playbackContext", "contentPlaybackContext", "signatureTimestamp"),
            )
            assertNull(json["serviceIntegrityDimensions"])
        }

        val visionOs = client(YouTubeClientProfiles.VISION_OS)
        assertEquals(101, YouTubeClientProfiles.VISION_OS.clientNameId)
        assertEquals("1.02", visionOs["clientVersion"].text)
        assertEquals("Apple", visionOs["deviceMake"].text)
        assertEquals("RealityDevice17,1", visionOs["deviceModel"].text)
        assertEquals("visionOS", visionOs["osName"].text)
        assertEquals("26.5.23O471", visionOs["osVersion"].text)
        assertNull(visionOs["androidSdkVersion"])

        val android = client(YouTubeClientProfiles.ANDROID)
        assertEquals(3, YouTubeClientProfiles.ANDROID.clientNameId)
        assertEquals("21.26.364", android["clientVersion"].text)
        assertEquals(
            "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            android["userAgent"].text,
        )
        assertEquals(30L, android["androidSdkVersion"].asLongOrNull)
        assertEquals("Android", android["osName"].text)
        assertEquals("11", android["osVersion"].text)
        assertNull(android["deviceMake"])
        assertNull(android["deviceModel"])
    }

    @Test
    fun `the mobile site is asked with the session and the mobile site's own agent`() {
        val profile = YouTubeClientProfiles.mobileWeb()

        assertEquals("MWEB", profile.clientName)
        assertEquals(2, profile.clientNameId)
        assertEquals(YouTubeClientProfiles.MOBILE_CLIENT_VERSION, profile.clientVersion)
        assertTrue(profile.replaysSession)
        assertTrue(profile.usesPlayerScript)
        assertTrue(profile.usesPoToken)
        assertTrue(requireNotNull(profile.userAgent).contains("iPad"))
        assertNull(profile.device)
        assertEquals(profile.userAgent, client(profile)["userAgent"].text)
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
        assertTrue(mobile.usesPlayerScript)
        assertTrue(mobile.usesPoToken)
        assertNull(mobile.userAgent)
        assertNull(mobile.thirdPartyEmbedUrl)

        listOf(null, "WEB", "ANDROID", "TVHTML5").forEach { name ->
            val profile = YouTubeClientProfiles.page(signals(clientName = name))
            assertEquals(name.toString(), "WEB", profile.clientName)
            assertEquals(name.toString(), 1, profile.clientNameId)
        }
    }

    @Test
    fun `the request body is valid JSON and acknowledges warnings as YouTube's player does`() {
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
        // Content warnings any viewer can click through are acknowledged; age checks are not
        // a request field at all, so they still fail with their own reason.
        assertEquals(true, json["contentCheckOk"].asBooleanOrNull)
        assertEquals(true, json["racyCheckOk"].asBooleanOrNull)
        assertEquals("en", json.path("context", "client", "hl").text)
        assertEquals("UTC", json.path("context", "client", "timeZone").text)
        assertEquals(0L, json.path("context", "client", "utcOffsetMinutes").asLongOrNull)
        assertNull(json.path("context", "client", "gl"))
        assertNull(json.path("context", "client", "userAgent"))
        assertNull(json["serviceIntegrityDimensions"])
    }

    @Test
    fun `a proof-of-origin token travels in the request's integrity dimensions`() {
        val body = YouTubeClientProfiles.page(signals()).playerRequestBody(
            videoId = "Yft0Fixture",
            visitorData = null,
            signatureTimestamp = 20_726,
            poToken = "MnFixturePoToken-0123456789_abcdefghijklmnop",
        )

        val json = BoundedJsonParser.parse(body, maxNodes = 1_000)
        assertEquals(
            "MnFixturePoToken-0123456789_abcdefghijklmnop",
            json.path("serviceIntegrityDimensions", "poToken").text,
        )
        assertFalse(YouTubeClientProfiles.page(signals()).toString().contains("MnFixture"))
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

    private fun client(profile: YouTubeClientProfile): JsonValue? = BoundedJsonParser.parse(
        profile.playerRequestBody(
            videoId = "Yft0Fixture",
            visitorData = null,
            signatureTimestamp = null,
        ),
        maxNodes = 1_000,
    ).path("context", "client")

    private val JsonValue?.text: String?
        get() = asStringOrNull

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
