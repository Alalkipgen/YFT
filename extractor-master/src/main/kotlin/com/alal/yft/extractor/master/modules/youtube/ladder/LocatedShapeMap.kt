package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.JsonValue

/**
 * Phase 1.4 item 3 (MASTER_KEY_PHASE1_3_PLAN.md §10): the shape map L located, recorded only.
 *
 * The canonical names' key paths L found in one answer — the format lists (where
 * `streamingData.adaptiveFormats[]` sits now), the video object's `lengthSeconds`, the status
 * object's `status` and the first DRM marker's place — under the answer's shape fingerprint (the
 * core hash, [ShapeFingerprint]). Key paths only: never a value, address or ID (a key equal to
 * the video ID is already `*`, [ShapeLocator]). Written to [ClientHealth] when L runs and to the
 * census record (W's report, `lmap`); nothing reads with it, so the whole-answer checks stay as
 * they are (guardrail 9). It shows a moved layout before L step 7 is needed (§10 item 2).
 */
internal object LocatedShapeMap {
    private val PATH = Regex("^[A-Za-z0-9_.*\\[\\]]{1,200}$")

    /** The located paths of [scan] (bounded, distinct, in answer order). */
    fun paths(scan: ShapeLocator.Scan): List<String> = buildList {
        scan.candidates.forEach { add(it.path) }
        scan.videos.forEach { add(child(it.path, "lengthSeconds")) }
        scan.statuses.forEach { add(child(it.path, "status")) }
        scan.drmPath?.let(::add)
    }.filter(::isPath).distinct().take(ClientHealth.MAX_PATHS)

    /** The answer's shape fingerprint (the core hash; key names only). */
    fun fingerprint(root: JsonValue, videoId: String): String = ShapeFingerprint.of(root, redact = setOf(videoId)).coreHash

    /** L's write: the map of one answer under client [id]; false when there is nothing to keep. */
    fun record(health: ClientHealth, id: String, root: JsonValue?, scan: ShapeLocator.Scan?, videoId: String): Boolean {
        if (root == null || scan == null) return false
        val paths = paths(scan).takeIf { it.isNotEmpty() } ?: return false
        health.rememberShape(id, fingerprint(root, videoId), paths)
        return true
    }

    fun isPath(text: String): Boolean = PATH.matches(text)

    private fun child(path: String, key: String): String = if (path.isEmpty()) key else "$path.$key"
}
