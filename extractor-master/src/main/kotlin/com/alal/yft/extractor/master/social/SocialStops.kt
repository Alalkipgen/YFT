package com.alal.yft.extractor.master.social

/**
 * Social plan S1a (5), S7 (`MASTER_KEY_SOCIAL_PLAN.md` §4 S7, §7.3): a site's budget per lookup
 * for the ladder's rungs that send requests. "Lookup" is the user's whole lookup, main's asks and
 * Master's (the lookup's `LookupRecord`): a rung asks only while main's and Master's asks so far
 * are fewer than [requests], the lookup is younger than [millis] (main's time included; the rung
 * waits at most the time left) and Master's answers in the lookup hold fewer than [bytes]
 * characters (a rung reads at most the characters left). Main's pages are main's: F2 reads them
 * with no request, so they count only as main's asks.
 *
 * v1.0.2 (item 18): [gapMillis], the least time between two asks of the site's rung across lookups
 * in the app's session ([SocialCooldown.pace]); 0, none.
 */
data class SocialBudget(
    val requests: Int,
    val bytes: Long,
    val millis: Long,
    val gapMillis: Long = 0,
) {
    companion object {
        /**
         * Facebook's (the pilot): main asks at most three pages for a link (the public page, the
         * page with the session, the video's page for its sizes), so F1's one ask fits; F1's
         * answer is at most 6 MiB (its recipe's bound).
         */
        val FACEBOOK = SocialBudget(requests = 4, bytes = 8L * 1024 * 1024, millis = 20_000)

        /**
         * S2b, TikTok's, sized by TikTok's main, T1 and V (not copied from Facebook's): main asks
         * at most 19 in a lookup (the phone and the desktop page, 8 file checks, the download
         * service and its 8; each file check is an ask in the record), so T1's one ask (the embed
         * player) fits after main's most; T1's answer is at most 3 MiB (its recipe's bound). Main's
         * pages take long (two pages, the file checks, the service), so the lookup's time is 30 s.
         *
         * v1.0.2 (item 18): T1's asks at least 10 s apart. In S5's run 3 the embed player answered
         * 429 after one or two asks a few seconds apart (the census's posts in a row; in runs 1
         * and 2 six such asks got none). A lookup within the gap waits the rest when T1's ask still
         * fits its 30 s after the wait, else T1 is not asked ([TikTokLadder]).
         */
        val TIKTOK = SocialBudget(
            requests = 20,
            bytes = 4L * 1024 * 1024,
            millis = 30_000,
            gapMillis = 10_000,
        )

        /**
         * S2c, X's, sized by X's main and V (not copied from TikTok's): main asks once in a lookup
         * (the widget's answer) and X's ladder asks nothing (main's answer replayed), so the rows
         * go to V only while main's ask and V's reads (at most 8) fit; the answer the ladder reads
         * is main's, at most 2 MiB (main's bound). Main's one ask is short, so the lookup's time
         * is 20 s, as Facebook's.
         */
        val X = SocialBudget(requests = 9, bytes = 2L * 1024 * 1024, millis = 20_000)

        /**
         * S2d, Instagram's, sized by Instagram's main, the probe and V (not copied from X's): main
         * asks at most four in a lookup (the app API when signed in, GraphQL, the post's page, the
         * embed page), I2 asks nothing (main's answers read), the tab-fetch probe at most once, so
         * the rows go to V only while those and V's reads (at most 8) fit; the answer I2 reads is
         * main's, at most 6 MiB (main's bound), and the probe's at most 1 MiB (its script's bound).
         * Main's four pages take long, so the lookup's time is 30 s, as TikTok's.
         */
        val INSTAGRAM = SocialBudget(requests = 13, bytes = 7L * 1024 * 1024, millis = 30_000)

        /**
         * VK-Master plan §6, VK's (agent D registers it here; sized in [VkSite.BUDGET], not copied
         * from Instagram's): main's at most 6 asks, V2's heal read (the page and its common script),
         * one token and two `video.get`s, and V's 8 reads — 19 requests, 7,356,416 bytes at each
         * ask's bound — within main's one 15 s deadline.
         */
        val VK = VkSite.BUDGET
    }
}

/**
 * S1a (5), S7: the hosts that answered a 429 or a bot check, each cooling for [millis] (30
 * minutes) after its last stop, across lookups: a rung that sends requests does not ask a host
 * while it cools (F1, the plugin player); main's asks and the rungs that read without a request
 * (F2, the tab's own playback) are not stopped by it. Memory only (the process's: an app start
 * forgets it), hosts only (lowercase), never a link, an ID or a page's words; nothing is logged.
 *
 * v1.0.2 (item 18): the same memory paces a rung's asks to a host ([pace]): each at least a gap
 * after the one before, across lookups (TikTok's T1, [SocialBudget.gapMillis]).
 */
class SocialCooldown(
    private val clock: () -> Long = System::currentTimeMillis,
    private val millis: Long = COOLDOWN_MILLIS,
) {
    private val until = HashMap<String, Long>()

    /** v1.0.2: per host, the earliest time a paced rung may ask it again ([pace]). */
    private val nextAsk = HashMap<String, Long>()

    /** [hosts] stopped now: each cools until [millis] from now (a later stop extends it). */
    fun note(hosts: Collection<String>) {
        if (hosts.isEmpty()) return
        val now = clock()
        synchronized(until) {
            until.values.removeAll { it <= now }
            for (host in hosts) {
                val key = keyOf(host) ?: continue
                until[key] = maxOf(until[key] ?: 0L, now + millis)
            }
        }
    }

    /** Whether [host] is still cooling. */
    fun cooling(host: String): Boolean {
        val key = keyOf(host) ?: return false
        val now = clock()
        return synchronized(until) { until[key]?.let { now < it } == true }
    }

    /**
     * v1.0.2 (item 18): a rung's next ask to [hosts], at least [gapMillis] after its last one: the
     * millis to wait before it (0: at once), its turn then taken, so a lookup at the same time
     * waits for the turn after it; null, nothing taken, when the wait would be over
     * [maxWaitMillis] (the rung does not ask). A gap of 0 paces nothing. It does not cool a host.
     */
    fun pace(hosts: Collection<String>, gapMillis: Long, maxWaitMillis: Long): Long? {
        val keys = hosts.mapNotNull { keyOf(it) }.distinct()
        if (gapMillis <= 0 || keys.isEmpty()) return 0L
        val now = clock()
        synchronized(until) {
            nextAsk.values.removeAll { it <= now }
            val wait = keys.maxOf { (nextAsk[it] ?: now) - now }
            if (wait > 0 && wait > maxWaitMillis) return null
            for (key in keys) nextAsk[key] = now + wait + gapMillis
            return wait
        }
    }

    override fun toString(): String = synchronized(until) { "SocialCooldown(hosts=${until.size})" }

    companion object {
        /** S7's cooldown: 30 minutes after a host's last 429 or bot check. */
        const val COOLDOWN_MILLIS = 30L * 60 * 1000

        private fun keyOf(host: String): String? = host.trim().lowercase().trimEnd('.').takeIf(String::isNotEmpty)
    }
}
