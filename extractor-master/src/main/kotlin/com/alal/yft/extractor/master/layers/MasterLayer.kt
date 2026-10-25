package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.recipes.ContractRecipe
import com.alal.yft.extractor.master.recipes.ContractRecipes
import com.alal.yft.extractor.master.toolkit.UrlPolicy

/**
 * The evidence layers (MASTER_KEY_ARCHITECTURE.md). L3 shape search arrived in Phase 1 R3;
 * L2 contract endpoints come in R8. Layers plug in without touching the engine.
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

    /**
     * An additive layer only adds files no earlier layer found: what it repeats is dropped, so
     * the earlier layers' rows reach the normalizer unchanged (R3: L3 after L1).
     */
    val additive: Boolean get() = false

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
            // Once a layer reported a terminal state, an additive layer has nothing to add.
            if (layer.additive && terminal != null) return@forEach
            val found = layer.collect(request, snapshot).let { evidence ->
                if (!layer.additive) return@let evidence
                val known = candidates.flatMap { listOf(it.mediaUrl, UrlPolicy.whole(it.mediaUrl)) }
                    .toHashSet()
                evidence.copy(
                    candidates = evidence.candidates.filterNot {
                        it.mediaUrl in known || UrlPolicy.whole(it.mediaUrl) in known
                    },
                )
            }
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

        /**
         * Delivered markup, known payload keys, captured requests, then (R3) the additive shape
         * search: L3 only adds files the first three did not find, so their order is unchanged.
         */
        fun standard(recipeOf: (String) -> ContractRecipe? = ContractRecipes::of) = LayerStack(
            listOf(ContractLayer(recipeOf), RecipeLayer(), CaptureLayer(), ShapeLayer()),
        )

        /** The stack before R3, kept for the parity tests (L3 must only add). */
        fun withoutShape() = LayerStack(listOf(ContractLayer(), RecipeLayer(), CaptureLayer()))
    }
}
