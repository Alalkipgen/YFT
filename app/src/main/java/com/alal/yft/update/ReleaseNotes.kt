package com.alal.yft.update

/**
 * The release notes' Markdown as blocks for the Updates screen (§4.5, v1.0.2): a small subset of
 * CommonMark that [ReleaseNotesView] renders with an AnnotatedString.
 *
 * Blocks: `#` to `###` headings; paragraphs (a line break inside one is a space, two spaces or a
 * backslash at a line's end keep it); `- `, `* ` and `1. ` list items, nested by their indent;
 * fenced code (```` ``` ```` or `~~~`) as written. Inline: `**bold**` and `__bold__`,
 * `*italic*` and `_italic_` (CommonMark's rules, so `2 * 3` and `snake_case` stay as written),
 * `` `code` `` and backslash escapes. A link shows its text only and an image its alt text:
 * nothing in the notes is ever opened (U3). Anything else stays plain text as written (`####`, a
 * rule, a table, a quote, HTML), and a mark without its other half stays a character, so no text
 * is lost, whatever the body holds.
 */
internal object ReleaseNotes {
    /** The blocks of [markdown] in order; empty when it holds no text. */
    fun blocks(markdown: String): List<NoteBlock> =
        BlockParser().parse(markdown.replace("\r\n", "\n").replace('\r', '\n'))

    /** [text] with its inline marks applied: the text as shown and its styled ranges. */
    fun inline(text: String): NoteText = InlineParser(text).parse()
}

/** What a block is; see [NoteBlock]. */
internal enum class NoteKind { HEADING, PARAGRAPH, ITEM, CODE }

/**
 * One block of the notes. [level] is a heading's level (1 to 3). [depth] is the list depth the
 * block belongs to: -1 outside lists, 0 for a top-level item and its later paragraphs or code, 1
 * for an item nested in one, and so on. [marker] is a list item's "•" or its number as written
 * ("2."); null on headings, paragraphs and code, and on an item's later paragraphs.
 */
internal data class NoteBlock(
    val kind: NoteKind,
    val text: NoteText,
    val level: Int = 0,
    val depth: Int = -1,
    val marker: String? = null,
)

/** A block's text as shown, with [marks] over ranges of it. */
internal data class NoteText(val text: String, val marks: List<NoteMark> = emptyList())

/** [style] from [start] (inclusive) to [end] (exclusive). */
internal data class NoteMark(val style: NoteStyle, val start: Int, val end: Int)

internal enum class NoteStyle { BOLD, ITALIC, CODE }

/** The blocks, line by line. */
private class BlockParser {
    private val blocks = mutableListOf<NoteBlock>()

    /** The content columns of the open list items, the outermost first. */
    private val items = mutableListOf<Int>()

    /** The lines of the open paragraph or list item text. */
    private var pending: Pending? = null
    private var fence: Fence? = null
    private var blankBefore = false

    private class Pending(val depth: Int, val marker: String?) {
        val lines = mutableListOf<String>()
    }

    private class Fence(val mark: Char, val length: Int, val indent: Int, val depth: Int) {
        val lines = mutableListOf<String>()
    }

    fun parse(markdown: String): List<NoteBlock> {
        markdown.split('\n').forEach(::take)
        fence?.let(::closeFence)
        flush()
        return blocks
    }

    private fun take(line: String) {
        val open = fence
        if (open != null) {
            if (closes(open, line)) {
                closeFence(open)
            } else {
                open.lines += dropIndent(line, open.indent)
            }
            return
        }
        if (line.isBlank()) {
            flush()
            blankBefore = true
            return
        }
        val after = blankBefore
        blankBefore = false
        val indent = indentOf(line)
        val content = line.trimStart(' ', '\t')
        // Less than 4 columns past the item the line is in; more is text (no indented code).
        val own = indent - (items.lastOrNull { it <= indent } ?: 0) < CODE_INDENT
        when {
            own && openFence(content, indent) -> Unit
            own && heading(content) -> Unit
            own && RULE.matches(content) -> {
                flush()
                items.clear()
                blocks += NoteBlock(NoteKind.PARAGRAPH, NoteText(content.trimEnd()))
            }
            listItem(indent, content, after) -> Unit
            else -> text(content, indent, after)
        }
    }

