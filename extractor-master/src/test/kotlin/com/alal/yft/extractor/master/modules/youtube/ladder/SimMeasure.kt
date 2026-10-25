package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls

/** The reader a column runs (plan §4 D1); the ladder columns come with D6's seam. */
internal enum class SimColumn(val label: String) {
    R6_OFF("R6, own solver off"),
    R6_ON("R6, own solver on"),
}

/** How one lookup ended, judged by what the world's media host would do with each row. */
internal enum class SimOutcome(val label: String) {
    /** P22: a video merged with its audio track and the audio track (or a good HLS row), all sized. */
    COMPLETE("complete"),

    /** P22's rule met, but a merged or audio row has no size. */
    UNSIZED("without sizes"),
    PARTIAL("partial"),
    STOP("clean stop"),
    WRONG("with a wrong row"),
}

internal data class SimLookup(
    val outcome: SimOutcome,
    val rows: Int,
    /** Why each wrong row is wrong (the world's [SimWorld.truth], or "after a verdict"). */
    val wrong: List<String>,
    val failure: SiteExtractionFailure?,
    val players: Int,
    val pages: Int,
    val probes: Int,
    val hls: Int,
    val sources: Int,
    val media: Int,
    val afterTerminal: Int,
    val credentials: Int,
) {
    val completed: Boolean get() = outcome == SimOutcome.COMPLETE || outcome == SimOutcome.UNSIZED
}

internal data class SimRun(val case: SimCase, val column: SimColumn, val lookups: List<SimLookup>) {
    val wrong: Int get() = lookups.sumOf { it.wrong.size }

    /**
     * Plan §5.1: a lookup offers a wrong row, or ends without the complete correct rows although
     * the world had a checkable complete path ([SimCase.paths]); a verdict about the video is never
     * Master-caused. The paths count every simulated rung (the live-backed paths come with D0).
     */
    val mcf: Int
        get() = lookups.count { lookup ->
            lookup.wrong.isNotEmpty() || (!case.terminal && case.paths.isNotEmpty() && !lookup.completed)
        }

    /** One golden line: what the user would get and what it cost (never an address or value). */
    fun line(): String = buildString {
        val first = lookups.first()
        val last = lookups.last()
        append(case.id).append(" | ").append(column.name)
        append(" | ").append(lookups.joinToString(" > ") { it.outcome.name })
        append(" | rows ").append(lookups.joinToString("/") { it.rows.toString() })
        append(" | wrong ").append(wrong)
        val reasons = lookups.flatMap(SimLookup::wrong).distinct().sorted()
        if (reasons.isNotEmpty()) append(" (").append(reasons.joinToString(", ")).append(')')
        append(" | MCF ").append(mcf)
        append(" | ").append(lookups.joinToString(" > ") { it.failure?.name ?: "-" })
        append(" | player ").append(first.players)
        if (lookups.size > 1) append('/').append(last.players)
        append(", page ").append(first.pages)
        append(", probe ").append(first.probes)
        append(", hls ").append(first.hls)
        append(", source ").append(lookups.sumOf(SimLookup::sources))
        append(", media ").append(first.media)
        append(" | after stop ").append(lookups.sumOf(SimLookup::afterTerminal))
    }
}

/** Runs the cases in the simulated world and judges every row (plan §4 D1). */
internal object SimMeasure {
    suspend fun run(case: SimCase, column: SimColumn): SimRun {
        val world = case.world()
        val module = when (column) {
            SimColumn.R6_OFF -> MasterYouTubeModule(world.http)
            SimColumn.R6_ON -> MasterYouTubeModule(world.http, SimSolver(world))
        }
        val lookups = (1..case.lookups).map {
            val from = world.http.log.size
            judge(world, case, module.extract(request()), from)
        }
        return SimRun(case, column, lookups)
    }

    fun request(): SiteExtractionRequest = SiteExtractionRequest(
        identity = requireNotNull(YouTubeUrls.identify(Sim.SHARED_LINK)),
        requestContext = BrowserRequestContext(pageUrl = Sim.MOBILE_PAGE, userAgent = Sim.USER_AGENT, cookie = Sim.COOKIE),
        nowEpochMs = Sim.NOW,
    )

    fun judge(world: SimWorld, case: SimCase, result: SiteExtractionResult, from: Int): SimLookup {
        val rows = (result as? SiteExtractionResult.Success)?.candidates.orEmpty()
        val wrong = rows.mapNotNull { row ->
            val why = world.truth(row.mediaUrl) ?: row.audioCompanion?.let { world.truth(it.mediaUrl) }
            if (case.terminal) why ?: "after a verdict" else why
        }
        val audio = rows.filter { it.audioCompanion == null && it.mimeType.orEmpty().startsWith("audio/") }
        val merged = rows.filter { it.audioCompanion != null }
        val hls = rows.any { it.kind == MediaKind.HLS && world.truth(it.mediaUrl) == null }
        val complete = hls || (audio.isNotEmpty() && merged.isNotEmpty())
        val sized = (audio + merged).all(::sized)
        val outcome = when {
            wrong.isNotEmpty() -> SimOutcome.WRONG
            complete && (hls || sized) -> SimOutcome.COMPLETE
            complete -> SimOutcome.UNSIZED
            rows.isNotEmpty() -> SimOutcome.PARTIAL
            else -> SimOutcome.STOP
        }
        val log = world.http
        return SimLookup(
            outcome = outcome,
            rows = rows.size,
            wrong = wrong,
            failure = (result as? SiteExtractionResult.Failure)?.reason,
            players = log.count(SimHttp.Kind.PLAYER, from),
            pages = log.count(SimHttp.Kind.PAGE, from),
            probes = log.count(SimHttp.Kind.PROBE, from),
            hls = log.count(SimHttp.Kind.HLS, from),
            sources = log.count(SimHttp.Kind.SOURCE, from),
            media = log.count(SimHttp.Kind.MEDIA, from),
            afterTerminal = log.afterTerminal(from),
            credentials = log.log.drop(from).count(SimHttp.Entry::credentials),
        )
    }

    private fun sized(row: MediaCandidate): Boolean =
        (row.contentLengthBytes ?: 0L) > 0L && (row.audioCompanion?.let { (it.contentLengthBytes ?: 0L) > 0L } ?: true)

    /** One §5 baseline cell: the class's runs in one column, over every base (and V mode). */
    fun cell(runs: List<SimRun>): String {
        val outcomes = runs.flatMap { run -> run.lookups.map(SimLookup::outcome) }
        fun count(outcome: SimOutcome) = outcomes.count { it == outcome }
        val parts = mutableListOf("${count(SimOutcome.COMPLETE)}/${outcomes.size} complete")
        listOf(SimOutcome.UNSIZED, SimOutcome.PARTIAL, SimOutcome.STOP, SimOutcome.WRONG).forEach { outcome ->
            val n = count(outcome)
            if (n > 0) parts += "$n ${outcome.label}"
        }
        return parts.joinToString(", ") + "; wrong ${runs.sumOf(SimRun::wrong)}, MCF ${runs.sumOf(SimRun::mcf)}"
    }
}
