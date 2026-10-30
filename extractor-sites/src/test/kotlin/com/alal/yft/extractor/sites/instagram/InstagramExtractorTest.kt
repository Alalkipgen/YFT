package com.alal.yft.extractor.sites.instagram

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramExtractorTest {
    private companion object {
        /** 2023-11-14: after `oe=5F000000` (2020), before `oe=7A000000` (2034). */
        const val NOW = 1_700_000_000_000L

        /**
         * v1.0.2 item 51: a carousel of a video, a photo, a video without files, a video and a
         * video whose link expired. Here, not under `fixtures/`: Master's offline parity reads
         * every file there against its own baseline, a Master file item 51 leaves as it is.
         */
        val MIXED_CAROUSEL = """
            {
             "data": {
              "xdt_shortcode_media": {
               "__typename": "XDTGraphSidecar",
               "id": "5",
               "shortcode": "C9fixtMIXD1",
               "display_url": "https://instagram.fxyz1-1.fna.fbcdn.net/v/m.jpg",
               "is_video": false,
               "owner": {
                "username": "fixture.creator",
                "full_name": "Fixture Creator"
               },
               "edge_media_to_caption": {
                "edges": [
                 {
                  "node": {
                   "text": "Four moments from the beach day\nsecond line"
                  }
                 }
                ]
               },
               "edge_sidecar_to_children": {
                "edges": [
                 {
                  "node": {
                   "__typename": "XDTGraphVideo",
                   "id": "51",
                   "shortcode": "C9fixtMXC1",
                   "display_url": "https://instagram.fxyz1-1.fna.fbcdn.net/v/m1.jpg",
                   "is_video": true,
                   "video_url": "https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/mix1.mp4?oh=00_m1&oe=7A000000",
                   "dimensions": {
                    "height": 1280,
                    "width": 720
                   },
                   "video_duration": 5.0
                  }
                 },
                 {
                  "node": {
                   "__typename": "XDTGraphImage",
                   "id": "52",
                   "shortcode": "C9fixtMXC2",
                   "display_url": "https://instagram.fxyz1-1.fna.fbcdn.net/v/m2.jpg",
                   "is_video": false,
                   "dimensions": {
                    "height": 1080,
                    "width": 1080
                   }
                  }
                 },
                 {
                  "node": {
                   "__typename": "XDTGraphVideo",
                   "id": "53",
                   "shortcode": "C9fixtMXC3",
                   "display_url": "https://instagram.fxyz1-1.fna.fbcdn.net/v/m3.jpg",
                   "is_video": true,
                   "dimensions": {
                    "height": 1080,
                    "width": 1080
                   },
                   "video_duration": 6.0
                  }
                 },
                 {
                  "node": {
                   "__typename": "XDTGraphVideo",
                   "id": "54",
                   "shortcode": "C9fixtMXC4",
                   "display_url": "https://instagram.fxyz1-1.fna.fbcdn.net/v/m4.jpg",
                   "is_video": true,
                   "video_url": "https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/mix4.mp4?oh=00_m4&oe=7A000000",
                   "dimensions": {
                    "height": 640,
                    "width": 640
                   },
                   "video_duration": 3.5
                  }
                 },
                 {
                  "node": {
                   "__typename": "XDTGraphVideo",
                   "id": "55",
                   "shortcode": "C9fixtMXC5",
                   "display_url": "https://instagram.fxyz1-1.fna.fbcdn.net/v/m5.jpg",
                   "is_video": true,
                   "video_url": "https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/mix5.mp4?oh=00_m5&oe=5F000000",
                   "dimensions": {
                    "height": 480,
                    "width": 480
                   },
                   "video_duration": 2.0
                  }
                 }
                ]
               }
              }
             },
             "status": "ok"
            }
        """.trimIndent()
    }

    private val agent = "Mozilla/5.0 (Linux; Android 14) Fixture"
    private val session = "csrftoken=csrfFixture; sessionid=555%3Asecret; ds_user_id=555"

    private fun request(url: String, cookie: String? = null, now: Long = 1_000L) =
        SiteExtractionRequest(
            identity = requireNotNull(InstagramUrls.identify(url)),
            requestContext = BrowserRequestContext(url, agent, cookie),
            nowEpochMs = now,
        )

    /** Answers by kind of address: the app API, GraphQL, the post page or the embed page. */
    private fun client(
        api: ExtractorHttpResult? = null,
        graphQl: ExtractorHttpResult? = null,
        page: ExtractorHttpResult? = null,
        embed: ExtractorHttpResult? = null,
    ) = FakeExtractorHttpClient(
        getResponder = { url, _ ->
            when {
                "/api/v1/media/" in url -> api
                "/graphql/query/" in url -> graphQl
                url.endsWith("/embed/captioned/") -> embed
                else -> page
            }
        },
    )

    private fun json(fixture: String) =
        FakeExtractorHttpClient.json(
            Fixtures.read("instagram/$fixture"),
            "https://www.instagram.com/x",
        )

    private fun html(fixture: String, finalUrl: String = "https://www.instagram.com/p/x/") =
        FakeExtractorHttpClient.html(Fixtures.read("instagram/$fixture"), finalUrl)

    @Test
    fun `signed out, the GraphQL answer gives the file and every merged size`() = runTest {
        val http = client(graphQl = json("graphql_reel.json"))

        val result = InstagramExtractor(http).extract(
            request("https://www.instagram.com/reel/C9fixtREEL1/?igsh=abc"),
        ) as SiteExtractionResult.Success

        assertEquals(1, http.requestedUrls.size)
        assertTrue(http.requestedUrls.single().startsWith(
            "https://www.instagram.com/graphql/query/?doc_id=8845758582119845&variables=",
        ))
        assertTrue(http.requestedUrls.single().contains("C9fixtREEL1"))
        val headers = http.requestedHeaders.single()
        assertEquals("936619743392459", headers["X-IG-App-ID"])
        assertNull(headers["Cookie"])
        val rows = result.candidates
        assertEquals(listOf(1280, 1920, 1280, 854, null), rows.map { it.height })
        val file = rows.first()
        assertTrue(file.mediaUrl.contains("gql_prog720.mp4"))
        assertNull(file.audioCompanion)
        val merged = rows.subList(1, 4)
        merged.forEach { row ->
            assertEquals(MediaKind.DIRECT, row.kind)
            assertTrue(row.audioCompanion!!.mediaUrl.contains("gql_audio.mp4"))
            assertTrue(row.codecs.single().startsWith("avc1"))
        }
        val sound = rows.last()
        assertEquals("audio/mp4", sound.mimeType)
        assertEquals(listOf("mp4a.40.5"), sound.codecs)
        rows.forEach { row ->
            assertNull(row.requestContext.cookie)
            assertEquals("GraphQL caption", row.title)
            assertEquals(15_200L, row.durationMillis)
            assertEquals("https://www.instagram.com/reel/C9fixtREEL1/", row.pageUrl)
            assertNotNull(row.expiresAtEpochMs)
            // v1.0.2 item 51: a post of one video names no item.
            assertNull(row.postItem)
        }
        assertEquals("session: none", result.details.first())
        assertEquals(
            "item 1 of 1: 1 files (1280), DASH AVC 480/720/1080, VP9 360 + audio",
            result.details.last(),
        )
    }

    @Test
    fun `signed in, the app API is asked first with the session`() = runTest {
        val http = client(api = json("api_info_reel.json"))

        val result = InstagramExtractor(http).extract(
            request("https://www.instagram.com/reel/C9fixtREEL1/", cookie = session),
        ) as SiteExtractionResult.Success

        assertEquals(
            listOf("https://www.instagram.com/api/v1/media/3413599992010523381/info/"),
            http.requestedUrls,
        )
        val headers = http.requestedHeaders.single()
        assertEquals(session, headers["Cookie"])
        assertEquals("csrfFixture", headers["X-CSRFToken"])
        assertEquals(agent, headers["User-Agent"])
        assertEquals(listOf(1280, 854, 1920, 1280, 854, null), result.candidates.map { it.height })
        assertEquals("Reel caption line one", result.candidates.first().title)
        result.candidates.forEach { assertNull(it.requestContext.cookie) }
    }

    @Test
    fun `the signed-in page's data is read when GraphQL has no post`() = runTest {
        val http = client(
            api = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500),
            graphQl = json("graphql_null.json"),
            page = html("page_signed_in.html"),
        )

        val result = InstagramExtractor(http).extract(
            request("https://www.instagram.com/reel/C9fixtREEL1/", cookie = session),
        ) as SiteExtractionResult.Success

        assertEquals("https://www.instagram.com/reel/C9fixtREEL1/", http.requestedUrls.last())
        assertEquals(session, http.requestedHeaders.last()["Cookie"])
        assertTrue(result.candidates.first().mediaUrl.contains("page_prog720.mp4"))
        assertFalse(result.candidates.any { it.mediaUrl.contains("other.mp4") })
        assertTrue(result.details.any { it.startsWith("GraphQL: 200") && it.endsWith("no post") })
    }

    @Test
    fun `the public embed page is the last answer`() = runTest {
        val http = client(
            graphQl = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404),
            page = html("embed_no_video.html"),
            embed = html("embed_captioned.html"),
        )

        val result = InstagramExtractor(http).extract(
            request("https://www.instagram.com/p/C9fixtREEL1/"),
        ) as SiteExtractionResult.Success

        val row = result.candidates.single()
        assertTrue(row.mediaUrl.contains("embed_prog640.mp4"))
        assertEquals(640, row.width)
        assertEquals("Embed caption", row.title)
        assertNull(http.requestedHeaders.last()["Cookie"])
    }

    @Test
    fun `a post behind the login wall says where to sign in`() = runTest {
        val http = client(
            graphQl = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 401),
            page = html(
                "embed_no_video.html",
                finalUrl = "https://www.instagram.com/accounts/login/?next=%2Freel%2F",
            ),
            embed = html("embed_no_video.html"),
        )

        val failure = InstagramExtractor(http).extract(
            request("https://www.instagram.com/reel/C9fixtREEL1/"),
        ) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, failure.reason)
        assertEquals(InstagramExtractor.LOGIN_MESSAGE, failure.message)
        assertFalse(failure.allowsGenericFallback)
        assertEquals(
            listOf(
                "session: none",
                "GraphQL GET failed (HTTP_STATUS 401)",
                "page: login page",
                "embed page: 200 (98 characters), no post",
            ),
            failure.details,
        )
    }

    @Test
    fun `GraphQL's login answer counts as the wall`() = runTest {
        val failure = InstagramExtractor(
            client(graphQl = json("graphql_login.json"), page = html("embed_no_video.html")),
        ).extract(request("https://www.instagram.com/reel/C9fixtREEL1/"))
            as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, failure.reason)
    }

    @Test
    fun `a carousel link picks its item, else the first video`() = runTest {
        val http = client(graphQl = json("graphql_carousel.json"))
        val extractor = InstagramExtractor(http)

        val third = extractor.extract(
            request("https://www.instagram.com/p/C9fixtSIDE1/?img_index=3"),
        ) as SiteExtractionResult.Success
        val first = extractor.extract(
            request("https://www.instagram.com/p/C9fixtSIDE1/?img_index=1"),
        ) as SiteExtractionResult.Success

        assertEquals("one request each", 2, http.requestedUrls.size)
        val linked = third.candidates.single()
        assertTrue(linked.mediaUrl.contains("side3.mp4"))
        assertEquals(4_000L, linked.durationMillis)
        assertEquals(2 to 2, linked.postItem?.let { it.number to it.count })
        // v1.0.2 item 51: the post's other video comes with it, under its own name.
        val other = linked.postItem!!.others.single()
        assertTrue(other.mediaUrl.contains("side2.mp4"))
        assertEquals("instagram:C9fixtSIDE1:2", other.videoId)
        assertEquals(1 to 2, other.postItem?.let { it.number to it.count })
        // The photo (item 1) names no video: the first video comes first.
        val firstRow = first.candidates.single()
        assertTrue(firstRow.mediaUrl.contains("side2.mp4"))
        assertEquals(1 to 2, firstRow.postItem?.let { it.number to it.count })
        assertTrue(firstRow.postItem!!.others.single().mediaUrl.contains("side3.mp4"))
        assertEquals(
            listOf(
                "item 2 of 3: 1 files (1080), DASH none",
                "also item 3 of 3: 1 files (720), DASH none",
            ),
            first.details.takeLast(2),
        )
    }

    @Test
    fun `every video item of a carousel is offered, the linked one first`() = runTest {
        val http = client(
            graphQl = FakeExtractorHttpClient.json(MIXED_CAROUSEL, "https://www.instagram.com/x"),
        )
        val extractor = InstagramExtractor(http)

        val fourth = extractor.extract(
            request("https://www.instagram.com/p/C9fixtMIXD1/?img_index=4", now = NOW),
        ) as SiteExtractionResult.Success

        assertEquals("the answer holds every item: no request more", 1, http.requestedUrls.size)
        val linked = fourth.candidates.single()
        assertTrue(linked.mediaUrl.contains("mix4.mp4"))
        assertNull("the lookup names the linked item", linked.videoId)
        assertEquals("Four moments from the beach day", linked.title)
        val item = linked.postItem!!
        assertEquals("Video 3 of 4", item.label)
        // Item 1 is offered; the photo (2) is skipped, item 3 has no file, item 5's links expired.
        val other = item.others.single()
        assertTrue(other.mediaUrl.contains("mix1.mp4"))
        assertEquals("instagram:C9fixtMIXD1:1", other.videoId)
        assertEquals("Video 1 of 4", other.postItem!!.label)
        assertTrue(other.postItem!!.others.isEmpty())
        assertEquals(1280, other.height)
        assertEquals(5_000L, other.durationMillis)
        assertEquals(linked.pageUrl, other.pageUrl)
        assertNull(other.requestContext.cookie)
        assertEquals(
            listOf(
                "item 4 of 5: 1 files (640), DASH none",
                "also item 1 of 5: 1 files (1280), DASH none",
                "also item 3 of 5: a video without files, not offered",
                "also item 5 of 5: 0 files, DASH none, links expired, not offered",
            ),
            fourth.details.takeLast(4),
        )

        val plain = extractor.extract(
            request("https://www.instagram.com/p/C9fixtMIXD1/", now = NOW),
        ) as SiteExtractionResult.Success
        val firstVideo = plain.candidates.single()
        assertTrue(firstVideo.mediaUrl.contains("mix1.mp4"))
        assertEquals("Video 1 of 4", firstVideo.postItem!!.label)
        assertEquals(
            listOf("instagram:C9fixtMIXD1:4"),
            firstVideo.postItem!!.others.map { it.videoId },
        )
        assertEquals("Video 3 of 4", firstVideo.postItem!!.others.single().postItem!!.label)
    }

    @Test
    fun `a photo post, expired links and a lost line fail with their reasons`() = runTest {
        val photo = InstagramExtractor(client(graphQl = json("graphql_photo.json"))).extract(
            request("https://www.instagram.com/p/C9fixtPHOTO/"),
        ) as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, photo.reason)
        assertEquals(InstagramExtractor.PHOTOS_MESSAGE, photo.message)

        val expired = InstagramExtractor(client(graphQl = json("graphql_reel.json"))).extract(
            request("https://www.instagram.com/reel/C9fixtREEL1/", now = 4_000_000_000_000L),
        ) as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.EXPIRED_LINK, expired.reason)

        val offline = InstagramExtractor(
            FakeExtractorHttpClient(
                fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK),
            ),
        ).extract(request("https://www.instagram.com/reel/C9fixtREEL1/"))
            as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.NETWORK, offline.reason)
        assertTrue(offline.allowsGenericFallback)
    }
}
