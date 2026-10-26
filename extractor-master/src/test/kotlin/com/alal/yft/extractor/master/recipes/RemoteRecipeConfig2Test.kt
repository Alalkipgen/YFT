package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK Master plan §9: R9 schema 2 — schema 1 plus site `vk` and rungs V1–V3, read by the unchanged
 * schema-1 reader through two projections; the v1.0.0 file is the schema-1 projection. Most cases
 * lay over a made-up VK stand-in ([VK_RECIPE]) so they never depend on VK's real addresses; the
 * bundled recipes ([ContractRecipes.ALL], VK's own [com.alal.yft.extractor.master.social.VkRecipe]
 * included since wave 2) are checked on their own.
 */
class RemoteRecipeConfig2Test {
    /** The bundled recipes a build without VK's recipe has (schema 1's sites). */
    private val schema1Recipes = ContractRecipes.ALL.filter { it.site != RemoteRecipeConfig2.VK }
    private val bundled = schema1Recipes + VK_RECIPE

    @Test
    fun `a schema-2 config lays the four sites over schema 1's reader and VK over its own recipe`() {
        val text = config(
            sites = """{"instagram":{"agent":"Config agent"},"vk":{"headers":{"Referer":"https://vk.example/"},"titlePaths":[["title"]]}}""",
            rungsOff = """["probe","V2"]""",
        )
        val parsed = RemoteRecipeConfig2.parse(text, bundled) as RemoteRecipeConfig.Parsed.Valid
        assertEquals(4L, parsed.version)
        assertEquals(setOf("probe", "V2"), parsed.rungsOff)
        assertEquals(bundled.map { it.site }, parsed.recipes.map { it.site })
        val vk = parsed.recipes.last()
        assertEquals("https://vk.example/", vk.headers["Referer"])
        assertEquals("application/json", vk.headers["Accept"])
        assertEquals(listOf(listOf("title")), vk.titlePaths)
        assertEquals(VK_RECIPE.endpoint, vk.endpoint)
        assertEquals(VK_RECIPE.answerHosts, vk.answerHosts)
        assertEquals("Config agent", parsed.recipes.single { it.site == "instagram" }.agent)
        assertEquals(schema1Recipes.filter { it.site != "instagram" }, parsed.recipes.filter { it.site != "instagram" && it.site != "vk" })

        // The four sites as the schema-1 reader lays the projection over them.
        val schema1 = RemoteRecipeConfig.parse(checkNotNull(RemoteRecipeConfig2.schema1(text)), schema1Recipes)
            as RemoteRecipeConfig.Parsed.Valid
        assertEquals(schema1.recipes, parsed.recipes.dropLast(1))
        assertEquals(setOf("probe"), schema1.rungsOff)
        assertEquals((schema1.version to schema1.expiresEpochDay), (parsed.version to parsed.expiresEpochDay))

        // Without a VK part VK's recipe is the bundled one; a build without VK reads the four sites.
        val fourSites = config(sites = """{"instagram":{"agent":"Config agent"}}""", rungsOff = """["V1","I3"]""")
        val withVk = RemoteRecipeConfig2.parse(fourSites, bundled) as RemoteRecipeConfig.Parsed.Valid
        assertEquals(VK_RECIPE, withVk.recipes.last())
        val withoutVk = RemoteRecipeConfig2.parse(fourSites, schema1Recipes) as RemoteRecipeConfig.Parsed.Valid
        assertEquals(withVk.recipes.dropLast(1), withoutVk.recipes)
        assertEquals(setOf("I3", "V1"), withoutVk.rungsOff)
        assertEquals(RemoteRecipeConfig.Parsed.Valid(4L, withVk.expiresEpochDay, bundled), RemoteRecipeConfig2.parse(config(), bundled))
    }

