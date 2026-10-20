package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot

/**
 * Social plan S1a (3) (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): a social site's ladder inside Master's
 * engine (Facebook's: [FacebookLadder]). For a lookup it [claims], its [stage] answers the
 * engine's first stage in place of R8's unjudged ask; the page data and the visible tab's own
 * playback (capture) follow as before. The engine without one: exactly as before.
 */
interface SocialStage {
    /** Whether [request]'s lookup is the ladder's (its site, inside the lookup's own record). */
    suspend fun claims(request: MasterRequest): Boolean

    /**
     * The ladder's rungs for a lookup it claims, within [policy]'s time: a snapshot to read
     * ([Staged]: the rows to check, or a final answer), or null when nothing is left to read (the
     * next stage). Its lines go to [details] (short labels, never a link, an ID or a page's words).
     */
    suspend fun stage(request: MasterRequest, policy: MasterPolicy, details: MutableList<String>): Staged?

    /**
     * A rung's answer: its [snapshot] (of the request's page and generation), read as [stage] for
     * [request] — the lookup's request with the video the rung asked about (a link main resolves
     * first: the video main's resolved link names).
     */
    class Staged(val stage: MasterStage, val snapshot: PageSnapshot, val request: MasterRequest)
}
