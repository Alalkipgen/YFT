package com.alal.yft.extractor.sites.facebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookUrlsTest {
    @Test
    fun `watch, video and profile urls canonicalize onto one identity`() {
        listOf(
            "https://www.facebook.com/watch/?v=1234567890123456&ref=sharing",
            "https://www.facebook.com/watch?v=1234567890123456",
            "https://m.facebook.com/video.php?v=1234567890123456&_rdr",
            "https://web.facebook.com/FixturePage/videos/1234567890123456/",
            "https://www.facebook.com/FixturePage/videos/fixture-title/1234567890123456/",
        ).forEach { url ->
            val identity = FacebookUrls.identify(url)
            assertEquals(url, "facebook", identity?.siteId)
            assertEquals(url, "1234567890123456", identity?.contentId)
            assertEquals(
                url,
                "https://www.facebook.com/watch/?v=1234567890123456",
                identity?.canonicalPageUrl,
            )
            assertFalse(url, identity!!.requiresCanonicalResolution)
        }
    }

    @Test
    fun `reels keep their own canonical path`() {
        listOf(
            "https://www.facebook.com/reel/7180001112223334",
            "https://www.facebook.com/reels/7180001112223334?s=single_unit",
            "https://m.facebook.com/reel/7180001112223334/",
        ).forEach { url ->
            assertEquals(
                url,
                "https://www.facebook.com/reel/7180001112223334",
                FacebookUrls.identify(url)?.canonicalPageUrl,
            )
        }
        assertEquals(
            FacebookUrls.PostKind.REEL,
            FacebookUrls.kindOf("https://www.facebook.com/reel/7180001112223334"),
        )
    }

    @Test
    fun `short and share links are matched but explicitly marked unresolved`() {
        val short = FacebookUrls.identify("https://fb.watch/aBc123dEf-/")!!
        assertEquals("aBc123dEf-", short.contentId)
        assertEquals("https://fb.watch/aBc123dEf-", short.canonicalPageUrl)
        assertTrue(short.requiresCanonicalResolution)

        val video = FacebookUrls.identify("https://www.facebook.com/share/v/aBc123dEf/?mi=1")!!
        assertEquals("aBc123dEf", video.contentId)
        assertEquals("https://www.facebook.com/share/v/aBc123dEf", video.canonicalPageUrl)
        assertTrue(video.requiresCanonicalResolution)

        val reel = FacebookUrls.identify("https://www.facebook.com/share/r/aBc123dEf/")!!
        assertEquals("https://www.facebook.com/share/r/aBc123dEf", reel.canonicalPageUrl)
        assertTrue(reel.requiresCanonicalResolution)
    }

    @Test
    fun `story, permalink, post and group post pages are posts asked on their posts path`() {
        // P3-FIX: the owner's phone opened story.php; no identity meant no adapter and no sheet.
        val owner = "100012345678901"
        val cases = listOf(
            "https://m.facebook.com/story.php?story_fbid=1234567890123456&id=$owner&mibextid=x"
                to "https://www.facebook.com/$owner/posts/1234567890123456",
            "https://www.facebook.com/permalink.php?story_fbid=pfbid0AbCdEfGhIjKlMn&id=$owner"
                to "https://www.facebook.com/$owner/posts/pfbid0AbCdEfGhIjKlMn",
            "https://www.facebook.com/Fixture.Page/posts/1234567890123456/?__tn__=R"
                to "https://www.facebook.com/Fixture.Page/posts/1234567890123456",
            "https://mbasic.facebook.com/groups/fixture.group/permalink/1234567890123456/"
                to "https://www.facebook.com/groups/fixture.group/posts/1234567890123456",
            "https://www.facebook.com/groups/123456789/posts/1234567890123456"
                to "https://www.facebook.com/groups/123456789/posts/1234567890123456",
        )
        cases.forEach { (url, address) ->
            val identity = FacebookUrls.identify(url)
            assertEquals(url, address, identity?.canonicalPageUrl)
            assertTrue(url, identity!!.requiresCanonicalResolution)
            assertTrue(url, FacebookUrls.isPost(identity))
        }
        assertEquals(
            "post:1234567890123456",
            FacebookUrls.identify(cases.first().first)?.contentId,
        )
        val share = FacebookUrls.identify("https://www.facebook.com/share/p/aBc123dEf/")!!
        assertEquals("https://www.facebook.com/share/p/aBc123dEf", share.canonicalPageUrl)
        assertTrue(share.requiresCanonicalResolution)
        assertTrue(FacebookUrls.isPost(share))
        val video = FacebookUrls.identify("https://www.facebook.com/share/v/aBc123dEf/")!!
        assertFalse(FacebookUrls.isPost(video))
        val watch = FacebookUrls.identify("https://www.facebook.com/watch/?v=1234567890123456")!!
        assertFalse(FacebookUrls.isPost(watch))
    }

    @Test
    fun `unrelated, non-video and malformed urls are not claimed`() {
        listOf(
            "http://www.facebook.com/watch/?v=1234567890123456",
            "https://www.facebook.com/watch/?v=123",
            "https://www.facebook.com/watch/",
            "https://www.facebook.com/photo/?fbid=1234567890123456",
            "https://www.facebook.com/groups/1234567890/videos/9876543210987",
            "https://www.facebook.com/groups/1234567890/about",
            "https://www.facebook.com/profile.php?id=1234567890123456",
            // A post needs its owner, and IDs have to look like IDs.
            "https://www.facebook.com/story.php?story_fbid=1234567890123456",
            "https://www.facebook.com/story.php?story_fbid=123&id=100012345678901",
            "https://www.facebook.com/story.php/extra?story_fbid=1234567890123456&id=1000123456",
            "https://www.facebook.com/FixturePage/posts/not-an-id",
            "https://www.facebook.com/",
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            "https://evil.example/www.facebook.com/watch/?v=1234567890123456",
            "not a url",
        ).forEach { url -> assertNull(url, FacebookUrls.identify(url)) }
    }

    @Test
    fun `media expiry is read only from a plausible cdn parameter`() {
        val media = "https://video.example-cdn.test/v/fixture.mp4"

        assertEquals(4_070_908_800_000L, FacebookUrls.mediaExpiryEpochMs("$media?oe=F2A52380&oh=x"))
        assertEquals(
            1_700_000_000_000L,
            FacebookUrls.mediaExpiryEpochMs("$media?_nc_exp=1700000000000"),
        )
        assertEquals(
            1_700_000_000_000L,
            FacebookUrls.mediaExpiryEpochMs("$media?expire=1700000000"),
        )
        assertNull(FacebookUrls.mediaExpiryEpochMs("$media?oe=zz"))
        assertNull(FacebookUrls.mediaExpiryEpochMs("$media?oe=5"))
        assertNull(FacebookUrls.mediaExpiryEpochMs(media))
    }

    @Test
    fun `a media address's own average bitrate is read, never anything else`() {
        val media = "https://video.example-cdn.test/o1/v/fixture.mp4"
        // {"vencode_tag":"dash_h264-basic-gen2_720p","bitrate":312410}
        val label = "eyJ2ZW5jb2RlX3RhZyI6ImRhc2hfaDI2NC1iYXNpYy1nZW4yXzcyMHAi" +
            "LCJiaXRyYXRlIjozMTI0MTB9"
        // {"vencode_tag":"dash_h264-basic-gen2_360p~ÿ?","bitrate":84255}: Base64 with + and /.
        val signs = "eyJ2ZW5jb2RlX3RhZyI6ImRhc2hfaDI2NC1iYXNpYy1nZW4yXzM2MHB+w78/" +
            "IiwiYml0cmF0ZSI6ODQyNTV9"

        assertEquals(373_059L, FacebookUrls.statedBitrate("$media?bitrate=373059&efg=$label"))
        assertEquals(312_410L, FacebookUrls.statedBitrate("$media?efg=$label&oe=F2A52380"))
        assertEquals(312_410L, FacebookUrls.statedBitrate("$media?efg=$label%3D%3D"))
        assertEquals(84_255L, FacebookUrls.statedBitrate("$media?efg=$signs"))
        assertEquals(84_255L, FacebookUrls.statedBitrate("$media?efg=${signs.replace("+", "%2B")}"))
        val urlSafe = signs.replace('+', '-').replace('/', '_')
        assertEquals(84_255L, FacebookUrls.statedBitrate("$media?efg=$urlSafe"))
        // {"vencode_tag":"xpv_progressive.FACEBOOK..C3.1280.dash_h264-basic-gen2_720p"}
        assertNull(
            FacebookUrls.statedBitrate(
                "$media?efg=eyJ2ZW5jb2RlX3RhZyI6Inhwdl9wcm9ncmVzc2l2ZS5GQUNFQk9PSy4uQzMuMTI4MC5k" +
                    "YXNoX2gyNjQtYmFzaWMtZ2VuMl83MjBwIn0%3D",
            ),
        )
        // {"bitrate":999999999999}, a misread number and a label that is not Base64.
        assertNull(FacebookUrls.statedBitrate("$media?efg=eyJiaXRyYXRlIjo5OTk5OTk5OTk5OTl9"))
        assertNull(FacebookUrls.statedBitrate("$media?bitrate=5"))
        assertNull(FacebookUrls.statedBitrate("$media?efg=not*base64!"))
        assertNull(FacebookUrls.statedBitrate("$media?efg=${"A".repeat(5_000)}"))
        assertNull(FacebookUrls.statedBitrate(media))
    }

    @Test
    fun `the ladder page is the video's reel or videos page, never a watch page`() {
        val id = "1234567890123456"
        val watch = "https://www.facebook.com/watch/?v=$id"

        assertEquals(
            "https://www.facebook.com/reel/$id",
            FacebookUrls.videoPage(id, listOf("https://m.facebook.com/reel/$id/?s=1")),
        )
        assertEquals(
            "https://www.facebook.com/FixturePage/videos/$id/",
            FacebookUrls.videoPage(
                id,
                listOf("https://web.facebook.com/FixturePage/videos/$id/?x=1"),
            ),
        )
        assertEquals(
            "https://www.facebook.com/reel/$id",
            FacebookUrls.videoPage(id, listOf(watch, null, "https://www.facebook.com/reel/$id/")),
        )
        listOf(
            watch,
            "https://www.facebook.com/video.php?v=$id",
            "https://www.facebook.com/reel/9999999999999999/",
            "https://www.facebook.com/share/r/aBc123dEf/",
            "https://evil.example/reel/$id/",
            "not a url",
        ).forEach { address -> assertNull(address, FacebookUrls.videoPage(id, listOf(address))) }
    }

    @Test
    fun `only a resolved reel is asked as Safari first`() {
        listOf(
            "https://www.facebook.com/reel/1234567890123456" to true,
            "https://www.facebook.com/watch/?v=1234567890123456" to false,
            "https://www.facebook.com/FixturePage/videos/1234567890123456/" to false,
            "https://www.facebook.com/share/r/aBc123dEf/" to false,
        ).forEach { (url, reel) ->
            assertEquals(url, reel, FacebookUrls.isReel(requireNotNull(FacebookUrls.identify(url))))
        }
    }

    @Test
    fun `login and checkpoint redirects are recognized as access walls`() {
        assertTrue(FacebookUrls.isAccessWall("https://www.facebook.com/login/?next=%2Fwatch"))
        assertTrue(FacebookUrls.isAccessWall("https://m.facebook.com/checkpoint/block/?u=1"))
        assertFalse(FacebookUrls.isAccessWall("https://www.facebook.com/watch/?v=1234567890123456"))
        assertFalse(FacebookUrls.isAccessWall("https://login.example/facebook"))
    }
}
