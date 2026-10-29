package com.alal.yft.update

import java.io.File
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notes' Markdown (§4.5, v1.0.2): `#` to `###` headings, bold, italic, code, `- `, `* ` and
 * `1. ` lists and paragraphs; a link's text only and an image's alt text; anything else as
 * written. Blocks read as lines here: "H2 " before a heading, the marker before an item (two
 * spaces per depth), <b>, <i> and <c> around the marks.
 */
class ReleaseNotesTest {
    @Test
    fun headingsOneToThree() {
        assertEquals("H1 Video Downloader 1.0.1", render("# Video Downloader 1.0.1"))
        assertEquals("H2 Requirements", render("## Requirements"))
        assertEquals("H3 Known issues", render("   ### Known issues ###"))
        assertEquals("H2 What's <b>new</b>", render("## What's **new**"))
        // No heading: four marks or more, no space after the marks, marks inside the text.
        assertEquals("#### Four", render("#### Four"))
        assertEquals("####### seven", render("####### seven"))
        assertEquals("#1 on the list", render("#1 on the list"))
        assertEquals("C# and F#", render("C# and F#"))
        // An empty heading shows nothing.
        assertEquals("", render("#"))
        assertEquals("", render("## ##"))
    }

    @Test
    fun paragraphsJoinTheirLinesAndKeepHardBreaks() {
        assertEquals(
            "One paragraph on two lines.\nThe next one.",
            render("One paragraph", "on two lines.", "", "The next one."),
        )
        assertEquals("One\ntwo\nthree", render("One  ", "two\\", "three"))
        assertEquals("Ends with a backslash\\", render("Ends with a backslash\\"))
        assertEquals("H2 Title\nText", render("## Title", "Text"))
        assertEquals("CRLF line ends", ReleaseNotes.blocks("CRLF\r\nline ends\r\n").text())
        assertEquals("", render(""))
        assertEquals("", render("", "  ", "\t", ""))
    }

    @Test
    fun boldItalicAndCode() {
        assertEquals("<b>Beta.</b> Video Downloader", render("**Beta.** Video Downloader"))
        assertEquals("It has <b>not</b> yet", render("It has __not__ yet"))
        assertEquals("An <i>italic</i> and <i>another</i>", render("An *italic* and _another_"))
        assertEquals("<b><i>both</i></b>", render("***both***"))
        assertEquals("<b>bold <i>in</i> bold</b>", render("**bold *in* bold**"))
        assertEquals("<i>it <b>bold</b> it</i>", render("*it **bold** it*"))
        assertEquals("Run <c>sha256sum -c SHA256SUMS</c>", render("Run `sha256sum -c SHA256SUMS`"))
        assertEquals(
            "<c>**not bold**</c> and <c>a ` b</c>",
            render("`**not bold**` and ``a ` b``"),
        )
        assertEquals("<b>From 1.0.0:</b> Settings", render("**From 1.0.0:** Settings"))
        assertEquals("a<b>b</b>", render("a**b**"))
        assertEquals("un<i>frigging</i>believable", render("un*frigging*believable"))
        // CommonMark's flanking rules: these are no marks.
        assertEquals("2 * 3 and snake_case", render("2 * 3 and snake_case"))
        assertEquals("a * b * c", render("a * b * c"))
        assertEquals("file_name_here", render("file_name_here"))
        // Escaped marks stay characters.
        assertEquals(
            "*not italic*, __init__ and `not code`",
            render("\\*not italic\\*, \\_\\_init\\_\\_ and \\`not code\\`"),
        )
    }

    @Test
    fun aLinkShowsItsTextAndAnImageItsAltText() {
        assertEquals(
            "See the guide for more.",
            render("See [the guide](https://example.com/guide \"Guide\") for more."),
        )
        assertEquals(
            "Fixes: one, <b>two</b>",
            render("Fixes: [one](https://example.com/1), [**two**](https://example.com/2)"),
        )
        assertEquals("logo", render("![logo](https://example.com/logo.png)"))
        assertEquals("a (b) link", render("a [(b)](https://example.com/a_(b)) link"))
        assertEquals("empty", render("[empty]()"))
        assertEquals("angle", render("[angle](<https://example.com/a b>)"))
        // A bare address, a reference link and an autolink stay text: nothing is opened.
        assertEquals("https://example.com/notes", render("https://example.com/notes"))
        assertEquals("[text][ref] and [text]", render("[text][ref] and [text]"))
        assertEquals("<https://example.com>", render("<https://example.com>"))
    }

