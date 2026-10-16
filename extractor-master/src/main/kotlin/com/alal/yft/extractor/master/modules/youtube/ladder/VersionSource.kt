package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.path
import java.net.URI

/**
 * Phase 1.3 D5a — A's versions, built after the close (MASTER_KEY_PHASE1_3_PLAN.md §3 A, §4 D5a
 * "As built (A)", guardrail 5, §9 items 3 and 13). When YouTube rejects a client's version
 * (`VERSION_REJECTED`), the ladder's A rung asks once more per version, in guardrail 5's order:
 *
 * 1. the **learned** working version ([learned]; stored in the health only after an answer judged
 *    OK with a row that passed V, dropped when it is rejected or no longer newer than the bundled
 *    one);
 * 2. (a signed version: D8, dropped by the owner, §9 item 4);
 * 3. a **self-healed** version ([selfHealed]): one value read from the client's first live version
 *    source in the table ([MasterClientTable.Entry.versionSources]) with the census's reader
 *    ([CensusSources.DOCUMENTS]): exactly one value, the client's strict pattern, newer than the
 *    bundled one; never code, never invented or counted up. Owner, 2026-10-15 (§9 item 3): only on
 *    [SOURCE_HOSTS] (`raw.githubusercontent.com`), no redirect followed, ≤ 64 KiB, text only, in a
 *    lookup only after `VERSION_REJECTED`, at most once a day per identity (the health's clock,
 *    claimed before the read: a failed read spends the day too); not after a bot check
 *    (guardrail 4); the user's own User-Agent only, no cookie, no token ([VersionSourceReader]);
 * 4. ≤ [MAX_ALTERNATES] **bundled alternates** from a public reference ([alternates]; none today).
 *
 * The value read today is kept in memory only, for a day, so a later lookup (after a bot check or
 * an answer without a V pass) may try it again without a second read; it is never stored before
 * OK + V. Only the version field changes ([withVersion]: the body's `clientVersion` and the version
 * header); the user agent and device stay. Nothing here logs; notes name a version by its
 * [Candidate.label], never by its value.
 */
