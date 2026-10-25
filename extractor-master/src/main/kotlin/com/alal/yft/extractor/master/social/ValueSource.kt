package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure

/**
 * VK Master plan §8 (`docs/VK_MASTER_PLAN.md`; `MASTER_KEY_SOCIAL_PLAN.md` §3.2, guardrail S5):
 * where a rung's named value comes from — VK's web-client values today, Instagram's `doc_id` or
 * X's token later — YouTube's `VersionSource` rules (that file stays YouTube's) for any site.
 *
 * The S5 order for one value ([ValueKind]), a value rejected today ([rejected]) left out:
 * 1. an **R9-signed** value ([SignedValues]; for VK none: the owner's answer of 2026-10-25 is
 *    that R9 does not carry the web client's values, plan §2 and §9);
 * 2. a **self-healed** value read today ([heal]; memory only, for the day);
 * 3. a **learned** value ([SiteHealth.learn]: kept only after an OK answer and a V pass; the
 *    health keeps the value itself only for a public version, so this is VK's API version, never an
 *    ID or a secret);
 * 4. the **bundled** value the rung was built with;
 * 5. after a rejection, one heal read: **one** pattern-matched value ([ValueKind.anchor]'s group
 *    1, the whole value matching [ValueKind.pattern]) from a [ValueReader] (VK: [VkClientSource],
 *    the source agent A proved); at most once a day per value (the health's clock, claimed before
 *    the read), not while the site is stopped (a bot check or 429, which a read also starts), at
 *    most [ValueKind.maxBytes];
 * 6. ≤ [MAX_ALTERNATES] **bundled alternates** ([ValueAlternate]: each with its source and date).
 *
 * Never code (a value is [VALUE]-shaped: letters, digits and `._,%=+-`, never a quote, a space,
 * a slash or a colon), never invented, counted up or brute-forced: every value comes from one of
 * the places above. Nothing here logs; notes name a value by its [Candidate.label], never by its
 * value.
 */
