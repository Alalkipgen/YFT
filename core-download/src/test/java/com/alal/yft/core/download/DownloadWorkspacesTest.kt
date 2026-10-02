package com.alal.yft.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkspacesTest {
    @Test
    fun namesAreStableHashesThatNeverContainTheTaskId() {
        val name = DownloadWorkspaces.nameFor(DownloadWorkspaces.HLS_PREFIX, "task-1")

        // SHA-256("task-1"), first 24 hex characters.
        assertEquals("hls-7afaa346b4bf92bf9dc21e9a", name)
        assertEquals(4 + DownloadWorkspaces.HASH_CHARS, name.length)
        assertFalse("task-1" in name)
        assertEquals(name, DownloadWorkspaces.nameFor(DownloadWorkspaces.HLS_PREFIX, "task-1"))
        assertNotEquals(name, DownloadWorkspaces.nameFor(DownloadWorkspaces.HLS_PREFIX, "task-2"))
    }

    @Test
    fun onlyGeneratedNamesAreRecognised() {
        val dash = DownloadWorkspaces.nameFor(DownloadWorkspaces.DASH_PREFIX, "abc")

        assertTrue(DownloadWorkspaces.isWorkspaceName(DownloadWorkspaces.DASH_PREFIX, dash))
        assertFalse(DownloadWorkspaces.isWorkspaceName(DownloadWorkspaces.HLS_PREFIX, dash))
        assertFalse(DownloadWorkspaces.isWorkspaceName("dash", "dash-notes"))
        assertFalse(DownloadWorkspaces.isWorkspaceName("dash", "dash-" + "A".repeat(24)))
        assertFalse(DownloadWorkspaces.isWorkspaceName("dash", dash + "0"))
    }
}
