package com.alal.yft.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YftDestinationTest {
    @Test
    fun containsEveryRequiredPhaseOneScreen() {
        assertEquals(
            setOf("Home", "Browser", "Found on this page", "Preview", "Downloads", "Library", "Settings", "About"),
            YftDestination.entries.map(YftDestination::title).toSet(),
        )
    }

    @Test
    fun routesAreUniqueAndHomeIsStartCandidate() {
        val routes = YftDestination.entries.map(YftDestination::route)

        assertEquals(routes.size, routes.toSet().size)
        assertEquals("home", YftDestination.HOME.route)
        assertTrue(YftDestination.fullScreen.none { it == YftDestination.HOME })
    }

    @Test
    fun bottomBarHasFourTabsAndEverythingElseOpensFullScreen() {
        assertEquals(
            listOf(
                YftDestination.HOME,
                YftDestination.DOWNLOADS,
                YftDestination.LIBRARY,
                YftDestination.SETTINGS,
            ),
            YftDestination.topLevel,
        )
        assertEquals(
            listOf(
                YftDestination.BROWSER,
                YftDestination.DETECTED_MEDIA,
                YftDestination.PREVIEW,
                YftDestination.ABOUT,
            ),
            YftDestination.fullScreen,
        )
        assertEquals(YftDestination.LIBRARY, YftDestination.topLevelFor("library"))
        assertEquals(null, YftDestination.topLevelFor("browser?link={link}"))
        assertEquals(null, YftDestination.topLevelFor(null))
    }
}
