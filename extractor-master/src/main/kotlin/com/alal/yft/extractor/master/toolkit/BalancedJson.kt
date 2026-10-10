/*
 * Provenance (Master toolkit T2, copied, not moved; main 34a41890):
 *   extractor-sites/.../tiktok/TikTokPageParser.kt      balancedObject, lenientRead, decodeEntities
 *   extractor-sites/.../instagram/InstagramMedia.kt     InstagramPageDocuments.objectAt, stringAt
 *   extractor-sites/.../vimeo/VimeoConfigParser.kt      objectAfter (marker, then the object)
 * Adapted: one string-aware scanner for objects and arrays, a size cap on every read.
 * Drift check: scripts/master-toolkit-drift.py (toolkit-provenance.tsv).
 */
package com.alal.yft.extractor.master.toolkit

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue

/** String-aware balanced JSON slices of page text, with one lenient retry. Never runs script. */
internal object BalancedJson {
    const val MAX_CHARS = 4 * 1024 * 1024
    const val MAX_DEPTH = 48
    const val MAX_NODES = 30_000

    /** The balanced `{…}` or `[…]` starting at [start]; null when it does not close in bounds. */
    fun objectAt(text: String, start: Int, maxChars: Int = MAX_CHARS): String? {
        if (start !in text.indices) return null
        val open = text[start]
        if (open != '{' && open != '[') return null
        var depth = 0
        var inString = false
        var escaped = false
        var index = start
        while (index < text.length && index - start < maxChars) {
            val character = text[index]
            when {
                escaped -> escaped = false
                inString && character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' || character == '[' -> depth += 1
                character == '}' || character == ']' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
            index += 1
        }
        return null
    }

    /** The first balanced object in [text]; null when there is none. */
    fun firstObject(text: String): String? =
        text.indexOf('{').takeIf { it >= 0 }?.let { objectAt(text, it) }

    /** The object right after [marker] (whitespace allowed); null when absent or unbalanced. */
    fun objectAfter(text: String, marker: String, from: Int = 0): String? {
        val at = text.indexOf(marker, from).takeIf { it >= 0 } ?: return null
        var index = at + marker.length
        while (index < text.length && text[index].isWhitespace()) index += 1
        return objectAt(text, index)
    }

    /** The JSON string literal starting at the quote at [start], decoded; null if broken. */
    fun stringAt(text: String, start: Int, maxChars: Int = MAX_CHARS): String? {
        if (start !in text.indices || text[start] != '"') return null
        var index = start + 1
        while (index < text.length && index - start < maxChars) {
            when (text[index]) {
                '\\' -> index += 2
                '"' -> return (
                    BoundedJsonParser.parse(text.substring(start, index + 1)) as? JsonValue.Text
                    )?.value
                else -> index += 1
            }
        }
        return null
    }

    /** A strict parse, then once more with HTML entities decoded and trailing text cut. */
    fun lenientParse(body: String, maxNodes: Int = MAX_NODES): JsonValue? {
        val trimmed = body.trim()
        if (trimmed.length > MAX_CHARS) return null
        BoundedJsonParser.parse(trimmed, maxDepth = MAX_DEPTH, maxNodes = maxNodes)?.let {
            return it
        }
        val cleaned = decodeEntities(trimmed)
        val start = cleaned.indexOfFirst { it == '{' || it == '[' }.takeIf { it >= 0 }
            ?: return null
        val slice = objectAt(cleaned, start) ?: return null
        return BoundedJsonParser.parse(slice, maxDepth = MAX_DEPTH, maxNodes = maxNodes)
    }

    fun decodeEntities(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