    private fun openFence(content: String, indent: Int): Boolean {
        val found = FENCE.find(content) ?: return false
        val marks = found.groupValues[1]
        if (marks[0] == '`' && '`' in found.groupValues[2]) return false
        flush()
        fence = Fence(marks[0], marks.length, indent, depthFor(indent))
        return true
    }

    private fun closes(open: Fence, line: String): Boolean {
        val content = line.trim(' ', '\t')
        return indentOf(line) < open.indent + CODE_INDENT &&
            content.length >= open.length &&
            content.all { it == open.mark }
    }

    private fun closeFence(open: Fence) {
        fence = null
        val code = open.lines.joinToString("\n").trimEnd('\n', ' ', '\t')
        if (code.isNotBlank()) {
            blocks += NoteBlock(NoteKind.CODE, NoteText(code), depth = open.depth)
        }
    }

    private fun heading(content: String): Boolean {
        val found = HEADING.find(content) ?: return false
        flush()
        items.clear()
        val text = content.substring(found.range.last + 1).replace(CLOSING_MARKS, "").trim()
        if (text.isNotEmpty()) {
            val level = found.groupValues[1].length
            blocks += NoteBlock(NoteKind.HEADING, ReleaseNotes.inline(text), level = level)
        }
        return true
    }

    private fun listItem(indent: Int, content: String, after: Boolean): Boolean {
        val bullet = BULLET.find(content)
        val numbered = if (bullet == null) NUMBERED.find(content) else null
        val found = bullet ?: numbered ?: return false
        val open = pending
        // As in CommonMark, a numbered line other than "1." goes on with a paragraph.
        if (numbered != null && open != null && open.marker == null && open.depth < 0 &&
            !after && numbered.groupValues[1] != "1"
        ) {
            return false
        }
        flush()
        while (items.isNotEmpty() && items.last() > indent) items.removeAt(items.lastIndex)
        val marker = if (numbered != null) "${numbered.groupValues[1]}." else BULLET_MARK
        val markLength = marker.length.takeIf { numbered != null } ?: 1
        val spaces = columnsOf(found.groupValues[2], indent + markLength)
        val depth = items.size
        items += indent + markLength + if (spaces > CODE_INDENT) 1 else spaces
        pending = Pending(depth, marker).also { it.lines += found.groupValues[3] }
        return true
    }

    private fun text(content: String, indent: Int, after: Boolean) {
        val open = pending
        if (open != null && !after) {
            open.lines += content
            return
        }
        flush()
        pending = Pending(depthFor(indent), null).also { it.lines += content }
    }

    /** The list depth a block at [indent] belongs to; closes the items it is outside of. */
    private fun depthFor(indent: Int): Int {
        while (items.isNotEmpty() && items.last() > indent) items.removeAt(items.lastIndex)
        return items.size - 1
    }

    private fun flush() {
        val open = pending ?: return
        pending = null
        val text = ReleaseNotes.inline(joined(open.lines))
        if (text.text.isBlank()) return
        blocks += when {
            open.depth >= 0 || open.marker != null ->
                NoteBlock(NoteKind.ITEM, text, depth = open.depth, marker = open.marker)
            else -> NoteBlock(NoteKind.PARAGRAPH, text)
        }
    }

