package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R9: a signed, schema-checked, data-only recipe config. A good config changes recipe data and
 * the engine reads with it; a bad, unsigned, older or expired config is ignored and the bundled
 * recipes keep working; the config is asked only from GitHub, without cookies, at most once a day.
 */
class RemoteRecipesTest {
    private val keys = keyPair()

    @Test
    fun `a signed config changes recipe data only, and the engine reads with it`() = runTest {
        val identity = identify()
        val embed = "https://www.instagram.com/reel/${identity.contentId}/embed/captioned/"
        val renamed = fixture().replace("\\\"video_url\\\"", "\\\"video_src\\\"")
        val config = config(
            sites = """{"instagram":{"endpoint":"https://www.instagram.com/reel/{id}/embed/captioned/",""" +
                """"headers":{"accept-language":"my-MM,my;q=0.9"},""" +
                """"media":[{"path":["video_src"],"mime":"video/mp4","metaPath":["dimensions"]}]}}""",
        )
        val http = FakeExtractorHttpClient(
            responses = mapOf(
                CONFIG_URL to ExtractorHttpResult.Success(200, sign(keys, config), CONFIG_URL, "text/plain"),
                embed to ExtractorHttpResult.Success(200, renamed, embed, "text/html"),
            ),
        )
        val remote = remote(http)
        val capture = CountingCapture()

        val result = engine(http, remote, capture).extract(contractRequest(REEL, identity, cookie = SESSION))

        val success = result as MasterResult.Success
        val row = success.result.candidates.single()
        assertEquals(MasterStage.CONTRACT, success.stage)
        assertTrue(row.mediaUrl, "embed_prog640.mp4" in row.mediaUrl)
        assertEquals(640, row.width)
        assertTrue("${success.result.details}", success.result.details.any { "1 files from the key table" in it })
        assertEquals(0, capture.calls)
        assertEquals(1L, remote.version)
        assertEquals(listOf(CONFIG_URL, embed), http.requestedUrls)
        assertNull("the config is asked without cookies", http.requestedHeaders[0]["Cookie"])
        assertEquals(RemoteRecipes.MAX_ENVELOPE_BYTES, http.requestedBodyLimits[0])
        assertEquals("my-MM,my;q=0.9", http.requestedHeaders[1]["Accept-Language"])
        assertNull(http.requestedHeaders[1]["Cookie"])
        val recipe = checkNotNull(remote.recipeOf("instagram"))
        val bundled = ContractRecipes.INSTAGRAM
        assertEquals(bundled.answerHosts, recipe.answerHosts)
        assertEquals(bundled.maxBytes, recipe.maxBytes)
        assertEquals(bundled.cookieDomain, recipe.cookieDomain)
        assertEquals(bundled.photoMarkers, recipe.photoMarkers)
        assertEquals(bundled.wallPaths, recipe.wallPaths)
        assertEquals(bundled.headers["Referer"], recipe.headers["Referer"])
        assertSame(ContractRecipes.X, remote.recipeOf("x"))
    }