internal class ValueSource(
    private val health: SiteHealth,
    private val reader: ValueReader? = null,
    private val signed: SignedValues = SignedValues.NONE,
) {
    enum class Origin { SIGNED, HEALED, LEARNED, BUNDLED, ALTERNATE }

    /** One value a rung may send; [source] is where it came from (a source ID, never an address). */
    class Candidate internal constructor(val kind: ValueKind, val value: String, val origin: Origin, val source: String) {
        /** For notes: "r9", the heal source's ID, "learned", "bundled" or "alternate" — never the value. */
        val label: String
            get() = when (origin) {
                Origin.SIGNED -> SIGNED
                Origin.HEALED -> source
                Origin.LEARNED -> LEARNED
                Origin.BUNDLED -> BUNDLED
                Origin.ALTERNATE -> "$ALTERNATE $source"
            }

        override fun toString(): String = "ValueSource.Candidate(${kind.site} ${kind.name}, $label)"
    }

    /** A heal read: the candidate, or why there is none (a reason, never a value). */
    class Read internal constructor(val candidate: Candidate?, val why: String) {
        override fun toString(): String = "ValueSource.Read(${candidate?.label ?: "-"}, $why)"
    }

    private class Healed(val candidate: Candidate, val at: Long)

    /** The value healed today per kind: memory only, never stored before OK + V (S5). */
    private val healed = HashMap<String, Healed>()

    /** The values rejected today per kind (memory only): never sent again that day. */
    private val rejectedToday = HashMap<String, LinkedHashMap<String, Long>>()

    /** The value to send now: signed, healed today, learned, else [bundled]; null when all were rejected today. */
    fun value(kind: ValueKind, bundled: String?): Candidate? =
        listOfNotNull(
            signed.valueOf(kind)?.let { Candidate(kind, it, Origin.SIGNED, SIGNED) },
            healedToday(kind),
            health.learned(kind.site, kind.name)?.takeIf { kind.publicVersion }?.let { learned ->
                learned.value?.let { Candidate(kind, it, Origin.LEARNED, learned.source) }
            },
            bundled?.let { Candidate(kind, it, Origin.BUNDLED, BUNDLED) },
        ).firstOrNull { valid(kind, it.value) && !isRejected(kind, it.value) }

    /**
     * The next value after a rejection ([rejected] first): [value], else one heal read, else the
     * first bundled alternate not rejected today; null when nothing is left (the rung gives up).
     */
    suspend fun next(kind: ValueKind, bundled: String?, userAgent: String?): Candidate? =
        value(kind, bundled) ?: heal(kind, userAgent).candidate ?: alternates(kind, bundled).firstOrNull()

    /**
     * One self-heal read of [kind] (S5), only after a value of it was rejected today: the value
     * healed today again, else one read of the source with the user's own [userAgent] — exactly
     * one pattern-matched value, not one rejected today.
     */
    suspend fun heal(kind: ValueKind, userAgent: String?): Read {
        healedToday(kind)?.let { return Read(it, "read today") }
        val reader = reader ?: return Read(null, "no source reader")
        val anchor = kind.anchor ?: return Read(null, "no source pattern")
        if (!SiteHealth.WORD.matches(reader.id)) return Read(null, "the source has no ID")
        if (!anyRejected(kind)) return Read(null, "not read (no value rejected today)")
        if (health.stopped(kind.site)) return Read(null, "not read (bot check or 429)")
        if (!health.claimRead(kind.site, kind.name)) return Read(null, "not read (read in the last day)")
        val text = when (val got = reader.read(kind, userAgent?.takeIf(String::isNotBlank), kind.maxBytes)) {
            is ExtractorHttpResult.Failure -> {
                if (got.reason in STOPS || got.statusCode == HTTP_TOO_MANY) health.stop(kind.site)
                val status = got.statusCode?.let { " $it" }.orEmpty()
                val note = got.details.firstOrNull()?.takeIf(NOTE::matches)?.let { ": $it" }.orEmpty()
                return Read(null, "read failed (${got.reason.name}$status$note)")
            }
            is ExtractorHttpResult.Success -> got.body
        }
        if (text.length > kind.maxBytes || text.toByteArray(Charsets.UTF_8).size > kind.maxBytes) {
            return Read(null, "over ${kind.maxBytes} bytes")
        }
        val values = anchor.findAll(text).mapNotNull { it.groups[1]?.value }.distinct().take(2).toList()
        val value = when (values.size) {
            0 -> return Read(null, "no value")
            1 -> values.single()
            else -> return Read(null, "more than one value")
        }
        if (!valid(kind, value)) return Read(null, "not the value's pattern")
        if (isRejected(kind, value)) return Read(null, "the rejected value")
        val candidate = Candidate(kind, value, Origin.HEALED, reader.id)
        synchronized(healed) { healed[key(kind)] = Healed(candidate, health.now()) }
        return Read(candidate, "read")
    }

    /** The bundled alternates of [kind] (≤ [MAX_ALTERNATES], pattern-valid), [bundled] and today's rejected left out. */
    fun alternates(kind: ValueKind, bundled: String?): List<Candidate> =
        kind.alternates
            .filter { valid(kind, it.value) && it.value != bundled && !isRejected(kind, it.value) }
            .map { Candidate(kind, it.value, Origin.ALTERNATE, it.source) }

    /**
     * [candidate] answered [answerClass]: kept (true) only after an OK answer (`DIRECT` or
     * `PARTIAL`) whose rows passed V ([rowsPassed]) — a healed, learned or alternate value is then
     * learned (its source and date; the value only for a public version). Signed and bundled
     * values are the config's and the build's: nothing to keep.
     */
    fun kept(candidate: Candidate, answerClass: AnswerClass, rowsPassed: Boolean): Boolean {
        if (!rowsPassed || answerClass.next != Next.CHECK_ROWS) return false
        if (candidate.origin == Origin.SIGNED || candidate.origin == Origin.BUNDLED) return false
        val kind = candidate.kind
        health.learn(kind.site, kind.name, candidate.source, candidate.value.takeIf { kind.publicVersion })
        return true
    }

    /** [candidate] was rejected (`VALUE_REJECTED`): not sent again today; a learned or healed one dropped. */
    fun rejected(candidate: Candidate) {
        val kind = candidate.kind
        synchronized(rejectedToday) {
            val values = rejectedToday.getOrPut(key(kind)) { LinkedHashMap() }
            values.remove(candidate.value)
            values[candidate.value] = health.now()
            while (values.size > MAX_REJECTED) values.remove(values.keys.first())
        }
        val learned = health.learned(kind.site, kind.name)
        if (candidate.origin == Origin.LEARNED || (learned?.value != null && learned.value == candidate.value)) {
            health.forget(kind.site, kind.name)
        }
        synchronized(healed) {
            if (healed[key(kind)]?.candidate?.value == candidate.value) healed.remove(key(kind))
        }
    }

    private fun healedToday(kind: ValueKind): Candidate? = synchronized(healed) {
        val kept = healed[key(kind)] ?: return null
        if (health.now() - kept.at in 0 until SiteHealth.DAY_MILLIS) return kept.candidate
        healed.remove(key(kind))
        null
    }

    private fun isRejected(kind: ValueKind, value: String): Boolean = synchronized(rejectedToday) {
        val at = rejectedToday[key(kind)]?.get(value) ?: return false
        health.now() - at in 0 until SiteHealth.DAY_MILLIS
    }

    private fun anyRejected(kind: ValueKind): Boolean = synchronized(rejectedToday) {
        rejectedToday[key(kind)]?.values?.any { health.now() - it in 0 until SiteHealth.DAY_MILLIS } == true
    }

    override fun toString(): String = "ValueSource(reader=${reader != null})"

    companion object {
        const val MAX_ALTERNATES = 2
        const val SIGNED = "r9"
        const val LEARNED = "learned"
        const val BUNDLED = "bundled"
        const val ALTERNATE = "alternate"

        /** Every value a rung may send: no quote, space, slash, colon or bracket — never code or an address. */
        val VALUE = Regex("^[A-Za-z0-9._,%=+-]{1,256}$")

        /** A reader's note that may join a failed read's reason: short lowercase words, never an address. */
        private val NOTE = Regex("^[a-z][a-z0-9 _.()-]{0,63}$")

        private const val MAX_REJECTED = 8
        private const val HTTP_TOO_MANY = 429
        private val STOPS = setOf(SiteExtractionFailure.RATE_LIMITED, SiteExtractionFailure.BOT_CHECK)

        fun valid(kind: ValueKind, value: String): Boolean = VALUE.matches(value) && kind.pattern.matches(value)

        private fun key(kind: ValueKind): String = "${kind.site}/${kind.name}"
    }
}

