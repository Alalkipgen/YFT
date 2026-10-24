package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.solver.OwnSolverProtocol.appendJsonString
import java.security.MessageDigest

/**
 * Phase 1.2 N5b A.4: the locator cache's data (ownSolverNext only), as the core gives and takes it
 * (`own.solver.core.js`, "locator"; this is the core's `locatorOk` form check again). A part says
 * where in one player the core found a kind's answer: strategy P the candidate indexes that
 * answered every input (registry.c of the prepared program), strategy B the winning group's
 * members (static function id, shape index, a hash of the function's source and its size); both
 * with a hash of the probe outputs. Data only: whole numbers and 8-digit hex hashes, never player
 * text, an input or a value, and nothing from it ever runs. A part only narrows the core's work;
 * the core checks that it still fits and keeps every check (a part that does not fit is a miss
 * and the kind runs in full in the same job).
 */
internal object OwnSolverLocator {
    /** The core's VERSION (own.solver.core.js `var VERSION`); a JS test keeps the two equal. */
    const val VERSION: Int = 1

    /** As the core: at most 16 P candidates (MAX_CANDIDATES), 8 B members, these bounds. */
    private const val MAX_CANDIDATES = 16
    private const val MAX_MEMBERS = 8
    private const val MAX_ID = 1_000_000
    private const val MAX_SHAPE = 100_000
    private const val MAX_SIZE = 100_000_000
    private const val LOCATOR_HASH_CHARS = 16

    private val HASH = Regex("[0-9a-f]{8}")

    /** A whole number as JSON writes one: no sign, fraction, exponent or leading zero. */
    private val WHOLE = Regex("0|[1-9][0-9]{0,8}")

    private val KINDS = setOf("v", "n", "sig")
    private val P_KEYS = setOf("s", "c", "o")
    private val B_KEYS = setOf("s", "m", "o")

    sealed interface Part {
        /** The probe outputs' hash (32-bit FNV-1a): whether the entry still fits this player. */
        val outputs: String

        /** Strategy B's part (else P's). */
        val isB: Boolean get() = this is BPart
    }

    /** Strategy P: the candidate indexes, distinct, as the core gives them. */
    data class PPart(val candidates: List<Int>, override val outputs: String) : Part

    /** Strategy B: the winning group's members, each id and shape once. */
    data class BPart(val members: List<Member>, override val outputs: String) : Part

    /** One B member: static function id, shape index, its source's hash and its size. */
    data class Member(val id: Int, val shape: Int, val source: String, val size: Int)

    /** One job's or reply's parts: n (rate) and sig (signature). None: locate only (`{}`). */
    data class Parts(val rate: Part? = null, val signature: Part? = null) {
        val isEmpty: Boolean get() = rate == null && signature == null

        fun of(kind: OwnSolverLadderRule.Kind): Part? = when (kind) {
            OwnSolverLadderRule.Kind.RATE -> rate
            OwnSolverLadderRule.Kind.SIGNATURE -> signature
        }
    }

    /**
     * A reply's locator, `{"v":1,"n"?:part,"sig"?:part}`; null when it is absent, of another core
     * version or not exactly that form (the reply's locator is then ignored).
     */
    fun decode(node: JsonValue?): Parts? {
        val root = node as? JsonValue.Object ?: return null
        if (!root.entries.keys.all { it in KINDS }) return null
        if (wholeOf(root.entries["v"], MAX_ID) != VERSION) return null
        val rate = root.entries["n"]?.let { decodePart(it) ?: return null }
        val signature = root.entries["sig"]?.let { decodePart(it) ?: return null }
        return Parts(rate, signature)
    }

    /** One part, P's or B's by its `s`; null when it is not exactly the core's form. */
    fun decodePart(node: JsonValue?): Part? {
        val part = node as? JsonValue.Object ?: return null
        val outputs = (part.entries["o"] as? JsonValue.Text)?.value?.takeIf(HASH::matches) ?: return null
        return when ((part.entries["s"] as? JsonValue.Text)?.value) {
            "P" -> {
                if (!part.entries.keys.all { it in P_KEYS }) return null
                val items = (part.entries["c"] as? JsonValue.Array)?.items ?: return null
                if (items.isEmpty() || items.size > MAX_CANDIDATES) return null
                val candidates = items.map { wholeOf(it, MAX_CANDIDATES - 1) ?: return null }
                if (candidates.toSet().size != candidates.size) return null
                PPart(candidates, outputs)
            }

            "B" -> {
                if (!part.entries.keys.all { it in B_KEYS }) return null
                val items = (part.entries["m"] as? JsonValue.Array)?.items ?: return null
                if (items.isEmpty() || items.size > MAX_MEMBERS) return null
                val members = items.map { memberOf(it) ?: return null }
                // One function may win in two shapes: two members; each id and shape once.
                if (members.map { it.id to it.shape }.toSet().size != members.size) return null
                BPart(members, outputs)
            }

            else -> null
        }
    }

    private fun memberOf(node: JsonValue): Member? {
        val items = (node as? JsonValue.Array)?.items?.takeIf { it.size == 4 } ?: return null
        val id = wholeOf(items[0], MAX_ID) ?: return null
        val shape = wholeOf(items[1], MAX_SHAPE) ?: return null
        val source = (items[2] as? JsonValue.Text)?.value?.takeIf(HASH::matches) ?: return null
        val size = wholeOf(items[3], MAX_SIZE)?.takeIf { it >= 1 } ?: return null
        return Member(id, shape, source, size)
    }

    private fun wholeOf(node: JsonValue?, max: Int): Int? {
        val text = (node as? JsonValue.Number)?.text?.takeIf(WHOLE::matches) ?: return null
        return text.toInt().takeIf { it <= max }
    }

    /** A job's locator field: `{}` (locate only) or the parts with the core version. */
    fun StringBuilder.appendLocator(parts: Parts) {
        if (parts.isEmpty) {
            append("{}")
            return
        }
        append("{\"v\":").append(VERSION)
        parts.rate?.let { append(",\"n\":"); appendPart(it) }
        parts.signature?.let { append(",\"sig\":"); appendPart(it) }
        append('}')
    }

    fun StringBuilder.appendPart(part: Part) {
        when (part) {
            is PPart -> {
                append("{\"s\":\"P\",\"c\":[")
                part.candidates.forEachIndexed { index, candidate ->
                    if (index > 0) append(',')
                    append(candidate)
                }
                append(']')
            }

            is BPart -> {
                append("{\"s\":\"B\",\"m\":[")
                part.members.forEachIndexed { index, member ->
                    if (index > 0) append(',')
                    append('[').append(member.id).append(',').append(member.shape).append(',')
                    appendJsonString(member.source)
                    append(',').append(member.size).append(']')
                }
                append(']')
            }
        }
        append(",\"o\":")
        appendJsonString(part.outputs)
        append('}')
    }

    /**
     * N5b A.5: the V ledger's name for a part a run used (a hit): the first 16 hex digits of the
     * SHA-256 of its JSON form. Another part, another key: V runs again.
     */
    fun hashOf(part: Part): String = sha256Hex(buildString { appendPart(part) }).take(LOCATOR_HASH_CHARS)

    /** The SHA-256 of [text] (UTF-8) as 64 hex digits; the player text's hash (the cache key). */
    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4]).append(HEX[value and 0xF])
            }
        }
    }

    private const val HEX = "0123456789abcdef"
}