internal class VersionSource(
    private val health: ClientHealth,
    private val reader: VersionSourceReader?,
) {
    enum class Kind { LEARNED, SOURCE, BUNDLED }

    /** One version A may send. [source] is where it came from (a source ID or "bundled"). */
    class Candidate internal constructor(val version: String, val kind: Kind, val source: String) {
        /** For notes: "learned", the source's ID or "bundled" — never the value. */
        val label: String
            get() = when (kind) {
                Kind.LEARNED -> LEARNED
                Kind.SOURCE -> source
                Kind.BUNDLED -> BUNDLED
            }

        override fun toString(): String = "VersionSource.Candidate($label)"
    }

    /** A self-healed lookup: the candidate, or why there is none (a reason, never a value). */
    class Read internal constructor(val candidate: Candidate?, val why: String) {
        override fun toString(): String = "VersionSource.Read(${candidate?.label ?: "-"}, $why)"
    }

    /** A player request with only its version changed. */
    class Edited internal constructor(val body: String, val headers: Map<String, String>)

    private class Fetched(val candidate: Candidate, val at: Long)

    /** The value read today per identity: memory only, never stored (guardrail 5). */
    private val fetched = HashMap<String, Fetched>()

    /** The learned working version of [entry], or null; one that no longer qualifies is forgotten. */
    fun learned(entry: MasterClientTable.Entry, bundled: String): Candidate? {
        val version = health.learnedVersion(entry.id) ?: return null
        if (!newer(entry, version, bundled)) {
            health.forgetVersion(entry.id)
            return null
        }
        return Candidate(version, Kind.LEARNED, health.client(entry.id)?.learnedSource ?: LEARNED)
    }

    /** A self-healed version of [entry]: today's value, else one read of its first live source. */
    suspend fun selfHealed(entry: MasterClientTable.Entry, bundled: String, userAgent: String?): Read {
        synchronized(fetched) {
            val kept = fetched[entry.id]
            if (kept != null) {
                val age = health.now() - kept.at
                if (age in 0 until ClientHealth.DAY_MILLIS && newer(entry, kept.candidate.version, bundled)) {
                    return Read(kept.candidate, "read today")
                }
                fetched.remove(entry.id)
            }
        }
        val source = entry.versionSources.firstOrNull { it.status == MasterClientTable.Status.LIVE && urlOf(it.id) != null }
            ?: return Read(null, "no live source")
        val url = urlOf(source.id) ?: return Read(null, "no live source")
        if (!approved(url, source.host)) return Read(null, "not on an approved host")
        val values = CensusSources.DOCUMENTS.firstOrNull { it.url == url }?.readers?.firstOrNull { it.identity == entry.id }?.values
            ?: return Read(null, "no reader for this client")
        val reader = reader ?: return Read(null, "not read (no source reader)")
        if (health.botChecked()) return Read(null, "not read (bot check)")
        if (!health.claimSourceRead(entry.id)) return Read(null, "not read (read in the last day)")
        val headers = userAgent?.takeIf(String::isNotBlank)?.let { mapOf(USER_AGENT to it) }.orEmpty()
        val text = when (val got = reader.read(url, headers, CensusSources.MAX_SOURCE_BYTES)) {
            is ExtractorHttpResult.Failure -> return Read(null, "read failed (${got.reason.name}${got.statusCode?.let { " $it" }.orEmpty()})")
            is ExtractorHttpResult.Success -> when {
                got.finalUrl != url -> return Read(null, "redirected, not read")
                !isText(got.contentType) -> return Read(null, "not text")
                got.body.toByteArray(Charsets.UTF_8).size > CensusSources.MAX_SOURCE_BYTES -> return Read(null, "over 64 KiB")
                else -> got.body
            }
        }
        val fact = CensusSources.fact(entry.id, source.id, reachable = true, note = null, values = values(text), bundled = bundled)
        val value = fact.value?.takeIf { fact.pattern && fact.newer == true && newer(entry, it, bundled) }
            ?: return Read(null, "no newer version (${whyNot(fact)})")
        val candidate = Candidate(value, Kind.SOURCE, source.id)
        synchronized(fetched) { fetched[entry.id] = Fetched(candidate, health.now()) }
        return Read(candidate, "read")
    }

    /** The bundled alternates of [entry] (≤ 2, each from a public reference), in table order. */
    fun alternates(entry: MasterClientTable.Entry, bundled: String): List<Candidate> =
        entry.alternates.take(MAX_ALTERNATES)
            .filter { valid(entry, it.version) && it.version != bundled }
            .map { Candidate(it.version, Kind.BUNDLED, BUNDLED) }

    /** [candidate] was rejected too: a learned version is forgotten, today's value dropped. */
    fun rejected(id: String, candidate: Candidate) {
        if (candidate.kind == Kind.LEARNED) health.forgetVersion(id)
        synchronized(fetched) {
            if (fetched[id]?.candidate?.version == candidate.version) fetched.remove(id)
        }
    }

    private fun valid(entry: MasterClientTable.Entry, version: String): Boolean =
        entry.versionPattern.matches(version) && ClientHealth.VERSION.matches(version)

    private fun newer(entry: MasterClientTable.Entry, version: String, bundled: String): Boolean =
        valid(entry, version) && (CensusSources.compare(version, bundled) ?: 0) > 0

    private fun whyNot(fact: CensusVersion): String = when {
        fact.values == 0 -> "no value"
        fact.values > 1 -> "${fact.values} values"
        !fact.pattern -> "not the version pattern"
        fact.sameAsBundled == true -> "the bundled value"
        else -> "not newer"
    }

    override fun toString(): String = "VersionSource(reader=${reader != null})"

    companion object {
        /** Owner, 2026-10-15 (§9 item 3): the only host a lookup may read a version from. */
        val SOURCE_HOSTS: Set<String> = setOf("raw.githubusercontent.com")
        const val MAX_ALTERNATES = 2
        const val VERSION_HEADER = "X-YouTube-Client-Version"
        const val LEARNED = "learned"
        const val BUNDLED = "bundled"
        private const val USER_AGENT = "User-Agent"
        private const val TEXT_PLAIN = "text/plain"

        /** The address of a table source A may read (the reference tables), or null. */
        fun urlOf(sourceId: String): String? = when (sourceId) {
            "yt-dlp" -> CensusSources.YT_DLP_URL
            "youtube.js" -> CensusSources.YOUTUBE_JS_URL
            else -> null
        }

        /** HTTPS on [host], an approved source host, with no user, port or fragment. */
        fun approved(url: String, host: String): Boolean {
            if (host !in SOURCE_HOSTS) return false
            val address = runCatching { URI(url) }.getOrNull() ?: return false
            return address.scheme == "https" && address.rawUserInfo == null && address.port == -1 &&
                address.rawFragment == null && address.host?.lowercase() == host
        }

        /** The `clientVersion` of a player request [body] whose client is [clientName], or null. */
        fun versionOf(body: String, clientName: String): String? {
            val client = ShapeLocator.parse(body).path("context", "client")
            if (client.path("clientName").asStringOrNull != clientName) return null
            return client.path("clientVersion").asStringOrNull
        }

        /**
         * [body] and [headers] with version [from] replaced by [to]: the body's one
         * `"clientVersion":"<from>"` field and the version header when it says [from]; every other
         * byte, header and header order stays. Null when either version is not digits and dots,
         * they are equal, or the field is not there exactly once for [clientName].
         */
        fun withVersion(body: String, headers: Map<String, String>, clientName: String, from: String, to: String): Edited? {
            if (from == to || !ClientHealth.VERSION.matches(from) || !ClientHealth.VERSION.matches(to)) return null
            val needle = "\"clientVersion\":\"$from\""
            val first = body.indexOf(needle)
            if (first < 0 || body.indexOf(needle, first + 1) >= 0) return null
            val edited = body.replaceRange(first, first + needle.length, "\"clientVersion\":\"$to\"")
            if (versionOf(edited, clientName) != to) return null
            val out = LinkedHashMap<String, String>()
            for ((name, value) in headers) out[name] = if (name.equals(VERSION_HEADER, ignoreCase = true) && value == from) to else value
            return Edited(edited, out)
        }

        private fun isText(contentType: String?): Boolean =
            contentType?.substringBefore(';')?.trim()?.equals(TEXT_PLAIN, ignoreCase = true) == true
    }
}

/**
 * Reads a version source for A (D5a): one GET of [url] with [headers] (the user's User-Agent
 * only), no cookie, **no redirect followed** (a redirect is a failure), at most [maxBodyBytes]
 * read, as text. The app's reader allows HTTPS on `raw.githubusercontent.com` only; nothing is
 * logged.
 */
fun interface VersionSourceReader {
    suspend fun read(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult
}
