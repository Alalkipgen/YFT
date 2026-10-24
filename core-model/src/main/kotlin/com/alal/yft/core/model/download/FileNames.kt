package com.alal.yft.core.model.download

import java.text.Normalizer

/**
 * D3: file names that keep every script's marks (Myanmar, Thai, Hindi…) and fit the file system.
 * [keepWords] cleans a title, [cut] shortens text at a grapheme boundary within a UTF-16 and a
 * UTF-8 budget, [fit] shortens a whole name and keeps its extension.
 */
object FileNames {
    /** D3: the longest name Android's file systems take, in UTF-8 bytes. */
    const val FILE_SYSTEM_NAME_BYTES = 255

    /**
     * D3: room for what is added to a saved name: app storage's partial file
     * `<name>.<id ≤16>.part` and " (n)"; MediaStore's pending `.pending-<10 digits>-<name>`
     * (Android 11+), which MediaProvider would otherwise shorten in the middle with "...".
     */
    const val RESERVED_NAME_BYTES = 32

    /** D3: the longest name, extension included, in UTF-8 bytes. */
    const val MAX_NAME_BYTES = FILE_SYSTEM_NAME_BYTES - RESERVED_NAME_BYTES

    /** D3: the longest extension [fit] keeps whole: ASCII letters and digits only. */
    const val MAX_EXTENSION_CHARS = 10

    /**
     * D3: [text] in NFC with its letters, digits, [allowed] characters and the marks that follow
     * a kept letter or digit. A mark with no kept base is dropped (at the very start, or the
     * U+FE0F left behind by a removed emoji), but a spacing mark is kept: Zawgyi types ေ and ျ
     * before their consonant. Myanmar's word signs ၌ ၍ ၎ ၏ count as letters. Everything else
     * becomes a space; runs of spaces become one, and the ends are trimmed.
     */
    fun keepWords(text: String, allowed: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val kept = StringBuilder(normalized.length)
        var based = false
        var index = 0
        while (index < normalized.length) {
            val point = normalized.codePointAt(index)
            index += Character.charCount(point)
            when {
                Character.isLetterOrDigit(point) || point in MYANMAR_WORD_SIGNS -> {
                    kept.appendCodePoint(point)
                    based = true
                }
                isMark(point) -> if (based || isSpacingMark(point)) {
                    kept.appendCodePoint(point)
                    based = true
                }
                point < Character.MIN_SUPPLEMENTARY_CODE_POINT && point.toChar() in allowed -> {
                    kept.appendCodePoint(point)
                    based = false
                }
                else -> {
                    kept.append(' ')
                    based = false
                }
            }
        }
        return kept.toString().replace(SPACE_RUN, " ").trim()
    }