    @Test
    fun listsWithBulletsAndNumbers() {
        assertEquals(
            "• Android 7.0 or newer\n• Wi-Fi only\n• <b>Bold</b> item",
            render("- Android 7.0 or newer", "* Wi-Fi only", "* **Bold** item"),
        )
        assertEquals(
            "1. Download the APK\n2. Check it:\n  • Linux: <c>sha256sum -c SHA256SUMS</c>\n" +
                "  • macOS\n3. Open it on the phone, which takes two lines.",
            render(
                "1. Download the APK",
                "2. Check it:",
                "   - Linux: `sha256sum -c SHA256SUMS`",
                "   - macOS",
                "3. Open it on the phone,",
                "   which takes two lines.",
            ),
        )
        // An item's later paragraph lines up with its text ("|"); a line without indent joins.
        assertEquals(
            "• First\n  | its second paragraph\n• Second item continued\nAfter the list",
            render(
                "- First",
                "",
                "  its second paragraph",
                "- Second item",
                "continued",
                "",
                "After the list",
            ),
        )
        // No list: no space after the mark, another mark, a number that is no item.
        assertEquals("-5 °C", render("-5 °C"))
        assertEquals("+ plus", render("+ plus"))
        assertEquals("1) one", render("1) one"))
        val version = "1.0.1 is signed with the same key"
        assertEquals(version, render(version))
        // As in CommonMark, a number other than "1." goes on with a paragraph; a bullet starts.
        assertEquals("Released in 2026. A year", render("Released in", "2026. A year"))
        assertEquals("Fixes:\n• one", render("Fixes:", "- one"))
    }

    @Test
    fun fencedCodeStaysAsWritten() {
        assertEquals(
            "Run:\ncode: sha256sum -c SHA256SUMS\n  **not bold** [no](link)\nDone",
            render(
                "Run:",
                "```sh",
                "sha256sum -c SHA256SUMS",
                "  **not bold** [no](link)",
                "```",
                "Done",
            ),
        )
        assertEquals("code: ~~~ inside\n```", render("~~~", "~~~ inside", "```"))
        // An open fence runs to the end; an empty one shows nothing.
        assertEquals("code: open\n\n# not a heading", render("```", "open", "", "# not a heading"))
        assertEquals("", render("```", "```"))
        assertEquals(
            "1. Step\n  code: inside the item",
            render("1. Step", "", "   ```", "   inside the item", "   ```"),
        )
    }

    @Test
    fun anythingElseStaysPlainText() {
        assertEquals("Above\n---\nBelow\n* * *", render("Above", "---", "Below", "* * *"))
        assertEquals("> A quote", render("> A quote"))
        assertEquals(
            "| a | b | |---|---| | 1 | 2 |",
            render("| a | b |", "|---|---|", "| 1 | 2 |"),
        )
        assertEquals("<details>Hidden</details>", render("<details>Hidden</details>"))
        assertEquals("~~struck~~ &amp; ==marked==", render("~~struck~~ &amp; ==marked=="))
    }

    @Test
    fun brokenMarkdownKeepsEveryCharacter() {
        listOf(
            "**never closed",
            "__never *closed",
            "`never closed",
            "``a`",
            "[text](https://example.com",
            "[text] (https://example.com)",
            "![alt](",
            "]] [[ ][",
            "** __ * _ ` ~~~",
            "\\ \\a",
        ).forEach { broken -> assertEquals(broken, render(broken)) }
        // CommonMark pairs these: the two `*` make the first `_` italic, the last `_` stays.
        assertEquals("<i>_</i>_", render("*_*_"))
    }

