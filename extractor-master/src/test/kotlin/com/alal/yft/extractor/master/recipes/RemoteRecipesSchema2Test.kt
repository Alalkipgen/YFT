package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.master.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * VK Master plan §9 (R9, wave 2): v1.0.1's address (master-recipes-2.json) is read as schema 2,
 * VK's part and rungs V1–V3 included; v1.0.0's (master-recipes.json) as schema 1, laid over the
 * bundled recipes minus VK's, VK's bundled recipe kept in its place.
 */
class RemoteRecipesSchema2Test {
    private val schema2Url = SignedRecipes.CONFIG_URL.removeSuffix(".json") + RemoteRecipes.SCHEMA_2_SUFFIX

    private fun remote(url: String) = RemoteRecipes(null, url, SignedRecipes.signature(), ContractRecipes.ALL) { NOW }

    private fun signed(payload: String) = SignedRecipes.sign(SignedRecipes.keys, payload)

    private fun schema2(version: Long, sites: String = "{}", rungsOff: String? = null): String {
        val off = rungsOff?.let { ""","rungsOff":$it""" }.orEmpty()
        return """{"schema":2,"version":$version,"expires":"2027-12-31","sites":$sites$off}"""
    }

    @Test
    fun `the schema-2 address reads VK's part and V1-V3`() {
        assertEquals("https://raw.githubusercontent.com/Alalkipgen/YFT/recipes/master-recipes-2.json", schema2Url)
        val remote = remote(schema2Url)
        val sites = """{"vk":{"agent":"VK agent"},"x":{"agent":"X agent"}}"""
        assertEquals("recipe config 3 applied", remote.accept(signed(schema2(3, sites, """["V2","probe"]"""))))
        assertEquals(3L, remote.version)
        assertEquals(ContractRecipes.ALL.map { it.site }, remote.recipes().map { it.site })
        assertEquals("VK agent", remote.recipeOf("vk")?.agent)
        assertEquals(ContractRecipes.ALL.single { it.site == "vk" }.endpoint, remote.recipeOf("vk")?.endpoint)
        assertEquals("X agent", remote.recipeOf("x")?.agent)
        assertEquals(setOf("V2", "probe"), remote.rungsOff())
        assertEquals(
            "a schema-1 file at the schema-2 address changes nothing",
            "recipe config ignored: schema 1 is not 2",
            remote.accept(signed(SignedRecipes.payload(version = 4))),
        )
        assertEquals(3L, remote.version)
        assertEquals("recipe config ignored: version 3 is not newer", remote.accept(signed(schema2(3))))
    }

    @Test
    fun `the schema-1 address keeps v1_0_0's reader and VK's bundled recipe`() {
        val remote = remote(SignedRecipes.CONFIG_URL)
        val vk = ContractRecipes.ALL.single { it.site == "vk" }
        val payload = SignedRecipes.payload(version = 2, sites = """{"x":{"agent":"X agent"}}""", rungsOff = listOf("probe"))
        assertEquals("recipe config 2 applied", remote.accept(signed(payload)))
        assertEquals(ContractRecipes.ALL.map { it.site }, remote.recipes().map { it.site })
        assertSame(vk, remote.recipeOf("vk"))
        assertEquals("X agent", remote.recipeOf("x")?.agent)
        assertEquals(ContractRecipes.ALL.filter { it.site != "x" }, remote.recipes().filter { it.site != "x" })
        assertEquals(setOf("probe"), remote.rungsOff())
        listOf(
            SignedRecipes.payload(version = 3, sites = """{"vk":{"agent":"a"}}""") to "unknown site vk",
            SignedRecipes.payload(version = 3, rungsOff = listOf("V1")) to "rungsOff: unknown rung V1",
            schema2(3) to "schema 2 is not 1",
        ).forEach { (text, reason) ->
            assertEquals(reason, "recipe config ignored: $reason", remote.accept(signed(text)))
        }
        assertEquals(2L, remote.version)
    }
}
