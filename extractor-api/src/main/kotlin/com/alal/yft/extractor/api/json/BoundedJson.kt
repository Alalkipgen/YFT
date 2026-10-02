package com.alal.yft.extractor.api.json

/**
 * Minimal immutable JSON tree used by site adapters.
 *
 * Adapters read deeply nested site payloads, so the accessors below return null instead of
 * throwing: a missing or differently typed field means "the response changed", not a crash.
 */
sealed interface JsonValue {
    data object Null : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    /** Numbers keep their original text so large IDs survive without precision loss. */
    data class Number(val text: String) : JsonValue {
        val asLong: Long?
            get() = text.toLongOrNull() ?: text.toDoubleOrNull()?.let {
                if (it.isFinite() && it % 1.0 == 0.0) it.toLong() else null
            }

        val asDouble: Double?
            get() = text.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    data class Text(val value: String) : JsonValue

    data class Array(val items: List<JsonValue>) : JsonValue

    data class Object(val entries: Map<String, JsonValue>) : JsonValue
}

/** Returns the child for [key], or null when this is not an object or the key is absent. */
operator fun JsonValue?.get(key: String): JsonValue? =
    (this as? JsonValue.Object)?.entries?.get(key)

/** Returns the element at [index], or null when this is not an array or the index is absent. */
operator fun JsonValue?.get(index: Int): JsonValue? =
    (this as? JsonValue.Array)?.items?.getOrNull(index)

/** Walks a key path, stopping at the first missing or mistyped node. */
fun JsonValue?.path(vararg keys: String): JsonValue? =
    keys.fold(this) { current, key -> current[key] }

val JsonValue?.asStringOrNull: String?
    get() = (this as? JsonValue.Text)?.value?.takeIf(String::isNotBlank)

val JsonValue?.asLongOrNull: Long?
    get() = when (this) {
        is JsonValue.Number -> asLong
        is JsonValue.Text -> value.toLongOrNull()
        else -> null
    }

val JsonValue?.asDoubleOrNull: Double?
    get() = when (this) {
        is JsonValue.Number -> asDouble
        is JsonValue.Text -> value.toDoubleOrNull()?.takeIf(Double::isFinite)
        else -> null
    }

val JsonValue?.asBooleanOrNull: Boolean?
    get() = when (this) {
        is JsonValue.Bool -> value
        is JsonValue.Text -> value.toBooleanStrictOrNull()
        else -> null
    }

val JsonValue?.asArrayOrEmpty: List<JsonValue>
    get() = (this as? JsonValue.Array)?.items ?: emptyList()

/**
 * Strict, bounded JSON reader with no third-party dependency.
 *
 * Site payloads are untrusted input, so the parser caps nesting depth and node count and rejects
 * trailing content. Any malformed or oversized document returns null rather than a partial tree,
 * which lets an adapter report a structured failure instead of inventing data.
 */
object BoundedJsonParser {
    const val DEFAULT_MAX_DEPTH = 64
    const val DEFAULT_MAX_NODES = 200_000

    fun parse(
        text: String,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): JsonValue? {
        require(maxDepth > 0)
        require(maxNodes > 0)
        val reader = Reader(text, maxDepth, maxNodes)
        return try {
            val value = reader.readValue(depth = 1)
            reader.skipWhitespace()
            if (reader.hasMore) null else value
        } catch (_: MalformedJson) {
            null
        }
    }

    private class MalformedJson : Exception(null, null, false, false)

    private class Reader(
        private val text: String,
        private val maxDepth: Int,
        private val maxNodes: Int,
    ) {
        private var index = 0
        private var nodes = 0

        val hasMore: Boolean
            get() = index < text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isJsonWhitespace()) index += 1
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > maxDepth) throw MalformedJson()
            countNode()
            skipWhitespace()
            if (index >= text.length) throw MalformedJson()
            return when (text[index]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Text(readString())
                't' -> readLiteral("true", JsonValue.Bool(true))
                'f' -> readLiteral("false", JsonValue.Bool(false))
                'n' -> readLiteral("null", JsonValue.Null)
                else -> readNumber()
            }
        }

        private fun readObject(depth: Int): JsonValue {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                index += 1
                return JsonValue.Object(entries)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw MalformedJson()
                val key = readString()
                skipWhitespace()
                expect(':')
                entries[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index += 1
                    '}' -> {
                        index += 1
                        return JsonValue.Object(entries)
                    }
                    else -> throw MalformedJson()
                }
            }
        }

        private fun readArray(depth: Int): JsonValue {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                index += 1
                return JsonValue.Array(items)
            }
            while (true) {
                items += readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index += 1
                    ']' -> {
                        index += 1
                        return JsonValue.Array(items)
                    }
                    else -> throw MalformedJson()
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (index >= text.length) throw MalformedJson()
                when (val character = text[index]) {
                    '"' -> {
                        index += 1
                        return builder.toString()
                    }
                    '\\' -> {
                        index += 1
                        builder.append(readEscape())
                    }
                    else -> {
                        if (character.code < 0x20) throw MalformedJson()
                        builder.append(character)
                        index += 1
                    }
                }
            }
        }

        private fun readEscape(): Char {
            if (index >= text.length) throw MalformedJson()
            val marker = text[index]
            index += 1
            return when (marker) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> throw MalformedJson()
            }
        }

        private fun readUnicodeEscape(): Char {
            if (index + 4 > text.length) throw MalformedJson()
            val hex = text.substring(index, index + 4)
            if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                throw MalformedJson()
            }
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun readNumber(): JsonValue {
            val start = index
            if (peek() == '-') index += 1
            var digits = 0
            val firstDigitIndex = index
            while (index < text.length && text[index].isDigit()) {
                index += 1
                digits += 1
            }
            if (digits == 0) throw MalformedJson()
            // JSON forbids leading zeros, so "01" is a malformed document rather than 1.
            if (digits > 1 && text[firstDigitIndex] == '0') throw MalformedJson()
            if (index < text.length && text[index] == '.') {
                index += 1
                var fraction = 0
                while (index < text.length && text[index].isDigit()) {
                    index += 1
                    fraction += 1
                }
                if (fraction == 0) throw MalformedJson()
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index += 1
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index += 1
                var exponent = 0
                while (index < text.length && text[index].isDigit()) {
                    index += 1
                    exponent += 1
                }
                if (exponent == 0) throw MalformedJson()
            }
            return JsonValue.Number(text.substring(start, index))
        }

        private fun readLiteral(literal: String, value: JsonValue): JsonValue {
            if (!text.startsWith(literal, index)) throw MalformedJson()
            index += literal.length
            return value
        }

        private fun peek(): Char {
            if (index >= text.length) throw MalformedJson()
            return text[index]
        }

        private fun expect(character: Char) {
            if (peek() != character) throw MalformedJson()
            index += 1
        }

        private fun countNode() {
            nodes += 1
            if (nodes > maxNodes) throw MalformedJson()
        }

        private fun Char.isJsonWhitespace(): Boolean =
            this == ' ' || this == '\t' || this == '\n' || this == '\r'
    }
}