    /**
     * D3: the longest start of [text] that ends at a grapheme boundary and has at most
     * [maxChars] UTF-16 units and [maxBytes] UTF-8 bytes. Never before a mark, ZWJ, ZWNJ, an
     * emoji modifier or a tag character; never after ZWJ, or after a stacking virama (Myanmar
     * ္, Khmer, Tai Tham, the Indic linkers) when a letter follows; regional-indicator pairs and
     * Hangul jamo stay together. If not even the first grapheme fits, it is cut between code
     * points.
     */
    fun cut(text: String, maxChars: Int, maxBytes: Int = Int.MAX_VALUE): String {
        if (maxChars <= 0 || maxBytes <= 0) return ""
        var bytes = 0
        var boundary = 0
        var previous = -1
        var indicators = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            if (index > 0 && breaksBetween(previous, point, indicators)) boundary = index
            val next = index + Character.charCount(point)
            bytes += utf8Length(point)
            if (next > maxChars || bytes > maxBytes) {
                return text.substring(0, if (boundary > 0) boundary else index)
            }
            indicators = if (isRegionalIndicator(point)) indicators + 1 else 0
            previous = point
            index = next
        }
        return text
    }

    /**
     * D3: [name] within [maxChars] UTF-16 units and [maxBytes] UTF-8 bytes, cut by [cut]. A short
     * extension (up to [MAX_EXTENSION_CHARS] ASCII letters and digits after the last dot) is
     * kept and only the part before it is shortened.
     */
    fun fit(name: String, maxChars: Int, maxBytes: Int = MAX_NAME_BYTES): String {
        if (name.length <= maxChars && utf8Bytes(name) <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0) name.substring(dot + 1) else ""
        val short = extension.length in 1..MAX_EXTENSION_CHARS &&
            extension.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        if (!short) return cut(name, maxChars, maxBytes)
        val suffix = ".$extension"
        val base = cut(name.substring(0, dot), maxChars - suffix.length, maxBytes - suffix.length)
            .trimEnd(' ', '.')
        return if (base.isEmpty()) cut(name, maxChars, maxBytes) else base + suffix
    }

    /** D3: [text]'s length in UTF-8 bytes; an unpaired surrogate counts 3, U+FFFD's length. */
    fun utf8Bytes(text: String): Int {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            bytes += utf8Length(point)
            index += Character.charCount(point)
        }
        return bytes
    }

    private fun utf8Length(point: Int): Int = when {
        point < 0x80 -> 1
        point < 0x800 -> 2
        point < Character.MIN_SUPPLEMENTARY_CODE_POINT -> 3
        else -> 4
    }

    private fun breaksBetween(previous: Int, point: Int, indicators: Int): Boolean = when {
        isMark(point) || point == ZWJ || point == ZWNJ -> false
        point in EMOJI_MODIFIERS || point in TAGS -> false
        previous == ZWJ -> false
        previous in STACKING_VIRAMAS && Character.isLetter(point) -> false
        isRegionalIndicator(previous) && isRegionalIndicator(point) -> indicators % 2 == 0
        else -> !joinsHangul(hangul(previous), hangul(point))
    }

    private fun joinsHangul(before: Int, after: Int): Boolean = when (before) {
        HANGUL_L -> after == HANGUL_L || after == HANGUL_V || after == HANGUL_LV ||
            after == HANGUL_LVT
        HANGUL_LV, HANGUL_V -> after == HANGUL_V || after == HANGUL_T
        HANGUL_LVT, HANGUL_T -> after == HANGUL_T
        else -> false
    }

    private fun hangul(point: Int): Int = when (point) {
        in 0x1100..0x115F, in 0xA960..0xA97C -> HANGUL_L
        in 0x1160..0x11A7, in 0xD7B0..0xD7C6 -> HANGUL_V
        in 0x11A8..0x11FF, in 0xD7CB..0xD7FB -> HANGUL_T
        in HANGUL_SYLLABLES -> if ((point - HANGUL_SYLLABLES.first) % 28 == 0) {
            HANGUL_LV
        } else {
            HANGUL_LVT
        }
        else -> HANGUL_NONE
    }

    private fun isMark(point: Int): Boolean {
        val type = Character.getType(point).toByte()
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK ||
            type == Character.COMBINING_SPACING_MARK
    }

    private fun isSpacingMark(point: Int): Boolean =
        Character.getType(point).toByte() == Character.COMBINING_SPACING_MARK

    private fun isRegionalIndicator(point: Int): Boolean = point in 0x1F1E6..0x1F1FF

    private const val ZWJ = 0x200D
    private const val ZWNJ = 0x200C
    private val EMOJI_MODIFIERS = 0x1F3FB..0x1F3FF
    private val TAGS = 0xE0020..0xE007F
    private val MYANMAR_WORD_SIGNS = 0x104C..0x104F
    private val STACKING_VIRAMAS =
        setOf(0x1039, 0x17D2, 0x1A60, 0x094D, 0x09CD, 0x0ACD, 0x0B4D, 0x0C4D, 0x0D4D)
    private val HANGUL_SYLLABLES = 0xAC00..0xD7A3
    private const val HANGUL_NONE = 0
    private const val HANGUL_L = 1
    private const val HANGUL_V = 2
    private const val HANGUL_T = 3
    private const val HANGUL_LV = 4
    private const val HANGUL_LVT = 5
    private val SPACE_RUN = Regex(" {2,}")
}
