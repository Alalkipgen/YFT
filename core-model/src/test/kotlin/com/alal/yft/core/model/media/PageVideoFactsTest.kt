package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P28: what a page states about its own video, and how a file's length is matched to it. */
class PageVideoFactsTest {
    @Test
    fun aLengthWithinTwoSecondsMatchesAShortVideo() {
        val facts = PageVideoFacts(durationMillis = 300_000)

        assertTrue(facts.matchesLength(300_000))
        assertTrue(facts.matchesLength(298_000))
        assertTrue(facts.matchesLength(302_000))
        assertFalse(facts.matchesLength(302_001))
        assertFalse(facts.matchesLength(null))
        assertFalse(PageVideoFacts().matchesLength(300_000))
    }

    @Test
    fun aVideoOverTenMinutesMatchesWithinOnePercent() {
        // 16:24 is 984 s: players and manifests may round it by up to 9.84 s.
        val facts = PageVideoFacts(durationMillis = 984_000)

        assertTrue(facts.matchesLength(974_160))
        assertTrue(facts.matchesLength(993_000))
        assertFalse(facts.matchesLength(974_000))
        assertFalse(facts.matchesLength(30_000))
    }

    @Test
    fun underHalfTheStatedLengthIsFarShorter() {
        val facts = PageVideoFacts(durationMillis = 984_000)

        assertTrue(facts.isFarShorter(30_000))
        assertTrue(facts.isFarShorter(491_999))
        assertFalse(facts.isFarShorter(492_000))
        assertFalse(facts.isFarShorter(null))
        assertTrue(facts.statesLongVideo)
        assertFalse(PageVideoFacts(durationMillis = 119_000).statesLongVideo)
        assertTrue(PageVideoFacts(durationMillis = 120_000).statesLongVideo)
    }

    @Test
    fun newerFactsKeepWhatOlderOnesFoundAndTheyLack() {
        val older = PageVideoFacts(durationMillis = 984_000, title = "Old", thumbnailUrl = null)
        val newer = PageVideoFacts(title = "New", thumbnailUrl = "https://img.example.test/p.jpg")

        val merged = newer.orElse(older)

        assertEquals(PageVideoFacts(984_000, "New", "https://img.example.test/p.jpg"), merged)
        assertTrue(PageVideoFacts().isEmpty)
        assertFalse(merged.isEmpty)
    }

    @Test
    fun theTextFormNamesNoTitleOrPicture() {
        val text = PageVideoFacts(984_000, "Harbour lights", "https://img.example.test/p.jpg")
            .toString()

        assertFalse(text.contains("Harbour"))
        assertFalse(text.contains("img.example.test"))
    }
}
