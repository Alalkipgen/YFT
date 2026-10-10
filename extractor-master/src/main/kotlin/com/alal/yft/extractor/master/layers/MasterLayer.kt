package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot

/**
 * The evidence layers (MASTER_KEY_ARCHITECTURE.md). L3 shape search arrives in Phase 1 R3 and
 * L2 contract endpoints in R8; the slots exist so they plug in without touching the engine.
 */
internal enum class LayerId {
    /** Requests the visible browser made while the user played the page. */
    L1_CAPTURE,

    /** Published standards in delivered markup: HTML5 `<video>`, OpenGraph. */
    L2_CONTRACT,

    /** Key-name-independent search for media-shaped JSON (R3). */
    L3_SHAPE,

    /** Known site key tables from [com.alal.yft.extractor.master.recipes]. */
    L4_RECIPE,
}

/** What one layer read from one snapshot. Memory-only; addresses never reach logs. */
internal data class Evidence(
    val candidates: List<MediaCandidate>,
    val details: List<String> = emptyList(),
    val terminalFailure: SiteExtractionFailure? = null,
) {
    override fun toString(): String =
        "Evidence(candidateCount=${candidates.size}, terminalFailure=$terminalFailure)"
}

/** One way to read material already delivered to a page. Never fetches, signs or logs in. */
internal interface MasterLayer {
    val id: LayerId

    fun collect(request: MasterRequest, snapshot: PageSnapshot): Evidence
}

/**
 * Runs layers in a configured order and merges their evidence under one raw-candidate budget.
 * The first layer that reports a terminal state decides it.
 */
internal class LayerStack(private val layers: List<MasterLayer>) {
    fun collect(request: MasterRequest, snapshot: PageSnapshot): Evidence {
        val candidates = mutableListOf<MediaCandidate>()
        val details = mutableListOf<String>()
        var terminal: SiteExtractionFailure? = null
        layers.forEach { layer ->
            val found = layer.collect(request, snapshot)
            candidates += found.candidates.take(
                (MAX_RAW_CANDIDATES - candidates.size).coerceAtLeast(0),
            )
            details += found.details
            if (terminal == null) terminal = found.terminalFailure
        }
        return Evidence(
            candidates,
            details + "master: discovered ${candidates.size} bounded media observations",
            terminal,
        )
    }

    companion object {
        const val MAX_RAW_CANDIDATES = 200

        /** Today's order: delivered markup, then known payload keys, then captured requests. */
        fun standard() = LayerStack(listOf(ContractLayer(), RecipeLayer(), CaptureLayer()))
    }
}
