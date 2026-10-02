package com.alal.yft.feature.library

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPrivateDownloadsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun resolvesOnlyPublishedFilesDirectlyInsideTheRoot() {
        val root = temporary.newFolder("downloads")
        val published = File(root, "Clip (1).mp4").apply { writeText("video") }
        File(root, "Clip.mp4.abc123.part").writeText("partial")
        File(root, "nested").mkdirs()
        File(root.parentFile, "secret.db").writeText("private")

        assertEquals(published.canonicalFile, AppPrivateDownloads.resolve(root, "Clip (1).mp4"))
        listOf(
            null,
            "",
            " ",
            ".",
            "..",
            "../secret.db",
            "nested/../../secret.db",
            "..\\secret.db",
            "Clip.mp4\u0000.txt",
            "Clip.mp4.abc123.part",
            "nested",
            "missing.mp4",
        ).forEach { name ->
            assertNull("must refuse <$name>", AppPrivateDownloads.resolve(root, name))
        }
    }

    @Test
    fun refusesLinksThatLeaveTheDirectory() {
        val root = temporary.newFolder("downloads")
        val outside = temporary.newFile("outside.mp4").apply { writeText("not a download") }
        Files.createSymbolicLink(File(root, "link.mp4").toPath(), outside.toPath())

        assertNull(AppPrivateDownloads.resolve(root, "link.mp4"))
    }

    @Test
    fun finishedFilesSkipStagingFilesAndDirectories() {
        val root = temporary.newFolder("downloads")
        File(root, "Song.m4a").writeText("audio")
        File(root, "Song (1).m4a.0f3a.part").writeText("partial")
        File(root, "folder").mkdirs()

        assertEquals(
            listOf("Song.m4a"),
            AppPrivateDownloads.finishedFiles(root).map(File::getName),
        )
        assertEquals(emptyList<File>(), AppPrivateDownloads.finishedFiles(File(root, "absent")))
    }
}