    @Test
    fun randomMarkdownNeverLosesALetter() {
        // No parentheses, so no link address: every letter and digit shows, in order.
        val tokens = listOf(
            "*", "**", "***", "_", "__", "`", "``", "[", "]", "![", "!", "\\", "#", "# ", "## ",
            "#### ", "- ", "* ", "1. ", "2. ", "10. ", "\n", "\n\n", "  ", "   ", "\t", "```\n",
            "~~~\n", "---", "> ", "<", ">", "|", "a", "b", "word", "é", "ß", "7", "Ü", "😀", " ",
        )
        val random = Random(1_000_200L)
        var tested = 0
        repeat(ROUNDS) { round ->
            val markdown = randomText(tokens, random)
            // A fence's info string ("```kotlin") names a language, shown no more than on GitHub.
            if (FENCE_INFO.containsMatchIn(markdown)) return@repeat
            tested++
            val blocks = ReleaseNotes.blocks(markdown)
            assertEquals(
                "round $round: ${markdown.quoted()}",
                letters(markdown),
                letters(blocks.shown()),
            )
            blocks.forEach { block ->
                block.text.marks.forEach { mark ->
                    assertTrue(
                        "round $round: $mark in ${block.text.text.quoted()}",
                        mark.start in 0 until mark.end && mark.end <= block.text.text.length,
                    )
                }
            }
        }
        assertTrue("$tested of $ROUNDS rounds", tested > ROUNDS * 9 / 10)
    }

    @Test
    fun randomMarkdownWithLinksNeverCrashes() {
        val tokens = listOf(
            "*", "**", "_", "`", "[", "]", "](", "(", ")", "![", "<", ">", "\"", "'", " ", "\n",
            "\\", "a", "b", "https://example.com/x", "# ", "- ", "1. ",
        )
        val random = Random(7L)
        repeat(ROUNDS) { round ->
            val markdown = randomText(tokens, random)
            val shown = letters(ReleaseNotes.blocks(markdown).shown())
            val kept = isSubsequence(shown, letters(markdown))
            assertTrue("round $round: ${markdown.quoted()}", kept)
        }
    }

    @Test
    fun aLongBodyIsReadInFull() {
        val body = (1..2_000).joinToString("\n") { "- **Fix $it** in `code` _x_ [y](z) *" }
        val blocks = ReleaseNotes.blocks(body)
        assertEquals(2_000, blocks.size)
        assertEquals("• <b>Fix 2000</b> in <c>code</c> <i>x</i> y *", blocks.last().describe())
        val mixed = "*a_b`c[d]e(".repeat(2_000)
        assertEquals(letters(mixed), letters(ReleaseNotes.blocks(mixed).shown()))
    }

    @Test
    fun theNotesOf101() {
        val markdown = repositoryFile("docs/release/1.0.1.md")
        val blocks = ReleaseNotes.blocks(markdown)
        val described = blocks.map { it.describe() }

        assertEquals(letters(markdown), letters(blocks.shown()))
        listOf("**", "`", "](", "\n#").forEach { mark ->
            assertTrue("$mark in the notes as shown", mark !in blocks.shown())
        }
        assertEquals(
            listOf(
                "H1 Video Downloader 1.0.1", "H2 Requirements", "H2 Install", "H2 What's new",
                "H3 Finding videos", "H3 Fixes", "H2 Privacy", "H2 Known issues",
                "H2 Testing status",
            ),
            blocks.filter { it.kind == NoteKind.HEADING }.map { it.describe() },
        )
        assertTrue(
            described[1],
            described[1].startsWith(
                "<b>The backup finder for VK, and fixes to what it offers on every site.</b> " +
                    "Video Downloader (YFT) is an ad-free Android app that detects,",
            ),
        )
        val install = described.indexOf("H2 Install")
        assertEquals(
            listOf(
                "H2 Install",
                "<b>From 1.0.0:</b> Settings → About → Updates offers 1.0.1; YFT checks its " +
                    "checksum and signature, then Android asks you to confirm. Settings and " +
                    "downloads stay.",
                "By hand:",
                "1. Download <c>video-downloader-1.0.1.apk</c> and <c>SHA256SUMS</c> from the " +
                    "release assets.",
                "2. Check the file against the SHA-256 under \"Verify the download\" below:",
                "  • Linux: <c>sha256sum -c SHA256SUMS</c>",
                "  • macOS: <c>shasum -a 256 -c SHA256SUMS</c>",
                "  • Windows PowerShell: <c>Get-FileHash .\\video-downloader-1.0.1.apk " +
                    "-Algorithm SHA256</c>",
                "3. Open the APK on the phone. When Android asks, allow your browser or file " +
                    "manager to install unknown apps (Settings › Apps › Special app access › " +
                    "Install unknown apps).",
                "4. Optional: <c>apksigner verify --print-certs video-downloader-1.0.1.apk</c> " +
                    "should print the signing certificate SHA-256 shown below.",
                "5. On Android 13 or newer, allow notifications so download progress and " +
                    "controls are visible.",
            ),
            described.subList(install, install + 11),
        )
        assertTrue(described[install + 11].startsWith("1.0.1 is signed with the same key as 1.0.0"))
        val finder = described.single { it.startsWith("• <b>Backup finder for VK:</b> when VK") }
        assertTrue(
            finder,
            finder.endsWith(
                "<b>Settings → Backup finder</b> covers VK like the other sites; VK's site data " +
                    "can be updated from GitHub, only by a file signed with the key YFT carries.",
            ),
        )
        assertTrue(described.any { it.startsWith("• DASH <c>SegmentBase</c>/SIDX and live") })
    }

