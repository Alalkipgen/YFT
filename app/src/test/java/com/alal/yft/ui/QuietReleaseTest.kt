package com.alal.yft.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.BuildConfig
import com.alal.yft.feature.about.SevenTaps
import com.alal.yft.feature.browser.SiteCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Decision 30 (v): the quiet release's one line, its hidden Show details and the 7 taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuietReleaseTest {
    @Test
    fun everyFailureIsOneLineTikToksCheckAndPlayingKeepTheirFunction() {
        assertEquals("Couldn't get this video.", QuietLines.of(null))
        assertEquals(QuietLines.NOT_FOUND, QuietLines.of("This video is private."))
        assertEquals(QuietLines.NOT_FOUND, QuietLines.of("YouTube asks to sign in for this video."))
        assertEquals(
            QuietLines.NOT_FOUND,
            QuietLines.of("Route B trial: main's rows were set aside and Master found none."),
        )
        assertEquals(
            "Play the video, then tap Download again.",
            QuietLines.of("Play the video on this page, then tap Try again."),
        )
        assertEquals(
            QuietLines.PLAY,
            QuietLines.of("Play only the video you want, then tap Try again."),
        )
        assertEquals(
            QuietLines.PLAY,
            QuietLines.of("No verified media was captured. Play the video and tap Try again."),
        )
        assertEquals(
            QuietLines.PLAY,
            QuietLines.of("Open the post, let it play for a moment, then tap Try again."),
        )
        assertEquals(QuietLines.NOT_FOUND, QuietLines.of("Display the video list again."))
        assertEquals(QuietLines.CHECK, QuietLines.of(SiteCheck.SHEET, siteCheck = true))
        assertTrue(QuietLines.CHECK.contains("Show check"))
    }

    @Test
    fun showDetailsIsOffUntilTurnedOnAndRemembered() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val preferences =
            context.getSharedPreferences(ShowDetails.PREFERENCES, Context.MODE_PRIVATE)
        val first = ShowDetails(preferences)
        assertFalse(first.on.value)

        assertTrue(first.toggle())

        assertTrue(first.on.value)
        assertTrue(ShowDetails(preferences).on.value)
        assertFalse(first.toggle())
        assertFalse(ShowDetails(preferences).on.value)
    }

    @Test
    fun theDebugBuildIsNeverQuiet() {
        // Unit tests run on the debug variant: it shows everything, as before.
        assertTrue(BuildConfig.DEBUG)
        assertFalse(ShowDetails.quietBuild)
    }

    @Test
    fun sevenTapsInARowToggleAndAPauseStartsAgain() {
        var now = 1_000L
        val taps = SevenTaps { now }
        repeat(SevenTaps.TAPS - 1) {
            assertFalse(taps.tap())
            now += 300
        }
        assertTrue(taps.tap())

        // The count starts again after a toggle.
        now += 300
        repeat(SevenTaps.TAPS - 1) {
            assertFalse(taps.tap())
            now += 300
        }
        // A pause longer than the window starts the run again.
        now += SevenTaps.WINDOW_MS + 1
        repeat(SevenTaps.TAPS - 1) {
            assertFalse(taps.tap())
            now += 300
        }
        assertTrue(taps.tap())
    }
}
