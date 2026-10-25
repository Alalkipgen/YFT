package com.alal.yft.update

import com.alal.yft.update.ReleaseFixtures.DOWNLOAD
import com.alal.yft.update.ReleaseFixtures.apk
import com.alal.yft.update.ReleaseFixtures.list
import com.alal.yft.update.ReleaseFixtures.release
import com.alal.yft.update.ReleaseFixtures.sums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesTest {
    private fun pick(json: String, installedCode: Long = 3, testMode: Boolean = false) =
        GitHubReleases.candidate(checkNotNull(GitHubReleases.parse(json)), installedCode, testMode)

    @Test
    fun aHigherStableReleaseIsOfferedWithItsApkChecksumAndNotes() {
        val update = checkNotNull(pick(list(release("v1.0.1")), installedCode = 1_000_000))

        assertEquals("1.0.1", update.version.name)
        assertEquals(1_000_100L, update.code)
        assertFalse(update.prerelease)
        assertEquals("$DOWNLOAD/v1.0.1/video-downloader-1.0.1.apk", update.apkUrl)
        assertEquals(6_334_176L, update.apkSize)
        assertEquals("$DOWNLOAD/v1.0.1/SHA256SUMS", update.sumsUrl)
        assertEquals("Fixes and a faster download list.", update.notes)
        assertEquals("2026-10-25T09:00:00Z", update.publishedAt)
    }

    @Test
    fun draftsAreNeverOffered() {
        assertNull(pick(list(release("v1.0.1", draft = true)), installedCode = 1_000_000))
        assertNull(pick(list(release("v1.0.1", draft = true)), testMode = true))
    }

    @Test
    fun prereleasesOnlyInTestMode() {
        val json = list(release("v1.0.0-beta.1"))

        assertNull(pick(json, installedCode = 0))
        val test = checkNotNull(pick(json, installedCode = 3, testMode = true))
        assertEquals("1.0.0-beta.1", test.version.name)
        assertNull(test.code)
        assertTrue(test.prerelease)
        assertEquals("$DOWNLOAD/v1.0.0-beta.1/video-downloader-1.0.0-beta.1.apk", test.apkUrl)
    }

    @Test
    fun aStableTagMarkedPrereleaseOrAPrereleaseTagMarkedStableIsNotOfferedOutsideTestMode() {
        assertNull(pick(list(release("v1.0.1", prerelease = true)), installedCode = 1_000_000))
        assertNull(pick(list(release("v1.0.1-rc.1", prerelease = false)), installedCode = 3))
        val markedStable = list(release("v1.0.1-rc.1", prerelease = false))
        assertTrue(checkNotNull(pick(markedStable, testMode = true)).prerelease)
    }

    @Test
    fun anEqualOrLowerCodeIsNotOffered() {
        val json = list(release("v1.0.0"), release("v0.9.0"))

        assertNull(pick(json, installedCode = 1_000_000))
        assertNull(pick(json, installedCode = 1_000_001))
        assertEquals("1.0.0", pick(json, installedCode = 999_999)?.version?.name)
    }

    @Test
    fun theHighestCodeWins() {
        val json = list(
            release("v1.0.1", publishedAt = "2026-11-02T09:00:00Z"),
            release("v1.1.0", publishedAt = "2026-11-01T09:00:00Z"),
            release("v1.0.2", publishedAt = "2026-11-03T09:00:00Z"),
        )

        assertEquals("1.1.0", pick(json, installedCode = 1_000_000)?.version?.name)
    }

    @Test
    fun testModeOffersEveryReleaseAndTakesTheNewestPublished() {
        val json = list(
            release("v1.0.0", publishedAt = "2026-10-30T09:00:00Z"),
            release("v1.0.1-beta.1", publishedAt = "2026-11-04T09:00:00Z"),
            release("v0.9.0", publishedAt = "2026-10-01T09:00:00Z"),
        )

        // The installed code counts as 0: even a debug build "newer" than all of them is offered.
        val update = pick(json, installedCode = 5_000_000, testMode = true)
        assertEquals("1.0.1-beta.1", update?.version?.name)
    }

    @Test
    fun aTagWithoutTheVReadsAndItsApkHasNoV() {
        val json = list(release("1.0.1", assets = listOf(apk("1.0.1"), sums("1.0.1"))))
        val update = checkNotNull(pick(json, installedCode = 1_000_000))

        assertEquals("v1.0.1", update.version.label)
        assertEquals("$DOWNLOAD/1.0.1/video-downloader-1.0.1.apk", update.apkUrl)
    }

    @Test
    fun theApkIsTakenOnlyByItsExactNameAndWhenUploaded() {
        fun only(vararg assets: String) =
            pick(list(release("v1.0.1", assets = assets.toList())), installedCode = 1_000_000)

        assertNull(only(apk("v1.0.1", name = "video-downloader-1.0.0.apk"), sums("v1.0.1")))
        assertNull(only(apk("v1.0.1", name = "video-downloader-v1.0.1.apk"), sums("v1.0.1")))
        assertNull(only(apk("v1.0.1", name = "app-release.apk"), sums("v1.0.1")))
        assertNull(only(apk("v1.0.1", state = "starter"), sums("v1.0.1")))
        assertNull(only(apk("v1.0.1", size = 0), sums("v1.0.1")))
        assertNull(only(sums("v1.0.1")))
        assertEquals(
            "$DOWNLOAD/v1.0.1/video-downloader-1.0.1.apk",
            only(apk("v1.0.1", name = "app-release.apk"), apk("v1.0.1"), sums("v1.0.1"))?.apkUrl,
        )
    }

    @Test
    fun aReleaseWithoutItsApkIsSkippedForAnOlderOneThatHasIt() {
        val json = list(
            release("v1.0.2", assets = listOf(sums("v1.0.2"))),
            release("v1.0.1"),
        )

        assertEquals("1.0.1", pick(json, installedCode = 1_000_000)?.version?.name)
    }

    @Test
    fun noChecksumFileStillOffersTheUpdateWithoutOne() {
        val json = list(release("v1.0.1", assets = listOf(apk("v1.0.1"))))
        val update = checkNotNull(pick(json, installedCode = 1_000_000))

        assertNull(update.sumsUrl)
        val notUploaded =
            list(release("v1.0.1", assets = listOf(apk("v1.0.1"), sums("v1.0.1", state = "new"))))
        assertNull(pick(notUploaded, installedCode = 1_000_000)?.sumsUrl)
    }

    @Test
    fun linksOffGitHubOrPlainHttpAreSkipped() {
        listOf(
            "http://github.com/Alalkipgen/YFT/releases/download/v1.0.1/video-downloader-1.0.1.apk",
            "https://github.com/someone/YFT/releases/download/v1.0.1/video-downloader-1.0.1.apk",
            "https://example.com/Alalkipgen/YFT/releases/download/v1.0.1/video-downloader.apk",
            "https://github.com.example.com/Alalkipgen/YFT/releases/download/v1.0.1/x.apk",
            "https://objects.githubusercontent.com/v1.0.1/video-downloader-1.0.1.apk",
            "https://github.com/Alalkipgen/YFT/releases/download/v1.0.1/video downloader.apk",
        ).forEach { url ->
            val assets = listOf(apk("v1.0.1", url = url), sums("v1.0.1"))
            val json = list(release("v1.0.1", assets = assets))
            assertNull(url, pick(json, installedCode = 1_000_000))
        }
        val offSums = list(
            release(
                "v1.0.1",
                assets = listOf(apk("v1.0.1"), sums("v1.0.1", url = "http://github.com/x")),
            ),
        )
        assertNull(pick(offSums, installedCode = 1_000_000)?.sumsUrl)
    }

    @Test
    fun tagsThatDoNotReadAreSkipped() {
        val json = list(
            release("latest", assets = listOf(apk("latest"))),
            release("v1.100.0", assets = listOf(apk("v1.100.0"))),
            release("vv1.0.1", assets = listOf(apk("vv1.0.1"))),
        )

        assertNull(pick(json, installedCode = 0))
        assertNull(pick(json, installedCode = 0, testMode = true))
    }

    @Test
    fun theBodyIsKeptAsTheNotesCutAtALineEnd() {
        val line = "- " + "x".repeat(97) + "\n"
        val long = line.repeat(300)
        val notes = checkNotNull(pick(list(release("v1.0.1", body = long)), 1_000_000)).notes

        assertTrue(notes.length <= GitHubReleases.MAX_NOTES)
        assertEquals(GitHubReleases.MAX_NOTES - 1, notes.length)
        assertTrue(notes.endsWith("x"))
        assertEquals("a\nb", GitHubReleases.notes("a\r\nb\r\n"))
        assertEquals("", GitHubReleases.notes(null))
        assertEquals("", checkNotNull(pick(list(release("v1.0.1", body = null)), 1_000_000)).notes)
        assertEquals(GitHubReleases.MAX_NOTES, GitHubReleases.notes("y".repeat(30_000)).length)
    }

    @Test
    fun malformedAndWrongShapedAnswersDoNotRead() {
        assertNull(GitHubReleases.parse(""))
        assertNull(GitHubReleases.parse("{"))
        assertNull(GitHubReleases.parse("""{"message": "Not Found"}"""))
        assertNull(GitHubReleases.parse("""[{"tag_name": "v1.0.1"}, 7]"""))
        assertNull(GitHubReleases.parse("[] trailing"))
        assertEquals(emptyList<GitHubRelease>(), GitHubReleases.parse("[]"))
        assertNull(pick("[]"))
    }

    @Test
    fun missingFlagsFailClosed() {
        val noDraft = list(release("v1.0.1").replace("\"draft\": false,", ""))
        val noPrerelease = list(release("v1.0.1").replace("\"prerelease\": false,", ""))

        assertNull(pick(noDraft, installedCode = 1_000_000))
        assertNull(pick(noPrerelease, installedCode = 1_000_000))
    }
}
