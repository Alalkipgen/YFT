package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.recipes.SignedRecipes.CONFIG_URL
import com.alal.yft.extractor.master.recipes.SignedRecipes.payload
import com.alal.yft.extractor.master.recipes.SignedRecipes.sign
import com.alal.yft.extractor.master.recipes.SignedRecipes.toHex
import com.alal.yft.extractor.master.social.InstagramLadder
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S4 (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): R9's signed config hosted and live. Its
 * `rungsOff` turns ladder rungs off by name (anything else rejects the whole config); its status
 * is one line of fixed words; the census screen asks on demand; the ask runs outside the caller's
 * lookup (never one of its asks, never under its rules); every committed config parses as the
 * publish workflow composes it, and the workflow helper's signed output applies. Throwaway keys
 * only; no live calls.
 */
class RemoteRecipesS4Test {
    @Test
    fun `rungsOff turns the named rungs off, and anything else rejects the whole config`() {
        val off = SignedRecipes.applied(payload(rungsOff = listOf("F1", "probe", "I3")))
        assertEquals(setOf("F1", "probe", "I3"), off.rungsOff())
        assertEquals("the recipes stay the bundled ones", ContractRecipes.ALL, off.recipes())
        assertEquals(RemoteRecipeConfig.RUNGS.toSet(), SignedRecipes.applied(payload(rungsOff = RemoteRecipeConfig.RUNGS)).rungsOff())
        assertTrue(SignedRecipes.applied(payload(rungsOff = emptyList())).rungsOff().isEmpty())
        assertTrue(SignedRecipes.applied(payload()).rungsOff().isEmpty())

        val bad = mapOf(
            """"F9"""" to "rungsOff: unknown rung F9",
            """"f1"""" to "rungsOff: unknown rung f1",
            """"F1","F1"""" to "rungsOff names a rung twice",
            """""""" to "rungsOff must be a text of 1-512 characters",
            "1" to "rungsOff must be a text of 1-512 characters",
            (RemoteRecipeConfig.RUNGS + "F1").joinToString(",") { "\"$it\"" } to null,
        )
        for ((items, reason) in bad) {
            val remote = RemoteRecipes(null, CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { NOW }
            val text = """{"schema":1,"version":1,"expires":"2027-12-31","sites":{},"rungsOff":[$items]}"""
            val outcome = remote.accept(sign(SignedRecipes.keys, text))
            assertTrue("$items: $outcome", outcome.startsWith("recipe config ignored: "))
            reason?.let { assertEquals(items, "recipe config ignored: $it", outcome) }
            assertEquals(items, 0L, remote.version)
            assertTrue(items, remote.rungsOff().isEmpty())
        }
        listOf("\"F1\"", "{}", "null").forEach { value ->
            val remote = RemoteRecipes(null, CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { NOW }
            val text = """{"schema":1,"version":1,"expires":"2027-12-31","sites":{},"rungsOff":$value}"""
            assertTrue(value, remote.accept(sign(SignedRecipes.keys, text)).startsWith("recipe config ignored: "))
            assertTrue(value, remote.rungsOff().isEmpty())
        }
        // The next config replaces the rungs off as a whole; an expired one turns none off.
        var now = NOW
        val remote = RemoteRecipes(null, CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { now }
        remote.accept(sign(SignedRecipes.keys, payload(version = 1, expires = "2027-01-20", rungsOff = listOf("T1"))))
        assertEquals(setOf("T1"), remote.rungsOff())
        remote.accept(sign(SignedRecipes.keys, payload(version = 2, expires = "2027-01-20")))
        assertTrue(remote.rungsOff().isEmpty())
        remote.accept(sign(SignedRecipes.keys, payload(version = 3, expires = "2027-01-20", rungsOff = listOf("X1"))))
        assertEquals(setOf("X1"), remote.rungsOff())
        now += 10 * RemoteRecipes.DAY_MILLIS
        assertTrue(remote.rungsOff().isEmpty())
        // Without a config (no key: every build but the social-ladder one) nothing is ever off.
        assertTrue(SiteContracts(FakeExtractorHttpClient()).rungsOff().isEmpty())
        assertTrue(SiteContracts(FakeExtractorHttpClient(), off).rungsOff().contains("probe"))
    }

    @Test
    fun `the status line says which recipes are in use, in fixed words, never a link`() = runTest {
        val noKey = "recipes: bundled (no key in this build)"
        assertEquals(noKey, RemoteRecipes(FakeExtractorHttpClient(), CONFIG_URL, "").status())
        assertEquals(noKey, RemoteRecipes(FakeExtractorHttpClient(), "https://example.com/c.json", SignedRecipes.keys.public.encoded.toHex()).status())
        assertEquals(noKey, SiteContracts(FakeExtractorHttpClient()).status())

        var now = NOW
        var answer: ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        var slow = false
        val http = object : ExtractorHttpClient {
            val asked = mutableListOf<String>()

            override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
                asked += url
                if (slow) delay(RemoteRecipes.ASK_MILLIS + 1)
                return answer
            }

            override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long) =
                error("never posted")
        }
        val remote = RemoteRecipes(http, CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { now }
        val statuses = mutableListOf<String>()
        fun step(expected: String?, ask: String?) {
            assertEquals(expected, ask)
            statuses += remote.status()
        }
        statuses += remote.status()
        step("recipe config unanswered: HTTP_STATUS", remote.refreshIfDue())
        answer = ExtractorHttpResult.Success(200, sign(SignedRecipes.keys, payload(version = 1, expires = "2027-01-20")), CONFIG_URL, "text/plain")
        step("recipe config 1 applied", remote.refreshNow())
        step("recipe config ignored: version 1 is not newer", remote.refreshNow())
        answer = ExtractorHttpResult.Success(200, sign(SignedRecipes.keyPair(), payload(version = 2)), CONFIG_URL, "text/plain")
        step("recipe config ignored: not signed by this build's key", remote.refreshNow())
        slow = true
        step("recipe config unanswered: timed out", remote.refreshNow())
        slow = false
        answer = ExtractorHttpResult.Success(200, sign(SignedRecipes.keys, payload(version = 3, expires = "2027-01-20")), CONFIG_URL, "text/plain")
        step("recipe config 3 applied", remote.refreshNow())
        answer = ExtractorHttpResult.Success(200, sign(SignedRecipes.keys, payload(version = 2)), CONFIG_URL, "text/plain")
        step("recipe config ignored: version 2 is not newer", remote.refreshNow())
        answer = ExtractorHttpResult.Success(200, "<html>a page</html>", "https://example.com/elsewhere", "text/html")
        step("recipe config ignored: the answer left GitHub", remote.refreshNow())
        now += 10 * RemoteRecipes.DAY_MILLIS
        statuses += remote.status()

        assertEquals(
            listOf(
                "recipes: bundled (not asked yet)",
                "recipes: bundled (no answer: http status)",
                "recipes: remote version 1, signed",
                "recipes: remote version 1, signed",
                "recipes: remote version 1, signed (last check: not signed by this build's key)",
                "recipes: remote version 1, signed (last check: no answer in time)",
                "recipes: remote version 3, signed",
                "recipes: remote version 3, signed (last check: an older version 2)",
                "recipes: remote version 3, signed (last check: the answer left GitHub)",
                "recipes: bundled (version 3 expired)",
            ),
            statuses,
        )
        assertEquals(List(8) { CONFIG_URL }, http.asked)
        statuses.forEach { line -> assertFalse(line, "://" in line || "githubusercontent" in line) }
        // The census screen's ask on open, on a fresh config: what it said.
        val fresh = RemoteRecipes(FakeExtractorHttpClient(), CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { NOW }
        assertEquals("recipe config unanswered: HTTP_STATUS", fresh.refreshNow())
        assertEquals("recipes: bundled (no answer: http status)", SiteContracts(FakeExtractorHttpClient(), fresh).status())
    }

    @Test
    fun `refreshNow asks whatever the interval, and the next ask waits six hours after an answer`() = runTest {
        var now = NOW
        val envelope = sign(SignedRecipes.keys, payload())
        val http = FakeExtractorHttpClient(getResponder = { url, _ -> ExtractorHttpResult.Success(200, envelope, url, "text/plain") })
        val remote = RemoteRecipes(http, CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { now }

        assertEquals("recipe config 1 applied", remote.refreshIfDue())
        assertNull(remote.refreshIfDue())
        assertEquals("recipe config ignored: version 1 is not newer", remote.refreshNow())
        assertEquals(2, http.requestedUrls.size)
        now += RemoteRecipes.ANSWERED_MILLIS - 1
        assertNull("an answer waits six hours", remote.refreshIfDue())
        now += 1
        assertEquals("recipe config ignored: version 1 is not newer", remote.refreshIfDue())
        assertEquals(3, http.requestedUrls.size)
        assertEquals(6 * 3_600_000L, RemoteRecipes.ANSWERED_MILLIS)

        // No key, or no client: nothing is asked, on demand either.
        val quiet = FakeExtractorHttpClient()
        assertNull(RemoteRecipes(quiet, CONFIG_URL, "").refreshNow())
        assertNull(RemoteRecipes(null, CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { NOW }.refreshNow())
        assertTrue(quiet.requestedUrls.isEmpty())
    }

    @Test
    fun `the ask runs outside the caller's lookup - never one of its asks, never under its rules`() = runTest {
        val envelope = sign(SignedRecipes.keys, payload(rungsOff = listOf("F1")))
        val http = FakeExtractorHttpClient(getResponder = { url, _ -> ExtractorHttpResult.Success(200, envelope, url, "text/plain") })
        // Main's recorder and route B's client around the config's client, as a ladder's or the app's.
        val remote = RemoteRecipes(RouteBHttpClient(LookupRecorder(http)), CONFIG_URL, SignedRecipes.signature(), ContractRecipes.ALL) { NOW }
        val record = LookupRecord(clock = { NOW })
        // A 429 in the lookup: route B sends nothing new in it; the config's ask is not the lookup's.
        record.record(
            LookupAsk(LookupAsk.GET, "https://example.test/page", null),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            NOW,
        )
        assertTrue(record.rateLimited)
        val before = record.asks().size

        val outcome = withContext(record + RouteBLookup(record)) { SiteContracts(http, remote).also { it.refresh() }.rungsOff() }

        assertEquals(setOf("F1"), outcome)
        assertEquals(1L, remote.version)
        assertEquals(listOf(CONFIG_URL), http.requestedUrls)
        assertNull(http.requestedHeaders.single()["Cookie"])
        assertEquals(before, record.asks().size)
        assertEquals(0, record.masterAsks)
        assertEquals(1, record.mainAsks)
    }

    @Test
    fun `every committed config parses as the workflow composes it, and each changes what its drill needs`() {
        val docs = File(checkNotNull(System.getProperty("yft.recipeDocs")) { "recipe docs unset" })
        val configs = checkNotNull(File(docs, "configs").listFiles { file -> file.name.endsWith(".json") })
            .associate { it.name.removeSuffix(".json") to it.readText() }
        assertEquals(setOf("defaults", "drill-rung-off", "drill-key-change"), configs.keys)
        val parsed = configs.mapValues { (name, text) ->
            val keys = (BoundedJsonParser.parse(text) as JsonValue.Object).entries.keys
            assertTrue("$name: $keys", keys.containsAll(setOf("schema", "sites")))
            assertTrue("$name: version and expires are the workflow's", (keys - setOf("schema", "sites", "rungsOff")).isEmpty())
            val composed = text.trim().removeSuffix("}").trimEnd() + ""","version":1,"expires":"2027-12-31"}"""
            // VK Master plan §9: a committed config is schema 2; v1.0.0's file is its schema-1 projection.
            val schema1 = checkNotNull(RemoteRecipeConfig2.schema1(composed)) { "$name is not a schema-2 config" }
            val result = RemoteRecipeConfig.parse(schema1, ContractRecipes.ALL)
            assertEquals(name, result, RemoteRecipeConfig2.parse(composed, ContractRecipes.ALL))
            assertTrue("$name: $result", result is RemoteRecipeConfig.Parsed.Valid)
            result as RemoteRecipeConfig.Parsed.Valid
        }
        // defaults: the bundled recipes, no rung off (the drill's last step and the safe state).
        assertEquals(ContractRecipes.ALL, parsed.getValue("defaults").recipes)
        assertTrue(parsed.getValue("defaults").rungsOff.isEmpty())
        // drill-rung-off: Instagram's tab-fetch rung off, the recipes as bundled.
        assertEquals(ContractRecipes.ALL, parsed.getValue("drill-rung-off").recipes)
        assertEquals(setOf(InstagramLadder.PROBE_RUNG), parsed.getValue("drill-rung-off").rungsOff)
        // drill-key-change: Facebook's media keys renamed (the key table finds nothing, the open read
        // still finds the files); every other site's recipe as bundled.
        val changed = parsed.getValue("drill-key-change")
        assertTrue(changed.rungsOff.isEmpty())
        val facebook = checkNotNull(changed.recipes.firstOrNull { it.site == "facebook" })
        assertEquals(listOf(listOf("drill_missing_key")), facebook.media.map { it.path })
        assertEquals(ContractRecipes.ALL.filter { it.site != "facebook" }, changed.recipes.filter { it.site != "facebook" })
        val bundledFacebook = checkNotNull(ContractRecipes.ALL.firstOrNull { it.site == "facebook" })
        assertEquals(bundledFacebook.answerHosts, facebook.answerHosts)
        assertEquals(bundledFacebook.endpoint, facebook.endpoint)
    }

    @Test
    fun `the publish helper's signed output applies, the committed key is a key, and the schema lists the reader's rungs`() {
        val envelope = checkNotNull(javaClass.getResource("/recipes/publish-signed.json")).readText()
        val remote = RemoteRecipes(null, CONFIG_URL, RemoteRecipeSignature.of(PUBLISH_PUBLIC_KEY), ContractRecipes.ALL) { NOW }
        assertEquals("recipe config 2 applied", remote.accept(envelope))
        assertEquals(setOf(InstagramLadder.PROBE_RUNG), remote.rungsOff())
        assertEquals(ContractRecipes.ALL, remote.recipes())

        val docs = File(checkNotNull(System.getProperty("yft.recipeDocs")) { "recipe docs unset" })
        val committed = File(docs, "master-recipes.pub.hex").readText().trim()
        assertEquals(182, committed.length)
        assertNotNull("the committed public key is a P-256 key", RemoteRecipeSignature.of(committed))
        val other = RemoteRecipes(null, CONFIG_URL, RemoteRecipeSignature.of(committed), ContractRecipes.ALL) { NOW }
        assertEquals("recipe config ignored: not signed by this build's key", other.accept(envelope))

        val schema = BoundedJsonParser.parse(File(docs, "master-recipes.schema.json").readText()) as JsonValue.Object
        val rungs = ((schema.entries["properties"] as JsonValue.Object).entries["rungsOff"] as JsonValue.Object).entries
        val items = (rungs["items"] as JsonValue.Object).entries["enum"] as JsonValue.Array
        assertEquals(RemoteRecipeConfig.RUNGS, items.items.map { (it as JsonValue.Text).value })
        assertEquals(RemoteRecipeConfig.RUNGS.size.toLong(), (rungs["maxItems"] as JsonValue.Number).text.toLong())
    }

    private companion object {
        /** The public half of a throwaway key (private half deleted) that signed publish-signed.json. */
        const val PUBLISH_PUBLIC_KEY =
            "3059301306072a8648ce3d020106082a8648ce3d0301070342000409a8bde4fdb04a807f821cef44bb917181bc62c393639cf2f7f81cbd975c5776c3bb5193553c501bc7260f08ae617be46b92cf09fe5d1267b3103c0be0352a63"
    }
}
