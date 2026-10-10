package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.recipes.RemoteRecipes

/**
 * R9: one process-wide, data-only recipe config for Master's contract stage. Without a build key
 * (`-Pyft.masterRecipeKey`) Master uses its bundled recipes and never asks for a config.
 */
internal object MasterRecipeConfig {
    @Volatile
    private var remote: RemoteRecipes? = null

    fun contracts(http: ExtractorHttpClient): SiteContracts {
        val key = BuildConfig.MASTER_RECIPE_KEY
        if (key.isBlank()) return SiteContracts(http)
        val shared = remote ?: synchronized(this) {
            remote ?: RemoteRecipes(http, BuildConfig.MASTER_RECIPE_URL, key).also { remote = it }
        }
        return SiteContracts(http, shared)
    }
}