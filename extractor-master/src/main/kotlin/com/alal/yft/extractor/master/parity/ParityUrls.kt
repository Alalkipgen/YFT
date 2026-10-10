package com.alal.yft.extractor.master.parity

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get

/**
 * R1: the frozen parity list (`parity-urls.json`, MASTER_KEY_PLAN §4.3) as data. A case with no
 * URL is an "owner picks" slot: it is never committed with a link and the live run skips it.
 */
data class ParityCase(
    val id: String,
    val site: String,
    val url: String?,
    /** Acceptable outcomes: [VIDEO], [NOT_HANDLED] or [SiteExtractionFailure] names. */
    val expect: Set<String>,
    val purpose: String,
) {
    val positive: Boolean get() = VIDEO in expect

    companion object {
        const val VIDEO = "video"
        const val NOT_HANDLED = "not_handled"
    }
}

object ParityUrls {
    val SITES = setOf("facebook", "tiktok", "instagram", "x", "vimeo", "youtube", "generic")

    /** Query names that only signed, session or expiring links carry: never in the list. */
    private val SECRET_QUERY = Regex(
        """(?i)(?:^|[?&])(?:token|sig|signature|expires?|oh|oe|key|auth|session|_nc_[a-z]+|""" +
            """x-amz-[a-z-]+|policy|hmac|hash)=""",
    )
    private val ID = Regex("""[a-z0-9][a-z0-9-]{0,62}""")
    private val OUTCOMES = SiteExtractionFailure.entries.map { it.name }.toSet() +
        ParityCase.VIDEO + ParityCase.NOT_HANDLED

    /** Parses and validates the whole list; any bad entry fails the list (fail closed). */
    fun parse(text: String): List<ParityCase> {
        val root = requireNotNull(BoundedJsonParser.parse(text, maxDepth = 8, maxNodes = 20_000)) {
            "parity list is not JSON"
        }
        require(root["version"] is JsonValue.Number) { "parity list has no version" }
        val cases = root["cases"].asArrayOrEmpty.map(::case)
        require(cases.isNotEmpty()) { "parity list is empty" }
        val duplicate = cases.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "duplicate parity ids: $duplicate" }
        return cases
    }

    private fun case(node: JsonValue): ParityCase {
        val id = requireNotNull(node["id"].asStringOrNull) { "parity case without id" }
        require(ID.matches(id)) { "bad parity id: $id" }
        val site = node["site"].asStringOrNull
        require(site in SITES) { "$id: unknown site $site" }
        val url = node["url"].asStringOrNull
        url?.let { require(publicLink(it)) { "$id: link is not a public https page link" } }
        val expect = node["expect"].asArrayOrEmpty.mapNotNull { it.asStringOrNull }.toSet()
        require(expect.isNotEmpty() && expect.all { it in OUTCOMES }) { "$id: bad expect $expect" }
        val purpose = node["purpose"].asStringOrNull.orEmpty()
        require(purpose.isNotBlank()) { "$id: no purpose" }
        return ParityCase(id, checkNotNull(site), url, expect, purpose)
    }

    /** https, a host, no user info, no fragment and no signed/session query. */
    fun publicLink(url: String): Boolean {
        val match = LINK.matchEntire(url) ?: return false
        val authority = match.groupValues[1]
        return '@' !in authority && authority.isNotEmpty() && '#' !in url &&
            !SECRET_QUERY.containsMatchIn(url.substringAfter('?', ""))
    }

    private val LINK = Regex("""https://([A-Za-z0-9.-]+)(?:[/?][^\s\\]*)?""")
}
