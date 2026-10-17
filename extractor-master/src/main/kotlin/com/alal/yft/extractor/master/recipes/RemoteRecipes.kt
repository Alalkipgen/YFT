/*
 * R9 (MASTER_KEY_PHASE1_PLAN.md): Master's recipe config, data only. The bundled recipes
 * ([ContractRecipes]) are the defaults and always work. A config hosted on GitHub replaces their
 * data only when it is signed with the build's key ([RemoteRecipeSignature]), passes the schema
 * ([RemoteRecipeConfig]), is newer than the config in use (no rollback) and has not expired; an
 * expired config falls back to the bundled recipes. Without a key nothing is ever asked. The ask
 * is bounded: HTTPS to raw.githubusercontent.com only, no cookie, 128 KiB, 1.5 s, at most once a
 * day (after a failed ask, once an hour). No code is ever loaded remotely.
 */
package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import java.net.URI
import kotlinx.coroutines.sync.Mutex
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

    private class Applied(val version: Long, val expiresEpochDay: Long, val recipes: List<ContractRecipe>)

    @Volatile private var applied: Applied? = null

    @Volatile private var nextAskAt = 0L

    private val asking = Mutex()

    /** Whether a config can apply at all: a valid key and an https GitHub address. */
    val active: Boolean = signature != null && http != null && onGitHub(configUrl)

    /** The version of the config in use; 0 = the bundled recipes. */
    val version: Long get() = current()?.version ?: 0L

    internal fun recipes(): List<ContractRecipe> = current()?.recipes ?: bundled

    internal fun recipeOf(site: String): ContractRecipe? = recipes().firstOrNull { it.site == site }

    private fun current(): Applied? = applied?.takeIf { today() <= it.expiresEpochDay }

    private fun today(): Long = Math.floorDiv(clock(), DAY_MILLIS)

    /** Applies [envelope] when signed, valid, newer and unexpired; anything else changes nothing. */
    internal fun accept(envelope: String): String {
        val key = signature ?: return "recipe config: no key"
        val payload = key.payload(envelope) ?: return "recipe config ignored: not signed by this build's key"
        return when (val parsed = RemoteRecipeConfig.parse(payload, bundled)) {
            is RemoteRecipeConfig.Parsed.Invalid -> "recipe config ignored: ${parsed.reason}"
            is RemoteRecipeConfig.Parsed.Valid -> when {
                parsed.expiresEpochDay < today() -> "recipe config ignored: expired"
                parsed.version <= (applied?.version ?: 0L) ->
                    "recipe config ignored: version ${parsed.version} is not newer"
                else -> {
                    applied = Applied(parsed.version, parsed.expiresEpochDay, parsed.recipes)
                    "recipe config ${parsed.version} applied"
                }
            }
        }
    }

    /** One bounded ask when due; returns what happened, or null when nothing was asked. */
    internal suspend fun refreshIfDue(): String? {
        val client = http?.takeIf { active } ?: return null
        if (clock() < nextAskAt || !asking.tryLock()) return null
        try {
            if (clock() < nextAskAt) return null
            val result = withTimeoutOrNull(ASK_MILLIS) {
                client.get(configUrl, mapOf("Accept" to "application/json"), MAX_ENVELOPE_BYTES)
            }
            val outcome = when {
                result is ExtractorHttpResult.Success && onGitHub(result.finalUrl) -> accept(result.body)
                result is ExtractorHttpResult.Success -> "recipe config ignored: the answer left GitHub"
                result is ExtractorHttpResult.Failure -> "recipe config unanswered: ${result.reason.name}"
                else -> "recipe config unanswered: timed out"
            }
            nextAskAt = clock() + if (result is ExtractorHttpResult.Success) DAY_MILLIS else RETRY_MILLIS
            return outcome
        } finally {
            asking.unlock()
        }
    }

    internal companion object {
        const val ASK_MILLIS = 1_500L
        const val MAX_ENVELOPE_BYTES = 128L * 1024
        const val DAY_MILLIS = 86_400_000L
        const val RETRY_MILLIS = 3_600_000L
        val CONFIG_HOSTS = setOf("raw.githubusercontent.com")

        fun onGitHub(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme.equals("https", true) && uri.userInfo == null &&
                uri.host?.lowercase() in CONFIG_HOSTS
        }
    }
}