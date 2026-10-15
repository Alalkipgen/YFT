package com.alal.yft.extractor.master.modules.youtube.ladder

/**
 * Phase 1.3 D0: the weekly check's diff (MASTER_KEY_PHASE1_3_PLAN.md §4 D0 "Weekly check (W)").
 *
 * Each answer of the new run is compared with the latest earlier run that asked the same client
 * the same way about the same video (same slot tag). The first line is `NO CHANGE`,
 * `CHANGED (n)` or `BROKEN (n)` with the days since the last run; then one line per change, each
 * naming its runbook step (§7). **BROKEN** is a worsening: a class that got worse (DIRECT → any
 * other class, a working class → a refusal or a verdict), V `PASS` → `FAIL`, an H step `2xx` →
 * anything else, a key or a duration mismatch on H, a **BREAKING** shape (a removed path the copied
 * parser reads, [ShapeFingerprint.PARSER_READS]), or a version accepted → rejected. Shape paths are
 * gathered per client and step over all its videos; a path one video gained and another lost is
 * not a change (owner, 2026-10-15). Bot checks (`ClientCensus`, owner 2026-10-15) are shown even
 * on a first run: a bot check the run answered as R6 whose answer (`R0+vd`) was DIRECT with V
 * passed is an INFO line — no runbook step, not counted (owner, 2026-10-15); any other answered
 * bot check is one CHANGED line, and the bot check that stopped the run (a request that already
 * carried visitor data) is BROKEN. A bot check refuses the request, not the client, so an answer
 * that is one is never compared as a class, count or shape change. The rung that answered a
 * lookup is added from D6 on.
 */
internal object CensusDiff {
    enum class Level { CHANGED, BROKEN }

    data class Line(val level: Level, val text: String, val step: Int) {
        fun render(): String = "${level.name} $text → §7 step $step"
    }

