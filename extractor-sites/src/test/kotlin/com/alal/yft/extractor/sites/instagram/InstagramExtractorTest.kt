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

        assertTrue(third.candidates.single().mediaUrl.contains("side3.mp4"))
        assertEquals(4_000L, third.candidates.single().durationMillis)
        assertTrue(first.candidates.single().mediaUrl.contains("side2.mp4"))
        assertEquals("item 2 of 3: 1 files (1080), DASH none", first.details.last())
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
