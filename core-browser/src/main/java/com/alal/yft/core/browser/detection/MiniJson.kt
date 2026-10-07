package com.alal.yft.core.browser.detection

/**
 * P28: a small, bounded JSON reader for the JSON-LD a page states about itself, so the page's
 * facts are read the same way on Home (plain JVM, no `org.json`) and in the browser. Objects
 * become maps, arrays lists, numbers doubles. Anything malformed, deeper than [MAX_DEPTH] or
 * longer than [MAX_LENGTH] reads as null; it never throws.
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        if (text.length > MAX_LENGTH) return null
        val reader = Reader(text)
        return runCatching {
            val value = reader.value(depth = 0)
            reader.skipSpace()
            value.takeIf { reader.atEnd }
        }.getOrNull()
    }

    private class Reader(private val text: String) {
        private var index = 0
        val atEnd: Boolean get() = index >= text.length

        fun value(depth: Int): Any? {
            require(depth <= MAX_DEPTH)
            skipSpace()
            require(!atEnd)
            return when (val char = text[index]) {
                '{' -> objectValue(depth)
                '[' -> arrayValue(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> {
                    require(char == '-' || char.isDigit())
                    number()
                }
            }
        }

        private fun objectValue(depth: Int): Map<String, Any?> {
            index++
            val values = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                index++
                return values
            }
            while (true) {
                skipSpace()
                val key = string()
                skipSpace()
                expect(':')
                values.putIfAbsent(key, value(depth + 1))
                skipSpace()
                when (next()) {
                    ',' -> continue
                    '}' -> return values
                    else -> error("object")
                }
            }
        }

        private fun arrayValue(depth: Int): List<Any?> {
            index++
            val values = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                index++
                return values
            }
            while (true) {
                values += value(depth + 1)
                skipSpace()
                when (next()) {
                    ',' -> continue
                    ']' -> return values
                    else -> error("array")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val char = next()
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> when (val escaped = next()) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            require(index + 4 <= text.length)
                            out.append(text.substring(index, index + 4).toInt(16).toChar())
                            index += 4
                        }
                        else -> out.append(escaped)
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun number(): Double {
            val start = index
            while (!atEnd && text[index] in NUMBER_CHARS) index++
            return text.substring(start, index).toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            require(text.startsWith(word, index))
            index += word.length
            return value
        }

        fun skipSpace() {
            while (!atEnd && text[index].isWhitespace()) index++
        }

        private fun peek(): Char? = text.getOrNull(index)

        private fun next(): Char {
            require(!atEnd)
            return text[index++]
        }

        private fun expect(char: Char) {
            require(next() == char)
        }
    }

    private const val MAX_DEPTH = 32
    private const val MAX_LENGTH = 400_000
    private const val NUMBER_CHARS = "+-0123456789.eE"
}
