package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.fixture
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.solver.OwnSolverTextFile
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK Master plan §8, part 2, as agent D's wiring reads it (the live check, 2026-10-26): V2's
 * self-heal source, vkvideo.ru's page then its one common script, with desktop Chrome's agent;
 * inside V2's pass, by the lookup's rules, counted, the script read once for both values. Made-up
 * fixtures only (`fixtures/vk-master-kit`), no network.
 */
class VkClientSourceTest {
    private var now = START
    private val file = MemoryFile()
    private val health = SiteHealth(file) { now }

    @Test
    fun `the client id comes from the common script the page lists today - two requests, desktop Chrome's agent only`() = runTest {
        val http = vk()
        val source = ValueSource(health, VkClientSource(http))
        source.rejected(bundled(source, CLIENT_ID))
        val healed = checkNotNull(source.next(CLIENT_ID, BUNDLED_ID, PHONE_AGENT))
        assertEquals(listOf(ValueSource.Origin.HEALED, "100000", "vkvideo-script"), listOf(healed.origin, healed.value, healed.label))
        assertEquals("the off-host, plain-http and other scripts are skipped", listOf(VkClientSource.PAGE, COMMON), http.urls)
        assertEquals(VkClientSource.REQUESTS_PER_READ, http.calls.size)
        http.calls.forEach { assertEquals("no cookie, token or Referer", mapOf("User-Agent" to DESKTOP_AGENT), it.headers) }
        assertEquals(listOf(VkClientSource.PAGE_MAX_BYTES, VkClientSource.COMMON_MAX_BYTES), http.calls.map { it.maxBytes })
        assertEquals("read today", source.heal(CLIENT_ID, PHONE_AGENT).why)
        assertEquals(2, http.calls.size)
        assertFalse(healed.toString().contains("100000"))

        assertNull("nothing is stored before OK + V", health.learned(VK, CLIENT_ID.name))
        assertFalse(source.kept(healed, AnswerClass.DIRECT, rowsPassed = false))
        assertTrue(source.kept(healed, AnswerClass.DIRECT, rowsPassed = true))
        assertEquals("vkvideo-script", health.learned(VK, CLIENT_ID.name)?.source)
        assertEquals("the client id is kept by its own pattern", "100000", health.learned(VK, CLIENT_ID.name)?.value)
        assertFalse("never the secret", checkNotNull(file.text).contains("FIXTUREsecret"))

        val restarted = ValueSource(SiteHealth(file) { now }, VkClientSource(http))
        val learned = checkNotNull(restarted.value(CLIENT_ID, BUNDLED_ID))
        assertEquals(listOf(ValueSource.Origin.LEARNED, "100000", "vkvideo-script"), listOf(learned.origin, learned.value, learned.source))
        assertEquals(2, http.calls.size)

        val direct = vk()
        val got = VkClientSource(direct).read(CLIENT_ID, PHONE_AGENT, 1_000)
        assertTrue(got is ExtractorHttpResult.Success && got.details.isEmpty() && got.cookies.isEmpty())
        assertEquals("the caller's cap wins when smaller", 1_000L, direct.calls.last().maxBytes)
    }

    @Test
    fun `desktop Chrome's agent with the phone's Chrome major, else 131 - the phone's own agent never goes`() = runTest {
        assertEquals(DESKTOP_AGENT, VkClientSource.desktop(PHONE_AGENT))
        listOf(null, "", " ", "Mozilla/5.0 (FIXTURE)", "Chrome/9.0", "Chrome/12345.0").forEach { agent ->
            assertEquals(
                "$agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                VkClientSource.desktop(agent),
            )
        }
        for (agent in listOf(null, " ", "Mozilla/5.0 (FIXTURE)")) {
            val http = vk()
            assertEquals("100000", heal(http, CLIENT_ID, agent).candidate?.value)
            http.calls.forEach { assertEquals("$agent", mapOf("User-Agent" to VkClientSource.desktop(null)), it.headers) }
        }
    }

