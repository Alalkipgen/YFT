package com.alal.yft.feature.about

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.diagnostics.CrashReportStore
import com.alal.yft.ui.theme.YftTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class AboutRouteTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        CrashReportStore(context).delete()
    }

    @Test
    fun localReportIsReadCopiedAndDeletedWithoutAutomaticShare() {
        val file = File(context.noBackupFilesDir, "diagnostics/last-crash.txt")
        file.parentFile!!.mkdirs()
        file.writeText("page https://a.test/private?opaque=redaction-fixture")
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) { AboutRoute(onNavigateBack = {}) }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("about-crash-report").fetchSemanticsNodes().size == 1
        }
        assertNull(shadowOf(context).nextStartedActivity)
        composeRule.onNodeWithTag("about-crash-copy").performScrollTo().performClick()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("page https://a.test", clipboard.primaryClip!!.getItemAt(0).text)
        assertNull(shadowOf(context).nextStartedActivity)
        composeRule.onNodeWithTag("about-crash-delete").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("about-crash-report").fetchSemanticsNodes().isEmpty()
        }
        assertFalse(file.exists())
    }

    @Test
    fun shareUsesASanitizedTextChooserWithoutAttachingPrivateFiles() {
        shareCrashReport(context, "page https://a.test/private?opaque=redaction-fixture")
        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("page https://a.test", send.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
        // Android's chooser migrates EXTRA_TEXT into text ClipData; it must never carry a URI.
        val clip = requireNotNull(send.clipData)
        assertEquals(1, clip.itemCount)
        assertNull(clip.getItemAt(0).uri)
        assertEquals("page https://a.test", clip.getItemAt(0).text)
    }
}
