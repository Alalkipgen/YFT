package com.alal.yft.extractor.master.social

/**
 * Social plan S1a (5), S7 (`MASTER_KEY_SOCIAL_PLAN.md` §4 S7, §7.3): a site's budget per lookup
 * for the ladder's rungs that send requests. "Lookup" is the user's whole lookup, main's asks and
 * Master's (the lookup's `LookupRecord`): a rung asks only while main's and Master's asks so far
 * are fewer than [requests], the lookup is younger than [millis] (main's time included; the rung
 * waits at most the time left) and Master's answers in the lookup hold fewer than [bytes]
 * characters (a rung reads at most the characters left). Main's pages are main's: F2 reads them
 * with no request, so they count only as main's asks.
 */
data class SocialBudget(val requests: Int, val bytes: Long, val millis: Long) {
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
         */
        val TIKTOK = SocialBudget(requests = 20, bytes = 4L * 1024 * 1024, millis = 30_000)

        /**
         * S2c, X's, sized by X's main and V (not copied from TikTok's): main asks once in a lookup
         * (the widget's answer) and X's ladder asks nothing (main's answer replayed), so the rows
         * go to V only while main's ask and V's reads (at most 8) fit; the answer the ladder reads
         * is main's, at most 2 MiB (main's bound). Main's one ask is short, so the lookup's time
         * is 20 s, as Facebook's.
         */
        val X = SocialBudget(requests = 9, bytes = 2L * 1024 * 1024, millis = 20_000)
    }
}

/**
 * S1a (5), S7: the hosts that answered a 429 or a bot check, each cooling for [millis] (30
 * minutes) after its last stop, across lookups: a rung that sends requests does not ask a host
 * while it cools (F1, the plugin player); main's asks and the rungs that read without a request
 * (F2, the tab's own playback) are not stopped by it. Memory only (the process's: an app start
 * forgets it), hosts only (lowercase), never a link, an ID or a page's words; nothing is logged.
 */
class SocialCooldown(
    private val clock: () -> Long = System::currentTimeMillis,
    private val millis: Long = COOLDOWN_MILLIS,
) {
    private val until = HashMap<String, Long>()

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

    override fun toString(): String = synchronized(until) { "SocialCooldown(hosts=${until.size})" }

    companion object {
        /** S7's cooldown: 30 minutes after a host's last 429 or bot check. */
        const val COOLDOWN_MILLIS = 30L * 60 * 1000

        private fun keyOf(host: String): String? = host.trim().lowercase().trimEnd('.').takeIf(String::isNotEmpty)
    }
}
