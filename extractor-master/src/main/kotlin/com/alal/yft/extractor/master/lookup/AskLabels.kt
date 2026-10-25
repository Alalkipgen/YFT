package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpResult

/**
 * Social plan S0a (`MASTER_KEY_SOCIAL_PLAN.md` §7.1): what a [LookupRecord] made for the site
 * census writes next to each ask: the way that asked ([way]: main's "public page", Master's
 * "plugin player", a "file check", ...), and what a 2xx answer held ([read]). The site table
 * ([com.alal.yft.extractor.master.social.SiteWays]) gives one instance per census row. A record
 * given none (every lookup of the app, route B included) labels nothing and counts as before.
 *
 * Both are read-only looks at the ask and the answer, outside the record's lock; a label that
 * throws is no label. They never see a cookie value (only the ask's headers as sent) and keep
 * nothing of the body: a reading is a mark and a short fingerprint.
 */
interface AskLabels {
    /** The way [ask] belongs to (a short label), or null when none fits. */
    fun way(ask: LookupAsk): String?

    /** What the 2xx answer [result] to [ask] (of [way]) held; null: not read. */
    fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading? = null
}

/**
 * S0a: one answer's reading: its [mark] and its [shape], a short fingerprint of the answer's
 * structure (key names or markers, never a value): `j` + 8 hex for JSON, `h` + 8 hex for a page.
 */
data class AskReading(val mark: AnswerMark, val shape: String? = null)

/** S0a: what an answer held; [stops]: the host is asked nothing more (as a 429). */
enum class AnswerMark(val label: String, val stops: Boolean = false) {
    /** The post's media data, as main or Master reads it. */
    DATA("data"),

    /** The site's word that the post is not shown (removed, private, region, a status code). */
    NO_POST("no post"),

    /** An answer about the post without its media data (photos only, a changed page, unknown). */
    NO_DATA("no data"),

    /** A JSON way answered something that is not JSON. */
    NOT_JSON("not JSON"),

    /** The site's login or checkpoint page (or its JSON saying so). */
    LOGIN_PAGE("login page"),

    /** The site asked the request to prove it is not automated. */
    BOT_CHECK("bot check", stops = true),
}
