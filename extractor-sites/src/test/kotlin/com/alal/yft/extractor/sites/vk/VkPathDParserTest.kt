package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VK5 (path D, VK_PLAN §13): the embed's adult answer, the API's answer, the token, the hosts. */
class VkPathDParserTest {
    @Test
    fun `vk's adult answer is its own, before not-found and the shell`() {
        val adult = VkFixtures.adult()

        assertEquals(VkEmbed.Adult, parse(adult))
        // The real adult page also names spa_root in a script; the message still decides.
        assertEquals(VkEmbed.Adult, parse(adult.replace("</body>", "<i>spa_root</i></body>")))
        assertEquals(VkEmbed.NotFound, parse(VkFixtures.notFound()))
        assertEquals(VkEmbed.Shell, parse(VkFixtures.shell()))
    }

    @Test
    fun `the russian text alone or vk's error callback 9 beside its message is the adult answer`() {
        val russian = VkFixtures.adultRussian().withoutCallback()
        assertFalse("adult content" in russian)
        assertTrue("взрослых" in russian)
        assertEquals(VkEmbed.Adult, parse(russian))

        val callbackOnly = VkFixtures.adult().replace(VkFixtures.ADULT_TEXT_EN, "Something else.")
        assertEquals(VkEmbed.Adult, parse(callbackOnly))

        // Without VK's message box neither the callback nor the words count.
        val noBox = VkFixtures.adult().replace("id=\"video_ext_msg\"", "id=\"other\"")
        assertEquals(VkEmbed.Changed("no item for the video"), parse(noBox))
        val wordsElsewhere = VkFixtures.notFound()
            .replace("</body>", "<script>var t = 'may contain adult content';</script></body>")
        assertEquals(VkEmbed.NotFound, parse(wordsElsewhere))
        assertEquals(VkEmbed.NotFound, parse(VkFixtures.adult().withoutCallback()
            .replace(VkFixtures.ADULT_TEXT_EN, "Video not found.")))
    }

    @Test
    fun `a video's own data still wins over any message`() {
        val page = VkFixtures.video().replace("</body>", MESSAGE_BOX + "</body>")

        assertTrue(VkEmbedParser.parse(page, VIDEO_OWNER, VIDEO_ID) is VkEmbed.Found)
    }

    @Test
    fun `path d reads the api's item, its files on vkuser_net, never the trailer`() {
        val answer = VkEmbedParser.parseApi(VkFixtures.apiAdult(), ADULT_OWNER, ADULT_ID)
        val video = (answer as VkApiAnswer.Found).video

        assertEquals("Video by Asianese", video.title)
        assertEquals(118_000L, video.durationMillis)
        assertFalse(video.isLive)
        assertEquals("table", video.foundBy)
        assertEquals(0, video.refusedAddresses)
        assertTrue(
            checkNotNull(video.thumbnailUrl).startsWith("https://iv.okcdn.ru/getVideoPreview/719x1280?"),
        )
        assertEquals(
            listOf(
                "mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240", "mp4_144",
                "hls", "hls_fmp4", "dash_sep",
            ),
            video.renditions.map(VkRendition::key),
        )
        assertEquals(
            List(6) { MediaKind.DIRECT } + List(2) { MediaKind.HLS } + MediaKind.DASH,
            video.renditions.map(VkRendition::kind),
        )
        assertEquals(listOf(1080, 720, 480, 360, 240, 144), video.renditions.mapNotNull { it.height })
        video.renditions.forEach { rendition ->
            assertTrue(rendition.url, rendition.url.startsWith("https://vk6-4.vkuser.net/${rendition.key}/"))
            assertEquals(EXPIRES_MS, rendition.expiresAtEpochMs)
        }
    }

