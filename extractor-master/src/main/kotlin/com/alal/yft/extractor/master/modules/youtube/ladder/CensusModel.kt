package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Phase 1.3 D0: the answer classes of the census (MASTER_KEY_PHASE1_3_PLAN.md §3, v2's table),
 * decided from the answer, the HTTP result and the checks, never from the client's name. D2's
 * answer judge refines them (shape kinds, HLS locate).
 */
internal enum class CensusClass {
    DIRECT,
    NEEDS_SCRIPT,
    SABR_ONLY,
    INCOMPLETE,
    REFUSED,
    VERSION_REJECTED,
    SHAPE_CHANGED,
    TOKEN_NEEDED,
    AGE_CHECK,
    VIDEO_VERDICT,
    BOT_CHECK,
    ;

    /** 0 working without help … 4 a verdict about the video; a rise is a worsening (BROKEN). */
    val rank: Int
        get() = when (this) {
            DIRECT -> 0
            INCOMPLETE -> 1
            NEEDS_SCRIPT -> 2
            SABR_ONLY, TOKEN_NEEDED, SHAPE_CHANGED, VERSION_REJECTED, REFUSED, BOT_CHECK -> 3
            AGE_CHECK, VIDEO_VERDICT -> 4
        }

    /**
     * A verdict about the video itself (age, sign-in, private, removed, region, DRM, live): no row
     * may follow and no other client is asked for it (D2; V7 keeps one client's refusal `REFUSED`).
     */
    val terminal: Boolean
        get() = this == AGE_CHECK || this == VIDEO_VERDICT

    /** The runbook step (§7) a change to this class points to. */
    val runbookStep: Int
        get() = when (this) {
            DIRECT -> 1
            VERSION_REJECTED -> 3
            NEEDS_SCRIPT -> 4
            INCOMPLETE -> 5
            SHAPE_CHANGED -> 6
            SABR_ONLY, TOKEN_NEEDED -> 7
            REFUSED, BOT_CHECK -> 8
            AGE_CHECK, VIDEO_VERDICT -> 2
        }

    companion object {
        fun of(name: String?): CensusClass? = entries.firstOrNull { it.name == name }
    }
}

/** One player request of the census and what it showed. Never an address, a value or an ID. */
internal data class CensusAsk(
    val video: Int,
    val client: String,
    /** `R0` (as R6 asks it), `S` (with the page's timestamp and visitor data), `A1`/`A2`, `SH`. */
    val step: String,
    /** The public client version sent. */
    val version: String,
    /** `200`, another status code, or the failure reason. */
    val http: String,
    val answerClass: CensusClass,
    /** After V: [CensusClass.TOKEN_NEEDED] when the checked row got 403, else [answerClass]. */
    val finalClass: CensusClass = answerClass,
    val status: String? = null,
    val reason: String? = null,
    /** Whether the answer's `videoDetails.videoId` is the video asked. */
    val bound: Boolean? = null,
    val lengthSeconds: Long? = null,
    val progressive: Int = 0,
    val adaptive: Int = 0,
    val protectedFormats: Int = 0,
    val unaddressed: Int = 0,
    val missingSizes: Int = 0,
    val sabr: Boolean = false,
    val hls: Boolean = false,
    val heights: List<Int> = emptyList(),
    val videoCodecs: List<String> = emptyList(),
    val audioCodecs: List<String> = emptyList(),
    val videoRows: Int = 0,
    val audioRows: Int = 0,
    /** S through the own solver: `ok`, `implausible`, `failed`, `unavailable`, `no-player`, `none-needed`. */
    val solve: String? = null,
    /** V: `PASS`, `FAIL`, `UNKNOWN`, or a reason it did not run (`skip-…`). */
    val v: String? = null,
    val vTarget: String? = null,
    val vControl: String? = null,
    val vReason: String? = null,
    val vRow: String? = null,
    val shapeCore: String? = null,
    val shapeFull: String? = null,
    val shapePaths: List<String>? = null,
    val shapeTruncated: Boolean = false,
    /** D2's `SHAPE_CHANGED` kind: `moved` or `partial` ([AnswerJudge.ShapeKind]), else null. */
    val shapeKind: String? = null,
    val kilobytes: Long = 0,
    val millis: Long = 0,
) {
    val key: String get() = "$video|$client|$step"

    override fun toString(): String = "CensusAsk(video=$video, client=$client, step=$step, class=$finalClass)"
}

/** One watch page read: its public config only. */
internal data class CensusPage(
    val video: Int,
    val http: String,
    val clientName: String? = null,
    val clientVersion: String? = null,
    val playerId: String? = null,
    val signatureTimestamp: Boolean = false,
    val visitorData: Boolean = false,
    val apiKey: Boolean = false,
    val kilobytes: Long = 0,
    val millis: Long = 0,
)

/** One H check with the token step. */
internal data class CensusHls(
    val video: Int,
    val client: String,
    val result: HlsCheck.Result,
)

/** One version source read (or the acceptance test of its value). */
internal data class CensusVersion(
    val identity: String,
    val source: String,
    val reachable: Boolean,
    val note: String? = null,
    val values: Int = 0,
    val value: String? = null,
    val pattern: Boolean = false,
    val newer: Boolean? = null,
    val sameAsBundled: Boolean? = null,
    /** `yes` (OK + V pass), `rejected`, `no`, `equal` (the bundled value) or `not-tested`. */
    val accepted: String? = null,
)

internal data class CensusRun(
    val tool: String,
    val mode: String,
    val atMillis: Long,
    val millis: Long = 0,
    val bytes: Long = 0,
    val metered: Boolean = false,
    val requests: Int = 0,
    val stopped: String? = null,
    /** Video slot → 4 hex digits of SHA-256 of its ID (never the ID itself). */
    val slots: Map<Int, String> = emptyMap(),
    val pages: List<CensusPage> = emptyList(),
    val asks: List<CensusAsk> = emptyList(),
    val hls: List<CensusHls> = emptyList(),
    val versions: List<CensusVersion> = emptyList(),
    val notes: List<String> = emptyList(),
)

internal data class CensusState(
    val runs: List<CensusRun> = emptyList(),
    val cooldownUntil: Long = 0,
    val sourcesAt: Long = 0,
)

/** Writes and reads the census report and `census-last.json` (format [FORMAT]). */
internal object CensusJson {
    const val FORMAT = 1
    const val MAX_RUNS = 8
    private const val MAX_STATE_NODES = 600_000
    private val SHAPE_KINDS = setOf("moved", "partial")

    fun tagOf(videoId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray(Charsets.UTF_8))
        return digest.take(2).joinToString("") { "%02x".format(it) }
    }

    fun dateOf(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMillis))

    fun state(state: CensusState): String = buildString {
        append("{\"fmt\":").append(FORMAT)
        append(",\"cooldownUntil\":").append(state.cooldownUntil)
        append(",\"sourcesAt\":").append(state.sourcesAt)
        append(",\"runs\":[")
        state.runs.forEachIndexed { index, run ->
            if (index > 0) append(",\n")
            append(run(run))
        }
        append("]}")
    }

    /** One run as one JSON object, one line per list item so a phone screen can read it. */
    fun run(run: CensusRun): String {
        val groups = ShapeDictionary.groups(run.asks.mapNotNull(CensusAsk::shapePaths))
        val order = ShapeDictionary.flatten(groups)
        return buildString {
            append('{')
            field("tool", run.tool, first = true)
            field("fmt", FORMAT)
            field("mode", run.mode)
            field("at", run.atMillis)
            field("date", dateOf(run.atMillis))
            field("ms", run.millis)
            field("bytes", run.bytes)
            field("metered", run.metered)
            field("req", run.requests)
            field("stop", run.stopped)
            append(",\"slots\":{")
            append(run.slots.entries.sortedBy { it.key }.joinToString(",") { "\"${it.key}\":${quote(it.value)}" })
            append('}')
            list("pages", run.pages.map(::page))
            list("asks", run.asks.map { ask(it, order) })
            list("hls", run.hls.map(::hls))
            list("versions", run.versions.map(::version))
            list("notes", run.notes.map(::quote))
            list("dict", groups.map(::quote))
            append('}')
        }
    }

    private fun page(page: CensusPage): String = buildString {
        append('{')
        field("v", page.video, first = true)
        field("http", page.http)
        field("name", page.clientName)
        field("ver", page.clientVersion)
        field("player", page.playerId)
        field("sts", page.signatureTimestamp)
        field("visitor", page.visitorData)
        field("key", page.apiKey)
        field("kb", page.kilobytes)
        field("ms", page.millis)
        append('}')
    }

    private fun ask(ask: CensusAsk, order: List<String>): String = buildString {
        append('{')
        field("v", ask.video, first = true)
        field("c", ask.client)
        field("s", ask.step)
        field("ver", ask.version)
        field("http", ask.http)
        field("cls", ask.answerClass.name)
        field("fin", ask.finalClass.name)
        field("ps", ask.status)
        field("why", ask.reason)
        field("bound", ask.bound)
        field("len", ask.lengthSeconds)
        field("prog", ask.progressive)
        field("adap", ask.adaptive)
        field("prot", ask.protectedFormats)
        field("unad", ask.unaddressed)
        field("nosize", ask.missingSizes)
        field("sabr", ask.sabr)
        field("hls", ask.hls)
        if (ask.heights.isNotEmpty()) append(",\"h\":[").append(ask.heights.joinToString(",")).append(']')
        if (ask.videoCodecs.isNotEmpty()) list("vc", ask.videoCodecs.map(::quote), inline = true)
        if (ask.audioCodecs.isNotEmpty()) list("ac", ask.audioCodecs.map(::quote), inline = true)
        field("vr", ask.videoRows)
        field("ar", ask.audioRows)
        field("S", ask.solve)
        field("V", ask.v)
        field("vt", ask.vTarget)
        field("vk", ask.vControl)
        field("vwhy", ask.vReason)
        field("vrow", ask.vRow)
        field("sh", ask.shapeCore)
        field("shf", ask.shapeFull)
        ask.shapePaths?.let { field("bits", ShapeDictionary.bits(order, it)) }
        if (ask.shapeTruncated) field("trunc", true)
        field("kind", ask.shapeKind)
        field("kb", ask.kilobytes)
        field("ms", ask.millis)
        append('}')
    }

    private fun hls(entry: CensusHls): String = buildString {
        val result = entry.result
        append('{')
        field("v", entry.video, first = true)
        field("c", entry.client)
        field("m", result.master)
        field("va", result.variant)
        field("seg", result.segment)
        field("n", result.variants)
        field("maxh", result.maxHeight)
        field("audio", result.audio)
        if (result.codecs.isNotEmpty()) list("codecs", result.codecs.map(::quote), inline = true)
        field("key", result.key)
        field("segs", result.segments)
        field("dur", result.durationSeconds)
        field("match", result.durationMatch)
        field("req", result.requests)
        append('}')
    }

    private fun version(entry: CensusVersion): String = buildString {
        append('{')
        field("id", entry.identity, first = true)
        field("src", entry.source)
        field("ok", entry.reachable)
        field("note", entry.note)
        field("n", entry.values)
        field("val", entry.value)
        field("pat", entry.pattern)
        field("newer", entry.newer)
        field("same", entry.sameAsBundled)
        field("acc", entry.accepted)
        append('}')
    }

    private fun StringBuilder.field(name: String, value: Any?, first: Boolean = false) {
        if (value == null) return
        if (!first) append(',')
        append(quote(name)).append(':')
        when (value) {
            is String -> append(quote(value))
            is Double -> append(if (value.isFinite()) value.toString() else "null")
            else -> append(value.toString())
        }
    }

    private fun StringBuilder.list(name: String, items: List<String>, inline: Boolean = false) {
        append(',').append(quote(name)).append(":[")
        val separator = if (inline) "," else ",\n"
        if (!inline && items.isNotEmpty()) append('\n')
        append(items.joinToString(separator))
        append(']')
    }

    fun quote(text: String): String = buildString(text.length + 2) {
        append('"')
        text.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character == '\n' -> append("\\n")
                character.code < 0x20 -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
        append('"')
    }

    /** The stored state; an empty one when [text] is missing or unreadable. */
    fun readState(text: String?): CensusState {
        if (text.isNullOrBlank()) return CensusState()
        val root = BoundedJsonParser.parse(text, maxDepth = 16, maxNodes = MAX_STATE_NODES)
            ?: return CensusState()
        if (root["fmt"].asLongOrNull != FORMAT.toLong()) return CensusState()
        return CensusState(
            runs = root["runs"].asArrayOrEmpty.mapNotNull(::readRun),
            cooldownUntil = root["cooldownUntil"].asLongOrNull ?: 0,
            sourcesAt = root["sourcesAt"].asLongOrNull ?: 0,
        )
    }

    fun readRun(value: JsonValue): CensusRun? {
        val at = value["at"].asLongOrNull ?: return null
        val order = ShapeDictionary.flatten(value["dict"].asArrayOrEmpty.mapNotNull { (it as? JsonValue.Text)?.value })
        val slots = (value["slots"] as? JsonValue.Object)?.entries.orEmpty()
            .mapNotNull { (key, tag) -> key.toIntOrNull()?.let { slot -> tag.asStringOrNull?.let { slot to it } } }
            .toMap()
        return CensusRun(
            tool = value["tool"].asStringOrNull ?: "",
            mode = value["mode"].asStringOrNull ?: "",
            atMillis = at,
            millis = value["ms"].asLongOrNull ?: 0,
            bytes = value["bytes"].asLongOrNull ?: 0,
            metered = value["metered"].asBooleanOrNull ?: false,
            requests = value["req"].asLongOrNull?.toInt() ?: 0,
            stopped = value["stop"].asStringOrNull,
            slots = slots,
            pages = value["pages"].asArrayOrEmpty.mapNotNull(::readPage),
            asks = value["asks"].asArrayOrEmpty.mapNotNull { readAsk(it, order) },
            hls = value["hls"].asArrayOrEmpty.mapNotNull(::readHls),
            versions = value["versions"].asArrayOrEmpty.mapNotNull(::readVersion),
            notes = value["notes"].asArrayOrEmpty.mapNotNull { (it as? JsonValue.Text)?.value },
        )
    }

    private fun readPage(value: JsonValue): CensusPage? {
        val video = value["v"].asLongOrNull?.toInt() ?: return null
        return CensusPage(
        video = video,
        http = value["http"].asStringOrNull ?: "",
        clientName = value["name"].asStringOrNull,
        clientVersion = value["ver"].asStringOrNull,
        playerId = value["player"].asStringOrNull,
        signatureTimestamp = value["sts"].asBooleanOrNull ?: false,
        visitorData = value["visitor"].asBooleanOrNull ?: false,
        apiKey = value["key"].asBooleanOrNull ?: false,
        kilobytes = value["kb"].asLongOrNull ?: 0,
        millis = value["ms"].asLongOrNull ?: 0,
        )
    }

    private fun readAsk(value: JsonValue, order: List<String>): CensusAsk? {
        val answer = CensusClass.of(value["cls"].asStringOrNull) ?: return null
        return CensusAsk(
            video = value["v"].asLongOrNull?.toInt() ?: return null,
            client = value["c"].asStringOrNull ?: return null,
            step = value["s"].asStringOrNull ?: return null,
            version = value["ver"].asStringOrNull ?: "",
            http = value["http"].asStringOrNull ?: "",
            answerClass = answer,
            finalClass = CensusClass.of(value["fin"].asStringOrNull) ?: answer,
            status = value["ps"].asStringOrNull,
            reason = value["why"].asStringOrNull,
            bound = value["bound"].asBooleanOrNull,
            lengthSeconds = value["len"].asLongOrNull,
            progressive = value["prog"].asLongOrNull?.toInt() ?: 0,
            adaptive = value["adap"].asLongOrNull?.toInt() ?: 0,
            protectedFormats = value["prot"].asLongOrNull?.toInt() ?: 0,
            unaddressed = value["unad"].asLongOrNull?.toInt() ?: 0,
            missingSizes = value["nosize"].asLongOrNull?.toInt() ?: 0,
            sabr = value["sabr"].asBooleanOrNull ?: false,
            hls = value["hls"].asBooleanOrNull ?: false,
            heights = value["h"].asArrayOrEmpty.mapNotNull { it.asLongOrNull?.toInt() },
            videoCodecs = value["vc"].asArrayOrEmpty.mapNotNull { it.asStringOrNull },
            audioCodecs = value["ac"].asArrayOrEmpty.mapNotNull { it.asStringOrNull },
            videoRows = value["vr"].asLongOrNull?.toInt() ?: 0,
            audioRows = value["ar"].asLongOrNull?.toInt() ?: 0,
            solve = value["S"].asStringOrNull,
            v = value["V"].asStringOrNull,
            vTarget = value["vt"].asStringOrNull,
            vControl = value["vk"].asStringOrNull,
            vReason = value["vwhy"].asStringOrNull,
            vRow = value["vrow"].asStringOrNull,
            shapeCore = value["sh"].asStringOrNull,
            shapeFull = value["shf"].asStringOrNull,
            shapePaths = value["bits"].asStringOrNull?.let { ShapeDictionary.decode(order, it) },
            shapeTruncated = value["trunc"].asBooleanOrNull ?: false,
            shapeKind = value["kind"].asStringOrNull?.takeIf { it in SHAPE_KINDS },
            kilobytes = value["kb"].asLongOrNull ?: 0,
            millis = value["ms"].asLongOrNull ?: 0,
        )
    }

    private fun readHls(value: JsonValue): CensusHls? {
        val video = value["v"].asLongOrNull?.toInt() ?: return null
        val client = value["c"].asStringOrNull ?: return null
        val master = value["m"].asStringOrNull ?: return null
        return CensusHls(
        video = video,
        client = client,
        result = HlsCheck.Result(
            master = master,
            variant = value["va"].asStringOrNull ?: HlsCheck.SKIPPED,
            segment = value["seg"].asStringOrNull ?: HlsCheck.SKIPPED,
            variants = value["n"].asLongOrNull?.toInt() ?: 0,
            maxHeight = value["maxh"].asLongOrNull?.toInt(),
            audio = value["audio"].asStringOrNull ?: "unknown",
            codecs = value["codecs"].asArrayOrEmpty.mapNotNull { it.asStringOrNull },
            key = value["key"].asStringOrNull ?: "NONE",
            segments = value["segs"].asLongOrNull?.toInt() ?: 0,
            durationSeconds = value["dur"].asDoubleOrNull,
            durationMatch = value["match"].asBooleanOrNull,
            requests = value["req"].asLongOrNull?.toInt() ?: 0,
        ),
        )
    }

    private fun readVersion(value: JsonValue): CensusVersion? {
        val identity = value["id"].asStringOrNull ?: return null
        val source = value["src"].asStringOrNull ?: return null
        return CensusVersion(
        identity = identity,
        source = source,
        reachable = value["ok"].asBooleanOrNull ?: false,
        note = value["note"].asStringOrNull,
        values = value["n"].asLongOrNull?.toInt() ?: 0,
        value = value["val"].asStringOrNull,
        pattern = value["pat"].asBooleanOrNull ?: false,
        newer = value["newer"].asBooleanOrNull,
        sameAsBundled = value["same"].asBooleanOrNull,
        accepted = value["acc"].asStringOrNull,
        )
    }
}

/**
 * The census's last guard before text leaves it (report and state): every video ID, visitor
 * data or other secret it was given becomes `*`, any web address `[address]`, and a media host
 * name `[host]`. The report is built from counts and classes only; this catches a slip.
 */
internal object CensusSanitizer {
    private val ADDRESS = Regex("(?i)(?:https?|wss?)://[^\\s\"'<>]*")
    private val MEDIA_HOST = Regex("(?i)[a-z0-9.-]*googlevideo\\.com")
    private const val MIN_SECRET = 4

    fun clean(text: String, secrets: Collection<String>): String {
        var result = ADDRESS.replace(text, "[address]")
        result = MEDIA_HOST.replace(result, "[host]")
        secrets.filter { it.length >= MIN_SECRET }.sortedByDescending(String::length).forEach { secret ->
            result = result.replace(secret, "*")
        }
        return result
    }

    /** YouTube's own short reason text, letters and punctuation only, at most [max] characters. */
    fun reason(text: String?, max: Int = 120): String? = text
        ?.replace(Regex("\\s+"), " ")
        ?.filter { it.isLetterOrDigit() || it in " .,'’:;()!?&/-" }
        ?.trim()
        ?.take(max)
        ?.takeIf(String::isNotEmpty)
}
