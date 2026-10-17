package com.alal.yft.extractor.master.android.solver

import android.annotation.SuppressLint
import android.content.Context
import com.alal.yft.extractor.master.solver.OwnSolverBreaker

/**
 * Phase 1.2 N5b: the breaker's markers on disk (app-private preferences), so a job that took the
 * process down is still counted after the restart. Keys are a player ID and a step name; no
 * input, value or address is stored. Only ownSolverNext builds make one.
 */
class SharedPreferencesBreakerMarkers(context: Context) : OwnSolverBreaker.Markers {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun count(key: String): Int = preferences.getInt(key, 0)

    // commit, not apply: the marker must be on disk before the job that may end the process.
    @SuppressLint("ApplySharedPref")
    override fun set(key: String, count: Int) {
        val editor = preferences.edit()
        // Markers of old player versions are dropped once there are many.
        if (count > 0 && preferences.all.size >= MAX_KEYS) editor.clear()
        if (count <= 0) editor.remove(key) else editor.putInt(key, count)
        editor.commit()
    }

    private companion object {
        const val NAME = "yft_own_solver_breaker"
        const val MAX_KEYS = 32
    }
}
