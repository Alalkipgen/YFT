/*
 * R9 (MASTER_KEY_PHASE1_PLAN.md): Master's recipe config, data only. The bundled recipes
 * ([ContractRecipes]) are the defaults and always work. A config hosted on GitHub replaces their
 * data only when it is signed with the build's key ([RemoteRecipeSignature]), passes the schema
 * ([RemoteRecipeConfig]), is newer than the config in use (no rollback) and has not expired; an
 * expired config falls back to the bundled recipes. Without a key nothing is ever asked. The ask
 * is bounded: HTTPS to raw.githubusercontent.com only, no cookie, 128 KiB, 1.5 s, at most once
 * every six hours after an answer (after a failed ask, once an hour; the first lookup after the
 * app starts asks). No code is ever loaded remotely.
 *
 * Social plan S4 (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the config's rungs off ([rungsOff]), its
 * one-line [status] (the census screen's), an ask on demand ([refreshNow]: the census screen on
 * open) and an ask that runs on its own, outside the caller's lookup ([isolated]).
 *
 * VK Master plan §9: v1.0.1 asks for master-recipes-2.json (schema 2, VK included,
 * [RemoteRecipeConfig2]); a schema-1 address keeps v1.0.0's reader and VK's bundled recipe.
 */
package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import java.net.URI
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class RemoteRecipes internal constructor(
    private val http: ExtractorHttpClient?,
    private val configUrl: String,
    private val signature: RemoteRecipeSignature?,
    private val bundled: List<ContractRecipe>,
    private val clock: () -> Long,
) {
    /**
     * [publicKeyHex]: the build's X.509 P-256 public key as hex (`yft.masterRecipeKey`); blank or
     * invalid = the bundled recipes only, and no request is ever made.
     */
    constructor(http: ExtractorHttpClient, configUrl: String, publicKeyHex: String?) : this(
        http, configUrl, RemoteRecipeSignature.of(publicKeyHex), ContractRecipes.ALL, System::currentTimeMillis,
    )

    private class Applied(
        val version: Long,
        val expiresEpochDay: Long,
        val recipes: List<ContractRecipe>,
        val rungsOff: Set<String>,
    )

    @Volatile private var applied: Applied? = null

    @Volatile private var nextAskAt = 0L

    /** S4: the last ask's outcome ([refreshIfDue]'s words), for [status]; null: not asked yet. */
    @Volatile private var lastAsk: String? = null

    private val asking = Mutex()

    /** Whether a config can apply at all: a valid key and an https GitHub address. */
    val active: Boolean = signature != null && http != null && onGitHub(configUrl)

    /** The version of the config in use; 0 = the bundled recipes. */
    val version: Long get() = current()?.version ?: 0L

    internal fun recipes(): List<ContractRecipe> = current()?.recipes ?: bundled

    internal fun recipeOf(site: String): ContractRecipe? = recipes().firstOrNull { it.site == site }

    /** S4: the ladder rungs the config in use turns off ([RemoteRecipeConfig.RUNGS]); none without one. */
    internal fun rungsOff(): Set<String> = current()?.rungsOff.orEmpty()

    /**
     * S4: the config in use, one line of fixed words (never a link): `recipes: remote version N,
     * signed` (and the last ask's outcome when it did not confirm it), else `recipes: bundled (why)`.
     */
    fun status(): String {
        if (!active) return "$STATUS bundled (no key in this build)"
        val use = current()
        val last = lastAsk
        val why = last?.let { outcome -> whyOf(outcome, use?.version) }
        return when {
            use != null -> "$STATUS remote version ${use.version}, signed" + why?.let { " (last check: $it)" }.orEmpty()
            applied != null -> "$STATUS bundled (version ${applied?.version} expired)"
            last == null -> "$STATUS bundled (not asked yet)"
            else -> "$STATUS bundled (${why ?: "no config"})"
        }
    }

    private fun current(): Applied? = applied?.takeIf { today() <= it.expiresEpochDay }

    private fun today(): Long = Math.floorDiv(clock(), DAY_MILLIS)

    /** Applies [envelope] when signed, valid, newer and unexpired; anything else changes nothing. */
    internal fun accept(envelope: String): String {
        val key = signature ?: return "recipe config: no key"
        val payload = key.payload(envelope) ?: return "recipe config ignored: not signed by this build's key"
        return when (val parsed = parse(payload)) {
            is RemoteRecipeConfig.Parsed.Invalid -> "recipe config ignored: ${parsed.reason}"
            is RemoteRecipeConfig.Parsed.Valid -> when {
                parsed.expiresEpochDay < today() -> "recipe config ignored: expired"
                parsed.version <= (applied?.version ?: 0L) ->
                    "recipe config ignored: version ${parsed.version} is not newer"
                else -> {
                    applied = Applied(parsed.version, parsed.expiresEpochDay, parsed.recipes, parsed.rungsOff)
                    "recipe config ${parsed.version} applied"
                }
            }
        }
    }

    /**
     * VK Master plan §9: a `-2.json` address ([SCHEMA_2_SUFFIX], v1.0.1 on) carries schema 2, all
     * the bundled sites, VK's too ([RemoteRecipeConfig2]); any other (v1.0.0's
     * master-recipes.json) carries schema 1, which never names VK, so it is laid over the bundled
     * recipes minus VK's and VK's bundled recipe stays, in [bundled]'s order.
     */
    private fun parse(payload: String): RemoteRecipeConfig.Parsed {
        if (configUrl.endsWith(SCHEMA_2_SUFFIX)) return RemoteRecipeConfig2.parse(payload, bundled)
        val vk = RemoteRecipeConfig2.VK
        return when (val parsed = RemoteRecipeConfig.parse(payload, bundled.filter { it.site != vk })) {
            is RemoteRecipeConfig.Parsed.Invalid -> parsed
            is RemoteRecipeConfig.Parsed.Valid -> {
                val bySite = parsed.recipes.associateBy { it.site }
                parsed.copy(recipes = bundled.map { bySite[it.site] ?: it })
            }
        }
    }

    /** One bounded ask when due; returns what happened, or null when nothing was asked. */
    internal suspend fun refreshIfDue(): String? {
        val client = http?.takeIf { active } ?: return null
        if (clock() < nextAskAt || !asking.tryLock()) return null
        try {
            if (clock() < nextAskAt) return null
            return ask(client)
        } finally {
            asking.unlock()
        }
    }

    /**
     * S4: one bounded ask now, whatever the interval (the census screen on open), after an ask
     * under way; returns what happened, or null without a key.
     */
    suspend fun refreshNow(): String? {
        val client = http?.takeIf { active } ?: return null
        return asking.withLock { ask(client) }
    }

    private suspend fun ask(client: ExtractorHttpClient): String {
        val result = isolated {
            withTimeoutOrNull(ASK_MILLIS) {
                client.get(configUrl, mapOf("Accept" to "application/json"), MAX_ENVELOPE_BYTES)
            }
        }
        val outcome = when {
            result is ExtractorHttpResult.Success && onGitHub(result.finalUrl) -> accept(result.body)
            result is ExtractorHttpResult.Success -> "recipe config ignored: the answer left GitHub"
            result is ExtractorHttpResult.Failure -> "recipe config unanswered: ${result.reason.name}"
            else -> "recipe config unanswered: timed out"
        }
        nextAskAt = clock() + if (result is ExtractorHttpResult.Success) ANSWERED_MILLIS else RETRY_MILLIS
        lastAsk = outcome
        return outcome
    }

    /**
     * S4: [block] on its own, outside the caller's lookup: on the caller's dispatcher, as a child
     * of its job (cancelled with it), but with none of its context's elements (the lookup's
     * record, route B's rules, a ladder's kept answer, the tab's probe). The config's ask is never
     * one of the lookup's asks, its answer never the lookup's, whichever client asks it.
     */
    private suspend fun <T> isolated(block: suspend () -> T): T {
        val caller = coroutineContext
        val job = SupervisorJob(caller[Job])
        return try {
            CoroutineScope((caller[ContinuationInterceptor] ?: EmptyCoroutineContext) + job).async { block() }.await()
        } finally {
            job.cancel()
        }
    }

    internal companion object {
        const val ASK_MILLIS = 1_500L
        const val MAX_ENVELOPE_BYTES = 128L * 1024
        const val DAY_MILLIS = 86_400_000L

        /** S4: the next ask after an answer (applied or not): six hours. */
        const val ANSWERED_MILLIS = 6 * 3_600_000L
        const val RETRY_MILLIS = 3_600_000L

        /** VK Master plan §9: the end of a schema-2 config's address (master-recipes-2.json). */
        const val SCHEMA_2_SUFFIX = "-2.json"
        val CONFIG_HOSTS = setOf("raw.githubusercontent.com")
        private const val STATUS = "recipes:"
        private val NOT_NEWER = Regex("""^recipe config ignored: version (\d+) is not newer$""")

        fun onGitHub(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme.equals("https", true) && uri.userInfo == null &&
                uri.host?.lowercase() in CONFIG_HOSTS
        }

        /**
         * S4: the status's words for an ask's [outcome] (null: it applied or confirmed the config
         * in use, [inUse]): lowercase words, no underscores (the census note keeps them).
         */
        fun whyOf(outcome: String, inUse: Long?): String? {
            NOT_NEWER.matchEntire(outcome)?.let { match ->
                val asked = match.groupValues[1].toLongOrNull()
                return if (asked == inUse) null else "an older version $asked"
            }
            return when {
                outcome.startsWith("recipe config ") && outcome.endsWith(" applied") -> null
                outcome == "recipe config unanswered: timed out" -> "no answer in time"
                outcome.startsWith("recipe config unanswered: ") ->
                    "no answer: " + outcome.removePrefix("recipe config unanswered: ").lowercase().replace('_', ' ')
                outcome.startsWith("recipe config ignored: ") -> outcome.removePrefix("recipe config ignored: ")
                else -> outcome
            }
        }
    }
}