    @Test
    fun `a bad or unsigned config is ignored and the bundled recipes keep working`() = runTest {
        val good = config(sites = """{"instagram":{"agent":"Config agent"}}""")
        val signed = sign(keys, good)
        val envelopes = listOf(
            """{"alg":"ES256","payload":${quote(good)}}""",
            """{"alg":"ES256","payload":${quote(good)},"signature":""}""",
            sign(keyPair(), good),
            signed.replace("Config agent", "Other agent"),
            signed.replace("ES256", "none"),
            signed.replaceFirst("{", """{"extra":1,"""),
            "<html>not a config</html>",
        ) + listOf(
            """{"instagram":{"answerHosts":["evil.example"]}}""",
            """{"instagram":{"cookieDomain":"example.com"}}""",
            """{"instagram":{"maxBytes":1}}""",
            """{"instagram":{"itemNumber":"(a+)+$"}}""",
            """{"instagram":{"photoMarkers":[]}}""",
            """{"instagram":{"headers":{"Cookie":"sessionid=1"}}}""",
            """{"instagram":{"headers":{"User-Agent":"bot"}}}""",
            """{"instagram":{"headers":{"Referer":"a\r\nX: b"}}}""",
            """{"instagram":{"endpoint":"https://evil.example/p/{id}/"}}""",
            """{"instagram":{"endpoint":"http://www.instagram.com/p/{id}/"}}""",
            """{"instagram":{"endpoint":"https://user@www.instagram.com/p/{id}/"}}""",
            """{"instagram":{"endpoint":"https://www.instagram.com/p/{id}/{cookie}"}}""",
            """{"instagram":{"media":[{"path":["a","b","c","d","e","f","g","h","i","j","k","l","m"]}]}}""",
            """{"instagram":{"media":[{"path":["video_url"],"mime":"text/html"}]}}""",
            """{"instagram":{"media":[{"path":["video_url"],"script":"x"}]}}""",
            """{"instagram":{"wallPaths":["accounts"]}}""",
            """{"instagram":"x"}""",
            """{"vine":{}}""",
        ).map { sign(keys, config(sites = it)) } + listOf(
            config(schema = "2"),
            config(version = "0"),
            config(version = "1.5"),
            config(expires = "2026-12-31"),
            config(expires = "2027-02-30"),
            config(extra = ""","code":"x""""),
            config(sites = """{"instagram":{"agent":"${"a".repeat(RemoteRecipeConfig.MAX_CHARS)}"}}"""),
        ).map { sign(keys, it) }

        envelopes.forEachIndexed { index, envelope ->
            val remote = remote(null)
            val outcome = remote.accept(envelope)
            assertTrue("case $index: $outcome", "ignored" in outcome)
            assertEquals("case $index", 0L, remote.version)
            assertSame("case $index", ContractRecipes.ALL, remote.recipes())
        }
        // Asked live, a bad config changes nothing: the bundled recipe still answers.
        val identity = identify()
        val embed = "https://www.instagram.com/p/${identity.contentId}/embed/captioned/"
        val http = FakeExtractorHttpClient(
            responses = mapOf(
                CONFIG_URL to ExtractorHttpResult.Success(200, envelopes[2], CONFIG_URL, "text/plain"),
                embed to ExtractorHttpResult.Success(200, fixture(), embed, "text/html"),
            ),
        )
        val remote = remote(http)
        val result = engine(http, remote).extract(contractRequest(REEL, identity))
        assertEquals(MasterStage.CONTRACT, (result as MasterResult.Success).stage)
        assertEquals(0L, remote.version)
        assertEquals(listOf(CONFIG_URL, embed), http.requestedUrls)
    }

    @Test
    fun `a config never rolls back, and an expired one falls back to the bundled recipes`() {
        var now = NOW
        val remote = RemoteRecipes(null, CONFIG_URL, signature(), ContractRecipes.ALL) { now }
        fun agent(version: Int, expires: String = "2027-01-20") =
            sign(keys, config(version = "$version", expires = expires, sites = """{"x":{"agent":"agent $version"}}"""))

        assertEquals("recipe config 2 applied", remote.accept(agent(2)))
        assertTrue(remote.accept(agent(1)).endsWith("is not newer"))
        assertTrue(remote.accept(agent(2)).endsWith("is not newer"))
        assertEquals("agent 2", remote.recipeOf("x")?.agent)
        assertEquals("recipe config 3 applied", remote.accept(agent(3)))
        assertEquals(3L, remote.version)

        now += 10 * RemoteRecipes.DAY_MILLIS
        assertEquals(0L, remote.version)
        assertSame(ContractRecipes.ALL, remote.recipes())
        assertTrue(remote.accept(agent(4)).endsWith("expired"))
        assertEquals("recipe config 5 applied", remote.accept(agent(5, expires = "2027-12-31")))
    }

    @Test
    fun `the config is asked only from GitHub, without cookies, at most once a day`() = runTest {
        var now = NOW
        var answer: ExtractorHttpResult = ExtractorHttpResult.Failure(
            com.alal.yft.extractor.api.SiteExtractionFailure.HTTP_STATUS, 404,
        )
        val http = FakeExtractorHttpClient(getResponder = { _, _ -> answer })
        val remote = RemoteRecipes(http, CONFIG_URL, signature(), ContractRecipes.ALL) { now }

        assertTrue(remote.active)
        assertEquals("recipe config unanswered: HTTP_STATUS", remote.refreshIfDue())
        assertNull("a failed ask waits an hour", remote.refreshIfDue())
        now += RemoteRecipes.RETRY_MILLIS
        answer = ExtractorHttpResult.Success(200, sign(keys, config()), CONFIG_URL, "text/plain")
        assertEquals("recipe config 1 applied", remote.refreshIfDue())
        now += RemoteRecipes.DAY_MILLIS - 1
        assertNull("once a day", remote.refreshIfDue())
        now += 1
        answer = ExtractorHttpResult.Success(200, sign(keys, config(version = "2")), "https://evil.example/c", "text/plain")
        assertEquals("recipe config ignored: the answer left GitHub", remote.refreshIfDue())
        assertEquals(1L, remote.version)
        assertEquals(List(3) { CONFIG_URL }, http.requestedUrls)
        assertTrue(http.requestedHeaders.all { "Cookie" !in it })

        // No key, a bad key, or an address that is not https on GitHub: nothing is ever asked.
        val quiet = FakeExtractorHttpClient()
        listOf(
            RemoteRecipes(quiet, CONFIG_URL, ""),
            RemoteRecipes(quiet, CONFIG_URL, "zz"),
            RemoteRecipes(quiet, CONFIG_URL, keyPair("secp384r1").public.encoded.toHex()),
            RemoteRecipes(quiet, "http://raw.githubusercontent.com/o/r/b/c.json", keys.public.encoded.toHex()),
            RemoteRecipes(quiet, "https://example.com/c.json", keys.public.encoded.toHex()),
        ).forEach {
            assertFalse(it.active)
            assertNull(it.refreshIfDue())
            assertSame(ContractRecipes.ALL, it.recipes())
        }
        assertTrue(quiet.requestedUrls.isEmpty())
        // Without a config, the engine asks only the site, exactly as before R9.
        val identity = identify()
        val embed = "https://www.instagram.com/p/${identity.contentId}/embed/captioned/"
        val site = FakeExtractorHttpClient(
            responses = mapOf(embed to ExtractorHttpResult.Success(200, fixture(), embed, "text/html")),
        )
        val result = engine(site, RemoteRecipes(site, CONFIG_URL, "")).extract(contractRequest(REEL, identity))
        assertEquals(MasterStage.CONTRACT, (result as MasterResult.Success).stage)
        assertEquals(listOf(embed), site.requestedUrls)
    }

    @Test
    fun `the signing script's output verifies, and the schema and example match the reader`() {
        val envelope = checkNotNull(javaClass.getResource("/recipes/openssl-signed.json")).readText()
        val remote = RemoteRecipes(null, CONFIG_URL, RemoteRecipeSignature.of(OPENSSL_PUBLIC_KEY), ContractRecipes.ALL) { NOW }
        assertEquals("recipe config 1 applied", remote.accept(envelope))
        assertEquals("en-US,en;q=0.9", remote.recipeOf("instagram")?.headers?.get("Accept-Language"))
        assertTrue(remote(null).accept(envelope).contains("not signed"))

        val docs = File(checkNotNull(System.getProperty("yft.recipeDocs")) { "recipe docs unset" })
        val example = File(docs, "master-recipes.example.json").readText()
        assertTrue(RemoteRecipeConfig.parse(example, ContractRecipes.ALL) is RemoteRecipeConfig.Parsed.Valid)
        val schema = BoundedJsonParser.parse(File(docs, "master-recipes.schema.json").readText()) as JsonValue.Object
        fun obj(value: JsonValue?, vararg path: String): JsonValue.Object =
            path.fold(value) { node, key -> (node as JsonValue.Object).entries[key] } as JsonValue.Object
        fun enum(value: JsonValue?): Set<String> =
            ((value as JsonValue.Object).entries["enum"] as JsonValue.Array).items.map { (it as JsonValue.Text).value }.toSet()
        assertEquals(RemoteRecipeConfig.TOP_KEYS, obj(schema, "properties").entries.keys)
        assertEquals(ContractRecipes.ALL.map { it.site }.toSet(), obj(schema, "properties", "sites", "properties").entries.keys)
        val defs = obj(schema, "\$defs")
        assertEquals(RemoteRecipeConfig.SITE_KEYS, obj(defs, "site", "properties").entries.keys)
        assertEquals(RemoteRecipeConfig.MEDIA_KEYS, obj(defs, "media", "properties").entries.keys)
        assertEquals(RemoteRecipeConfig.FIELD_KEYS, obj(defs, "fields", "properties").entries.keys)
        assertEquals(RemoteRecipeConfig.HEADERS.toSet(), enum(obj(defs, "site", "properties", "headers").entries["propertyNames"]))
        assertEquals(RemoteRecipeConfig.MIMES, enum(obj(defs, "media", "properties").entries["mime"]))
        assertEquals(ContractOrder.entries.map { it.name }.toSet(), enum(obj(defs, "media", "properties").entries["order"]))
    }

    private fun remote(http: FakeExtractorHttpClient?) =
        RemoteRecipes(http, CONFIG_URL, signature(), ContractRecipes.ALL) { NOW }

    private fun signature() = checkNotNull(RemoteRecipeSignature.of(keys.public.encoded.toHex()))

    private fun engine(http: FakeExtractorHttpClient, remote: RemoteRecipes, capture: CountingCapture = CountingCapture()) =
        MasterFallbackEngine(
            RecordingValidator(), capture, MasterPolicy(enabled = true), contracts = SiteContracts(http, remote),
        )

    private fun identify(): SitePageIdentity =
        checkNotNull(InstagramExtractor(FakeExtractorHttpClient()).identify(REEL))

    private companion object {
        const val CONFIG_URL =
            "https://raw.githubusercontent.com/Alalkipgen/YFT/spike/master-extractor-backup/recipes/master-recipes.json"
        const val REEL = "https://www.instagram.com/reel/C9fixtREEL1/"
        const val SESSION = "csrftoken=csrfFixture; sessionid=555%3Asecret; ds_user_id=555"

        /** The public half of a throwaway key (private half deleted) that signed openssl-signed.json. */
        const val OPENSSL_PUBLIC_KEY =
            "3059301306072a8648ce3d020106082a8648ce3d030107034200046b78f5bf858237457d77f1734cdfc4c4" +
                "d8a54aa5c2b2b230f4267dcff33a0de432a9d206138ddef013ad3a4356e61407ec8393a7b3dc22211610e8" +
                "f8244be664"

        fun fixture() = Fixtures.read("instagram/embed_captioned.html")

        fun config(
            schema: String = "1",
            version: String = "1",
            expires: String = "2027-12-31",
            sites: String = """{"instagram":{"agent":"Config agent"}}""",
            extra: String = "",
        ) = """{"schema":$schema,"version":$version,"expires":"$expires","sites":$sites$extra}"""

        fun keyPair(curve: String = "secp256r1"): KeyPair =
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

        fun sign(keys: KeyPair, payload: String): String {
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(keys.private)
                update(payload.toByteArray(Charsets.UTF_8))
                sign()
            }
            return """{"alg":"ES256","payload":${quote(payload)},"signature":"${signature.toHex()}"}"""
        }

        fun quote(text: String): String = buildString {
            append('"')
            text.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(char)
                }
            }
            append('"')
        }

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}