package com.alal.yft.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YftDestinationTest {
    @Test
    fun containsEveryRequiredPhaseOneScreen() {
        assertEquals(
            setOf("Home", "Browser", "Detected Media", "Preview", "Downloads", "Library", "Settings", "About"),
            YftDestination.entries.map(YftDestination::title).toSet(),
        )
    }

    @Test
    fun routesAreUniqueAndHomeIsStartCandidate() {
        val routes = YftDestination.entries.map(YftDestination::route)

        assertEquals(routes.size, routes.toSet().size)
        assertEquals("home", YftDestination.HOME.route)
        assertTrue(YftDestination.homeActions.none { it == YftDestination.HOME })
    }
}
