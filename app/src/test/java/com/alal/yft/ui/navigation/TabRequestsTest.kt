package com.alal.yft.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabRequestsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun launch() = Intent(context, MainActivity::class.java)

    @Test
    fun aNotificationsIntentAsksForDownloadsAndReachesTheOpenYft() {
        val intent = TabRequests.showing(launch(), YftDestination.DOWNLOADS)

        assertEquals(YftDestination.DOWNLOADS, TabRequests.tabOf(intent))
        assertEquals("downloads", intent.getStringExtra(TabRequests.EXTRA_TAB))
        val reuse = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(reuse, intent.flags and reuse)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun aPlainLaunchAsksForNoTab() {
        assertNull(TabRequests.tabOf(null))
        assertNull(TabRequests.tabOf(launch()))
        assertNull(
            TabRequests.tabOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ),
        )
    }

    @Test
    fun aStartFromRecentsKeepsHome() {
        val fromRecents = TabRequests.showing(launch(), YftDestination.DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertNull(TabRequests.tabOf(fromRecents))
    }

    @Test
    fun onlyTheFourTabsCanBeAskedFor() {
        for (route in listOf("updates", "browser", "player", "about", "", "downloads/x")) {
            assertNull(route, TabRequests.tabOf(launch().putExtra(TabRequests.EXTRA_TAB, route)))
        }
        for (tab in YftDestination.topLevel) {
            val intent = launch().putExtra(TabRequests.EXTRA_TAB, tab.route)
            assertEquals(tab, TabRequests.tabOf(intent))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TabRequests.showing(launch(), YftDestination.UPDATES)
        }
    }

    @Test
    fun theTabWaitsUntilItIsShownOnce() {
        val requests = TabRequests()
        assertNull(requests.requested.value)

        requests.take(launch())
        assertNull(requests.requested.value)

        requests.take(TabRequests.showing(launch(), YftDestination.DOWNLOADS))
        assertEquals(YftDestination.DOWNLOADS, requests.requested.value)

        // Another tab shown meanwhile does not drop it; a plain intent does not either.
        requests.shown(YftDestination.LIBRARY)
        requests.take(launch())
        assertEquals(YftDestination.DOWNLOADS, requests.requested.value)

        requests.shown(YftDestination.DOWNLOADS)
        assertNull(requests.requested.value)
    }
}
