package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls

/**
 * Phase 1.4 item 4 (MASTER_KEY_PHASE1_3_PLAN.md §10; the owner's go under guardrail 4): the last
 * good player ID when the watch page names none.
 *
 * [named] reads the page the way the copied page reader does (`YouTubePlayerResponseParser`'s
 * `jsUrl` / `PLAYER_JS_URL` pattern and [YouTubeUrls.playerId]). When it finds none, the ladder
 * puts the player ID of the last successful lookup in front of the page ([withPlayerId]), so the
 * copied reader finds it first and S runs with it; nothing else in the page changes. V and the init
 * check stay on every row, so a stale ID ends in a clean stop, never a wrong row. Never after a bot
 * check (the bot-check window of [ClientHealth]).
 */
internal object PagePlayerId {
    /** The copied page reader's pattern; the first match is the one it reads. */
    private val JS_URL = Regex("\"(?:jsUrl|PLAYER_JS_URL)\"\\s*:\\s*\"([^\"]{1,400})\"")

    /** The player ID the copied reader reads from [page], or null when the page names none it can use. */
    fun named(page: String): String? = YouTubeUrls.playerId(JS_URL.find(page)?.groupValues?.get(1))

    /** [page] with [playerId] named first, the way the copied reader finds it. */
    fun withPlayerId(page: String, playerId: String): String =
        "<script>ytcfg.set({\"PLAYER_JS_URL\":\"/s/player/$playerId/player_ias.vflset/en_US/base.js\"});</script>\n$page"
}
