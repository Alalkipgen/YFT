package com.alal.yft.update

import org.junit.Assert.assertEquals
import org.junit.Test

/** The notes as plain text (§4.5): Markdown's marks dropped, everything else as written. */
class ReleaseNotesTest {
    private fun plain(vararg lines: String) = ReleaseNotes.plain(lines.joinToString("\n"))

    @Test
    fun headingsLoseTheirMarks() {
        assertEquals("Video Downloader 1.0.0-beta.1", plain("# Video Downloader 1.0.0-beta.1"))
        assertEquals("Requirements", plain("## Requirements"))
        assertEquals("Known issues", plain("   ### Known issues ###"))
        // No heading: no space after the marks, more than six, or marks inside the text.
        assertEquals("#1 on the list", plain("#1 on the list"))
        assertEquals("####### seven", plain("####### seven"))
        assertEquals("C# and F#", plain("C# and F#"))
    }

    @Test
    fun boldAndCodeMarksAreDropped() {
        assertEquals(
            "Beta for sideload testing. Video Downloader (YFT) is an ad-free app.",
            plain("**Beta for sideload testing.** Video Downloader (YFT) is an ad-free app."),
        )
        assertEquals("It has not yet been tested.", plain("It has __not__ yet been tested."))
        assertEquals("Run sha256sum -c SHA256SUMS", plain("Run `sha256sum -c SHA256SUMS`"))
        // A single * or _ stays: it is often no mark at all.
        assertEquals("2 * 3 and snake_case", plain("2 * 3 and snake_case"))
    }

    @Test
    fun aLinkKeepsOnlyItsText() {
        assertEquals(
            "See the guide for more.",
            plain("See [the guide](https://example.com/guide \"Guide\") for more."),
        )
        assertEquals(
            "Fixes: one, two",
            plain("Fixes: [one](https://example.com/1), [**two**](https://example.com/2)"),
        )
        assertEquals("logo", plain("![logo](https://example.com/logo.png)"))
        // A bare address stays text: nothing in the notes is opened.
        assertEquals("https://example.com/notes", plain("https://example.com/notes"))
    }

    @Test
    fun listItemsStartWithABullet() {
        assertEquals("• Android 7.0 or newer", plain("- Android 7.0 or newer"))
        assertEquals("• Wi-Fi only", plain("* Wi-Fi only"))
        assertEquals("• Bold item", plain("* **Bold** item"))
        assertEquals(
            "   • Linux: sha256sum -c SHA256SUMS",
            plain("   - Linux: `sha256sum -c SHA256SUMS`"),
        )
        // Numbered items, a rule and a minus sign stay as written.
        assertEquals("1. Download the APK", plain("1. Download the APK"))
        assertEquals("---", plain("---"))
        assertEquals("-5 °C", plain("-5 °C"))
    }

    @Test
    fun lineBreaksStayAndTheEdgesAreTrimmed() {
        assertEquals("One\ntwo\n\nThree", plain("", "#", "One  ", "two", "", "Three", "", ""))
        assertEquals("", plain(""))
    }

    @Test
    fun theBeta1NotesReadAsPlainText() {
        // The start of v1.0.0-beta.1's body on GitHub, as the check keeps it.
        val body = "# Video Downloader 1.0.0-beta.1\r\n\r\n" +
            "**Beta for sideload testing.** Video Downloader (YFT) is an ad-free Android app.\r\n" +
            "\r\n## Requirements\r\n\r\n" +
            "- Android 7.0 (API 24) or newer; built for Android 15 (API 35).\r\n\r\n" +
            "## Install\r\n\r\n" +
            "1. Download `video-downloader-1.0.0-beta.1.apk` and `SHA256SUMS`.\r\n" +
            "   - Linux: `sha256sum -c SHA256SUMS`\r\n"

        assertEquals(
            """
            Video Downloader 1.0.0-beta.1

            Beta for sideload testing. Video Downloader (YFT) is an ad-free Android app.

            Requirements

            • Android 7.0 (API 24) or newer; built for Android 15 (API 35).

            Install

            1. Download video-downloader-1.0.0-beta.1.apk and SHA256SUMS.
               • Linux: sha256sum -c SHA256SUMS
            """.trimIndent(),
            ReleaseNotes.plain(GitHubReleases.notes(body)),
        )
    }
}
