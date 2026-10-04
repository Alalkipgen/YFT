package com.alal.yft.detection.potoken

import android.content.Context
import com.alal.yft.detection.script.YouTubePlayerScriptRunner
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PoTokenProvider
import com.alal.yft.extractor.api.PoTokenRequest
import com.alal.yft.extractor.api.PoTokenResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Mints YouTube proof-of-origin tokens the way YouTube's own web player does (ADR-006).
 *
 * It reads the attestation key from YouTube's current player script, asks YouTube's
 * attestation service for today's BotGuard challenge, runs that challenge in [engine] (an
 * offscreen page with no network and no cookies), trades BotGuard's answer for an integrity
 * token and then mints one token per video ID. YFT never reimplements BotGuard: the page runs
 * YouTube's own interpreter and program. The page and its minter are kept while the integrity
 * token is fresh and closed after a few idle minutes; keys and tokens live only in memory.
 */
internal class BotGuardPoTokenProvider(
    private val http: ExtractorHttpClient,
    private val transport: AttestationTransport,
    private val engine: BotGuardEngine,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idleMillis: Long = DEFAULT_IDLE_MILLIS,
    private val budgetMillis: Long = DEFAULT_BUDGET_MILLIS,
) : PoTokenProvider {
    constructor(context: Context, client: OkHttpClient, http: ExtractorHttpClient) : this(
        http = http,
        transport = OkHttpAttestationTransport(client),
        engine = WebViewBotGuardEngine(context),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val lock = Mutex()

    /** Guarded by [lock]. The attestation key of the last player version seen. */
    private var key: CachedKey? = null

    /** Guarded by [lock]. */
    private var session: Session? = null

    /** Guarded by [lock]. */
    private var idleClose: Job? = null

    override val isAvailable: Boolean
        get() = engine.isAvailable

    override suspend fun mint(request: PoTokenRequest): PoTokenResult {
        if (!engine.isAvailable) return PoTokenResult.Unavailable
        val binding = request.contentBinding
        if (!BotGuardProtocol.BINDING.matches(binding)) {
            return PoTokenResult.Failed(SiteExtractionFailure.UNSUPPORTED_URL)
        }
        return lock.withLock {
            idleClose?.cancel()
            try {
                withTimeoutOrNull(budgetMillis) { mintLocked(binding, request) }
                    ?: PoTokenResult.Failed(SiteExtractionFailure.NETWORK).also { closeSession() }
            } finally {
                scheduleIdleClose()
            }
        }
    }

    private suspend fun mintLocked(binding: String, request: PoTokenRequest): PoTokenResult {
        val current = session?.takeIf { clock() < it.refreshAtEpochMs } ?: run {
            closeSession()
            when (val opened = openSession(request)) {
                is Opening.Ready -> opened.session.also { session = it }
                is Opening.Failed -> return PoTokenResult.Failed(opened.reason)
            }
        }
        current.tokens[binding]?.let { return PoTokenResult.Minted(it) }
        val token = current.page.mint(binding)?.takeIf(BotGuardProtocol.MINTED_TOKEN::matches)
        if (token == null) {
            // A minter that failed once is not trusted again; the next lookup starts afresh.
            closeSession()
            return PoTokenResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
        current.remember(binding, token)
        return PoTokenResult.Minted(token)
    }

    private suspend fun openSession(request: PoTokenRequest): Opening {
        val attestationKey = when (val found = attestationKey(request)) {
            is KeyLookup.Found -> found.key
            is KeyLookup.Failed -> return Opening.Failed(found.reason)
        }
        val userAgent = engine.userAgent()
        val created = transport.post(
            AttestationCall(
                url = BotGuardProtocol.CREATE_URL,
                attestationKey = attestationKey,
                body = BotGuardProtocol.CREATE_BODY,
                userAgent = userAgent,
            ),
        )
        val challenge = when (created) {
            is AttestationReply.Success -> BotGuardProtocol.parseChallenge(created.body)
                ?: return Opening.Failed(SiteExtractionFailure.RESPONSE_CHANGED)

            is AttestationReply.Failure -> return Opening.Failed(created.reason)
        }
        val page = engine.open(challenge)
            ?: return Opening.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        var kept = false
        try {
            val answer = page.snapshot()
                ?: return Opening.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
            val generated = transport.post(
                AttestationCall(
                    url = BotGuardProtocol.GENERATE_URL,
                    attestationKey = attestationKey,
                    body = BotGuardProtocol.generateBody(answer),
                    userAgent = userAgent,
                ),
            )
            val integrity = when (generated) {
                is AttestationReply.Success -> BotGuardProtocol.parseIntegrity(generated.body)
                    ?: return Opening.Failed(SiteExtractionFailure.RESPONSE_CHANGED)

                is AttestationReply.Failure -> return Opening.Failed(generated.reason)
            }
            if (!page.createMinter(integrity.token)) {
                return Opening.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
            }
            val freshSeconds = integrity.lifetimeSeconds - integrity.refreshSeconds
            kept = true
            return Opening.Ready(Session(page, clock() + freshSeconds * MILLIS_PER_SECOND))
        } finally {
            if (!kept) page.close()
        }
    }

    /** The key comes from the same player script the page named, read once per version. */
    private suspend fun attestationKey(request: PoTokenRequest): KeyLookup {
        val playerId = YouTubePlayerScriptRunner.playerIdOf(request.playerScriptUrl)
            ?: return KeyLookup.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        key?.takeIf { it.playerId == playerId }?.let { return KeyLookup.Found(it.key) }
        return when (
            val result = http.get(
                url = request.playerScriptUrl,
                headers = mapOf("Referer" to request.pageUrl),
                maxBodyBytes = YouTubePlayerScriptRunner.DEFAULT_MAX_PLAYER_BYTES,
            )
        ) {
            is ExtractorHttpResult.Success -> {
                val found = BotGuardProtocol.attestationKey(result.body)
                    ?: return KeyLookup.Failed(SiteExtractionFailure.RESPONSE_CHANGED)
                key = CachedKey(playerId, found)
                KeyLookup.Found(found)
            }

            is ExtractorHttpResult.Failure -> KeyLookup.Failed(result.reason)
        }
    }

    private fun scheduleIdleClose() {
        if (session == null) return
        idleClose = scope.launch {
            delay(idleMillis)
            lock.withLock { closeSession() }
        }
    }

    private fun closeSession() {
        session?.page?.close()
        session = null
    }

    private class CachedKey(val playerId: String, val key: String)

    private class Session(val page: BotGuardSession, val refreshAtEpochMs: Long) {
        /** Tokens already minted on this page, newest last. */
        val tokens = LinkedHashMap<String, String>()

        fun remember(binding: String, token: String) {
            tokens[binding] = token
            while (tokens.size > MAX_TOKENS) tokens.remove(tokens.keys.first())
        }
    }

    private sealed interface Opening {
        class Ready(val session: Session) : Opening

        class Failed(val reason: SiteExtractionFailure) : Opening
    }

    private sealed interface KeyLookup {
        class Found(val key: String) : KeyLookup

        class Failed(val reason: SiteExtractionFailure) : KeyLookup
    }

    companion object {
        /** A page with a fresh minter is kept this long after its last use. */
        const val DEFAULT_IDLE_MILLIS: Long = 5L * 60 * 1000

        /** Bounds one mint, including a first player fetch and the attestation round trips. */
        const val DEFAULT_BUDGET_MILLIS: Long = 60_000

        private const val MAX_TOKENS = 32
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
