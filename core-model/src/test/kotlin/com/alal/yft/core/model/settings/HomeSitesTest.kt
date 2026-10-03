package com.alal.yft.core.model.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSitesTest {
    @Test
    fun `encoded sites decode to the same list`() {
        val sites = listOf(
            HomeSite("Archive", "https://archive.org"),
            HomeSite("My videos", "https://media.example.test/path?q=1&b=2"),
        )

        assertEquals(sites, HomeSites.decode(HomeSites.encode(sites)))
    }

    @Test
    fun `damaged, insecure and duplicate lines are skipped`() {
        val stored = listOf(
            "Archive\thttps://archive.org",
            "no tab here",
            "\thttps://empty-name.example",
            "Plain\thttp://insecure.example",
            "Spaced\thttps://bad .example",
            "Again\thttps://archive.org",
            "Two  spaces\thttps://two.example",
        ).joinToString("\n")

        assertEquals(listOf(HomeSite("Archive", "https://archive.org")), HomeSites.decode(stored))
    }

    @Test
    fun `names are cleaned to one short line`() {
        assertEquals("My site", HomeSites.cleanName("  My\tsite\n "))
        assertEquals("Bell", HomeSites.cleanName("Be\u0007ll"))
        assertEquals(HomeSites.MAX_NAME_LENGTH, HomeSites.cleanName("x".repeat(80)).length)
    }

    @Test
    fun `lists are capped and initials come from the first letter or digit`() {
        val many = (1..20).map { HomeSite("Site $it", "https://site$it.example") }

        assertEquals(HomeSites.MAX_SITES, HomeSites.decode(HomeSites.encode(many)).size)
        assertEquals("A", HomeSite("archive", "https://archive.org").initial)
        assertEquals("9", HomeSite("(9gag)", "https://9gag.example").initial)
        assertEquals(listOf("A", "W", "N"), HomeSites.DEFAULTS.map(HomeSite::initial))
    }
}
