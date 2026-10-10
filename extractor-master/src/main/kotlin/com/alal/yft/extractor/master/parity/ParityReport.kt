package com.alal.yft.extractor.master.parity

import com.alal.yft.core.model.media.MediaCandidate
import java.security.MessageDigest

/** §4.1 arms: A = main (flag off), B = Master (flag on), C = universal only (measurement). */
enum class ParityArm { A, B, C }

/** One quality row as the report keeps it: shape only, never an address. */
data class ParityRow(
    val label: String,
    val height: Int?,
    val codec: String?,
    val hasAudio: Boolean,
    val audioOnly: Boolean,
    val bytes: Long?,
    val kind: String,
    /** True when the row's one-byte check (or manifest read) opened; null when not checked. */
    val opened: Boolean?,
) {
    /** The join key between arms (§4.2: "a row that A has and B lacks"). */
    val key: String get() = "${kind}:${height ?: "audio"}:${codec?.substringBefore('.') ?: "?"}"
}

/** What one arm found for one case. Every field is already safe for the report. */
data class ArmObservation(
    val arm: ParityArm,
    /** [ParityCase.VIDEO], [ParityCase.NOT_HANDLED] or a failure name. */
    val outcome: String,
    val contentId: String? = null,
    val durationMillis: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val titleHash: String? = null,
    val rows: List<ParityRow> = emptyList(),
    val firstRowMs: Long? = null,
    val sheetReadyMs: Long? = null,
    val totalMs: Long? = null,
    val requests: Int? = null,
    val requestBytes: Long? = null,
    /** Site requests or captures seen after a terminal failure (must be 0). */
    val afterTerminal: Int? = null,
)

data class ParityEntry(
    val caseId: String,
    val site: String,
    val host: String?,
    val observations: List<ArmObservation>,
    val problems: List<String>,
    val skipped: String? = null,
)

/**
 * R1: the parity report holds host and content ID only. Addresses, cookies, headers and titles
 * never enter it: rows keep their shape, titles a short hash, IDs a strict pattern or a hash.
 * [toJson] fails closed if any `scheme://` text would still be written.
 */
object ParityReport {
    private val SAFE_ID = Regex("""[A-Za-z0-9_-]{1,64}""")
    private val SAFE_CODEC = Regex("""[A-Za-z0-9.+-]{1,32}""")
    private val SAFE_HOST = Regex("""[a-z0-9.-]{1,253}""")
    /** Report text: no `/`, so never an address; anything else is hashed. */
    private val SAFE_TEXT = Regex("""[A-Za-z0-9_:. +=,<>()%-]{0,160}""")

    /** A content ID as reported: kept when plainly an ID, else hashed. */
    fun safeId(raw: String?): String? {
        val id = raw?.substringAfterLast(':')?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (SAFE_ID.matches(id)) id else "sha256:" + hash(id)
    }

    fun titleHash(title: String?): String? =
        title?.trim()?.takeIf { it.isNotEmpty() }?.let { hash(it.lowercase()) }

    /** The page host only (lowercase, no port, no user info), or null. */
    fun host(url: String?): String? {
        val authority = url?.let { Regex("""^https?://([^/?#\\]*)""").find(it) }
            ?.groupValues?.get(1) ?: return null
        return authority.substringAfterLast('@').substringBefore(':').lowercase()
            .takeIf(SAFE_HOST::matches)
    }

    fun row(candidate: MediaCandidate, opened: Boolean?): ParityRow {
        val mime = candidate.mimeType.orEmpty().lowercase()
        val codecs = candidate.codecs.map { it.lowercase() }
        val audioOnly = mime.startsWith("audio/") ||
            (candidate.height == null && codecs.isNotEmpty() && codecs.all(::audioCodec))
        val hasAudio = audioOnly || candidate.audioCompanion != null || codecs.any(::audioCodec)
        val codec = codecs.firstOrNull { audioOnly || !audioCodec(it) }?.takeIf(SAFE_CODEC::matches)
        val label = when {
            audioOnly -> "audio"
            candidate.height != null -> "${candidate.height}p"
            else -> candidate.kind.name.lowercase()
        }
        return ParityRow(
            label, candidate.height, codec, hasAudio, audioOnly, candidate.contentLengthBytes,
            candidate.kind.name, opened,
        )
    }

