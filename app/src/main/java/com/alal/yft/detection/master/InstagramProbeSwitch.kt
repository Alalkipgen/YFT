package com.alal.yft.detection.master

import android.content.Context

/**
 * Social plan S2d (`MASTER_KEY_SOCIAL_PLAN.md` decisions 19 and 24, debug only): the own switches
 * of Instagram's tab-fetch rung ([com.alal.yft.extractor.master.social.InstagramTabProbe]), off by
 * default: app preferences only the census screen sets, read when Instagram's ladder would ask the
 * rung. [KEY]: the rung itself (its "Instagram's tab-fetch rung" box; part 2, task 13: a new key,
 * so part 1's probe setting is not carried over and the rung starts off). [PROOF_KEY]: its proof
 * (the "Instagram rung proof" box): I2's rows set aside so the rung is asked, for a post main
 * answers. Only a social-ladder build ([SocialLadder.enabled]) has a rung ([reader],
 * [proofReader]); every other build: none, the preferences never read.
 */
internal object InstagramProbeSwitch {
    /** The app preferences that hold the switches. */
    const val PREFERENCES = "yft_instagram_probe"

    /** The rung's switch's key in [PREFERENCES] (task 13; part 1's probe's was `tab_fetch_probe`). */
    const val KEY = "tab_fetch_rung"

    /** The rung's proof's key in [PREFERENCES]. */
    const val PROOF_KEY = "rung_proof"

    /** Whether the rung's switch is on: off by default, and always off but in a social-ladder build. */
    fun on(context: Context, social: Boolean = SocialLadder.enabled): Boolean =
        social && preferences(context).getBoolean(KEY, false)

    /** Sets the rung's switch (the census screen's box). */
    fun set(context: Context, on: Boolean) {
        preferences(context).edit().putBoolean(KEY, on).apply()
    }

    /** Whether the rung's proof is on: off by default, and always off but in a social-ladder build. */
    fun proof(context: Context, social: Boolean = SocialLadder.enabled): Boolean =
        social && preferences(context).getBoolean(PROOF_KEY, false)

    /** Sets the rung's proof (the census screen's box). */
    fun setProof(context: Context, on: Boolean) {
        preferences(context).edit().putBoolean(PROOF_KEY, on).apply()
    }

    /**
     * The rung's switch as the browser's lookups read it, each time the rung would be asked: null
     * in every build but a social-ladder one (no rung in any lookup).
     */
    fun reader(context: Context, social: Boolean = SocialLadder.enabled): (() -> Boolean)? {
        if (!social) return null
        val app = context.applicationContext
        return { on(app, social) }
    }

    /** The rung's proof as the browser's lookups read it ([reader]'s rule): null but in a social-ladder build. */
    fun proofReader(context: Context, social: Boolean = SocialLadder.enabled): (() -> Boolean)? {
        if (!social) return null
        val app = context.applicationContext
        return { proof(app, social) }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