    private companion object {
        const val CODE_INDENT = 4
        const val BULLET_MARK = "\u2022"
        val HEADING = Regex("^(#{1,3})(?:[ \\t]+|$)")
        val CLOSING_MARKS = Regex("(?:^|[ \\t]+)#+[ \\t]*$")
        val RULE = Regex("^([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
        val FENCE = Regex("^(`{3,}|~{3,})(.*)$")
        val BULLET = Regex("^([-*])([ \\t]+)(\\S.*)$")
        val NUMBERED = Regex("^(\\d{1,9})\\.([ \\t]+)(\\S.*)$")

        /** A paragraph's lines as one text: a soft break is a space, a hard one a line break. */
        fun joined(lines: List<String>): String {
            val out = StringBuilder()
            lines.forEachIndexed { index, raw ->
                val trimmed = raw.trimEnd(' ', '\t')
                val slashes = trimmed.length - trimmed.trimEnd('\\').length
                when {
                    index == lines.lastIndex -> out.append(trimmed)
                    raw.length - trimmed.length >= 2 -> out.append(trimmed).append('\n')
                    slashes % 2 == 1 -> out.append(trimmed, 0, trimmed.length - 1).append('\n')
                    else -> out.append(trimmed).append(' ')
                }
            }
            return out.toString()
        }

        fun indentOf(line: String): Int =
            columnsOf(line.takeWhile { it == ' ' || it == '\t' }, start = 0)

        /** The columns [whitespace] spans from column [start]; a tab goes to the next 4th. */
        fun columnsOf(whitespace: String, start: Int): Int {
            var column = start
            whitespace.forEach { char ->
                column = if (char == '\t') column + TAB - column % TAB else column + 1
            }
            return column - start
        }

        /** [line] without up to [columns] columns of its leading whitespace. */
        fun dropIndent(line: String, columns: Int): String {
            var column = 0
            var index = 0
            while (index < line.length && column < columns) {
                val char = line[index]
                if (char != ' ' && char != '\t') break
                column = if (char == '\t') column + TAB - column % TAB else column + 1
                index++
            }
            return line.substring(index)
        }

        const val TAB = 4
    }
}

/**
 * Inline marks as CommonMark reads them: code spans first, then links and images (their text
 * kept, the address dropped), then emphasis by its delimiter-run rules. A delimiter that finds no
 * partner stays a character.
 */
private class InlineParser(private val src: String) {
    private val pieces = mutableListOf<Piece>()
    private val delimiters = mutableListOf<Run>()
    private val brackets = mutableListOf<Bracket>()
    private val marks = mutableListOf<PendingMark>()
    private var order = 0

    private sealed class Piece

    /** Text as shown; [code] for a code span's text. */
    private class Literal(val text: String, val code: Boolean = false) : Piece()

    /** A run of `*` or `_`: [count] of its characters are still unmatched, so shown. */
    private class Run(
        val char: Char,
        var count: Int,
        val original: Int,
        val canOpen: Boolean,
        val canClose: Boolean,
        val order: Int,
    ) : Piece()

    /** `[` or `![`, hidden once it opens a link or an image. */
    private class BracketText(val text: String) : Piece() {
        var shown = true
    }

    private class Bracket(val piece: BracketText, val image: Boolean, val firstDelimiter: Int) {
        var active = true
    }

    /** A style from the end of [from]'s shown text to the start of [to]'s. */
    private class PendingMark(val style: NoteStyle, val from: Piece, val to: Piece)

    fun parse(): NoteText {
        val text = StringBuilder()
        fun literal() {
            if (text.isNotEmpty()) pieces += Literal(text.toString())
            text.setLength(0)
        }
        var i = 0
        while (i < src.length) {
            val char = src[i]
            val image = char == '!' && i + 1 < src.length && src[i + 1] == '['
            when {
                char == '\\' && i + 1 < src.length && src[i + 1] in ESCAPABLE -> {
                    text.append(src[i + 1])
                    i += 2
                }
                char == '`' -> {
                    literal()
                    i = codeSpan(i)
                }
                char == '*' || char == '_' -> {
                    literal()
                    i = run(i)
                }
                char == '[' || image -> {
                    literal()
                    val piece = BracketText(if (image) "![" else "[")
                    pieces += piece
                    brackets += Bracket(piece, image, order)
                    i += piece.text.length
                }
                char == ']' -> {
                    literal()
                    i = closeBracket(i)
                }
                else -> {
                    text.append(char)
                    i++
                }
            }
        }
        literal()
        processEmphasis(0)
        return shown()
    }

