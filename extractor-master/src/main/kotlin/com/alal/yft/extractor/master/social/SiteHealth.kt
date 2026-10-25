package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.solver.OwnSolverTextFile

/**
 * VK Master plan §8 (`docs/VK_MASTER_PLAN.md`; `MASTER_KEY_SOCIAL_PLAN.md` §3.2, guardrail S5):
 * the social sites' health — YouTube's `ClientHealth` design (that file stays YouTube's) for the
 * site packs, data only.
 *
 * Per site and rung ([RUNGS]: the rung names R9's `rungsOff` uses, and VK's V1–V3): the last
 * class and its time, the last pass (rows that passed V), the cooldown end and the asked and
 * passed counts. Per site: the last good rung, the stop window after a bot check or HTTP 429
 * ([SocialCooldown.COOLDOWN_MILLIS], 30 min; it quiets only the rungs that send a request, a
 * value read included — [mayAsk]), the value-read clock ([claimRead]: at most one read a day per
 * value, claimed before the read, so a failed read spends the day too) and the last learned value
 * per name ([learn]: its source and date; the value itself only when it is a public version —
 * digits and dots, [VERSION] — the way `ClientHealth` keeps a version, or one of [VALUES]: agent
 * D's wiring keeps VK's two healed values, the web client's public client id and the API version,
 * each by its own strict pattern, so a value healed and passed survives a restart).
 *
 * Cooldowns: `VALUE_REJECTED` 1 h, `SHAPE_CHANGED` 24 h ([cooldownOf]); a pass ends a rung's
 * cooldown, and so does [clearCooldowns] (a new R9 config, agent D's wiring).
 *
 * Kept in memory and, when [file] is given, as one JSON text (agent D's wiring:
 * `files/master/site-health.json` through an `AtomicTextFile`, read on first use off the lookup
 * thread). It records never a token, a cookie, an address, a post or user ID or a secret value
 * (VK's client secret is never read, healed or kept): every stored string is checked against a
 * strict pattern on write and on read ([keeps] for a value), anything else is dropped, and a file
 * that cannot be read starts empty. Nothing here logs.
 */