    @Test
    fun `the API version comes from the same script, and is learned after an OK answer and a V pass`() = runTest {
        val http = vk()
        val source = ValueSource(health, VkClientSource(http))
        source.rejected(bundled(source, API_VERSION))
        val healed = checkNotNull(source.next(API_VERSION, BUNDLED_VERSION, PHONE_AGENT))
        assertEquals(listOf(ValueSource.Origin.HEALED, "5.000"), listOf(healed.origin, healed.value))
        assertEquals(listOf(VkClientSource.PAGE, COMMON), http.urls)
        assertEquals(VkClientSource.COMMON_MAX_BYTES, http.calls.last().maxBytes)
        assertNull("nothing is stored before OK + V", health.learned(VK, API_VERSION.name))
        assertFalse(source.kept(healed, AnswerClass.DIRECT, rowsPassed = false))
        assertTrue(source.kept(healed, AnswerClass.DIRECT, rowsPassed = true))

        val restarted = ValueSource(SiteHealth(file) { now }, VkClientSource(http))
        val learned = checkNotNull(restarted.value(API_VERSION, BUNDLED_VERSION))
        assertEquals(listOf(ValueSource.Origin.LEARNED, "5.000", "learned"), listOf(learned.origin, learned.value, learned.label))
        assertEquals(2, http.calls.size)
    }

