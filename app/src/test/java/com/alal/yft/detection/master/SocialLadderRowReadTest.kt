package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.Site
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.master.social.SocialRowCheck
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 9 (social plan S6, `MASTER_KEY_SOCIAL_PLAN.md` §7.3): S6 as the app's lookups and the site
 * census wire it ([SocialLadder.validator] over Master's client: one check, one reader, every
 * site). Each lookup's rows are read on its own site's media hosts: X's, TikTok's, Instagram's and
 * Facebook's files each reach the server once and pass, in the app's lookup and in the census's
 * Master part; another site's host (or a TikTok address outside its video paths) is refused
 * before route B lists an ask: never sent, no new ask, never `NETWORK`, counted refused; a file
 * that gets no answer is still `NETWORK`, one new ask. Flag off: the check given, no reader. A
 * local HTTPS server under the sites' media host names; made-up addresses and IDs; no live calls.
 */
class SocialLadderRowReadTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        val certificate = HeldCertificate.Builder().apply {
            HOSTS.forEach { addSubjectAlternativeName(it) }
        }.build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory(), false)
            start(InetAddress.getByName("127.0.0.1"), 0)
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .protocols(listOf(Protocol.HTTP_1_1))
            .proxy(Proxy.NO_PROXY)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("127.0.0.1"))
            })
            .build()
    }

    @After
    fun close() {
        server.shutdown()
    }

    @Test
    fun oneCheckReadsEachLookupsRowsOnItsOwnSitesHostsOnceAndTheyPass() = runBlocking {
        val own = Counting()
        val check = SocialLadder.validator(own, client, social = true)
        val files = listOf(
            SiteWays.X to x(FILE_720),
            SiteWays.TIKTOK to tiktok("/fixture/video/tos/useast2a/a.mp4?mime_type=video_mp4"),
            SiteWays.INSTAGRAM to instagram("/o1/v/t16/f2/m86/fixture1080.mp4?efg=fixture"),
            SiteWays.FACEBOOK to facebook("/v/t42/fixture-hd.mp4?oe=F2A52380&oh=fixture"),
        )
        for (wiring in Wiring.entries) {
            for ((site, file) in files) {
                server.enqueue(mp4())
                val lookup = wiring.lookup(site)

                val checked = withContext(lookup.context) { check.validate(row(file), NOW) }

                assertTrue("${site.id} ($wiring): $checked", checked is ValidationResult.Valid)
                assertEquals(TOTAL, (checked as ValidationResult.Valid).candidate.contentLengthBytes)
                val asked = server.takeRequest()
                assertEquals("${site.id} ($wiring)", "${hostOf(file)}:${server.port}", asked.getHeader("Host"))
                assertEquals("bytes=0-4095", asked.getHeader("Range"))
                assertEquals("fixture-agent", asked.getHeader("User-Agent"))
                assertNull("never the user's session", asked.getHeader("Cookie"))
                // One new ask, Master's; nothing refused.
                assertEquals(1, lookup.record.masterAsks)
                assertEquals(1, lookup.record.asks().count { it.asker == LookupRecord.Asker.MASTER })
                assertEquals(0, refused(check, lookup.record))

                // A later row of the same file in the lookup: the same bytes, not asked again.
                val again = withContext(lookup.context) { check.validate(row(file), NOW) }
                assertTrue(again is ValidationResult.Valid)
                assertEquals(1, lookup.record.masterAsks)
            }
        }
        assertEquals(files.size * Wiring.entries.size, server.requestCount)
        assertEquals("S6 checks in place of the engine's own", 0, own.calls.get())
    }

    @Test
    fun anotherSitesHostIsRefusedBeforeAnyAskNeverSentAndCountsRefused() = runBlocking {
        val check = SocialLadder.validator(Counting(), client, social = true)
        val offHost = listOf(
            SiteWays.X to facebook("/v/t42/fixture-hd.mp4?oe=F2A52380&oh=fixture"),
            SiteWays.TIKTOK to instagram("/o1/v/t16/f2/m86/fixture1080.mp4?efg=fixture"),
            SiteWays.INSTAGRAM to x(FILE_720),
            SiteWays.FACEBOOK to tiktok("/fixture/video/tos/useast2a/a.mp4?mime_type=video_mp4"),
            // TikTok's own host outside its video paths (a cover): not widened either.
            SiteWays.TIKTOK to tiktok("/obj/fixture-cover.mp4"),
        )
        for (wiring in Wiring.entries) {
            for ((site, file) in offHost) {
                val lookup = wiring.lookup(site)

                val checked = withContext(lookup.context) { check.validate(row(file), NOW) }

                assertEquals("${site.id} ($wiring)", SiteExtractionFailure.NO_MEDIA_FOUND, (checked as ValidationResult.Rejected).reason)
                // Not new: route B listed no ask; refused once, however often the file is offered.
                assertEquals(0, lookup.record.masterAsks)
                assertTrue(lookup.record.asks().isEmpty())
                assertEquals(1, refused(check, lookup.record))
                withContext(lookup.context) { check.validate(row(file), NOW) }
                assertEquals(1, refused(check, lookup.record))
            }
        }
        assertEquals("nothing was sent", 0, server.requestCount)
    }

    @Test
    fun aFileThatGetsNoAnswerIsStillNetworkWithItsOneNewAsk() = runBlocking {
        val check = SocialLadder.validator(Counting(), client, social = true)
        for (wiring in Wiring.entries) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val lookup = wiring.lookup(SiteWays.X)

            val checked = withContext(lookup.context) { check.validate(row(x(FILE_720)), NOW) }

            assertEquals("$wiring", SiteExtractionFailure.NETWORK, (checked as ValidationResult.Rejected).reason)
            assertEquals(1, lookup.record.masterAsks)
            assertEquals(0, refused(check, lookup.record))
        }
        assertEquals(Wiring.entries.size, server.requestCount)
    }

    @Test
    fun flagOffTheCheckGivenAndNoReader() {
        val own = Counting()
        assertSame(own, SocialLadder.validator(own, client, social = false))
    }

    /** A lookup's context as a wiring builds it, and its record. */
    private class Lookup(val context: CoroutineContext, val record: LookupRecord)

    private enum class Wiring {
        /** The app's lookup ([SocialLadder.lookupContext]: its record with the site's labels, a MainPages naming the site). */
        APP,

        /** The site census's Master part: route B over the slot's record, the record, a MainPages naming the site. */
        CENSUS,
        ;

        fun lookup(site: Site): Lookup = when (this) {
            APP -> SocialLadder.lookupContext("${site.id}:$CONTENT_ID").let { context ->
                Lookup(context, checkNotNull(context[LookupRecord]))
            }
            CENSUS -> LookupRecord(labels = site.labels()).let { record ->
                Lookup(RouteBLookup(record) + record + MainPages(site = site.id), record)
            }
        }
    }

    /** The engine's own file check (never asked where S6 checks): how often it was asked. */
    private class Counting : MasterMediaValidator {
        val calls = AtomicInteger()

        override suspend fun validate(candidate: MediaCandidate, nowEpochMs: Long): ValidationResult {
            calls.incrementAndGet()
            return ValidationResult.Valid(candidate)
        }
    }

    private fun x(path: String) = "https://$X_HOST:${server.port}$path"

    private fun tiktok(path: String) = "https://$TIKTOK_HOST:${server.port}$path"

    private fun instagram(path: String) = "https://$INSTAGRAM_HOST:${server.port}$path"

    private fun facebook(path: String) = "https://$FACEBOOK_HOST:${server.port}$path"

    private fun mp4() = MockResponse().setResponseCode(206)
        .setHeader("Content-Type", "video/mp4")
        .setHeader("Content-Range", "bytes 0-${MP4.size - 1}/$TOTAL")
        .setBody(Buffer().write(MP4))

    private fun refused(check: MasterMediaValidator, record: LookupRecord): Int = (check as SocialRowCheck).refused(record)

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val TOTAL = 32_768L
        const val CONTENT_ID = "1000000000000000001"
        const val PAGE = "https://www.example.test/fixture/1000000000000000001"
        const val X_HOST = "video.twimg.com"
        const val TIKTOK_HOST = "v16m.tiktokcdn.com"
        const val INSTAGRAM_HOST = "scontent-xyz1-1.cdninstagram.com"
        const val FACEBOOK_HOST = "video.fixture.fbcdn.net"
        val HOSTS = listOf(X_HOST, TIKTOK_HOST, INSTAGRAM_HOST, FACEBOOK_HOST)
        const val FILE_720 = "/amplify_video/1000000000000000002/vid/avc1/1280x720/a.mp4?tag=16"

        /** An MP4 file's start: an `ftyp` box, then nothing the init check reads as a marker. */
        val MP4: ByteArray = ByteArray(64).apply {
            this[3] = 24
            "ftypisom".toByteArray().copyInto(this, 4)
        }

        /** A row Master offers for [url]: an MP4 file, the download's headers with the user's cookie (never sent). */
        fun row(url: String) = MediaCandidate(
            pageUrl = PAGE,
            mediaUrl = url,
            sources = setOf(CandidateSource.MANIFEST),
            kind = MediaKind.DIRECT,
            mimeType = "video/mp4",
            requestContext = BrowserRequestContext(PAGE, "fixture-agent", "sessionid=fixture-cookie"),
        )

        fun hostOf(url: String): String = url.removePrefix("https://").substringBefore(':')
    }
}
