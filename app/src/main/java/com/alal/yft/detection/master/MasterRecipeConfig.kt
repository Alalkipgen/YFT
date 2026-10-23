package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.recipes.RemoteRecipes

/**
 * R9: one process-wide, data-only recipe config for Master's contract stage. Without a build key
 * (`-Pyft.masterRecipeKey`) Master uses its bundled recipes and never asks for a config. Social
 * plan S4: a social-ladder debug build carries the committed key by default (its config turns
 * ladder rungs off too); [status] is the census screen's line, [refreshNow] its ask on open.
 */
internal object MasterRecipeConfig {
    @Volatile
    private var remote: RemoteRecipes? = null

    fun contracts(http: ExtractorHttpClient): SiteContracts {
        val shared = remote(http) ?: return SiteContracts(http)
        return SiteContracts(http, shared)
    }

    /** S4: the config in use, one line (`recipes: …`; a build without a key: bundled). */
    fun status(): String = remote?.status()
        ?: if (BuildConfig.MASTER_RECIPE_KEY.isBlank()) NO_KEY else "recipes: bundled (not asked yet)"

    /** S4: one bounded ask now (the census screen on open), on [http] when nothing asked before. */
    suspend fun refreshNow(http: ExtractorHttpClient): String? = remote(http)?.refreshNow()

    /** The shared config, made on the first caller's client (its ask runs outside any lookup); null without a key. */
    private fun remote(http: ExtractorHttpClient): RemoteRecipes? {
        val key = BuildConfig.MASTER_RECIPE_KEY
        if (key.isBlank()) return null
        return remote ?: synchronized(this) {
            remote ?: RemoteRecipes(http, BuildConfig.MASTER_RECIPE_URL, key).also { remote = it }
        }
    }

    private const val NO_KEY = "recipes: bundled (no key in this build)"
}