internal class SiteHealth(
    private val file: OwnSolverTextFile? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** One rung of a site ([RUNGS]): what it answered last and whether its rows passed V. */
    class Rung internal constructor(val site: String, val name: String) {
        var lastClass: AnswerClass? = null
            internal set
        var lastAt: Long = 0
            internal set
        var lastPass: Long? = null
            internal set
        var cooldownUntil: Long? = null
            internal set
        var asked: Int = 0
            internal set
        var passed: Int = 0
            internal set

        override fun toString(): String = "SiteHealth.Rung($site $name, ${lastClass ?: "-"}, asked=$asked, passed=$passed)"
    }

    /**
     * A value [ValueSource] kept for a site after an OK answer and a V pass: its [name], its
     * [source] (a source ID, never an address) and when; [value] only for a public version.
     */
    class Learned internal constructor(val name: String, val source: String, val at: Long, val value: String?) {
        override fun toString(): String = "SiteHealth.Learned($name, $source, ${day(at)})"
    }

    private class Site(val id: String) {
        val rungs = LinkedHashMap<String, Rung>()
        var goodRung: String? = null
        var goodAt: Long = 0
        var stopUntil: Long = 0
        val reads = LinkedHashMap<String, Long>()
        val learned = LinkedHashMap<String, Learned>()
    }

    private val sites = LinkedHashMap<String, Site>()
    private var loaded = false

    @Synchronized
    fun rung(site: String, rung: String): Rung? = load().sites[site]?.rungs?.get(rung)

    /**
     * Records [answerClass] for [rung] of [site]. [rowsPassed]: the answer's rows passed V (a
     * `DIRECT` or `PARTIAL` answer only): a pass, the site's good rung, the cooldown ended. A bot
     * check or 429 stops the site's request-sending rungs for 30 min; [cooldownOf] cools the rung.
     */
    @Synchronized
    fun record(site: String, rung: String, answerClass: AnswerClass, rowsPassed: Boolean = false) {
        if (!known(site, rung)) return
        load()
        val now = clock()
        val state = siteOf(site)
        val entry = state.rungs.getOrPut(rung) { Rung(site, rung) }
        entry.asked += 1
        entry.lastClass = answerClass
        entry.lastAt = now
        when {
            rowsPassed && answerClass.next == Next.CHECK_ROWS -> {
                entry.lastPass = now
                entry.passed += 1
                entry.cooldownUntil = null
                state.goodRung = rung
                state.goodAt = now
            }
            answerClass == AnswerClass.BOT_CHECK || answerClass == AnswerClass.RATE_LIMITED ->
                state.stopUntil = now + SocialCooldown.COOLDOWN_MILLIS
            else -> cooldownOf(answerClass)?.let { entry.cooldownUntil = now + it }
        }
        save()
    }

    /** A bot check or 429 seen outside a rung (a value read, the request recorder): 30 min quiet. */
    @Synchronized
    fun stop(site: String) {
        if (site !in RUNGS) return
        load()
        siteOf(site).stopUntil = clock() + SocialCooldown.COOLDOWN_MILLIS
        save()
    }

    /** True inside the 30 min after [site]'s bot check or 429: its request-sending rungs stay quiet. */
    @Synchronized
    fun stopped(site: String): Boolean = clock() < (load().sites[site]?.stopUntil ?: 0L)

    @Synchronized
    fun cooling(site: String, rung: String): Boolean =
        load().sites[site]?.rungs?.get(rung)?.cooldownUntil?.let { clock() < it } == true

    /** Whether [rung] of [site] may be asked now; [sendsRequest]: a rung (or read) that sends one. */
    @Synchronized
    fun mayAsk(site: String, rung: String, sendsRequest: Boolean = true): Boolean =
        known(site, rung) && !(sendsRequest && stopped(site)) && !cooling(site, rung)

    @Synchronized
    fun passedWithin(site: String, rung: String, millis: Long): Boolean =
        load().sites[site]?.rungs?.get(rung)?.lastPass?.let { clock() - it in 0..millis } == true

    /** The rung of [site] whose rows passed V last, or null. */
    @Synchronized
    fun goodRung(site: String): String? = load().sites[site]?.goodRung

    /** Ends every cooldown of [site] (all sites when null): a new R9 config may have fixed them. */
    @Synchronized
    fun clearCooldowns(site: String? = null) {
        load()
        var changed = false
        sites.values.filter { site == null || it.id == site }.flatMap { it.rungs.values }.forEach { rung ->
            if (rung.cooldownUntil != null) {
                rung.cooldownUntil = null
                changed = true
            }
        }
        if (changed) save()
    }

    /** Whether [site]'s value [name] may be read now: no read of it in the last day. */
    @Synchronized
    fun mayRead(site: String, name: String): Boolean =
        load().sites[site]?.reads?.get(name)?.let { clock() - it !in 0 until DAY_MILLIS } ?: true

    /**
     * A self-heal read of [site]'s value [name] (S5): true — and the day's read spent — when it may
     * read now; false when it read in the last day, the site is stopped, or the name is not a name.
     */
    @Synchronized
    fun claimRead(site: String, name: String): Boolean {
        if (site !in RUNGS || !NAME.matches(name) || stopped(site) || !mayRead(site, name)) return false
        val reads = siteOf(site).reads
        reads.remove(name)
        reads[name] = clock()
        while (reads.size > MAX_NAMES) reads.remove(reads.keys.first())
        save()
        return true
    }

    /**
     * Keeps a value that answered OK with rows that passed V ([ValueSource.kept]): its [name], its
     * [source] and the time; [value] is kept only when [keeps] takes it (its name's own pattern in
     * [VALUES], else a public version, [VERSION]).
     */
    @Synchronized
    fun learn(site: String, name: String, source: String, value: String? = null) {
        if (site !in RUNGS || !NAME.matches(name) || !WORD.matches(source)) return
        val learned = load().siteOf(site).learned
        learned.remove(name)
        learned[name] = Learned(name, source, clock(), value?.takeIf { keeps(site, name, it) })
        while (learned.size > MAX_NAMES) learned.remove(learned.keys.first())
        save()
    }

    @Synchronized
    fun learned(site: String, name: String): Learned? = load().sites[site]?.learned?.get(name)

    /** A learned value that was rejected is dropped. */
    @Synchronized
    fun forget(site: String, name: String) {
        if (load().sites[site]?.learned?.remove(name) != null) save()
    }

    /** The health's clock (a value read today, in [ValueSource]'s memory). */
    fun now(): Long = clock()

    /** Lines for a debug screen: classes, minutes, counts, names and sources — never a value. */
    @Synchronized
    fun describe(): List<String> {
        load()
        val now = clock()
        return sites.values.flatMap { site ->
            listOfNotNull(
                "${site.id}: stopped (bot check or 429), request-sending rungs quiet".takeIf { now < site.stopUntil },
                site.goodRung?.let { "${site.id}: last good rung $it, ${minutes(now, site.goodAt)} min ago" },
            ) + site.rungs.values.map { rung ->
                buildString {
                    append(site.id).append(' ').append(rung.name).append(": ").append(rung.lastClass ?: "-")
                    append(", ").append(minutes(now, rung.lastAt)).append(" min ago")
                    append(", asked ").append(rung.asked).append(", passed ").append(rung.passed)
                    if (rung.cooldownUntil?.let { now < it } == true) append(", cooling")
                }
            } + site.learned.values.map { learned ->
                "${site.id} ${learned.name}: learned from ${learned.source}, ${day(learned.at)}"
            } + site.reads.entries.map { (name, at) -> "${site.id} ${name}: read ${day(at)}" }
        }
    }

    @Synchronized
    fun toJson(): String = buildString {
        load()
        append("{\"v\":1,\"sites\":{")
        sites.values.forEachIndexed { index, site ->
            if (index > 0) append(',')
            append('"').append(site.id).append("\":{\"stopUntil\":").append(site.stopUntil)
            site.goodRung?.let { append(",\"good\":\"").append(it).append("\",\"goodAt\":").append(site.goodAt) }
            append(",\"rungs\":{")
            site.rungs.values.forEachIndexed { i, rung ->
                if (i > 0) append(',')
                append('"').append(rung.name).append("\":{\"asked\":").append(rung.asked)
                append(",\"passed\":").append(rung.passed).append(",\"at\":").append(rung.lastAt)
                rung.lastClass?.let { append(",\"class\":\"").append(it.name).append('"') }
                rung.lastPass?.let { append(",\"pass\":").append(it) }
                rung.cooldownUntil?.let { append(",\"until\":").append(it) }
                append('}')
            }
            append("},\"reads\":{")
            site.reads.entries.forEachIndexed { i, (name, at) ->
                if (i > 0) append(',')
                append('"').append(name).append("\":").append(at)
            }
            append("},\"learned\":{")
            site.learned.values.forEachIndexed { i, learned ->
                if (i > 0) append(',')
                append('"').append(learned.name).append("\":{\"source\":\"").append(learned.source)
                append("\",\"at\":").append(learned.at)
                learned.value?.let { append(",\"value\":\"").append(it).append('"') }
                append('}')
            }
            append("}}")
        }
        append("}}")
    }

    private fun siteOf(site: String): Site = sites.getOrPut(site) { Site(site) }

    private fun save() {
        file?.write(toJson())
    }

    private fun load(): SiteHealth {
        if (loaded) return this
        loaded = true
        val text = file?.read()?.takeIf { it.length <= MAX_FILE_CHARS } ?: return this
        val root = BoundedJsonParser.parse(text, maxDepth = 8, maxNodes = 4_000) as? JsonValue.Object ?: return this
        if (number(root.entries["v"]) != 1L) return this
        (root.entries["sites"] as? JsonValue.Object)?.entries?.forEach { (id, value) ->
            val node = value as? JsonValue.Object ?: return@forEach
            val names = RUNGS[id] ?: return@forEach
            val site = Site(id)
            site.stopUntil = number(node.entries["stopUntil"]) ?: 0L
            text(node.entries["good"])?.takeIf { it in names }?.let { good ->
                site.goodRung = good
                site.goodAt = number(node.entries["goodAt"]) ?: 0L
            }
            (node.entries["rungs"] as? JsonValue.Object)?.entries?.forEach { (name, entry) ->
                val fields = (entry as? JsonValue.Object)?.entries ?: return@forEach
                if (name !in names) return@forEach
                site.rungs[name] = Rung(id, name).apply {
                    asked = count(fields["asked"])
                    passed = count(fields["passed"])
                    lastAt = number(fields["at"]) ?: 0L
                    lastClass = text(fields["class"])?.let { name -> AnswerClass.entries.firstOrNull { it.name == name } }
                    lastPass = number(fields["pass"])
                    cooldownUntil = number(fields["until"])
                }
            }
            (node.entries["reads"] as? JsonValue.Object)?.entries?.forEach { (name, at) ->
                if (NAME.matches(name) && site.reads.size < MAX_NAMES) number(at)?.let { site.reads[name] = it }
            }
            (node.entries["learned"] as? JsonValue.Object)?.entries?.forEach { (name, entry) ->
                val fields = (entry as? JsonValue.Object)?.entries ?: return@forEach
                val source = text(fields["source"])?.takeIf(WORD::matches) ?: return@forEach
                val at = number(fields["at"]) ?: return@forEach
                if (!NAME.matches(name) || site.learned.size >= MAX_NAMES) return@forEach
                site.learned[name] = Learned(name, source, at, text(fields["value"])?.takeIf { keeps(id, name, it) })
            }
            sites[id] = site
        }
        return this
    }

    private fun known(site: String, rung: String): Boolean = RUNGS[site]?.contains(rung) == true

    private fun number(value: JsonValue?): Long? = (value as? JsonValue.Number)?.text?.toLongOrNull()?.takeIf { it >= 0 }

    private fun count(value: JsonValue?): Int = number(value)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0

    private fun text(value: JsonValue?): String? = (value as? JsonValue.Text)?.value

    private fun minutes(now: Long, at: Long): Long = (now - at).coerceAtLeast(0) / MINUTE_MILLIS

    companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        const val DAY_MILLIS = 24 * HOUR_MILLIS

        /** VK's site ID (agent B's `VkLadder.SITE_ID` must say the same). */
        const val VK = "vk"

        /** The sites and their rungs, by the names R9's `rungsOff` uses (schema 2 adds V1–V3). */
        val RUNGS: Map<String, List<String>> = linkedMapOf(
            FacebookLadder.SITE_ID to listOf("F1", "F2", "F3"),
            TikTokLadder.SITE_ID to listOf("T1", "T2", "T3"),
            XLadder.SITE_ID to listOf("X1", "X3"),
            InstagramLadder.SITE_ID to listOf("I2", InstagramLadder.PROBE_RUNG, "I3"),
            VK to listOf("V1", "V2", "V3"),
        )

        /** The values the health keeps by default: a public version, digits and dots (as `ClientHealth`'s). */
        val VERSION = Regex("^[0-9]{1,4}(\\.[0-9]{1,8}){1,3}$")

        /**
         * Agent D's wiring (VK-Master plan §8, item 5): the only other values kept, per site and
         * name, each by its own strict pattern, checked on [learn] and again on load: VK's two
         * healed values — the web client's client id (VK's public app ID, digits) and the API
         * version. Never the client secret, a token or a cookie.
         */
        val VALUES: Map<String, Map<String, Regex>> = mapOf(
            VK to mapOf(
                "clientId" to Regex("^[0-9]{6,10}$"),
                "apiVersion" to Regex("^5\\.[0-9]{1,4}$"),
            ),
        )

        /** Whether [value] of [site]'s [name] may be kept: its own pattern in [VALUES], else [VERSION]. */
        fun keeps(site: String, name: String, value: String): Boolean =
            VALUES[site]?.get(name)?.matches(value) ?: VERSION.matches(value)

        /** Whether [site]'s [name] has its own pattern in [VALUES] (its value is kept, not only its source). */
        fun keepsValue(site: String, name: String): Boolean = VALUES[site]?.containsKey(name) == true

        /** A value's name ([ValueKind.name]). */
        val NAME = Regex("^[a-z][A-Za-z0-9_]{0,31}$")

        /** A source ID ("bundled", "r9", a source's short name): never an address. */
        val WORD = Regex("^[a-z][a-z0-9.-]{0,31}$")

        const val MAX_NAMES = 8
        private const val MAX_FILE_CHARS = 64 * 1024

        fun cooldownOf(answerClass: AnswerClass): Long? = when (answerClass) {
            AnswerClass.VALUE_REJECTED -> HOUR_MILLIS
            AnswerClass.SHAPE_CHANGED -> DAY_MILLIS
            else -> null
        }

        /** [millis] as its UTC day, `YYYY-MM-DD` (java.time is missing below Android 8). */
        fun day(millis: Long): String {
            // Howard Hinnant's civil_from_days.
            val z = Math.floorDiv(millis, DAY_MILLIS) + 719_468
            val era = Math.floorDiv(z, 146_097L)
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = if (mp < 10) mp + 3 else mp - 9
            val y = yoe + era * 400 + if (m <= 2) 1 else 0
            return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
        }
    }
}