    /** An arm's found video, from the main candidate and its rows. */
    fun found(
        arm: ParityArm,
        candidates: List<MediaCandidate>,
        opened: (MediaCandidate) -> Boolean? = { null },
        totalMs: Long? = null,
    ): ArmObservation {
        val main = candidates.firstOrNull { it.height != null } ?: candidates.firstOrNull()
        return ArmObservation(
            arm = arm,
            outcome = if (candidates.isEmpty()) "NO_MEDIA_FOUND" else ParityCase.VIDEO,
            contentId = safeId(candidates.firstNotNullOfOrNull { it.videoId }),
            durationMillis = candidates.firstNotNullOfOrNull { it.durationMillis },
            width = main?.width,
            height = candidates.mapNotNull { it.height }.maxOrNull(),
            titleHash = titleHash(candidates.firstNotNullOfOrNull { it.title }),
            rows = candidates.map { row(it, opened(it)) },
            totalMs = totalMs,
        )
    }

    fun toJson(entries: List<ParityEntry>, generatedAt: String): String {
        val out = StringBuilder()
        out.append("{\"version\":1,\"generatedAt\":").append(text(generatedAt))
        out.append(",\"entries\":[")
        entries.forEachIndexed { index, entry ->
            if (index > 0) out.append(',')
            entry(out, entry)
        }
        out.append("]}")
        val json = out.toString()
        check("://" !in json) { "parity report would leak an address" }
        return json
    }

    private fun entry(out: StringBuilder, entry: ParityEntry) {
        out.append("{\"case\":").append(text(entry.caseId))
        out.append(",\"site\":").append(text(entry.site))
        out.append(",\"host\":").append(text(entry.host?.takeIf(SAFE_HOST::matches)))
        out.append(",\"skipped\":").append(text(entry.skipped))
        out.append(",\"problems\":[")
        entry.problems.forEachIndexed { index, problem ->
            if (index > 0) out.append(',')
            out.append(text(problem))
        }
        out.append("],\"arms\":[")
        entry.observations.forEachIndexed { index, it ->
            if (index > 0) out.append(',')
            out.append("{\"arm\":").append(text(it.arm.name))
            out.append(",\"outcome\":").append(text(it.outcome))
            out.append(",\"contentId\":").append(text(it.contentId))
            out.append(",\"durationMs\":").append(it.durationMillis ?: "null")
            out.append(",\"width\":").append(it.width ?: "null")
            out.append(",\"height\":").append(it.height ?: "null")
            out.append(",\"titleHash\":").append(text(it.titleHash?.takeIf(SAFE_ID::matches)))
            out.append(",\"firstRowMs\":").append(it.firstRowMs ?: "null")
            out.append(",\"sheetReadyMs\":").append(it.sheetReadyMs ?: "null")
            out.append(",\"totalMs\":").append(it.totalMs ?: "null")
            out.append(",\"requests\":").append(it.requests ?: "null")
            out.append(",\"requestBytes\":").append(it.requestBytes ?: "null")
            out.append(",\"afterTerminal\":").append(it.afterTerminal ?: "null")
            out.append(",\"rows\":[")
            it.rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) out.append(',')
                out.append("{\"label\":").append(text(row.label))
                out.append(",\"height\":").append(row.height ?: "null")
                out.append(",\"codec\":").append(text(row.codec?.takeIf(SAFE_CODEC::matches)))
                out.append(",\"hasAudio\":").append(row.hasAudio)
                out.append(",\"audioOnly\":").append(row.audioOnly)
                out.append(",\"bytes\":").append(row.bytes ?: "null")
                out.append(",\"kind\":").append(text(row.kind))
                out.append(",\"opened\":").append(row.opened ?: "null")
                out.append('}')
            }
            out.append("]}")
        }
        out.append("]}")
    }

    /** A JSON string of report-safe text; anything else is replaced by its hash. */
    private fun text(value: String?): String {
        value ?: return "null"
        val safe = if (SAFE_TEXT.matches(value)) value else "sha256:" + hash(value)
        return "\"" + safe + "\""
    }

    private fun audioCodec(codec: String) =
        codec.startsWith("mp4a") || codec.startsWith("opus") || codec.startsWith("ac-3") ||
            codec.startsWith("ec-3") || codec.startsWith("vorbis") || codec.startsWith("flac")

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(16)
}
