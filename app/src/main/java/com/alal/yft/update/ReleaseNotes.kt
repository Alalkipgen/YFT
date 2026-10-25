package com.alal.yft.update

/**
 * The release notes as plain text (§4.5). A release's body is Markdown, which the Updates screen
 * does not render, so its marks would show as they are: heading marks, `**`, `__` and backticks
 * are dropped, a link (or an image) keeps only its text — nothing in the notes is ever opened —
 * and a `- ` or `* ` list item starts with "• ". Everything else stays as written: numbered
 * items, the line breaks, a single `*` or `_`.
 */
internal object ReleaseNotes {
    private val heading = Regex("^ {0,3}#{1,6}(?:[ \\t]+|$)")
    private val closingMarks = Regex("[ \\t]+#+[ \\t]*$")
    private val listItem = Regex("^([ \\t]*)[-*][ \\t]+")
    private val link = Regex("!?\\[([^\\]\\n]*)]\\([^)\\n]*\\)")
    private val marks = Regex("\\*\\*|__|`")

    fun plain(markdown: String): String = markdown.lineSequence()
        .map(::plainLine)
        .joinToString("\n")
        .trimStart('\n')
        .trimEnd()

    private fun plainLine(line: String): String {
        val headingMarks = heading.find(line)
        val text = if (headingMarks != null) {
            line.substring(headingMarks.range.last + 1).replace(closingMarks, "")
        } else {
            listItem.replace(line) { item -> "${item.groupValues[1]}• " }
        }
        return marks.replace(link.replace(text) { found -> found.groupValues[1] }, "").trimEnd()
    }
}
