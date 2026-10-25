package com.alal.yft.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import com.alal.yft.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decision 30 (v) (`MASTER_KEY_SOCIAL_PLAN.md` section 8): the quiet release — the debug and
 * trial APKs find the bugs; the release shows none of their tools. True while a quiet build
 * ([BuildConfig.QUIET_RELEASE]: the release and #12 normal) has Show details off ([ShowDetails]):
 * the browser shows no lookup notice and no Try again, the download sheet one line
 * ([QuietLines]), and Details and Copy details (the sheet, Home, Downloads) are hidden. False in
 * the debug APK and #12 trial, which show everything as before, and wherever nothing provides it.
 */
val LocalQuietRelease = compositionLocalOf { false }

/**
 * Decision 30 (v): the hidden "Show details" of a quiet build — the About screen's version line
 * tapped 7 times turns it on or off; off by default, not in Settings. On, the app shows Details
 * and Copy details as the debug APK does (the owner asks a friend for them). App preferences, one
 * per process.
 */
class ShowDetails internal constructor(
    private val preferences: SharedPreferences?,
    initial: Boolean = preferences?.getBoolean(KEY, false) ?: false,
) {
    private val state = MutableStateFlow(initial)

    val on: StateFlow<Boolean> = state.asStateFlow()

    /** Turns it on or off; returns the new state. */
    fun toggle(): Boolean {
        val next = !state.value
        preferences?.edit()?.putBoolean(KEY, next)?.apply()
        state.value = next
        return next
    }

    companion object {
        const val PREFERENCES = "yft_show_details"
        const val KEY = "show_details"

        /** Whether this build is quiet at all (the release and #12 normal). */
        val quietBuild: Boolean get() = BuildConfig.QUIET_RELEASE

        @Volatile
        private var instance: ShowDetails? = null

        fun get(context: Context): ShowDetails = instance ?: synchronized(this) {
            instance ?: ShowDetails(
                context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
            ).also { instance = it }
        }

        /** Kept in memory only (tests). */
        fun inMemory(on: Boolean = false): ShowDetails = ShowDetails(preferences = null, initial = on)
    }
}

/**
 * Decision 30 (v): the quiet build's one line for a lookup that failed — the download sheet's
 * (the browser's and Home's) and the browser's notice for the video on screen. TikTok's check
 * keeps its function (the sheet's Show check); the capture asks for the video to be played;
 * everything else is "Couldn't get this video.". Download again looks up again, as Try again did.
 */
object QuietLines {
    const val NOT_FOUND = "Couldn't get this video."
    const val PLAY = "Play the video, then tap Download again."
    const val CHECK =
        "TikTok wants to check that this is not a bot. Tap Show check, answer it, then tap " +
            "Done and Download again."

    /** The line for a failure that said [message]; [siteCheck]: TikTok's check is offered. */
    fun of(message: String?, siteCheck: Boolean = false): String = when {
        siteCheck -> CHECK
        needsPlayback(message) -> PLAY
        else -> NOT_FOUND
    }

    /**
     * Whether [message] asks for the video to be played (the capture's "Play the video on this
     * page…", "Play only the video you want…", a site's "…let it play for a moment…").
     */
    fun needsPlayback(message: String?): Boolean =
        message != null && PLAYBACK_WORDS.containsMatchIn(message)

    private val PLAYBACK_WORDS =
        Regex("""\b(play (only )?the video|let it play)\b""", RegexOption.IGNORE_CASE)
}
