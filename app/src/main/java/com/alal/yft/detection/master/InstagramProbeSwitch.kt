package com.alal.yft.detection.master

import android.content.Context

/**
 * Social plan S2d part 1 (`MASTER_KEY_SOCIAL_PLAN.md` decision 19 (v), debug only): the own
 * switch of Instagram's tab-fetch probe ([com.alal.yft.extractor.master.social.InstagramTabProbe]),
 * off by default: an app preference only the census screen sets (its "Instagram's tab-fetch probe"
 * box), read when Instagram's ladder would ask the probe. Only a social-ladder build
 * ([SocialLadder.enabled]) has a probe ([reader]); every other build: none, the preference never
 * read.
 */
internal object InstagramProbeSwitch {
    /** The app preferences that hold the switch. */
    const val PREFERENCES = "yft_instagram_probe"

    /** The switch's key in [PREFERENCES]. */
    const val KEY = "tab_fetch_probe"

    /** Whether the switch is on: off by default, and always off but in a social-ladder build. */
    fun on(context: Context, social: Boolean = SocialLadder.enabled): Boolean =
        social && preferences(context).getBoolean(KEY, false)

    /** Sets the switch (the census screen's box). */
    fun set(context: Context, on: Boolean) {
        preferences(context).edit().putBoolean(KEY, on).apply()
    }

    /**
     * The switch as the browser's lookups read it, each time the probe would be asked: null in
     * every build but a social-ladder one (no probe in any lookup).
     */
    fun reader(context: Context, social: Boolean = SocialLadder.enabled): (() -> Boolean)? {
        if (!social) return null
        val app = context.applicationContext
        return { on(app, social) }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
