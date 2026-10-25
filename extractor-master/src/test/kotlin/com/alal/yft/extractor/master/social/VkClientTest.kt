package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.NOW
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VK-Master plan Appendix B step 1: the web client's values, checked, and their asks' parts. */
class VkClientTest {
    @Test
    fun `main's values are bundled and every one has VK's shape`() {
        val bundled = VkClient.BUNDLED
        assertEquals(VkClient.CLIENT_ID, bundled.clientId)
        assertEquals(VkClient.CLIENT_SECRET, bundled.clientSecret)
        assertEquals(VkClient.APP_ID, bundled.appId)
        assertEquals(VkClient.SCOPES, bundled.scopes)
        assertEquals(VkClient.TOKEN_VERSION, bundled.tokenVersion)
        assertEquals("5.289", bundled.apiVersion)
        assertEquals(VkClient.Source.BUNDLED, bundled.source)
        assertEquals(VkClient.BUNDLED_DATE, bundled.date)
        assertEquals(setOf("vk.com", "vk.ru", "login.vk.ru", "api.vkvideo.ru"), VkClient.HOSTS)
    }

    @Test
    fun `a value that could carry another parameter, header or field is refused, and the refusal names no value`() {
        val bad = listOf(
            "1&owner_id=1", "1\"", "1 2", "", "1\n2", "x".repeat(65),
        )
        bad.forEach { value ->
            assertNull(value, of(clientId = value))
            assertNull(value, of(appId = value))
            assertNull(value, of(clientSecret = value))
            assertNull(value, of(scopes = value))
            assertNull(value, of(tokenVersion = value))
            assertNull(value, of(apiVersion = value))
        }
        assertNull(of(scopes = "video_anonymous,"))
        assertNull(of(apiVersion = "5"))
        assertNull(of(date = "2026-10-25T00:00"))
        val refused = runCatching { values(clientSecret = "secret\"value") }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException)
        assertFalse(refused!!.message!!.contains("secret\"value"))
        assertNotNull(of(apiVersion = "5.290", scopes = "video_anonymous"))
    }

    @Test
    fun `the text of values, links and tokens names no value`() {
        val text = VkClient.BUNDLED.toString()
        assertEquals("Values(bundled, 2026-10-25)", text)
        listOf(VkClient.CLIENT_ID, VkClient.CLIENT_SECRET, VkClient.APP_ID).forEach { assertFalse(text.contains(it)) }
        val link = VkClient.Link(-100000001, 400000001, "FIXTURELIST")
        assertFalse(link.toString().contains("100000001"))
        assertFalse(link.toString().contains("FIXTURELIST"))
        val token = checkNotNull(VkClient.token(vkToken(), NOW))
        assertFalse(token.toString().contains("fixture.anonymous"))
    }

    @Test
    fun `the token body is main's JSON, in main's order`() {
        assertEquals(
            "{\"client_id\":\"52461373\",\"client_secret\":\"o557NLIkAErNhakXrQ7A\"," +
                "\"scopes\":\"audio_anonymous,video_anonymous,photos_anonymous,profile_anonymous\"," +
                "\"isApiOauthAnonymEnabled\":\"false\",\"version\":\"1\",\"app_id\":\"6287487\"}",
            VkClient.BUNDLED.tokenBody,
        )
        val other = values(apiVersion = "5.290")
        assertEquals("the API version is not in the token's body", VkClient.BUNDLED.tokenBody, other.tokenBody)
        assertTrue(values(appId = "1").tokenBody.endsWith("\"app_id\":\"1\"}"))
    }

    @Test
    fun `video get names the link's video, its list and the values, never a token`() {
        assertEquals(
            "https://api.vkvideo.ru/method/video.get?v=5.289&client_id=52461373&owner_id=-100000001" +
                "&videos=-100000001_400000001&extended=1",
            VkClient.BUNDLED.videoGetUrl(VkClient.Link(-100000001, 400000001)),
        )
        assertEquals(
            "https://api.vkvideo.ru/method/video.get?v=5.290&client_id=52461373&owner_id=100000002" +
                "&videos=100000002_400000002_FIXTURE-list_1&extended=1",
            values(apiVersion = "5.290").videoGetUrl(VkClient.Link(100000002, 400000002, "FIXTURE-list_1")),
        )
    }

    @Test
    fun `a link is the identity's bounded video and the canonical link's list`() {
        assertEquals(
            VkClient.Link(-100000001, 400000001, null),
            VkClient.Link.of(identity("-100000001_400000001", "https://vk.com/video-100000001_400000001")),
        )
        assertEquals(
            VkClient.Link(-100000001, 400000001, "FIXTURE"),
            VkClient.Link.of(
                identity("-100000001_400000001", "https://vk.com/video-100000001_400000001?list=FIXTURE&hash=abc"),
            ),
        )
        assertEquals(
            "a list VK's shape does not allow is left out",
            VkClient.Link(-100000001, 400000001, null),
            VkClient.Link.of(identity("-100000001_400000001", "https://vk.com/video-100000001_400000001?list=a%26b")),
        )
        assertNull(VkClient.Link.of(identity("0_400000001", "https://vk.com/video0_400000001")))
        assertNull(VkClient.Link.of(identity("-100000001_0", "https://vk.com/video-100000001_0")))
        assertNull(VkClient.Link.of(identity("-100000001_x", "https://vk.com/video-100000001_x")))
        assertNull(VkClient.Link.of(identity("1".repeat(20) + "_1", "https://vk.com/")))
        assertNull(VkClient.Link.of(SitePageIdentity("x", "-100000001_400000001", "https://x.com/i/status/1")))
    }

    @Test
    fun `the token is read as main reads it, with VK's expiry less a minute and at most a day`() {
        val token = checkNotNull(VkClient.token(vkToken(), NOW))
        assertEquals("fixture.anonymous.token.not-real.0123456789", token.value)
        assertEquals("NOW is 2027; the fixture's expiry is past a day", NOW + 24L * 3600 * 1000 - 60_000, token.usableUntilEpochMs)
        val soon = NOW / 1000 + 600
        val inSeconds = checkNotNull(VkClient.token(answer("\"expires\":$soon"), NOW))
        assertEquals(soon * 1000 - 60_000, inSeconds.usableUntilEpochMs)
        val inMillis = checkNotNull(VkClient.token(answer("\"expires\":${soon * 1000}"), NOW))
        assertEquals(soon * 1000 - 60_000, inMillis.usableUntilEpochMs)
        val fromNow = checkNotNull(VkClient.token(answer("\"expired_at\":300"), NOW))
        assertEquals(NOW + 300_000 - 60_000, fromNow.usableUntilEpochMs)
        val unknown = checkNotNull(VkClient.token(answer(""), NOW))
        assertFalse("no known life: not usable", unknown.usableAt(NOW))
        assertTrue(inSeconds.usableAt(NOW))
    }

    @Test
    fun `any other token answer is no token`() {
        listOf(
            "{\"type\":\"error\",\"data\":{\"access_token\":\"$TOKEN\"}}",
            "{\"data\":{\"access_token\":\"$TOKEN\"}}",
            "{\"type\":\"okay\",\"data\":{\"access_token\":\"short\"}}",
            "{\"type\":\"okay\",\"data\":{\"access_token\":\"${"a".repeat(2049)}\"}}",
            "{\"type\":\"okay\",\"data\":{\"access_token\":\"fixture token with spaces\"}}",
            "{\"type\":\"okay\",\"data\":{\"access_token\":\"fixture\u00e9token0123456789\"}}",
            "{\"type\":\"okay\",\"data\":{}}",
            "[]",
            "<html>",
            "",
        ).forEach { assertNull(it.take(40), VkClient.token(it, NOW)) }
    }

    @Test
    fun `the asks' headers are main's, in main's order, with the token as a bearer and never a cookie`() {
        val token = checkNotNull(VkClient.token(vkToken(), NOW))
        assertEquals(
            listOf(
                "User-Agent" to "FixtureAgent/1.0",
                "Accept" to "*/*",
                "Origin" to "https://vkvideo.ru",
                "Referer" to "https://vkvideo.ru/",
                "Authorization" to "Bearer fixture.anonymous.token.not-real.0123456789",
            ),
            VkClient.headers("FixtureAgent/1.0", token).toList(),
        )
        assertEquals(
            listOf("Accept", "Origin", "Referer"),
            VkClient.headers(" ", null).keys.toList(),
        )
    }

    @Test
    fun `media files are VK's video servers only, never the image host`() {
        listOf(
            "https://vk1-1.vkuser.net/fixture/720.mp4?expires=1900000000",
            "https://vk12-345.vkuser.net/fixture/video.m3u8",
            "https://vkvd1.okcdn.ru/fixture/360.mp4",
            "https://VKVD123456.OKCDN.RU/fixture/video.mpd",
        ).forEach { assertTrue(it, VkClient.mediaFile(it)) }
        listOf(
            "https://iv.okcdn.ru/fixture/large.jpg",
            "http://vk1-1.vkuser.net/fixture/720.mp4",
            "https://user@vk1-1.vkuser.net/fixture/720.mp4",
            "https://vk1-1.vkuser.net.example.test/fixture/720.mp4",
            "https://cdn.vkuser.net/fixture/720.mp4",
            "https://vkvd1.okcdn.ru.example.test/fixture/360.mp4",
            "https://sun1-1.userapi.com/fixture/360.mp4",
            "https://vk1-1.vkuser.net/" + "a".repeat(8_200),
            "not a link",
        ).forEach { assertFalse(it.take(60), VkClient.mediaFile(it)) }
    }

    @Test
    fun `a signed address's expiry is read as main reads it`() {
        assertEquals(1_900_000_000_000L, VkClient.expiresAtOf("https://vk1-1.vkuser.net/a.mp4?srcAg=X&expires=1900000000"))
        assertEquals(1_900_000_000_123L, VkClient.expiresAtOf("https://vk1-1.vkuser.net/a.mp4?expires=1900000000123"))
        assertEquals(1_900_000_000_000L, VkClient.expiresAtOf("https://vkvd1.okcdn.ru/expires/1900000000/a.mp4"))
        assertNull(VkClient.expiresAtOf("https://vk1-1.vkuser.net/a.mp4?expires=19000"))
        assertNull(VkClient.expiresAtOf("https://vk1-1.vkuser.net/a.mp4?expires=19000000x0"))
        assertNull(VkClient.expiresAtOf("https://vk1-1.vkuser.net/a.mp4"))
    }

    @Test
    fun `bundled values are main's alone, and a debug hook's change comes first as the debug source`() = runTest {
        assertEquals(listOf(VkClient.BUNDLED), VkClientValues.bundled().values())
        val changed = VkClientValues.bundled { it.copy(apiVersion = "5.290") }.values()
        assertEquals(2, changed.size)
        assertEquals("5.290", changed[0].apiVersion)
        assertEquals(VkClient.Source.DEBUG, changed[0].source)
        assertEquals(VkClient.BUNDLED, changed[1])
        assertEquals("no change: main's only", listOf(VkClient.BUNDLED), VkClientValues.bundled { it }.values())
        assertEquals(
            "a source change alone is no change",
            listOf(VkClient.BUNDLED),
            VkClientValues.bundled { it.copy(source = VkClient.Source.SIGNED) }.values(),
        )
        assertEquals(
            "a value of the wrong shape: main's only",
            listOf(VkClient.BUNDLED),
            VkClientValues.bundled { it.copy(clientId = "1&x=2") }.values(),
        )
        assertEquals(
            "a hook that throws: main's only",
            listOf(VkClient.BUNDLED),
            VkClientValues.bundled { error("fixture") }.values(),
        )
    }

    private fun answer(expiry: String): String =
        "{\"type\":\"okay\",\"data\":{\"access_token\":\"$TOKEN\"${if (expiry.isEmpty()) "" else ",$expiry"}}}"

    private fun identity(contentId: String, url: String) = SitePageIdentity("vk", contentId, url)

    private fun values(
        clientId: String = VkClient.CLIENT_ID,
        clientSecret: String = VkClient.CLIENT_SECRET,
        appId: String = VkClient.APP_ID,
        scopes: String = VkClient.SCOPES,
        tokenVersion: String = VkClient.TOKEN_VERSION,
        apiVersion: String = VkClient.API_VERSION,
        date: String = "2026-10-26",
    ) = VkClient.Values(clientId, clientSecret, appId, scopes, tokenVersion, apiVersion, VkClient.Source.SIGNED, date)

    private fun of(
        clientId: String = VkClient.CLIENT_ID,
        clientSecret: String = VkClient.CLIENT_SECRET,
        appId: String = VkClient.APP_ID,
        scopes: String = VkClient.SCOPES,
        tokenVersion: String = VkClient.TOKEN_VERSION,
        apiVersion: String = VkClient.API_VERSION,
        date: String = "2026-10-26",
    ) = VkClient.Values.of(clientId, clientSecret, appId, scopes, tokenVersion, apiVersion, VkClient.Source.SIGNED, date)

    private companion object {
        const val TOKEN = "fixture.anonymous.token.not-real.0123456789"
    }
}
