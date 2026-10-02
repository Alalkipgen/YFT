package com.alal.yft.feature.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryIntentsTest {
    private val item = LibraryItem(
        id = "media:7",
        displayName = "Clip.mp4",
        uri = "content://media/external_primary/downloads/7",
        mimeType = "video/mp4",
        sizeBytes = 10,
        modifiedAtEpochMs = 1,
        location = LibraryLocation.SHARED_DOWNLOADS,
    )

    @Test
    fun openGrantsReadAccessToThatOneUri() {
        val intent = LibraryIntents.view(item)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(item.uri), intent.data)
        assertEquals("video/mp4", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
        assertEquals("*/*", LibraryIntents.view(item.copy(mimeType = null)).type)
    }

    @Test
    fun shareUsesAChooserThatCarriesTheStreamAndTheReadGrant() {
        val chooser = LibraryIntents.share(item)

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("video/mp4", send.type)
        @Suppress("DEPRECATION")
        assertEquals(Uri.parse(item.uri), send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(Uri.parse(item.uri), send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
    }

    @Test
    fun startReportsWhenNoAppCanHandleTheFile() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).checkActivities(true)

        assertFalse(
            LibraryIntents.start(
                application,
                LibraryIntents.view(item).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
        )
    }
}