    @Test
    fun theReleaseBodyWithItsDownloadLines() {
        // scripts/release-prep.sh adds "Verify the download" to the notes (made-up values here).
        val body = repositoryFile("docs/release/1.0.1.md").trimEnd() + "\r\n\r\n" +
            "## Verify the download\r\n\r\n" +
            "- File: `video-downloader-1.0.1.apk` (12345678 bytes)\r\n" +
            "- SHA-256: `0123abcd`\r\n" +
            "- Source commit: `0123456789abcdef`\r\n"
        val described = ReleaseNotes.blocks(GitHubReleases.notes(body)).map { it.describe() }

        assertEquals(
            listOf(
                "H2 Verify the download",
                "• File: <c>video-downloader-1.0.1.apk</c> (12345678 bytes)",
                "• SHA-256: <c>0123abcd</c>",
                "• Source commit: <c>0123456789abcdef</c>",
            ),
            described.takeLast(4),
        )
    }

    private fun render(vararg lines: String): String =
        ReleaseNotes.blocks(lines.joinToString("\n")).joinToString("\n") { it.describe() }

    private fun NoteBlock.describe(): String {
        val indent = "  ".repeat(depth.coerceAtLeast(0))
        val prefix = when (kind) {
            NoteKind.HEADING -> "H$level "
            NoteKind.PARAGRAPH -> ""
            NoteKind.ITEM -> indent + (marker ?: "  |") + " "
            NoteKind.CODE -> if (depth < 0) "code: " else "$indent  code: "
        }
        return prefix + text.tagged()
    }

    /** The text with <b>, <i> and <c> around its marks, nested: the outer one opens first. */
    private fun NoteText.tagged(): String {
        val tags = mapOf(NoteStyle.BOLD to "b", NoteStyle.ITALIC to "i", NoteStyle.CODE to "c")
        val nested = marks.sortedWith(compareBy({ it.start }, { -it.end }, { it.style }))
        val out = StringBuilder()
        for (index in 0..text.length) {
            nested.asReversed().filter { it.end == index }
                .forEach { out.append("</${tags.getValue(it.style)}>") }
            nested.filter { it.start == index }
                .forEach { out.append("<${tags.getValue(it.style)}>") }
            if (index < text.length) out.append(text[index])
        }
        return out.toString()
    }

    /** Every block's text as shown, its marker included. */
    private fun List<NoteBlock>.shown(): String =
        joinToString("\n") { block -> block.marker?.let { "$it " }.orEmpty() + block.text.text }

    private fun List<NoteBlock>.text(): String = joinToString("\n") { it.text.text }

    private fun letters(text: String): String = text.filter(Char::isLetterOrDigit)

    private fun randomText(tokens: List<String>, random: Random): String = buildString {
        repeat(random.nextInt(MAX_TOKENS)) { append(tokens[random.nextInt(tokens.size)]) }
    }

    private fun isSubsequence(part: String, whole: String): Boolean {
        var at = 0
        part.forEach { char ->
            at = whole.indexOf(char, at)
            if (at < 0) return false
            at++
        }
        return true
    }

    private fun String.quoted(): String =
        "\"" + replace("\n", "\\n").replace("\t", "\\t") + "\""

    private fun repositoryFile(path: String): String =
        listOf("../$path", path).map(::File).first(File::isFile).readText()

    private companion object {
        const val ROUNDS = 3_000
        const val MAX_TOKENS = 40

        /** A line that may open a fence with an info string; fence-like text is skipped too. */
        val FENCE_INFO = Regex(
            """(?m)^[ \t]*(?:(?:[-*]|\d{1,9}\.)[ \t]+)*(?:`{3,}|~{3,})[^\n]*[^\s`~]""",
        )
    }
}