    @Test
    fun `in V2's pass both values come from one read - the lookup's rules, Master's asks, counted, the script kept for the second`() = runTest {
        val http = vk()
        val record = LookupRecord(clock = { now })
        val pass = VkClientSource.Pass(RouteBLookup(record))
        val source = ValueSource(health, VkClientSource(http))
        source.rejected(bundled(source, CLIENT_ID))
        source.rejected(bundled(source, API_VERSION))
        assertFalse(pass.read)
        val id = withContext(pass) { source.heal(CLIENT_ID, PHONE_AGENT) }
        assertEquals("100000", id.candidate?.value)
        assertTrue(pass.read)
        assertEquals(2, pass.asks)
        val read = fixture(PAGE_FIXTURE).toByteArray(Charsets.UTF_8).size + fixture("vk-master-kit/script/common.js").toByteArray(Charsets.UTF_8).size
        assertEquals(read.toLong(), pass.bytes)
        val version = withContext(pass) { source.heal(API_VERSION, PHONE_AGENT) }
        assertEquals("5.000", version.candidate?.value)
        assertEquals("the same script: no new request", 2, http.calls.size)
        assertEquals(2, pass.asks)
        assertEquals(read.toLong(), pass.bytes)
        assertEquals("one heal read a day per value, both claimed", "not read (read in the last day)", ValueSource(SiteHealth(file) { now }, VkClientSource(http)).let { again ->
            again.rejected(bundled(again, API_VERSION))
            again.heal(API_VERSION, PHONE_AGENT).why
        })

        assertEquals(2, record.masterAsks)
        assertEquals(0, record.mainAsks)
        record.asks().forEach { ask ->
            assertEquals(LookupRecord.Asker.MASTER, ask.asker)
            assertEquals(LookupAsk.GET, ask.method)
            assertFalse(ask.authorized)
        }
        assertFalse(pass.toString().contains("100000"))
        assertEquals("VkClientSource.Pass(asks=2, bytes=$read)", pass.toString())

        // After a 429 in the lookup nothing new goes out, and nothing is counted.
        val limited = LookupRecord(clock = { now })
        limited.record(LookupAsk(LookupAsk.GET, "https://vk.com/FIXTURE", null, emptyMap()), ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429), now, LookupRecord.Asker.MAIN)
        val stopped = VkClientSource.Pass(RouteBLookup(limited))
        val quiet = vk()
        val refused = withContext(stopped) { VkClientSource(quiet).read(CLIENT_ID, PHONE_AGENT, ValueKind.MAX_BYTES) }
        assertTrue(refused is ExtractorHttpResult.Failure)
        assertEquals(0, quiet.calls.size)
        assertEquals(listOf(0, 0L, false), listOf(stopped.asks, stopped.bytes, stopped.read))
    }

    @Test
    fun `JSON-escaped addresses count, the query is dropped, one file on two shards is read once`() = runTest {
        val escaped = vk(page = html(page("<script>window.FIXTURE={\"src\":\"https:\\/\\/st7-8.vkvideo.ru\\/dist\\/web\\/chunks\\/common.FIXTUREj50n.js?v=FIXTURE\"}</script>")))
        assertEquals("100000", heal(escaped, CLIENT_ID).candidate?.value)
        assertEquals("no query kept", "https://st7-8.vkvideo.ru/dist/web/chunks/common.FIXTUREj50n.js", escaped.urls.last())

        val sameFile = vk(
            page = html(
                page(
                    "<script src=\"https://st.vkvideo.ru/dist/web/chunks/common.FIXTUREaaaa.js\"></script>",
                    "<link rel=\"modulepreload\" href=\"https://st1-2.vkvideo.ru/dist/web/chunks/common.FIXTUREaaaa.js\">",
                ),
            ),
        )
        assertEquals("one file on two shards: read once", "100000", heal(sameFile, CLIENT_ID).candidate?.value)
        assertEquals("https://st.vkvideo.ru/dist/web/chunks/common.FIXTUREaaaa.js", sameFile.urls.last())
        assertEquals(2, sameFile.calls.size)

        val protocolLess = vk(page = html(page("<script src=\"//st.vkvideo.ru/dist/web/chunks/common.FIXTUREpr0t.js\" defer></script>")))
        assertEquals("5.000", heal(protocolLess, API_VERSION).candidate?.value)
        assertEquals("https://st.vkvideo.ru/dist/web/chunks/common.FIXTUREpr0t.js", protocolLess.urls.last())
    }

    @Test
    fun `only VK's common script on its static host over HTTPS - every other address on the page is skipped`() = runTest {
        val decoys = listOf(
            "https://cdn.example/dist/web/chunks/common.FIXTUREoffhost.js",
            "http://st.vkvideo.ru/dist/web/chunks/common.FIXTUREplain.js",
            "https://st.vkvideo.ru.example/dist/web/chunks/common.FIXTUREsuffix.js",
            "https://FIXTURE.st.vkvideo.ru/dist/web/chunks/common.FIXTUREsub.js",
            "https://xst.vkvideo.ru/dist/web/chunks/common.FIXTUREprefix.js",
            "https://st.vkvideo.ru:8443/dist/web/chunks/common.FIXTUREport.js",
            "https://st.vkvideo.ru@cdn.example/dist/web/chunks/common.FIXTUREuser.js",
            "https://st.vkvideo.ru/dist/../web/chunks/common.FIXTUREdots.js",
            "https://st.vkvideo.ru/dist/web/chunks/common.FIXTURE%2Fesc.js",
            "https://st.vkvideo.ru/dist/web/chunks/xcommon.FIXTUREname.js",
            "https://st.vkvideo.ru/dist/web/chunks/common.FIXTUREjs.json",
            "https://st.vkvideo.ru/dist/web/common.FIXTUREnochunk.js",
            "https://st.vkvideo.ru/dist/entrypoints/error_monitoring.FIXTUREe4r0r.js",
            "https://st.vkvideo.ru/dist/entrypoints/core_spa.FIXTUREc0r3.js",
            "https://st1234-1.vkvideo.ru/dist/web/chunks/common.FIXTUREshard.js",
        )
        val http = vk(page = html(page(*decoys.map { "<script src=\"$it\"></script>" }.toTypedArray())))
        assertEquals("read failed (RESPONSE_CHANGED: no script on the page)", heal(http, CLIENT_ID).why)
        assertEquals(listOf(VkClientSource.PAGE), http.urls)

        val two = vk(
            page = html(
                page(
                    "<script src=\"https://st.vkvideo.ru/dist/web/chunks/common.FIXTUREaaaa.js\"></script>",
                    "<script src=\"https://st.vkvideo.ru/dist/web/chunks/common.FIXTUREbbbb.js\"></script>",
                ),
            ),
        )
        assertEquals("read failed (RESPONSE_CHANGED: two common scripts on the page)", heal(two, CLIENT_ID).why)
        assertEquals(1, two.calls.size)
    }

    @Test
    fun `a refused page or script gives no value, says why, and spends the day's read`() = runTest {
        val cases = listOf(
            Triple("read failed (HTTP_STATUS 403: the page)", 1, vk(page = failure(SiteExtractionFailure.HTTP_STATUS, 403))),
            Triple("read failed (NETWORK: the page)", 1, vk(page = failure(SiteExtractionFailure.NETWORK))),
            Triple("read failed (HTTP_STATUS: the page redirected)", 1, vk(page = html(fixture(PAGE_FIXTURE), "https://m.vkvideo.ru/"))),
            Triple("read failed (MALFORMED_RESPONSE: the page is not html)", 1, vk(page = html(fixture(PAGE_FIXTURE), type = "application/json"))),
            Triple("read failed (RESPONSE_TOO_LARGE 200: the page)", 1, vk(page = html("<html>" + "x".repeat(1024 * 1024) + "</html>"))),
            Triple("read failed (RESPONSE_CHANGED: no script on the page)", 1, vk(page = html(page()))),
            Triple("read failed (HTTP_STATUS 404: the common script)", 2, vk(script = { _, _ -> failure(SiteExtractionFailure.HTTP_STATUS, 404) })),
            Triple(
                "read failed (RESPONSE_TOO_LARGE 200: the common script)", 2,
                vk(scripts = mapOf("common" to "/* FIXTURE */" + "x".repeat(4 * 1024 * 1024))),
            ),
            Triple(
                "read failed (HTTP_STATUS: the script redirected)", 2,
                vk(script = { _, body -> ExtractorHttpResult.Success(200, body, "https://st.vkvideo.ru/dist/FIXTURE.js", "text/javascript") }),
            ),
            Triple(
                "read failed (MALFORMED_RESPONSE: not a script)", 2,
                vk(script = { url, body -> ExtractorHttpResult.Success(200, body, url, "text/html") }),
            ),
            Triple("no value", 2, vk(scripts = mapOf("common" to "/* FIXTURE */ var r={dsn:\"FIXTURE\",clientId:300000};"))),
        )
        cases.forEach { (why, requests, http) ->
            val health = SiteHealth { now }
            val source = ValueSource(health, VkClientSource(http))
            source.rejected(bundled(source, CLIENT_ID))
            assertEquals(why, why, source.heal(CLIENT_ID, PHONE_AGENT).why)
            assertEquals(why, requests, http.calls.size)
            assertEquals("$why: the read was claimed first", "not read (read in the last day)", source.heal(CLIENT_ID, PHONE_AGENT).why)
            assertEquals(why, requests, http.calls.size)
            assertFalse(why, health.stopped(VK))
            assertEquals("$why: a bundled alternate or nothing, never a guess", null, source.next(CLIENT_ID, BUNDLED_ID, PHONE_AGENT))
        }
    }

    @Test
    fun `a 429 or a bot check on the page or the script stops VK, and nothing is read while it is stopped`() = runTest {
        listOf(
            vk(page = failure(SiteExtractionFailure.HTTP_STATUS, 429)),
            vk(page = failure(SiteExtractionFailure.BOT_CHECK, 403)),
            vk(script = { _, _ -> failure(SiteExtractionFailure.RATE_LIMITED, 429) }),
        ).forEach { http ->
            val health = SiteHealth { now }
            val source = ValueSource(health, VkClientSource(http))
            source.rejected(bundled(source, CLIENT_ID))
            assertNull(source.heal(CLIENT_ID, PHONE_AGENT).candidate)
            assertTrue(health.stopped(VK))
            val asked = http.calls.size
            source.rejected(bundled(source, API_VERSION))
            assertEquals("not read (bot check or 429)", source.heal(API_VERSION, PHONE_AGENT).why)
            assertEquals(asked, http.calls.size)
        }
    }

    @Test
    fun `each value is one strict match - VK's export of the app ID and its nearby value, the version after API_VERSION`() = runTest {
        val snippet = fixture("vk-master-kit/script/web-client-snippet.js")
        val export = "n.d(t,{VK_WEB_VKVIDEO_APP_ID:()=>Ab,FIXTURE:()=>o});"
        val clientIds = listOf(
            fixture("vk-master-kit/script/common.js") to "100000",
            "$export const Ab=0x186a0;" to "100000",
            "$export const Ab=0x186A0;" to "100000",
            "$export var o=1,Ab=100000,c=2;" to "100000",
            "n.d(t,{VK_WEB_VKVIDEO_APP_ID:()=>Ab}) const Ab=0x186a0" to "100000",
            "$export const Ab=0x186a0;$export const Ab=100000;" to "100000",
            "$export const Ab=0x186a0;n.d(t,{VK_WEB_VKVIDEO_APP_ID:()=>Cd}); const Cd=500000;" to "more than one value",
            snippet to "no value",
            fixture("vk-master-kit/script/error-monitoring.js") to "no value",
            fixture("vk-master-kit/script/core-spa.js") to "no value",
            "$export${" ".repeat(601)}const Ab=0x186a0;" to "no value",
            "n.d(t,{xVK_WEB_VKVIDEO_APP_ID:()=>Ab}); const Ab=0x186a0;" to "no value",
            "n.d(t,{a.VK_WEB_VKVIDEO_APP_ID:()=>Ab}); const Ab=0x186a0;" to "no value",
            "n.d(t,{VK_WEB_VKVIDEO_APP_ID:Ab}); const Ab=0x186a0;" to "no value",
            "n.d(t,{VK_WEB_VKVIDEO_APP_ID:()=>Ab;}); const Ab=0x186a0;" to "no value",
            "n.d(t,{VK_WEB_APP_ID:()=>Ab}); const Ab=0x186a0;" to "no value",
            "$export const Bc=0x186a0;" to "no value",
            "$export const Abc=0x186a0;" to "no value",
            "$export;Ab=0x186a0;" to "no value",
            "$export const Ab=\"100000\";" to "no value",
            "$export const Ab=0x186a0g;" to "no value",
            "$export const Ab=0x123456789;" to "no value",
            "$export const Ab=0x;" to "no value",
            "$export const Ab=100000.5;" to "no value",
            "$export const Ab=12345;" to "no value",
            "$export const Ab=12345678901;" to "no value",
            "$export const Ab=012345;" to "no value",
            "$export const Ab=0x0;" to "not the value's pattern",
            "$export const Ab=0xffffffff;" to "4294967295",
        )
        clientIds.forEach { (text, expected) ->
            val read = heal(vk(scripts = mapOf("common" to text)), CLIENT_ID)
            assertEquals(text.take(120), expected, read.candidate?.value ?: read.why)
        }
        val versions = listOf(
            snippet to "no value",
            fixture("vk-master-kit/script/common.js") to "5.000",
            "n.d(t,{API_VERSION:()=>\"5.000\"})" to "5.000",
            "{API_VERSION:\"5.000\"}" to "5.000",
            "n.d(t,{FIXTURE:()=>o,API_VERSION:()=>Wt});let Wt=\"5.000\"" to "5.000",
            "n.d(t,{API_VERSION:()=>Wt})var Wt=\"5.000\"" to "5.000",
            "n.d(t,{API_VERSION:()=>Wt});const Wt=\"5.000\"" to "5.000",
            "{API_VERSION:()=>\"5.000\"};{API_VERSION:\"5.000\"}" to "5.000",
            "{API_VERSION:()=>\"5.000\"};{API_VERSION:()=>\"5.001\"}" to "more than one value",
            "n.d(t,{API_VERSION:()=>Wt,FIXTURE:()=>o});let Wt=\"5.000\"" to "no value",
            "n.d(t,{API_VERSION:()=>Wt});let Xy=\"5.000\"" to "no value",
            "n.d(t,{API_VERSION:()=>Wt});let Wt=5.000" to "no value",
            "{API_VERSION:()=>\"6.000\"}" to "no value",
            "{MIN_API_VERSION:()=>\"5.111\"}" to "no value",
            "{a.API_VERSION:()=>\"5.111\"}" to "no value",
            "{API_VERSION:()=>\"5.000.1\"}" to "no value",
            "{API_VERSION:()=>\"5.00001\"}" to "no value",
            "{API_VERSION:()=>'5.000'}" to "no value",
            "{apiVersion:\"5.000\",version:\"5.000\"}" to "no value",
        )
        versions.forEach { (text, expected) ->
            val read = heal(vk(scripts = mapOf("common" to text)), API_VERSION)
            assertEquals(text.take(120), expected, read.candidate?.value ?: read.why)
        }
    }

    @Test
    fun `only its two values are read - never the secret, never a look-alike kind - and with no request`() = runTest {
        val http = vk()
        val reader = VkClientSource(http)
        val secret = ValueKind(VK, "clientSecret", Regex("[A-Za-z]{8,64}"), Regex("clientSecret:\"([A-Za-z]+)\""))
        val lookAlike = ValueKind(VK, CLIENT_ID.name, Regex("[0-9]{1,12}"), Regex("clientId:([0-9]+)"), maxBytes = ValueKind.MAX_BYTES)
        val otherSite = ValueKind(XLadder.SITE_ID, API_VERSION.name, API_VERSION.pattern, API_VERSION.anchor, publicVersion = true)
        listOf(secret, lookAlike, otherSite).forEach { kind ->
            val refused = ExtractorHttpResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL, null, listOf("not a value this source reads"))
            assertEquals(kind.toString(), refused, reader.read(kind, PHONE_AGENT, ValueKind.MAX_BYTES))
        }
        val source = ValueSource(health, reader)
        source.rejected(checkNotNull(source.value(secret, "FIXTUREsecret")))
        assertEquals("read failed (UNSUPPORTED_URL: not a value this source reads)", source.heal(secret, PHONE_AGENT).why)
        assertEquals(0, http.calls.size)
        assertEquals(listOf(CLIENT_ID, API_VERSION), VkClientSource.KINDS)
        assertEquals(listOf("clientId", "apiVersion"), VkClientSource.KINDS.map { it.name })
        assertEquals(listOf(false, true), VkClientSource.KINDS.map { it.publicVersion })
    }

    @Test
    fun `once a day per value, memory only - a restart reads nothing more that day, the next day reads again`() = runTest {
        val http = vk()
        val source = ValueSource(health, VkClientSource(http))
        source.rejected(bundled(source, CLIENT_ID))
        assertEquals("100000", source.heal(CLIENT_ID, PHONE_AGENT).candidate?.value)
        source.rejected(bundled(source, API_VERSION))
        assertEquals("5.000", source.heal(API_VERSION, PHONE_AGENT).candidate?.value)
        assertEquals("both values on one day: four requests", 2 * VkClientSource.REQUESTS_PER_READ, http.calls.size)

        val restarted = ValueSource(SiteHealth(file) { now }, VkClientSource(http))
        restarted.rejected(bundled(restarted, CLIENT_ID))
        assertEquals("not read (read in the last day)", restarted.heal(CLIENT_ID, PHONE_AGENT).why)
        assertEquals(4, http.calls.size)

        now += SiteHealth.DAY_MILLIS
        restarted.rejected(bundled(restarted, CLIENT_ID))
        assertEquals("100000", restarted.heal(CLIENT_ID, PHONE_AGENT).candidate?.value)
        assertEquals(6, http.calls.size)
        assertFalse(checkNotNull(file.text).contains("100000"))
    }

    @Test
    fun `the source takes only a vkvideo page and shows no address or value`() {
        listOf(
            "https://vk.com/", "http://vkvideo.ru/", "https://vkvideo.ru", "https://vkvideo.ru.example/",
            "https://m.vkvideo.ru/", "https://vkvideo.ru/@FIXTURE", "https://vkvideo.ru/a b", "https://vkvideo.ru/a/b",
        ).forEach { page ->
            assertThrows(page, IllegalArgumentException::class.java) { VkClientSource(vk(), page) }
        }
        VkClientSource(vk(), "https://vkvideo.ru/video-100000_400000001")
        assertEquals("VkClientSource(vkvideo.ru)", VkClientSource(vk()).toString())
        assertEquals("vkvideo-script", VkClientSource(vk()).id)
        assertTrue(SiteHealth.WORD.matches(VkClientSource.ID))
        assertEquals("https://vkvideo.ru/", VkClientSource.PAGE)
    }

    @Test
    fun `the kit's fixtures are made up - FIXTURE markers, no network address, no long hex, A's snippet copied as is`() {
        FILES.forEach { name ->
            val text = fixture("vk-master-kit/$name")
            assertTrue(name, text.contains("FIXTURE"))
            assertFalse(name, IPV4.containsMatchIn(text))
            assertFalse(name, LONG_HEX.containsMatchIn(text))
            assertFalse(name, text.contains("access_token"))
        }
        val snippet = fixture("vk-master-kit/script/web-client-snippet.js")
        assertEquals("A's snippet, as copied (185 bytes)", 185, snippet.toByteArray(Charsets.UTF_8).size)
        assertTrue(snippet.contains("clientId:100000,clientSecret:\"FIXTUREsecretFIXTURE\",authClientId:200000"))
        assertTrue("A writes an assignment, not API_VERSION:", snippet.contains("API_VERSION=\"5.000\""))
    }

    private suspend fun heal(http: FakeHttp, kind: ValueKind, agent: String? = PHONE_AGENT): ValueSource.Read {
        val source = ValueSource(SiteHealth { now }, VkClientSource(http))
        source.rejected(bundled(source, kind))
        return source.heal(kind, agent)
    }

    private fun bundled(source: ValueSource, kind: ValueKind): ValueSource.Candidate =
        checkNotNull(source.value(kind, if (kind === CLIENT_ID) BUNDLED_ID else BUNDLED_VERSION))

    /** A made-up vkvideo.ru: the page and the kit's three scripts (by name), as [script] answers them. */
    private fun vk(
        page: ExtractorHttpResult = html(fixture(PAGE_FIXTURE)),
        scripts: Map<String, String> = emptyMap(),
        script: (String, String) -> ExtractorHttpResult = { url, body ->
            ExtractorHttpResult.Success(200, body, url, "application/javascript; charset=utf-8", details = listOf("FIXTURE"))
        },
    ): FakeHttp = FakeHttp { url ->
        val chunk = SCRIPTS.keys.firstOrNull { url.contains("/$it.") }
        when {
            url == VkClientSource.PAGE -> page
            chunk != null -> script(url, scripts[chunk] ?: fixture("vk-master-kit/script/${SCRIPTS.getValue(chunk)}"))
            else -> failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }
    }

    private fun page(vararg tags: String): String =
        "<!DOCTYPE html><!-- FIXTURE --><html><head><title>FIXTURE</title>${tags.joinToString("")}</head><body></body></html>"

    private fun html(body: String, finalUrl: String = VkClientSource.PAGE, type: String = "text/html; charset=utf-8"): ExtractorHttpResult =
        ExtractorHttpResult.Success(200, body, finalUrl, type)

    private fun failure(reason: SiteExtractionFailure, status: Int? = null): ExtractorHttpResult =
        ExtractorHttpResult.Failure(reason, status)

    private class Call(val url: String, val headers: Map<String, String>, val maxBytes: Long)

    /** Records each GET and answers it as [answer] says; a body over the cap is refused as the app's client does. */
    private class FakeHttp(private val answer: (String) -> ExtractorHttpResult) : ExtractorHttpClient {
        val calls = mutableListOf<Call>()
        val urls: List<String> get() = calls.map { it.url }

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            calls += Call(url, headers, maxBodyBytes)
            val got = answer(url)
            return if (got is ExtractorHttpResult.Success && got.body.toByteArray(Charsets.UTF_8).size > maxBodyBytes) {
                ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, got.statusCode)
            } else {
                got
            }
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            error("no POST")
    }

    private class MemoryFile(var text: String? = null) : OwnSolverTextFile {
        override fun read(): String? = text

        override fun write(text: String): Boolean {
            this.text = text
            return true
        }
    }

    private companion object {
        const val VK = SiteHealth.VK
        const val START = 1_791_234_567_890L
        const val PHONE_AGENT =
            "Mozilla/5.0 (Linux; Android 14; FIXTURE) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.6668.100 Mobile Safari/537.36"
        const val DESKTOP_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
        const val BUNDLED_ID = "12345678"
        const val BUNDLED_VERSION = "5.900"
        const val PAGE_FIXTURE = "vk-master-kit/page/vkvideo-page.html"
        const val COMMON = "https://st1-23.vkvideo.ru/dist/web/chunks/common.FIXTUREc0mm0n.js"

        val CLIENT_ID = VkClientSource.CLIENT_ID
        val API_VERSION = VkClientSource.API_VERSION
        val SCRIPTS = mapOf("error_monitoring" to "error-monitoring.js", "common" to "common.js", "core_spa" to "core-spa.js")
        val FILES = listOf(
            "README.md", "page/vkvideo-page.html", "script/error-monitoring.js", "script/common.js",
            "script/core-spa.js", "script/web-client-snippet.js",
        )
        val IPV4 = Regex("\\b[0-9]{1,3}(\\.[0-9]{1,3}){3}\\b")
        val LONG_HEX = Regex("[0-9A-Fa-f]{32,}")
    }
}