    /** [lines] are the changes the headline counts; [info] lines name no runbook step and count for nothing. */
    data class Result(
        val headline: String,
        val lines: List<Line>,
        val notes: List<String>,
        val info: List<String> = emptyList(),
    ) {
        val broken: Int get() = lines.count { it.level == Level.BROKEN }

        fun text(): String =
            (listOf(headline) + lines.map(Line::render) + info.map { "INFO $it" } + notes.map { "note: $it" })
                .joinToString("\n")
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val MAX_LISTED_PATHS = 8
    private const val PASS = "PASS"
    private val VERDICTS = setOf(PASS, "FAIL", "UNKNOWN")

    /** Sources whose value moves by design (the web pages' own version), so only acceptance counts. */
    private val MOVING_SOURCES = setOf("page")

    fun compare(previous: List<CensusRun>, current: CensusRun): Result {
        val earlier = previous.sortedByDescending(CensusRun::atMillis)
        val last = earlier.firstOrNull()
        val lines = ArrayList<Line>()
        val notes = ArrayList<String>()
        val info = ArrayList<String>()
        val botChecks = current.asks.filter { it.answerClass == CensusClass.BOT_CHECK }
        // Nothing is asked after the bot check that stops a run, so it is the run's last one.
        val stopper = if (current.stopped == "bot-check") botChecks.lastOrNull() else null
        when (current.stopped) {
            null -> Unit
            "bot-check" -> {
                val on = stopper?.let { " on ${it.client} ${it.step} video ${it.video} (a request with visitor data)" }.orEmpty()
                lines += Line(Level.BROKEN, "bot check$on — the run stopped; no census for 30 min", 8)
            }
            "rate-limited" -> lines += Line(Level.BROKEN, "rate limited (HTTP 429) — the run stopped; no census for 30 min", 8)
            else -> notes += "the run stopped early (${current.stopped}); later steps were not measured"
        }
        // Each video's answered bot check (owner, 2026-10-15): shown even on a first run.
        botChecks.filter { it !== stopper }.forEach { ask ->
            val label = "${ask.client} ${ask.step} video ${ask.video}"
            if (ask.client != "visionos" || ask.step != ClientCensus.R0) {
                lines += Line(Level.CHANGED, "$label: bot check without visitor data, nothing else asked for this video", 8)
                return@forEach
            }
            val again = current.asks.firstOrNull {
                it.video == ask.video && it.client == ask.client && it.step == ClientCensus.AGAIN
            }
            val next = again?.let { "visionOS with visitor data: ${it.finalClass}" + it.v?.let { v -> ", V $v" }.orEmpty() }
                ?: "visionOS not asked again"
            // R6 answered the lookup with checked rows (owner, 2026-10-15): nothing to do, so info only.
            if (again != null && again.finalClass == CensusClass.DIRECT && again.v == PASS) {
                info += "$label: bot check, answered as R6 ($next)"
            } else {
                lines += Line(Level.CHANGED, "$label: bot check, answered as R6 ($next)", 8)
            }
        }
        if (last != null) {
            current.slots.toSortedMap().forEach { (slot, tag) ->
                val before = last.slots[slot]
                if (before != null && before != tag) {
                    notes += "video $slot is not the video of the last run; it is compared only with runs of the same video"
                }
            }
            compareAsks(earlier, current, lines)
            compareHls(earlier, current, lines)
            compareVersions(earlier, current, lines)
        }
        val since = last?.let {
            "${Math.floorDiv(current.atMillis - it.atMillis, DAY_MILLIS).coerceAtLeast(0)} days since the last run"
        }
        val headline = when {
            lines.any { it.level == Level.BROKEN } -> "BROKEN (${lines.size}) — ${since ?: "first run"}"
            lines.isNotEmpty() -> "CHANGED (${lines.size}) — ${since ?: "first run"}"
            last == null -> "FIRST RUN — nothing to compare yet"
            else -> "NO CHANGE — $since"
        }
        return Result(headline, lines, notes, info)
    }

    private fun sameVideo(run: CensusRun, current: CensusRun, video: Int): Boolean {
        val tag = current.slots[video] ?: return false
        return run.slots[video] == tag
    }

    private fun compareAsks(earlier: List<CensusRun>, current: CensusRun, lines: MutableList<Line>) {
        val shapes = LinkedHashMap<String, Pair<MutableSet<String>, MutableSet<String>>>()
        for (ask in current.asks) {
            val prior = earlier.firstNotNullOfOrNull { run ->
                if (!sameVideo(run, current, ask.video)) null else run.asks.firstOrNull { it.key == ask.key }
            } ?: continue
            // A bot check refuses the request (the bot-check lines above), not the client.
            if (prior.answerClass == CensusClass.BOT_CHECK || ask.answerClass == CensusClass.BOT_CHECK) continue
            val label = "${ask.client} ${ask.step} video ${ask.video}"
            val bothVerdicts = prior.v in VERDICTS && ask.v in VERDICTS
            val before = if (bothVerdicts) prior.finalClass else prior.answerClass
            val now = if (bothVerdicts) ask.finalClass else ask.answerClass
            if (before != now) {
                val level = if (now.rank > before.rank) Level.BROKEN else Level.CHANGED
                lines += Line(level, "$label: class $before → $now", now.runbookStep)
            } else {
                if (bothVerdicts && prior.v != ask.v) {
                    val level = if (prior.v == "PASS" && ask.v == "FAIL") Level.BROKEN else Level.CHANGED
                    val target = ask.vTarget?.let { " ($it)" }.orEmpty()
                    lines += Line(level, "$label: V ${prior.v} → ${ask.v}$target", if (ask.vTarget == "403") 7 else 2)
                }
                val countsBefore = listOf(prior.progressive, prior.adaptive, prior.protectedFormats, prior.unaddressed, prior.missingSizes)
                val countsNow = listOf(ask.progressive, ask.adaptive, ask.protectedFormats, ask.unaddressed, ask.missingSizes)
                if (countsBefore != countsNow) {
                    val step = when {
                        ask.missingSizes > prior.missingSizes -> 5
                        ask.protectedFormats > prior.protectedFormats -> 4
                        else -> 2
                    }
                    lines += Line(
                        Level.CHANGED,
                        "$label: formats (progressive/adaptive/protected/no address/no size) " +
                            "${countsBefore.joinToString("/")} → ${countsNow.joinToString("/")}",
                        step,
                    )
                }
                val highestBefore = prior.heights.maxOrNull()
                val highestNow = ask.heights.maxOrNull()
                if (highestBefore != null && highestNow != null && highestBefore != highestNow) {
                    lines += Line(Level.CHANGED, "$label: highest video ${highestBefore}p → ${highestNow}p", 2)
                }
            }
            if (prior.hls != ask.hls) {
                lines += Line(
                    Level.CHANGED,
                    "$label: HLS address " + if (ask.hls) "appeared" else "gone",
                    7,
                )
            }
            val pathsBefore = prior.shapePaths
            val pathsNow = ask.shapePaths
            if (pathsBefore != null && pathsNow != null) {
                val added = pathsNow.toSet() - pathsBefore.toSet()
                val removed = pathsBefore.toSet() - pathsNow.toSet()
                if (added.isNotEmpty() || removed.isNotEmpty()) {
                    val entry = shapes.getOrPut("${ask.client} ${ask.step}") { mutableSetOf<String>() to mutableSetOf() }
                    entry.first += added
                    entry.second += removed
                }
            }
        }
        shapes.forEach { (key, change) ->
            // A path one video gained and another lost is not a change of the client (owner, 2026-10-15).
            val added = change.first - change.second
            val removed = change.second - change.first
            if (added.isEmpty() && removed.isEmpty()) return@forEach
            val breaking = removed.filter { it in ShapeFingerprint.PARSER_READS }.sorted()
            val listed = (added.sorted().map { "+$it" } + removed.sorted().map { "−$it" })
            val shown = listed.take(MAX_LISTED_PATHS).joinToString(", ") +
                if (listed.size > MAX_LISTED_PATHS) ", …" else ""
            val text = "shape $key: +${added.size} −${removed.size} ($shown)" +
                if (breaking.isNotEmpty()) " BREAKING: ${breaking.joinToString(", ")}" else ""
            lines += Line(if (breaking.isNotEmpty()) Level.BROKEN else Level.CHANGED, text, 6)
        }
    }

    private fun compareHls(earlier: List<CensusRun>, current: CensusRun, lines: MutableList<Line>) {
        for (entry in current.hls) {
            val prior = earlier.firstNotNullOfOrNull { run ->
                if (!sameVideo(run, current, entry.video)) {
                    null
                } else {
                    run.hls.firstOrNull { it.video == entry.video && it.client == entry.client }
                }
            }?.result ?: continue
            val now = entry.result
            val parts = ArrayList<String>()
            var broken = false
            for ((name, before, after) in listOf(
                Triple("master", prior.master, now.master),
                Triple("variant", prior.variant, now.variant),
                Triple("segment", prior.segment, now.segment),
            )) {
                if (before != after) {
                    parts += "$name $before → $after"
                    if (before == HlsCheck.OK) broken = true
                }
            }
            if (prior.maxHeight != now.maxHeight && prior.maxHeight != null && now.maxHeight != null) {
                parts += "highest ${prior.maxHeight}p → ${now.maxHeight}p"
            }
            if (prior.audio != now.audio && prior.master == HlsCheck.OK && now.master == HlsCheck.OK) {
                parts += "audio ${prior.audio} → ${now.audio}"
            }
            if (prior.codecs != now.codecs && prior.master == HlsCheck.OK && now.master == HlsCheck.OK) {
                parts += "codecs ${prior.codecs.joinToString("+")} → ${now.codecs.joinToString("+")}"
            }
            if (prior.key != now.key) {
                parts += "key ${prior.key} → ${now.key}"
                if (prior.key == "NONE") broken = true
            }
            if (prior.durationMatch == true && now.durationMatch == false) {
                parts += "duration no longer matches the video"
                broken = true
            }
            if (parts.isNotEmpty()) {
                lines += Line(
                    if (broken) Level.BROKEN else Level.CHANGED,
                    "H ${entry.client} video ${entry.video}: ${parts.joinToString("; ")}",
                    7,
                )
            }
        }
    }

    private fun compareVersions(earlier: List<CensusRun>, current: CensusRun, lines: MutableList<Line>) {
        for (fact in current.versions) {
            val prior = earlier.firstNotNullOfOrNull { run ->
                run.versions.firstOrNull { it.identity == fact.identity && it.source == fact.source }
            } ?: continue
            val label = "version ${fact.identity} from ${fact.source}"
            if (prior.reachable != fact.reachable) {
                val why = fact.note?.let { " ($it)" }.orEmpty()
                lines += Line(
                    Level.CHANGED,
                    "$label: " + if (fact.reachable) "reachable again" else "unreachable$why",
                    3,
                )
            }
            if (fact.source !in MOVING_SOURCES && prior.value != null && fact.value != null && prior.value != fact.value) {
                lines += Line(Level.CHANGED, "$label: ${prior.value} → ${fact.value}", 3)
            }
            if (prior.accepted == "yes" && (fact.accepted == "rejected" || fact.accepted == "no")) {
                lines += Line(Level.BROKEN, "$label: accepted → ${fact.accepted}", 3)
            }
        }
    }
}