    @Test
    fun `the api's other answers are vk's error, a restriction or a changed answer`() {
        assertEquals(
            VkApiAnswer.Error(5),
            VkEmbedParser.parseApi(VkFixtures.apiAuthError(), ADULT_OWNER, ADULT_ID),
        )
        assertEquals(
            VkApiAnswer.Restricted,
            VkEmbedParser.parseApi(VkFixtures.apiDeleted(), ADULT_OWNER, 1),
        )
        // Another video's item is never this video's.
        assertEquals(
            VkApiAnswer.Changed("no item for the video"),
            VkEmbedParser.parseApi(VkFixtures.apiAdult(), ADULT_OWNER, 456243446),
        )
        assertEquals(
            VkApiAnswer.Changed("not JSON"),
            VkEmbedParser.parseApi("<html>challenge</html>", ADULT_OWNER, ADULT_ID),
        )
        val noFiles = VkApiPages.videoGet(
            VkPages.item(files = null, ownerId = ADULT_OWNER, id = ADULT_ID),
        )
        val bare = VkEmbedParser.parseApi(noFiles, ADULT_OWNER, ADULT_ID) as VkApiAnswer.Found
        assertTrue(bare.video.renditions.isEmpty())
    }

    @Test
    fun `vkuser_net counts for path d alone, the embed and the browser keep v1's rule`() {
        listOf(
            "https://vk6-4.vkuser.net/mp4_720/F?expires=1",
            "https://vk12-345.vkuser.net/hls/F",
            "https://vkvd572.okcdn.ru/mp4_720/F",
        ).forEach { url ->
            assertTrue(url, VkUrls.isApiMediaFileUrl(url))
            assertTrue(url, VkUrls.isApiPlayerMediaUrl(url))
        }
        listOf(
            "https://vkuser.net/mp4_720/F",
            "https://vk6.vkuser.net/mp4_720/F",
            "https://evilvk6-4.vkuser.net/mp4_720/F",
            "https://vk6-4.vkuser.net.example.org/mp4_720/F",
            "http://vk6-4.vkuser.net/mp4_720/F",
            "https://iv.okcdn.ru/getVideoPreview",
            "https://sun9-47.vkuserphoto.ru/s/v1/F.jpg",
        ).forEach { url -> assertFalse(url, VkUrls.isApiMediaFileUrl(url)) }

        val onVkuser = "https://vk6-4.vkuser.net/mp4_720/F"
        assertFalse(VkUrls.isMediaFileUrl(onVkuser))
        assertFalse(VkUrls.isPlayerMediaUrl(onVkuser))
        assertFalse(VkExtractor(ScriptedVkHttp(emptyMap())).isPlayerMediaRequest(onVkuser))
        // The embed's table refuses vkuser_net files: v1's answer stays "files on unknown hosts".
        val embed = VkPages.embed(VkPages.item(files = "\"mp4_720\":\"$onVkuser\""))
        val video = (VkEmbedParser.parse(embed, VIDEO_OWNER, VIDEO_ID) as VkEmbed.Found).video
        assertTrue(video.renditions.isEmpty())
        assertEquals(1, video.refusedAddresses)
    }

