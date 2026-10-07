package com.alal.yft.core.browser.webview

import com.alal.yft.core.browser.policy.AdRedirectPolicy.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationGuardTest {
    private var now = 0L
    private val guard = BrowserNavigationGuard(clock = { now })

    @Test
    fun aPageThatOpenedCannotSendTheTabAwayButAForwardingPageCan() {
        guard.pageStarted()
        now = 7_999
        assertNull("still opening: a forwarding page", pageRedirect())
        now = 8_000
        assertEquals(
            BlockedNavigation(OTHER, "win.other.test", window = false, reason = Reason.NO_TAP),
            pageRedirect(),
        )

        guard.pageFinished()
        now = 9_499
        assertNull("just finished: still forwarding", pageRedirect())
        now = 9_500
        assertNotNull(pageRedirect())
        // A later finish of the same page keeps its first time.
        guard.pageFinished()
        assertNotNull(pageRedirect())
    }

    @Test
    fun theUsersChoiceLetsItsPageAndItsRedirectsThroughUntilThatPageOpened() {
        openPage()
        guard.userNavigation()
        assertNull("typed listed address", redirect(LISTED, isRedirect = true))
        now += 400
        guard.pageStarted()
        now += 400
        guard.pageFinished()
        now += 1_000
        assertNull("forwards as it opens", pageRedirect())
        now += 500
        assertNotNull("the chosen page opened", pageRedirect())
        assertNotNull(redirect(LISTED, isRedirect = true))
    }

    @Test
    fun theUsersChoiceEndsAfterTenSecondsEvenIfThePageNeverFinishes() {
        openPage()
        guard.userNavigation()
        guard.pageStarted()
        now += 9_999
        assertNull(redirect(LISTED, isRedirect = true))
        now += 1
        assertNotNull(redirect(LISTED, isRedirect = true))
    }

    @Test
    fun windowsOpenHereOnlyForATapToTheSameSite() {
        assertNull(guard.blockedWindow("https://m.example.test/live", PAGE, isUserGesture = true))
        assertEquals(
            BlockedNavigation(OTHER, "win.other.test", window = true, reason = Reason.NO_TAP),
            guard.blockedWindow(OTHER, PAGE, isUserGesture = true),
        )
        assertEquals(
            Reason.AD_NETWORK,
            guard.blockedWindow(LISTED, PAGE, isUserGesture = true)?.reason,
        )
    }

    @Test
    fun switchedOffNothingIsBlocked() {
        openPage()
        guard.enabled = false

        assertNull(pageRedirect())
        assertNull(redirect(LISTED, hasGesture = true))
        assertNull(guard.blockedWindow(OTHER, PAGE, isUserGesture = false))
        assertFalse(guard.blocksResource("c1.popads.net"))

        guard.enabled = true
        assertTrue(guard.blocksResource("c1.popads.net"))
        assertNotNull(pageRedirect())
    }

    private fun openPage() {
        guard.pageStarted()
        now += 1_000
        guard.pageFinished()
        now += 5_000
    }

    private fun pageRedirect() = redirect(OTHER)

    private fun redirect(url: String, hasGesture: Boolean = false, isRedirect: Boolean = false) =
        guard.blockedNavigation(url, PAGE, hasGesture, isRedirect)

    private companion object {
        const val PAGE = "https://m.example.test/watch?v=1"
        const val OTHER = "https://win.other.test/"
        const val LISTED = "https://www.popads.net/"
    }
}
