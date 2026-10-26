package com.alal.yft.detection.master

import android.content.Context
import android.content.SharedPreferences
import com.alal.yft.BuildConfig
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.master.social.VkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VK-Master wave 2 (agent D, item 7): the trial's VK value break, the owner's phone proof of V2's
 * heal. Only #12 trial ([BuildConfig.SOCIAL_TRIAL]; a social-trial debug build too) has it, in
 * Settings › Browser: Off, the client id, the API version or the client secret only. Main's VK
 * adapter then asks with that value broken ([client]: the token request's client id or secret,
 * or `video.get`'s version, changed before main's record, so the record keeps what was sent) and
 * Master's V2 knows main's set ([mutate]: the same change). With the client id or the version
 * broken VK refuses main, and V2 heals that value from vkvideo.ru's public script; with the
 * secret only VK still mints a token (it does not check it), so main's answer stands and V1
 * answers with no heal. Main keeps its token for a day, so the choice is read once per process
 * ([active]): a change applies after YFT restarts. Every other build: off, the preferences never
 * read, main's client as it is. The broken values are VK's shapes only, never a real value.
 */
class VkTrialBreak internal constructor(
    private val preferences: SharedPreferences?,
    /** Whether this build has the break (the trial), so Settings shows its row. */
    val available: Boolean,
    initial: Break = read(preferences),
) {
    /** The value main asks with broken; [label]: as Settings names it. */
    enum class Break(val label: String) {
        OFF("Off"),
        CLIENT_ID("Client id"),
        API_VERSION("API version"),
        CLIENT_SECRET("Client secret only"),
    }

    /** Settings' row: the choice kept ([chosen]) and the one this process runs with ([active]). */
    data class Row(val chosen: Break, val active: Break)

    /** The choice this process runs with: the one kept when it started (off but in the trial). */
    val active: Break = if (available) initial else Break.OFF

    private val state = MutableStateFlow(Row(active, active))

    /** The row as Settings shows it. */
    val row: StateFlow<Row> = state.asStateFlow()

    /** Keeps the next choice (Off, client id, API version, client secret only, Off …): after a restart. */
    fun next() {
        if (!available) return
        val next = Break.entries[(state.value.chosen.ordinal + 1) % Break.entries.size]
        preferences?.edit()?.putString(KEY, next.name)?.apply()
        state.value = state.value.copy(chosen = next)
    }

    /** Main's values as this process's choice breaks them, for Master's V2; null when off. */
    fun mutate(): ((VkClient.Values) -> VkClient.Values)? = when (active) {
        Break.OFF -> null
        Break.CLIENT_ID -> { values -> values.copy(clientId = BROKEN_CLIENT_ID) }
        Break.API_VERSION -> { values -> values.copy(apiVersion = BROKEN_API_VERSION) }
        Break.CLIENT_SECRET -> { values -> values.copy(clientSecret = BROKEN_CLIENT_SECRET) }
    }

    /** Main's VK client: [http] with this process's choice in each ask; [http] itself when off. */
    fun client(http: ExtractorHttpClient): ExtractorHttpClient =
        if (active == Break.OFF) http else BreakingClient(http, active)

    companion object {
        /** The app preferences that hold the choice. */
        const val PREFERENCES = "yft_vk_value_break"
        const val KEY = "break"

        /** The broken values: VK's shapes, never a real value. */
        const val BROKEN_CLIENT_ID = "999999999"
        const val BROKEN_API_VERSION = "5.0"
        const val BROKEN_CLIENT_SECRET = "YftTrialBrokenSecret"

        @Volatile
        private var instance: VkTrialBreak? = null

        /** The app's break, one per process; outside the trial off, its preferences never read. */
        fun get(context: Context, trial: Boolean = BuildConfig.SOCIAL_TRIAL): VkTrialBreak =
            instance ?: synchronized(this) {
                instance ?: VkTrialBreak(
                    preferences = if (trial) {
                        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    } else {
                        null
                    },
                    available = trial,
                ).also { instance = it }
            }

        /** Main's VK client in this build: [http] itself but in the trial ([get]'s choice there). */
        fun client(context: Context, http: ExtractorHttpClient): ExtractorHttpClient =
            if (BuildConfig.SOCIAL_TRIAL) get(context).client(http) else http

        /** Main's values as this build breaks them, for Master's V2: null but in the trial. */
        fun mutate(context: Context): ((VkClient.Values) -> VkClient.Values)? =
            if (BuildConfig.SOCIAL_TRIAL) get(context).mutate() else null

        /** Kept in memory only (tests). */
        fun inMemory(initial: Break = Break.OFF, available: Boolean = true): VkTrialBreak =
            VkTrialBreak(preferences = null, available = available, initial = initial)

        private fun read(preferences: SharedPreferences?): Break =
            preferences?.getString(KEY, null)?.let { name -> Break.entries.firstOrNull { it.name == name } }
                ?: Break.OFF
    }
}

/**
 * Main's VK asks with [choice]'s value broken: the token request's `client_id` or `client_secret`
 * (login.vk.ru), or `video.get`'s `v` (api.vkvideo.ru). Every other ask as it is.
 */
private class BreakingClient(
    private val http: ExtractorHttpClient,
    private val choice: VkTrialBreak.Break,
) : ExtractorHttpClient by http {
    override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
        http.get(brokenUrl(url), headers, maxBodyBytes)

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult = http.postJson(url, brokenBody(url, body), headers, maxBodyBytes)

    private fun brokenUrl(url: String): String =
        if (choice == VkTrialBreak.Break.API_VERSION && url.startsWith(VIDEO_GET)) {
            url.replace(VERSION, "$1v=${VkTrialBreak.BROKEN_API_VERSION}")
        } else {
            url
        }

    private fun brokenBody(url: String, body: String): String {
        if (!url.startsWith(TOKEN)) return body
        return when (choice) {
            VkTrialBreak.Break.CLIENT_ID -> body.replace(CLIENT_ID, "\"client_id\":\"${VkTrialBreak.BROKEN_CLIENT_ID}\"")
            VkTrialBreak.Break.CLIENT_SECRET ->
                body.replace(CLIENT_SECRET, "\"client_secret\":\"${VkTrialBreak.BROKEN_CLIENT_SECRET}\"")
            else -> body
        }
    }

    private companion object {
        const val TOKEN = "https://login.vk.ru/"
        const val VIDEO_GET = "https://api.vkvideo.ru/method/video.get?"
        val VERSION = Regex("""([?&])v=[0-9]{1,2}\.[0-9]{1,4}(?=&|$)""")
        val CLIENT_ID = Regex(""""client_id":"[0-9]{1,12}"""")
        val CLIENT_SECRET = Regex(""""client_secret":"[A-Za-z0-9]{8,64}"""")
    }
}
