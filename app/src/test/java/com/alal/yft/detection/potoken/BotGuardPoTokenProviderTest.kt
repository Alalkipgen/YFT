package com.alal.yft.detection.potoken

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PoTokenRequest
import com.alal.yft.extractor.api.PoTokenResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BotGuardPoTokenProviderTest {
    @Test
    fun `one page mints a token per video and reuses it`() = runTest {
        val fixture = Fixture()
        val provider = fixture.provider(backgroundScope)

        assertEquals(minted(FIRST), provider.mint(request(FIRST)))
        assertEquals(minted(FIRST), provider.mint(request(FIRST)))
        assertEquals(minted(SECOND), provider.mint(request(SECOND)))

        assertEquals(1, fixture.http.fetches.size)
        assertEquals(
            listOf(BotGuardProtocol.CREATE_URL, BotGuardProtocol.GENERATE_URL),
            fixture.transport.urls(),
        )
        assertTrue(fixture.transport.calls.all { it.attestationKey == TEST_KEY })
        assertTrue(fixture.transport.calls.all { it.userAgent == "FixtureWebView/1.0" })
        assertEquals(1, fixture.engine.pages.size)
        assertEquals(listOf(FIRST, SECOND), fixture.engine.pages.single().minted)
        assertEquals(INTEGRITY, fixture.engine.pages.single().integrityToken)
        assertFalse(fixture.engine.pages.single().closed)
    }

    @Test
    fun `a stale integrity token starts a new page, the player key is kept`() = runTest {
        val fixture = Fixture(lifetimeSeconds = 600, refreshSeconds = 100)
        val provider = fixture.provider(backgroundScope)

        provider.mint(request(FIRST))
        fixture.now += 499_000
        provider.mint(request(SECOND))
        assertEquals(1, fixture.engine.pages.size)

        fixture.now += 1_000
        assertEquals(minted(FIRST), provider.mint(request(FIRST)))
        assertEquals(2, fixture.engine.pages.size)
        assertTrue(fixture.engine.pages.first().closed)
        assertEquals(1, fixture.http.fetches.size)
        assertEquals(4, fixture.transport.calls.size)
    }

    @Test
    fun `an idle page is closed and the next lookup opens a new one`() = runTest {
        val fixture = Fixture()
        val provider = fixture.provider(backgroundScope, idleMillis = 60_000)

        provider.mint(request(FIRST))
        advanceTimeBy(59_000)
        runCurrent()
        provider.mint(request(SECOND))
        advanceTimeBy(59_000)
        runCurrent()
        assertFalse(fixture.engine.pages.single().closed)

        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(fixture.engine.pages.single().closed)
        provider.mint(request("vid00000003"))
        assertEquals(2, fixture.engine.pages.size)
    }

    @Test
    fun `each failing step is a structured failure and never leaves a page open`() = runTest {
        val cases = listOf(
            Fixture(playerFailure = SiteExtractionFailure.NETWORK) to SiteExtractionFailure.NETWORK,
            Fixture(playerSource = "function(){}") to SiteExtractionFailure.RESPONSE_CHANGED,
            Fixture(createFailure = SiteExtractionFailure.RATE_LIMITED) to
                SiteExtractionFailure.RATE_LIMITED,
            Fixture(challengeBody = "[]") to SiteExtractionFailure.RESPONSE_CHANGED,
            Fixture(pageOpens = false) to SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            Fixture(snapshotWorks = false) to SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            Fixture(integrityBody = "[null]") to SiteExtractionFailure.RESPONSE_CHANGED,
            Fixture(minterWorks = false) to SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
        )
        cases.forEach { (fixture, expected) ->
            val result = fixture.provider(backgroundScope).mint(request(FIRST))
            assertEquals(PoTokenResult.Failed(expected), result)
            assertTrue(fixture.engine.pages.all { it.closed })
        }
    }

    @Test
    fun `a failed mint closes the page and the next lookup starts afresh`() = runTest {
        val fixture = Fixture(mintWorks = false)
        val provider = fixture.provider(backgroundScope)

        val failed = provider.mint(request(FIRST))
        assertEquals(PoTokenResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED), failed)
        assertTrue(fixture.engine.pages.single().closed)

        fixture.engine.mintWorks = true
        assertEquals(minted(FIRST), provider.mint(request(FIRST)))
        assertEquals(2, fixture.engine.pages.size)
    }

    @Test
    fun `nothing is fetched without an engine, a YouTube player or a plain binding`() = runTest {
        val noEngine = Fixture(available = false)
        val unavailable = noEngine.provider(backgroundScope).mint(request(FIRST))
        assertEquals(PoTokenResult.Unavailable, unavailable)

        val fixture = Fixture()
        val provider = fixture.provider(backgroundScope)
        val foreignPlayer = PoTokenRequest(
            contentBinding = "vid00000001",
            playerScriptUrl = "https://cdn.example.test/s/player/abcd1234/base.js",
            pageUrl = PAGE,
        )
        assertEquals(
            PoTokenResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED),
            provider.mint(foreignPlayer),
        )
        assertEquals(
            PoTokenResult.Failed(SiteExtractionFailure.UNSUPPORTED_URL),
            provider.mint(request("vid\");alert(1)")),
        )
        assertTrue(noEngine.http.fetches.isEmpty() && fixture.http.fetches.isEmpty())
        assertTrue(noEngine.transport.calls.isEmpty() && fixture.transport.calls.isEmpty())
    }

    @Test
    fun `the player is fetched from the page the token is for`() = runTest {
        val fixture = Fixture()
        fixture.provider(backgroundScope).mint(request(FIRST))
        val (url, headers) = fixture.http.fetches.single()
        assertEquals(PLAYER, url)
        assertEquals(mapOf("Referer" to PAGE), headers)
    }

    private fun request(binding: String) = PoTokenRequest(
        contentBinding = binding,
        playerScriptUrl = PLAYER,
        pageUrl = PAGE,
    )

    private class Fixture(
        available: Boolean = true,
        playerSource: String = """x={"X-Goog-Api-Key"]:"$TEST_KEY"}""",
        playerFailure: SiteExtractionFailure? = null,
        createFailure: SiteExtractionFailure? = null,
        challengeBody: String = challenge(),
        integrityBody: String? = null,
        lifetimeSeconds: Long = 43_200,
        refreshSeconds: Long = 100,
        pageOpens: Boolean = true,
        snapshotWorks: Boolean = true,
        minterWorks: Boolean = true,
        mintWorks: Boolean = true,
    ) {
        var now = 1_000_000L
        val http = FakeHttp(playerSource, playerFailure)
        val transport = FakeTransport(
            createFailure = createFailure,
            challengeBody = challengeBody,
            integrityBody = integrityBody ?: """["$INTEGRITY",$lifetimeSeconds,$refreshSeconds]""",
        )
        val engine = FakeEngine(available, pageOpens, snapshotWorks, minterWorks, mintWorks)

        fun provider(scope: CoroutineScope, idleMillis: Long = 300_000) = BotGuardPoTokenProvider(
            http = http,
            transport = transport,
            engine = engine,
            scope = scope,
            clock = { now },
            idleMillis = idleMillis,
        )
    }

    private class FakeHttp(
        private val source: String,
        private val failure: SiteExtractionFailure?,
    ) : ExtractorHttpClient {
        val fetches = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            fetches += url to headers
            return failure?.let { ExtractorHttpResult.Failure(it) }
                ?: ExtractorHttpResult.Success(200, source, url)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = error("The provider never posts through the adapter client")
    }

    private class FakeTransport(
        private val createFailure: SiteExtractionFailure?,
        private val challengeBody: String,
        private val integrityBody: String,
    ) : AttestationTransport {
        val calls = mutableListOf<AttestationCall>()

        fun urls(): List<String> = calls.map { it.url }

        override suspend fun post(call: AttestationCall): AttestationReply {
            calls += call
            return when (call.url) {
                BotGuardProtocol.CREATE_URL -> createFailure?.let { AttestationReply.Failure(it) }
                    ?: AttestationReply.Success(challengeBody)

                else -> {
                    assertTrue(call.body.contains("snapshot-answer"))
                    AttestationReply.Success(integrityBody)
                }
            }
        }
    }

    private class FakeEngine(
        override val isAvailable: Boolean,
        private val pageOpens: Boolean,
        private val snapshotWorks: Boolean,
        private val minterWorks: Boolean,
        var mintWorks: Boolean,
    ) : BotGuardEngine {
        val pages = mutableListOf<FakePage>()

        override suspend fun userAgent(): String = "FixtureWebView/1.0"

        override suspend fun open(challenge: BotGuardProtocol.Challenge): BotGuardSession? {
            assertEquals("trayride", challenge.globalName)
            if (!pageOpens) return null
            return FakePage().also { pages += it }
        }

        inner class FakePage : BotGuardSession {
            val minted = mutableListOf<String>()
            var integrityToken: String? = null
            var closed = false

            override suspend fun snapshot(): String? =
                "snapshot-answer".takeIf { snapshotWorks && !closed }

            override suspend fun createMinter(integrityToken: String): Boolean {
                this.integrityToken = integrityToken
                return minterWorks && !closed
            }

            override suspend fun mint(binding: String): String? {
                if (closed || !mintWorks) return null
                minted += binding
                return token(binding)
            }

            override fun close() {
                closed = true
            }
        }
    }

    private companion object {
        const val TEST_KEY = "TestOnlyAttestationKey_0123456789"
        const val INTEGRITY = "SW50ZWdyaXR5VG9rZW5Gb3JUZXN0aW5nT25seQ"
        const val PLAYER = "https://www.youtube.com/s/player/abcd1234/" +
            "player-plasma-ias-phone-en_US.vflset/base.js"
        const val PAGE = "https://m.youtube.com/watch?v=vid00000001"

        const val FIRST = "vid00000001"
        const val SECOND = "vid00000002"

        fun minted(binding: String) = PoTokenResult.Minted(token(binding))

        fun token(binding: String): String =
            Base64.getUrlEncoder().encodeToString("minted-for-$binding-0123".toByteArray())

        fun challenge(): String =
            """[["message",["(function(){})()"],null,"hash","program-text","trayride"]]"""
    }
}