    /** A code span from the backtick run at [start], or the run as text; the index after. */
    private fun codeSpan(start: Int): Int {
        val length = runLength(start, '`')
        var search = start + length
        while (true) {
            val next = src.indexOf('`', search)
            if (next < 0) break
            val closing = runLength(next, '`')
            if (closing == length) {
                var code = src.substring(start + length, next).replace('\n', ' ')
                val padded = code.length >= 2 && code.first() == ' ' && code.last() == ' '
                if (padded && code.isNotBlank()) code = code.substring(1, code.length - 1)
                pieces += Literal(code, code = true)
                return next + closing
            }
            search = next + closing
        }
        pieces += Literal(src.substring(start, start + length))
        return start + length
    }

    /** A run of `*` or `_` at [start], with what it can open and close; the index after. */
    private fun run(start: Int): Int {
        val char = src[start]
        val length = runLength(start, char)
        val before = if (start == 0) ' ' else src[start - 1]
        val after = if (start + length >= src.length) ' ' else src[start + length]
        val left = !after.isWhitespace() &&
            (!after.isMarkPunctuation() || before.isWhitespace() || before.isMarkPunctuation())
        val right = !before.isWhitespace() &&
            (!before.isMarkPunctuation() || after.isWhitespace() || after.isMarkPunctuation())
        val canOpen = if (char == '*') left else left && (!right || before.isMarkPunctuation())
        val canClose = if (char == '*') right else right && (!left || after.isMarkPunctuation())
        val piece = Run(char, length, length, canOpen, canClose, order++)
        pieces += piece
        if (canOpen || canClose) delimiters += piece
        return start + length
    }

    /** `]` at [start]: a link or an image when `(…)` follows an open bracket, else text. */
    private fun closeBracket(start: Int): Int {
        val bracket = brackets.removeLastOrNull()
        val end = if (bracket != null && bracket.active) destinationEnd(start + 1) else -1
        if (bracket == null || end < 0) {
            pieces += Literal("]")
            return start + 1
        }
        bracket.piece.shown = false
        val bottom = delimiters.indexOfFirst { it.order >= bracket.firstDelimiter }
        processEmphasis(if (bottom < 0) delimiters.size else bottom)
        // As in CommonMark, no link inside a link.
        if (!bracket.image) brackets.forEach { if (!it.image) it.active = false }
        return end
    }

    /** The index after `(destination "title")` at [start], or -1 when there is none. */
    private fun destinationEnd(start: Int): Int {
        if (start >= src.length || src[start] != '(') return -1
        var i = skipSpaces(start + 1)
        if (i < src.length && src[i] == '<') {
            i++
            while (i < src.length && src[i] != '>') {
                if (src[i] == '\n' || src[i] == '<') return -1
                i += if (src[i] == '\\' && i + 1 < src.length) 2 else 1
            }
            if (i >= src.length) return -1
            i++
        } else {
            var depth = 0
            while (i < src.length && !src[i].isWhitespace() && !src[i].isISOControl()) {
                if (src[i] == '\\' && i + 1 < src.length) {
                    i += 2
                    continue
                }
                if (src[i] == '(') depth++
                if (src[i] == ')') {
                    if (depth == 0) break
                    depth--
                }
                i++
            }
            if (depth != 0) return -1
        }
        val destinationEnd = i
        i = skipSpaces(i)
        if (i < src.length && i > destinationEnd && src[i] in TITLE_OPENERS) {
            val close = if (src[i] == '(') ')' else src[i]
            i++
            while (i < src.length && src[i] != close) {
                i += if (src[i] == '\\' && i + 1 < src.length) 2 else 1
            }
            if (i >= src.length) return -1
            i = skipSpaces(i + 1)
        }
        return if (i < src.length && src[i] == ')') i + 1 else -1
    }

    private fun skipSpaces(start: Int): Int {
        var i = start
        while (i < src.length && (src[i] == ' ' || src[i] == '\t' || src[i] == '\n')) i++
        return i
    }

