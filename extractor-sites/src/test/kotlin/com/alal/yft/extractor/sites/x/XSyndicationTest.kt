package com.alal.yft.extractor.sites.x

import com.alal.yft.extractor.sites.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected texts were written by Node's own `Number.prototype.toString(36)`. */
class XSyndicationTest {
    @Test
    fun `the token is the embed widget's own`() {
        mapOf(
            "1785999999999999999" to "4buvua9lpjr",
            "1683920951807971329" to "42y6zv7ufp",
            "1460323737035677698" to "3jfqq1vhqna",
            "20" to "6dq1a2xwd93",
        ).forEach { (id, token) -> assertEquals(id, token, XSyndication.token(id)) }
        assertEquals(
            "https://cdn.syndication.twimg.com/tweet-result?id=20&token=6dq1a2xwd93&lang=en",
            XSyndication.url("20"),
        )
    }

    @Test
    fun `numbers are written in base 36 as JavaScript writes them`() {
        mapOf(
            0.5 to "0.i",
            255.5 to "73.i",
            1e21 to "5v1j4f4ds7c000",
            123456789.123 to "21i3v9.4feor",
            1_152_921_504_606_846_976.0 to "8rc4kbdvss00",
            0.1 to "0.3lllllllllm",
            1.0 / 3 to "0.c",
            35.99999999999999 to "z.zzzzzzzzz",
            -255.5 to "-73.i",
        ).forEach { (value, text) ->
            assertEquals(value.toString(), text, XSyndication.radixString(value, 36))
        }
    }

    @Test
    fun `a video's MP4 files are read with their sizes, highest bitrate first`() {
        val post = (XSyndication.parse(Fixtures.read("x/tweet_video.json"))
            as XParseResult.Success).post

        assertEquals("Fixture User", post.userName)
        assertFalse(post.quoted)
        val video = post.videos.single()
        assertEquals(XMediaType.VIDEO, video.type)
        assertEquals(31_533L, video.durationMillis)
        assertEquals(listOf(1280, 852, 568), video.variants.map { it.height })
        assertEquals(listOf(720, 480, 320), video.variants.map { it.width })
        assertEquals(2_176_000L, video.variants.first().bitrateBitsPerSecond)
        assertTrue(video.playlistUrl!!.endsWith(".m3u8?tag=12&v=1"))
    }

    @Test
    fun `media keep their place, files on other hosts are dropped`() {
        val post = (XSyndication.parse(Fixtures.read("x/tweet_mixed.json"))
            as XParseResult.Success).post

        assertEquals(listOf(2, 3), post.videos.map { it.position })
        assertEquals(1, post.videos[1].variants.size)
        assertEquals(1, post.photoCount)
    }

    @Test
    fun `tombstones, photo posts and broken answers fail`() {
        val tombstone = XSyndication.parse(Fixtures.read("x/tweet_tombstone.json"))
        assertTrue((tombstone as XParseResult.Failure).unavailable)
        val photos = XSyndication.parse(Fixtures.read("x/tweet_photos.json"))
        assertTrue((photos as XParseResult.Failure).photos)
        assertTrue((XSyndication.parse("{}") as XParseResult.Failure).unavailable)
        val broken = XSyndication.parse("{\"__typename\":") as XParseResult.Failure
        assertFalse(broken.unavailable)
        assertTrue(broken.detail, broken.detail.startsWith("answer: MalformedJson"))
    }
}
