package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue

/**
 * Phase 1.3 D1: a mutable JSON tree, so the simulator's changes are made in code on main's
 * fixtures (no YouTube data captured or committed). Objects keep their key order.
 */
internal object SimJson {
    /** A number kept as its text, so a write gives back what was read. */
    data class Num(val text: String)

    fun parse(text: String): Any? =
        from(requireNotNull(BoundedJsonParser.parse(text, maxDepth = 64, maxNodes = 400_000)) { "not JSON" })

    fun write(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?): MutableMap<String, Any?> = value as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun list(value: Any?): MutableList<Any?> = value as MutableList<Any?>

    /** The value at [keys], or null at the first missing or mistyped step. */
    fun at(root: Any?, vararg keys: String): Any? =
        keys.fold(root) { node, key -> (node as? Map<*, *>)?.get(key) }

    fun copy(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associateTo(LinkedHashMap<String, Any?>()) { (key, item) -> key as String to copy(item) }
        is List<*> -> value.mapTo(ArrayList()) { copy(it) }
        else -> value
    }

    /** Every object in [root], depth first (the root included). */
    fun objects(root: Any?): List<MutableMap<String, Any?>> {
        val found = ArrayList<MutableMap<String, Any?>>()
        fun walk(node: Any?) {
            when (node) {
                is MutableMap<*, *> -> {
                    found += obj(node)
                    node.values.forEach(::walk)
                }
                is List<*> -> node.forEach(::walk)
            }
        }
        walk(root)
        return found
    }

    private fun from(value: JsonValue): Any? = when (value) {
        JsonValue.Null -> null
        is JsonValue.Bool -> value.value
        is JsonValue.Number -> Num(value.text)
        is JsonValue.Text -> value.value
        is JsonValue.Array -> value.items.mapTo(ArrayList()) { from(it) }
        is JsonValue.Object -> value.entries.entries.associateTo(LinkedHashMap<String, Any?>()) { (key, item) -> key to from(item) }
    }

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Num -> out.append(value.text)
            is Int, is Long -> out.append(value)
            is String -> quote(out, value)
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    quote(out, key as String)
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            else -> error("not JSON: ${value::class.simpleName}")
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (char in text) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') out.append(String.format("\\u%04x", char.code)) else out.append(char)
            }
        }
        out.append('"')
    }
}