    @Test
    fun `login_vk_ru's answer gives the token and its life, nothing else counts`() {
        val token = checkNotNull(VkApiTokenParser.parse(VkApiPages.token(), NOW_MS))
        assertEquals(TOKEN, token.value)
        assertEquals(NOW_MS + 86_400_000L - VkApiTokenParser.SLACK_MS, token.usableUntilEpochMs)
        assertFalse(TOKEN in token.toString())

        // A life beyond 24 h by the phone's clock is cut to 24 h.
        val far = VkApiTokenParser.parse(VkApiPages.token(expiresAtEpochMs = NOW_MS * 2), NOW_MS)
        assertEquals(NOW_MS + VkApiTokenParser.MAX_LIFE_MS - VkApiTokenParser.SLACK_MS, far?.usableUntilEpochMs)
        // Seconds from now and epoch milliseconds are read too; no expiry serves one lookup.
        val relative = "{\"type\":\"okay\",\"data\":{\"access_token\":\"$TOKEN\",\"expires\":3600}}"
        assertEquals(NOW_MS + 3_540_000L, VkApiTokenParser.parse(relative, NOW_MS)?.usableUntilEpochMs)
        val millis = "{\"type\":\"okay\",\"data\":{\"access_token\":\"$TOKEN\",\"expires\":${NOW_MS + 7_200_000L}}}"
        assertEquals(NOW_MS + 7_140_000L, VkApiTokenParser.parse(millis, NOW_MS)?.usableUntilEpochMs)
        val noExpiry = checkNotNull(
            VkApiTokenParser.parse("{\"type\":\"okay\",\"data\":{\"access_token\":\"$TOKEN\"}}", NOW_MS),
        )
        assertFalse(noExpiry.usableAt(NOW_MS))

        listOf(
            "{\"type\":\"error\",\"error_code\":1,\"data\":{\"access_token\":\"$TOKEN\"}}",
            "{\"type\":\"okay\",\"data\":{}}",
            "{\"type\":\"okay\",\"data\":{\"access_token\":\"short\"}}",
            "{\"type\":\"okay\",\"data\":{\"access_token\":\"anonym.with space.0123456789\"}}",
            "{\"type\":\"okay\",\"data\":{\"access_token\":\"anonym.токен.0123456789abc\"}}",
            "<html>challenge</html>",
        ).forEach { body -> assertNull(body, VkApiTokenParser.parse(body, NOW_MS)) }
    }

    @Test
    fun `the cache keeps a token until 60 s before it expires and drops only a refused one`() {
        val cache = VkApiTokenCache()
        val first = VkApiToken(TOKEN, NOW_MS + 1_000)

        cache.keep(first, NOW_MS)
        assertEquals(first, cache.usable(NOW_MS))
        assertNull(cache.usable(NOW_MS + 1_000))

        val second = VkApiToken(TOKEN_2, NOW_MS + 5_000)
        cache.keep(second, NOW_MS)
        cache.drop(first)
        assertEquals(second, cache.usable(NOW_MS))
        cache.drop(second)
        assertNull(cache.usable(NOW_MS))

        cache.keep(VkApiToken(TOKEN, NOW_MS - 1), NOW_MS)
        assertNull(cache.usable(NOW_MS))
    }

    @Test
    fun `the web client's requests are the player's own`() {
        val listed = checkNotNull(
            VkUrls.linkOf("https://vkvideo.ru/video-215889201_456243446?list=96724186cd29efdc64"),
        )
        assertEquals(
            "https://api.vkvideo.ru/method/video.get?v=5.289&client_id=52461373" +
                "&owner_id=-215889201&videos=-215889201_456243446_96724186cd29efdc64&extended=1",
            VkWebClientProfile.videoGetUrl(listed),
        )
        val plain = checkNotNull(VkUrls.linkOf(ADULT_LINK))
        assertEquals(
            "https://api.vkvideo.ru/method/video.get?v=5.289&client_id=52461373" +
                "&owner_id=-215889201&videos=-215889201_456243439&extended=1",
            VkWebClientProfile.videoGetUrl(plain),
        )
        assertEquals("https://login.vk.ru/?act=get_anonym_token", VkWebClientProfile.TOKEN_URL)

        val body = BoundedJsonParser.parse(VkWebClientProfile.tokenBody) as JsonValue.Object
        assertEquals(
            mapOf(
                "client_id" to "52461373",
                "client_secret" to VkWebClientProfile.CLIENT_SECRET,
                "scopes" to "audio_anonymous,video_anonymous,photos_anonymous,profile_anonymous",
                "isApiOauthAnonymEnabled" to "false",
                "version" to "1",
                "app_id" to "6287487",
            ),
            body.entries.mapValues { (_, value) -> (value as JsonValue.Text).value },
        )
    }

    private fun parse(html: String): VkEmbed = VkEmbedParser.parse(html, ADULT_OWNER, ADULT_ID)

    private fun String.withoutCallback(): String = replace("window.embedErrorCallback?.(9);", "")

    private companion object {
        const val MESSAGE_BOX = "<div id=\"video_ext_msg\">may contain adult content</div>" +
            "<script>window.embedErrorCallback?.(9);</script>"
    }
}
