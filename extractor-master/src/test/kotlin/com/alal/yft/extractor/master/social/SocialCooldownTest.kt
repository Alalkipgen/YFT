package com.alal.yft.extractor.master.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 (item 18): [SocialCooldown.pace], a rung's asks to a host at least a gap apart across
 * lookups (TikTok's T1): the wait for its turn and the turn taken; null, nothing taken, when the
 * wait is over the rung's room; other hosts and a gap of 0 not paced; the 429 cooldown apart.
 */
class SocialCooldownTest {
    private var now = 0L
    private val cooldown = SocialCooldown({ now })

    @Test
    fun `a host's asks are paced the gap apart, each turn taken`() {
        assertEquals(0L, cooldown.pace(listOf(HOST), GAP, GAP))
        now = 3_000
        assertEquals(7_000L, cooldown.pace(listOf(HOST), GAP, GAP))
        // A lookup at the same time waits for the turn after it: over its room, nothing taken.
        assertNull(cooldown.pace(listOf(HOST), GAP, GAP))
        assertEquals(17_000L, cooldown.pace(listOf(HOST), GAP, 2 * GAP))
        // The gap after the last turn (3 s + 17 s + 10 s): at once, whatever the room.
        now = 30_000
        assertEquals(0L, cooldown.pace(listOf(HOST), GAP, 0))
    }

    @Test
    fun `hosts are keyed as the cooldown keys them, others not paced, a gap of 0 paces nothing`() {
        assertEquals(0L, cooldown.pace(listOf("WWW.TikTok.com."), GAP, GAP))
        assertEquals(GAP, cooldown.pace(listOf(HOST), GAP, GAP))
        assertEquals(0L, cooldown.pace(listOf(OTHER), GAP, GAP))
        assertEquals(0L, cooldown.pace(listOf(HOST), 0, 0))
        assertEquals(0L, cooldown.pace(emptyList(), GAP, 0))
        // A rung with several hosts waits for the latest turn among them.
        assertEquals(2 * GAP, cooldown.pace(listOf(OTHER, HOST), GAP, 3 * GAP))
        // Pacing never cools a host.
        assertFalse(cooldown.cooling(HOST))
    }

    @Test
    fun `a 429's cooldown still holds for 30 minutes beside the pace`() {
        cooldown.note(listOf(HOST))
        assertTrue(cooldown.cooling(HOST))
        assertEquals(0L, cooldown.pace(listOf(HOST), GAP, GAP))
        now = SocialCooldown.COOLDOWN_MILLIS - 1
        assertTrue(cooldown.cooling(HOST))
        now = SocialCooldown.COOLDOWN_MILLIS
        assertFalse(cooldown.cooling(HOST))
    }

    private companion object {
        const val HOST = "www.tiktok.com"
        const val OTHER = "www.facebook.com"
        const val GAP = 10_000L
    }
}