    @Test
    fun `anything wrong rejects the whole schema-2 config`() {
        val bad = mapOf(
            config(schema = "1") to "schema 1 is not 2",
            config(schema = "\"2\"") to "schema must be a whole number",
            config(sites = """{"vine":{}}""") to "unknown site vine",
            config(rungsOff = """["V4"]""") to "rungsOff: unknown rung V4",
            config(rungsOff = """["V1","V1"]""") to "rungsOff names a rung twice",
            config(rungsOff = """[""]""") to "rungsOff must be a text of 1-512 characters",
            config(rungsOff = """"V1"""") to "rungsOff must be a list",
            config(rungsOff = (RemoteRecipeConfig2.RUNGS + "F1").joinToString(",", "[", "]") { "\"$it\"" }) to
                "rungsOff has more than 14 entries",
            config(extra = ""","values":{}""") to "config: unknown key values",
            config(version = "0") to "version must be positive",
            config(expires = "2027-02-30") to "expires is not a date",
            config(sites = """{"vk":{"endpoint":"https://evil.example/{id}"}}""") to "vk.endpoint must stay on https://api.vk.example",
            config(sites = """{"vk":{"headers":{"Cookie":"remixsid=1"}}}""") to "vk.headers: Cookie is not a recipe header",
            config(sites = """{"vk":{"clientSecret":"x"}}""") to "vk: unknown key clientSecret",
            config(sites = """{"vk":"x"}""") to "vk must be an object",
            config(sites = """{"instagram":{"cookieDomain":"example.com"}}""") to "instagram: unknown key cookieDomain",
            "<html>not a config</html>" to "config is not a bounded JSON object",
            config(sites = """{"instagram":{"agent":"${"a".repeat(RemoteRecipeConfig2.MAX_CHARS)}"}}""") to
                "config larger than 65536 characters",
        )
        bad.forEach { (text, reason) ->
            assertEquals(reason, RemoteRecipeConfig.Parsed.Invalid(reason), RemoteRecipeConfig2.parse(text, bundled))
        }
        assertEquals(
            "a build without VK's recipe: vk is an unknown site, as in schema 1",
            RemoteRecipeConfig.Parsed.Invalid("unknown site vk"),
            RemoteRecipeConfig2.parse(config(sites = """{"vk":{}}"""), schema1Recipes),
        )
        assertNull(RemoteRecipeConfig2.schema1(config(schema = "1")))
        assertNull(RemoteRecipeConfig2.schema1("[]"))
    }

    @Test
    fun `schema 2 without VK reads exactly as schema 1, escapes and duplicates included`() {
        val sites = listOf(
            "{}",
            """{"instagram":{"agent":"Config agent"}}""",
            """{"instagram":{"agent":"Quoted \"agent\" \\ back\/slash \u0041"}}""",
            """{"instagram":{"agent":"caf\u00e9"}}""",
            """{"instagram":{"agent":"first"},"instagram":{"agent":"last"}}""",
            """{"facebook":{"drmPaths":[["drm","key"]],"wallPaths":["/login/"]},"x":{"headers":{"accept-language":"en"}}}""",
            """{"tiktok":{"media":[{"path":["video","playAddr"],"mime":"video/mp4","order":"HEIGHT_DESC","fields":{"height":["h"]}}]}}""",
            """{"vimeo":{"endpoint":"https://player.vimeo.com/video/{id}/config"}}""",
            """{"instagram":{"answerHosts":["evil.example"]}}""",
            """{"instagram":{"headers":{"Referer":"a\r\nX: b"}}}""",
            """{"instagram":{"endpoint":"https://www.instagram.com/p/{id}/{cookie}"}}""",
            """{"instagram":{"media":[{"path":["video_url"],"mime":"text/html"}]}}""",
            """{"instagram":"x"}""",
            """{"vine":{}}""",
        )
        val offs = listOf(null, "[]", """["probe"]""", """["F1","I3"]""", """["F9"]""", """["F1","F1"]""", "1")
        var cases = 0
        for (site in sites) for (off in offs) {
            val one = config(schema = "1", sites = site, rungsOff = off)
            val two = config(schema = "2", sites = site, rungsOff = off)
            val first = RemoteRecipeConfig.parse(one, schema1Recipes)
            val second = RemoteRecipeConfig2.parse(two, schema1Recipes)
            val projected = RemoteRecipeConfig2.schema1(two)?.let { RemoteRecipeConfig.parse(it, schema1Recipes) }
            if (first is RemoteRecipeConfig.Parsed.Valid) {
                assertEquals("$site $off", first, second)
                assertEquals("$site $off", first, projected)
                assertEquals("$site $off", first, RemoteRecipeConfig2.parse(two, bundled).let { (it as RemoteRecipeConfig.Parsed.Valid).copy(recipes = it.recipes.dropLast(1)) })
            } else {
                assertTrue("$site $off: $second", second is RemoteRecipeConfig.Parsed.Invalid)
            }
            cases += 1
        }
        assertEquals(sites.size * offs.size, cases)
        val quoted = RemoteRecipeConfig2.parse(config(sites = sites[2]), bundled) as RemoteRecipeConfig.Parsed.Valid
        assertEquals("Quoted \"agent\" \\ back/slash A", quoted.recipes.single { it.site == "instagram" }.agent)
    }

    @Test
    fun `the schema-1 projection leaves out VK and V1-V3 and keeps everything else in order`() {
        fun projected(sites: String, rungsOff: String?): JsonValue.Object =
            BoundedJsonParser.parse(checkNotNull(RemoteRecipeConfig2.schema1(config(sites = sites, rungsOff = rungsOff)))) as JsonValue.Object
        val full = projected("""{"vk":{"agent":"a"},"x":{"agent":"b"}}""", """["V1","X1","V3","probe"]""")
        assertEquals(listOf("schema", "version", "expires", "sites", "rungsOff"), full.entries.keys.toList())
        assertEquals(JsonValue.Number("1"), full.entries["schema"])
        assertEquals(setOf("x"), (full.entries["sites"] as JsonValue.Object).entries.keys)
        assertEquals(JsonValue.Array(listOf(JsonValue.Text("X1"), JsonValue.Text("probe"))), full.entries["rungsOff"])
        assertNull("only VK's rungs: no rungsOff at all", projected("{}", """["V2"]""").entries["rungsOff"])
        assertEquals(JsonValue.Array(emptyList()), projected("{}", "[]").entries["rungsOff"])
        assertFalse(projected("{}", null).entries.containsKey("rungsOff"))
        val text = config(sites = """{"vk":{"agent":"a"}}""", rungsOff = """["V1"]""")
        assertTrue(checkNotNull(RemoteRecipeConfig2.schema1(text)).length <= text.length)
    }

    @Test
    fun `the committed schema-2 docs - its JSON schema is schema 1's plus vk and V1-V3, its example projects to schema 1's`() {
        val docs = File(checkNotNull(System.getProperty("yft.recipeDocs")) { "recipe docs unset" })
        fun json(name: String) = BoundedJsonParser.parse(File(docs, name).readText()) as JsonValue.Object
        fun obj(value: JsonValue?, vararg path: String): JsonValue.Object =
            path.fold(value) { node, key -> (node as JsonValue.Object).entries[key] } as JsonValue.Object
        fun texts(value: JsonValue?): List<String> = (value as JsonValue.Array).items.map { (it as JsonValue.Text).value }
        val one = json("master-recipes.schema.json")
        val two = json("master-recipes-2.schema.json")
        assertEquals(JsonValue.Number("2"), obj(two, "properties", "schema").entries["const"])
        assertEquals(JsonValue.Number("1"), obj(one, "properties", "schema").entries["const"])
        assertEquals("the same keys, paths and bounds", one.entries["\$defs"], two.entries["\$defs"])
        assertEquals(one.entries["required"], two.entries["required"])
        assertEquals(RemoteRecipeConfig2.TOP_KEYS, obj(two, "properties").entries.keys)
        assertEquals(obj(one, "properties").entries["version"], obj(two, "properties").entries["version"])
        assertEquals(obj(one, "properties").entries["expires"], obj(two, "properties").entries["expires"])
        val sites = obj(two, "properties", "sites", "properties")
        assertEquals(RemoteRecipeConfig2.SITES, sites.entries.keys.toList())
        assertEquals(RemoteRecipeConfig2.SCHEMA_1_SITES, obj(one, "properties", "sites", "properties").entries.keys.toList())
        assertEquals(schema1Recipes.map { it.site }, RemoteRecipeConfig2.SCHEMA_1_SITES)
        assertEquals("the bundled recipes: schema 1's sites, then VK", RemoteRecipeConfig2.SITES, ContractRecipes.ALL.map { it.site })
        sites.entries.values.forEach { assertEquals(JsonValue.Object(mapOf("\$ref" to JsonValue.Text("#/\$defs/site"))), it) }
        val rungs = obj(two, "properties", "rungsOff")
        assertEquals(RemoteRecipeConfig2.RUNGS, texts(obj(rungs, "items").entries["enum"]))
        assertEquals(RemoteRecipeConfig2.RUNGS.size.toLong(), (rungs.entries["maxItems"] as JsonValue.Number).text.toLong())
        assertEquals(RemoteRecipeConfig.RUNGS + RemoteRecipeConfig2.VK_RUNGS, RemoteRecipeConfig2.RUNGS)

        // The golden pair (the publish helper's test reads the same two files).
        val example2 = File(docs, "master-recipes-2.example.json").readText()
        val example1 = File(docs, "master-recipes.example.json").readText()
        assertTrue(RemoteRecipeConfig2.parse(example2, bundled) is RemoteRecipeConfig.Parsed.Valid)
        val projection = checkNotNull(RemoteRecipeConfig2.schema1(example2))
        assertEquals(BoundedJsonParser.parse(example1), BoundedJsonParser.parse(projection))
        assertEquals(RemoteRecipeConfig.parse(example1, schema1Recipes), RemoteRecipeConfig.parse(projection, schema1Recipes))
    }

    @Test
    fun `every committed config is schema 2 and both files read it as the workflow composes them`() {
        val docs = File(checkNotNull(System.getProperty("yft.recipeDocs")) { "recipe docs unset" })
        val configs = checkNotNull(File(docs, "configs").listFiles { file -> file.name.endsWith(".json") })
        assertTrue(configs.isNotEmpty())
        configs.forEach { file ->
            val text = file.readText()
            val root = BoundedJsonParser.parse(text) as JsonValue.Object
            assertEquals(file.name, JsonValue.Number("2"), root.entries["schema"])
            val composed = text.trim().removeSuffix("}").trimEnd() + ""","version":1,"expires":"2027-12-31"}"""
            val two = RemoteRecipeConfig2.parse(composed, bundled)
            assertTrue("${file.name}: $two", two is RemoteRecipeConfig.Parsed.Valid)
            val one = RemoteRecipeConfig.parse(checkNotNull(RemoteRecipeConfig2.schema1(composed)), schema1Recipes)
            assertTrue("${file.name}: $one", one is RemoteRecipeConfig.Parsed.Valid)
            two as RemoteRecipeConfig.Parsed.Valid
            one as RemoteRecipeConfig.Parsed.Valid
            assertEquals(file.name, one.recipes, two.recipes.filter { it.site != RemoteRecipeConfig2.VK })
            assertEquals(file.name, one.rungsOff, two.rungsOff - RemoteRecipeConfig2.VK_RUNGS.toSet())
            assertNotNull(two.recipes.singleOrNull { it.site == RemoteRecipeConfig2.VK })
        }
    }

    @Test
    fun `the bundled recipes carry VK's own - schema 2 lays over it, schema 1 leaves it as bundled`() {
        val vk = ContractRecipes.ALL.single { it.site == RemoteRecipeConfig2.VK }
        assertEquals(ContractRecipes.ALL.last(), vk)
        val plain = RemoteRecipeConfig2.parse(config(), ContractRecipes.ALL) as RemoteRecipeConfig.Parsed.Valid
        assertEquals(ContractRecipes.ALL, plain.recipes)
        assertEquals(emptySet<String>(), plain.rungsOff)
        val agent = RemoteRecipeConfig2.parse(config(sites = """{"vk":{"agent":"Config agent"}}"""), ContractRecipes.ALL)
            as RemoteRecipeConfig.Parsed.Valid
        assertEquals("Config agent", agent.recipes.last().agent)
        assertEquals(vk.endpoint, agent.recipes.last().endpoint)
        assertEquals(schema1Recipes, agent.recipes.dropLast(1))
        assertEquals(
            "a schema-1 config never names VK",
            RemoteRecipeConfig.Parsed.Invalid("unknown site vk"),
            RemoteRecipeConfig.parse(config(schema = "1", sites = """{"vk":{}}"""), schema1Recipes),
        )
        val docs = File(checkNotNull(System.getProperty("yft.recipeDocs")) { "recipe docs unset" })
        checkNotNull(File(docs, "configs").listFiles { file -> file.name.endsWith(".json") }).forEach { file ->
            val composed = file.readText().trim().removeSuffix("}").trimEnd() + ""","version":1,"expires":"2027-12-31"}"""
            val two = RemoteRecipeConfig2.parse(composed, ContractRecipes.ALL)
            assertTrue("${file.name}: $two", two is RemoteRecipeConfig.Parsed.Valid)
            assertEquals(file.name, ContractRecipes.ALL.map { it.site }, (two as RemoteRecipeConfig.Parsed.Valid).recipes.map { it.site })
        }
    }

    private fun config(
        schema: String = "2",
        version: String = "4",
        expires: String = "2027-12-31",
        sites: String = "{}",
        rungsOff: String? = null,
        extra: String = "",
    ): String {
        val off = rungsOff?.let { ""","rungsOff":$it""" }.orEmpty()
        return """{"schema":$schema,"version":$version,"expires":"$expires","sites":$sites$off$extra}"""
    }

    private companion object {
        /** A made-up stand-in for agent B's VK recipe (not a real VK address). */
        val VK_RECIPE = ContractRecipe(
            site = RemoteRecipeConfig2.VK,
            endpoint = "https://api.vk.example/method/video.get?videos={id}",
            answerHosts = setOf("api.vk.example"),
            headers = mapOf("Accept" to "application/json"),
            media = listOf(ContractMedia(path = listOf("files", "mp4_720"))),
        )
    }
}
