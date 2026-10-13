package com.alal.yft.extractor.master.android.solver

import android.content.Context
import com.alal.yft.extractor.master.solver.OwnSolverBreaker

/**
 * Phase 1.2 N7: the app's one solver breaker (ownSolverNext only), with its markers on disk
 * ([SharedPreferencesBreakerMarkers]). The app's runner and the debug build's owner canary
 * ("YFT solver canary", owner's option b) share it: a step the canary turns off (a job timeout, a
 * gone renderer) is off for the app's lookups too until the process restarts, and the reverse; a
 * process death during either one's job counts for both.
 */
object OwnSolverAppBreaker {
    @Volatile
    private var breaker: OwnSolverBreaker? = null

    fun get(context: Context): OwnSolverBreaker =
        breaker ?: synchronized(this) {
            breaker ?: OwnSolverBreaker(SharedPreferencesBreakerMarkers(context)).also { breaker = it }
        }
}