/**
 * One named value of a site's rung ([ValueSource]): its [site] ([SiteHealth.RUNGS]) and [name]
 * ([SiteHealth.NAME]), the whole value's strict [pattern], where a heal read finds it ([anchor]:
 * group 1 is the value; null: never healed), [publicVersion] (a public version string such as
 * VK's API version: the health may keep the value itself; every other kind — an ID, a client
 * secret, a `doc_id` — is kept by name, source and date only), the bundled [alternates] (≤ 2) and
 * the most a heal read takes ([maxBytes]).
 */
internal class ValueKind(
    val site: String,
    val name: String,
    val pattern: Regex,
    val anchor: Regex? = null,
    val publicVersion: Boolean = false,
    alternates: List<ValueAlternate> = emptyList(),
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    val alternates: List<ValueAlternate> = alternates.take(ValueSource.MAX_ALTERNATES)

    init {
        require(site in SiteHealth.RUNGS) { "unknown site" }
        require(SiteHealth.NAME.matches(name)) { "not a value name" }
        require(maxBytes in 1..MAX_BYTES) { "maxBytes out of bounds" }
    }

    override fun toString(): String = "ValueKind($site $name)"

    companion object {
        const val DEFAULT_MAX_BYTES = 64L * 1024
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}

/** A bundled alternate of a value: from a public [source] (a source ID) on [date] (`YYYY-MM-DD`). */
internal class ValueAlternate(val value: String, val source: String, val date: String) {
    init {
        require(SiteHealth.WORD.matches(source)) { "not a source ID" }
        require(DATE.matches(date)) { "not a date" }
    }

    override fun toString(): String = "ValueAlternate($source, $date)"

    private companion object {
        val DATE = Regex("^2[0-9]{3}-[0-9]{2}-[0-9]{2}$")
    }
}

/**
 * R9's signed value for a kind (S5's first place): null when the config in use carries none. For
 * VK always null — the owner's answer (2026-10-25, VK plan §2 and §9): R9 does not carry the web
 * client's values (known limit: a rotated value with a broken source needs an APK; V3 serves
 * meanwhile). The slot stays for a site whose values R9 may carry later (S9).
 */
internal fun interface SignedValues {
    fun valueOf(kind: ValueKind): String?

    companion object {
        val NONE = SignedValues { null }
    }
}

/**
 * Reads a heal source for [ValueSource] (VK: [VkClientSource], vkvideo.ru's page then one script):
 * only the requests of the source this reader knows, with the user's own User-Agent ([read]'s
 * `userAgent`) and no cookie or token, **a redirected answer is a failure** (never used), the
 * text returned at most `maxBytes`. A failure's first detail, a short lowercase note, joins the
 * read's reason ([ValueSource.Read.why]). Nothing is logged.
 */
internal interface ValueReader {
    /** The source's ID for notes and the health ([SiteHealth.WORD]: never an address). */
    val id: String

    suspend fun read(kind: ValueKind, userAgent: String?, maxBytes: Long): ExtractorHttpResult
}