    /** CommonMark's "process emphasis" over the delimiters from index [bottom] on. */
    private fun processEmphasis(bottom: Int) {
        val openersBottom = HashMap<Triple<Char, Int, Boolean>, Int>()
        var current = bottom
        while (current < delimiters.size) {
            val closer = delimiters[current]
            if (!closer.canClose) {
                current++
                continue
            }
            val key = Triple(closer.char, closer.original % 3, closer.canOpen)
            val lowest = openersBottom[key] ?: Int.MIN_VALUE
            var found = -1
            var index = current - 1
            while (index >= bottom && delimiters[index].order >= lowest) {
                val opener = delimiters[index]
                if (opener.char == closer.char && opener.canOpen && !oddMatch(opener, closer)) {
                    found = index
                    break
                }
                index--
            }
            if (found < 0) {
                openersBottom[key] = closer.order
                if (closer.canOpen) current++ else delimiters.removeAt(current)
                continue
            }
            val opener = delimiters[found]
            val used = if (opener.count >= 2 && closer.count >= 2) 2 else 1
            opener.count -= used
            closer.count -= used
            val style = if (used == 2) NoteStyle.BOLD else NoteStyle.ITALIC
            marks += PendingMark(style, opener, closer)
            for (between in current - 1 downTo found + 1) delimiters.removeAt(between)
            current = found + 1
            if (opener.count == 0) {
                delimiters.removeAt(found)
                current = found
            }
            if (closer.count == 0) delimiters.removeAt(current)
        }
        while (delimiters.size > bottom) delimiters.removeAt(delimiters.lastIndex)
    }

    /** CommonMark's rule of 3 for runs that can both open and close. */
    private fun oddMatch(opener: Run, closer: Run): Boolean =
        (opener.canClose || closer.canOpen) &&
            closer.original % 3 != 0 &&
            (opener.original + closer.original) % 3 == 0

    /** The pieces as shown, with the marks' ranges in it. */
    private fun shown(): NoteText {
        val out = StringBuilder()
        val starts = HashMap<Piece, Int>()
        val ends = HashMap<Piece, Int>()
        val code = mutableListOf<NoteMark>()
        pieces.forEach { piece ->
            starts[piece] = out.length
            when (piece) {
                is Literal -> {
                    val from = out.length
                    out.append(piece.text)
                    if (piece.code && piece.text.isNotEmpty()) {
                        code += NoteMark(NoteStyle.CODE, from, out.length)
                    }
                }
                is Run -> repeat(piece.count) { out.append(piece.char) }
                is BracketText -> if (piece.shown) out.append(piece.text)
            }
            ends[piece] = out.length
        }
        val styled = marks.mapNotNull { mark ->
            val start = ends.getValue(mark.from)
            val end = starts.getValue(mark.to)
            NoteMark(mark.style, start, end).takeIf { end > start }
        }
        val sorted = (styled + code).sortedWith(compareBy({ it.start }, { -it.end }))
        return NoteText(out.toString(), sorted)
    }

    private fun runLength(start: Int, char: Char): Int {
        var end = start
        while (end < src.length && src[end] == char) end++
        return end - start
    }

    private companion object {
        const val ESCAPABLE = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        const val TITLE_OPENERS = "\"'("

        /** CommonMark's punctuation: Unicode's P and S categories. */
        fun Char.isMarkPunctuation(): Boolean = when (category) {
            CharCategory.CONNECTOR_PUNCTUATION, CharCategory.DASH_PUNCTUATION,
            CharCategory.START_PUNCTUATION, CharCategory.END_PUNCTUATION,
            CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION,
            CharCategory.OTHER_PUNCTUATION, CharCategory.MATH_SYMBOL,
            CharCategory.CURRENCY_SYMBOL, CharCategory.MODIFIER_SYMBOL, CharCategory.OTHER_SYMBOL,
            -> true
            else -> false
        }
    }
}
