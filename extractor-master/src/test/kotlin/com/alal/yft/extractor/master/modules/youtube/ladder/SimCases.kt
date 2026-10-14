package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures

/** The ladder's rungs (plan §3); a case names those through which a checkable complete path exists. */
internal enum class Rung { R0, A, S, Z, L, H, O }

/**
 * Phase 1.3 D1: one simulator case, a change class (plan §4 D1) on one base answer with one V
 * mode. [paths] are the rungs through which the simulated world still gives the complete correct
 * rows, checkable by V or the H check (MCF, plan §5.1): empty = a server closure or a fault, where
 * a clean stop is right. [terminal] = a verdict about the video: no row may be offered at all.
 */
internal class SimCase(
    val klass: String,
    val variant: String,
    val base: String,
    val vMode: VMode,
    val paths: Set<Rung>,
    val terminal: Boolean = false,
    val lookups: Int = 1,
    private val setup: SimWorld.() -> Unit,
) {
    val id: String = buildString {
        append(klass)
        if (variant.isNotEmpty()) append('/').append(variant)
        append('/').append(base.removePrefix("player_").removeSuffix(".json"))
        if (vMode != VMode.PASS) append("/v-").append(vMode.name.lowercase())
    }

    fun world(): SimWorld = SimWorld(base, vMode).apply(setup)

    override fun toString(): String = id
}

/** The change classes made in code (plan §4 D1), each on every base answer. */
internal object SimCases {
    /** The classes in the plan's table order, plus B0 (no change) as the control. */
    val CLASSES: List<String> = listOf("B0") + (1..11).map { "V$it" } + (1..16).map { "L$it" } +
        (1..6).map { "Y$it" } + listOf("T1", "T1b", "T2", "T3") + listOf("H1", "H2") + (1..4).map { "F$it" }

    fun all(): List<SimCase> = Sim.BASES.flatMap(::forBase)

    fun forBase(base: String): List<SimCase> = buildList {
        fun case(
            klass: String,
            paths: Set<Rung>,
            variant: String = "",
            vMode: VMode = VMode.PASS,
            terminal: Boolean = false,
            lookups: Int = 1,
            setup: SimWorld.() -> Unit,
        ) = add(SimCase(klass, variant, base, vMode, paths, terminal, lookups, setup))

        case("B0", setOf(Rung.R0, Rung.H, Rung.O)) {}

        // V: changes to visionOS alone; the other identities answer as before (O works when simulated).
        case("V1", setOf(Rung.A, Rung.O)) { retireBaseVersion(newSource = true) }
        case("V2", setOf(Rung.S, Rung.H, Rung.O)) { visionCipher = true }
        case("V3", setOf(Rung.Z, Rung.H, Rung.O)) { onVision { it.formats().forEach { format -> format.remove("contentLength") } } }
        case("V4", setOf(Rung.H, Rung.O)) { onVision(::sabrOnlyAdaptive) }
        case("V5", setOf(Rung.H, Rung.O)) { refuseVisionAdaptive() }
        case("V6", setOf(Rung.O)) {
            refuseVisionAdaptive()
            hls.master = SimWorld.FORBIDDEN
        }
        case("V7", setOf(Rung.O)) { override = { ask -> if (ask.client == SimClient.VISION_OS) SimReply.Json(NOT_ON_THIS_APP) else null } }
        case("V8", setOf(Rung.L, Rung.H, Rung.O)) { onVision(::renameLists) }
        case("V9", setOf(Rung.O)) { closeVision() }
        case("V10", setOf(Rung.H, Rung.O)) { onVision(::removeFormats) }
        case("V11", setOf(Rung.R0, Rung.O)) { hls.master = SimWorld.FORBIDDEN }

        // L: a layout change in every identity's answer (and the page's inline answer). [rest] are
        // the paths left when V fails or cannot answer on the L rows (VMode).
        for (mode in VMode.entries) {
            fun layout(
                klass: String,
                pass: Set<Rung>,
                rest: Set<Rung>,
                variant: String = "",
                terminal: Boolean = false,
                setup: SimWorld.() -> Unit,
            ) = case(klass, if (mode == VMode.PASS) pass else rest, variant, mode, terminal, setup = setup)

            val hls = setOf(Rung.H)
            layout("L1", setOf(Rung.L), emptySet()) { onAll(::moveStreamingData) }
            layout("L2", setOf(Rung.L, Rung.H), hls) { onAll(::renameLists) }
            layout("L3", setOf(Rung.L, Rung.H, Rung.O), setOf(Rung.H, Rung.O)) {
                onAll { json -> json.formats().forEach { it.rename("url", "streamUrl") } }
            }
            layout("L4", setOf(Rung.L, Rung.H), hls, "renamed") { onAll { json -> json.formats().forEach { it.rename("mimeType", "mime") } } }
            layout("L4", setOf(Rung.L, Rung.H), hls, "split") { onAll { json -> json.formats().forEach(::splitMime) } }
            layout("L5", setOf(Rung.L, Rung.S, Rung.H), hls) {
                visionCipher = true
                onAll { json -> json.formats().forEach { it.rename("signatureCipher", "streamCipher") } }
            }
            layout("L6", setOf(Rung.R0, Rung.L, Rung.H, Rung.O), setOf(Rung.R0, Rung.H, Rung.O)) { onAll(::driftTypes) }
            layout("L7", setOf(Rung.L, Rung.H), hls) { onAll(::listsToMaps) }
            layout("L8", setOf(Rung.L), emptySet()) {
                onAll(::moveStreamingData)
                onAll { json -> json["endScreen"] = decoy(this, "l8", Sim.OTHER_VIDEO, DECOY_SECONDS) }
            }
            layout("L9", emptySet(), emptySet()) {
                onAll(::moveStreamingData)
                onAll { json -> json["related"] = decoy(this, "l9", Sim.OTHER_VIDEO, lengthSeconds) }
            }
            layout("L10", setOf(Rung.L), emptySet()) {
                onAll(::moveStreamingData)
                onAll { json -> json.remove("playabilityStatus")?.let { status -> SimJson.obj(json["playback"])["status"] = status } }
            }
            for ((name, reason) in TERMINAL_REASONS) {
                layout("L11", emptySet(), emptySet(), name, terminal = true) {
                    onAll { json ->
                        json["playabilityStatus"] = SimJson.parse("""{"status":"LOGIN_REQUIRED","reason":"$reason"}""")
                        moveStreamingData(json)
                    }
                }
            }
            layout("L12", emptySet(), emptySet()) {
                encrypted = true
                onAll { json -> json.formats().forEach { it["drmFamiliesV2"] = SimJson.parse("""["WIDEVINE"]""") } }
                onAll(::moveStreamingData)
            }
            layout("L13", setOf(Rung.L, Rung.H), emptySet()) {
                onAll(::removeFormats)
                onVision { json ->
                    val streaming = SimJson.obj(json["streamingData"])
                    val address = streaming.remove("hlsManifestUrl")
                    json["playback"] = linkedMapOf<String, Any?>("hls" to linkedMapOf<String, Any?>("manifestUrl" to address))
                }
            }
            layout("L14", setOf(Rung.S, Rung.H, Rung.O), hls) {
                visionCipher = true
                pageLocated = true
                pageEdits += { html ->
                    html.replace("\"PLAYER_JS_URL\"", "\"PLAYER_SCRIPT_V2\"").replace("\"jsUrl\"", "\"scriptV2\"")
                        .replace("\"STS\"", "\"STS_V2\"").replace("\"signatureTimestamp\"", "\"stsV2\"")
                        .replace("\"VISITOR_DATA\"", "\"VISITOR_V2\"").replace("\"visitorData\"", "\"visitorV2\"")
                }
            }
            layout("L15", setOf(Rung.L, Rung.H), hls) { onAll(::splitAudioIntoNewList) }
            layout("L16", setOf(Rung.L), emptySet()) {
                onAll(::deepMove)
                onAll { json -> json["noise"] = MutableList<Any?>(NOISE_OBJECTS) { linkedMapOf<String, Any?>("k" to it, "v" to "x") } }
            }
        }

        // Y: visionOS's base version is retired; the version sources differ.
        case("Y1", setOf(Rung.A, Rung.O)) { retireBaseVersion(newSource = true) }
        for ((name, text) in listOf(
            "garbage" to "<html>no client table here</html>",
            "pattern" to Census.ytDlp("1.03b"),
            "older" to Census.ytDlp("1.01"),
            "same" to Census.ytDlp(Sim.BASE_VERSION),
        )) {
            case("Y2", setOf(Rung.O), name) {
                acceptedVersions = setOf(UNKNOWN_VERSION)
                sources[CensusSources.YT_DLP_URL] = SimReply.Json(text)
            }
        }
        for ((name, reply) in listOf("down" to SimReply.Network, "5xx" to SimReply.Status(503))) {
            case("Y3", setOf(Rung.O), name) {
                acceptedVersions = setOf(UNKNOWN_VERSION)
                sources[CensusSources.YT_DLP_URL] = reply
            }
        }
        case("Y4", setOf(Rung.O)) {
            acceptedVersions = emptySet()
            sources[CensusSources.YT_DLP_URL] = SimReply.Json(Census.ytDlp(Sim.NEW_VERSION))
        }
        case("Y5", setOf(Rung.A, Rung.O), lookups = 3) { retireBaseVersion(newSource = true) }
        case("Y6", setOf(Rung.A, Rung.O)) {
            retireBaseVersion(newSource = false)
            sources[CensusSources.YT_DLP_URL] = SimReply.Json(
                Census.ytDlp(Sim.NEW_VERSION).replace(
                    "'clientName': 'VISIONOS',",
                    "'clientName': 'VISIONOS',\n                'userAgent': 'Other/9.9',\n                'deviceModel': 'Other',",
                ),
            )
        }

        // T: verdicts about the video or the request, from every identity and the page.
        case("T1", emptySet(), terminal = true) { everyone(fixture("player_bot_check.json")) }
        // T1b (owner, 2026-10-15; the census's first run, 2026-10-14): only visionOS asked without
        // visitor data meets the bot check; R6's chain asks it again with the page's visitor data.
        case("T1b", setOf(Rung.R0, Rung.O)) {
            val check = fixture("player_bot_check.json")
            override = { ask -> if (ask.client == SimClient.VISION_OS && !ask.visitor) SimReply.Json(check) else null }
        }
        case("T2", emptySet(), terminal = true) { everyone(fixture("player_age_gate.json")) }
        for ((name, file) in listOf(
            "private" to "player_private.json",
            "removed" to "player_unavailable.json",
            "geo" to "player_geo.json",
            "drm" to "player_drm.json",
        )) {
            case("T3", emptySet(), name, terminal = true) { everyone(fixture(file)) }
        }

        // H: two lookups in a row; the second may use what the first learned.
        case("H1", setOf(Rung.O), lookups = 2) { closeVision() }
        case("H2", setOf(Rung.S, Rung.H, Rung.O), lookups = 2) { visionCipher = true }

        // F: faults; the only right outcome is no wrong row.
        for (mode in listOf(SolverMode.WRONG, SolverMode.UNSOLVED)) {
            case("F1", emptySet(), mode.name.lowercase()) {
                visionCipher = true
                hls.present = false
                solver = mode
            }
        }
        case("F2", emptySet()) {
            visionCipher = true
            hls.present = false
            probesDown = true
        }
        for ((name, fault) in listOf<Pair<String, SimHls.() -> Unit>>(
            "segment-403" to { segment = SimWorld.FORBIDDEN },
            "type" to { contentType = "text/html" },
            "start" to { startText = "<" },
            "host" to { segmentHost = Sim.FOREIGN_HOST },
            "duration" to { durationSeconds += DURATION_FAULT_SECONDS },
        )) {
            case("F3", emptySet(), name) {
                onVision(::removeFormats)
                closeOthers()
                hls.fault()
            }
        }
        case("F4", setOf(Rung.O)) { visionVideo = Sim.OTHER_VIDEO }
    }

    // --- changes, made in code on the parsed answer ---

    private fun SimWorld.onVision(edit: (MutableMap<String, Any?>) -> Unit) {
        edits += { client, json -> if (client == SimClient.VISION_OS) edit(json) }
    }

    private fun SimWorld.onAll(edit: (MutableMap<String, Any?>) -> Unit) {
        edits += { _, json -> edit(json) }
    }

    private fun SimWorld.retireBaseVersion(newSource: Boolean) {
        acceptedVersions = setOf(Sim.NEW_VERSION)
        if (newSource) sources[CensusSources.YT_DLP_URL] = SimReply.Json(Census.ytDlp(Sim.NEW_VERSION))
    }

    private fun SimWorld.refuseVisionAdaptive() {
        refused = { client, _, list -> client == SimClient.VISION_OS && list == "adaptiveFormats" }
    }

    private fun SimWorld.closeVision() {
        override = { ask -> if (ask.client == SimClient.VISION_OS) SimReply.Status(SimWorld.BAD_REQUEST) else null }
    }

    /** F3: no identity but visionOS's HLS can give a row (embedded refuses, the rest stream SABR only). */
    private fun SimWorld.closeOthers() {
        override = { ask ->
            when (ask.client) {
                SimClient.VISION_OS -> null
                SimClient.EMBEDDED -> SimReply.Json(fixture("player_embed_refused.json"))
                else -> SimReply.Json(fixture("player_sabr_only.json"))
            }
        }
        pageJson = fixture("player_sabr_only.json")
    }

    private fun SimWorld.everyone(answer: String) {
        override = { SimReply.Json(answer) }
        pageJson = answer
    }

    private fun MutableMap<String, Any?>.streaming(): MutableMap<String, Any?>? =
        (this["streamingData"] as? MutableMap<*, *>)?.let(SimJson::obj)

    private fun MutableMap<String, Any?>.formats(): List<MutableMap<String, Any?>> = streaming()?.let { data ->
        SimWorld.LIST_KEYS.flatMap { key -> (data[key] as? List<*>).orEmpty().map(SimJson::obj) }
    }.orEmpty()

    private fun MutableMap<String, Any?>.rename(from: String, to: String) {
        if (containsKey(from)) this[to] = remove(from)
    }

    private fun sabrOnlyAdaptive(json: MutableMap<String, Any?>) {
        (json.streaming()?.get("adaptiveFormats") as? List<*>).orEmpty().map(SimJson::obj).forEach {
            it.remove("url")
            it.remove("signatureCipher")
        }
    }

    private fun removeFormats(json: MutableMap<String, Any?>) {
        json.streaming()?.let { data ->
            data.remove("formats")
            data.remove("adaptiveFormats")
            data.remove("serverAbrStreamingUrl")
        }
    }

    private fun renameLists(json: MutableMap<String, Any?>) {
        json.streaming()?.let { data ->
            data.rename("formats", "muxedFormats")
            data.rename("adaptiveFormats", "separateFormats")
        }
    }

    private fun moveStreamingData(json: MutableMap<String, Any?>) {
        val data = json.remove("streamingData") ?: return
        json["playback"] = linkedMapOf<String, Any?>("streamingData" to data)
    }

    /** L16: the formats moved 10 levels deep, beside the noise. */
    private fun deepMove(json: MutableMap<String, Any?>) {
        val data = json.remove("streamingData") ?: return
        var node: Any? = linkedMapOf<String, Any?>("streamingData" to data)
        for (level in DEEP_LEVELS downTo 1) node = linkedMapOf<String, Any?>("level$level" to node)
        json["frame"] = node
    }

    private fun splitMime(format: MutableMap<String, Any?>) {
        val mime = format.remove("mimeType") as? String ?: return
        format["type"] = mime.substringBefore(';').trim()
        format["codecs"] = mime.substringAfter("codecs=\"", "").substringBefore('"')
    }

    private fun driftTypes(json: MutableMap<String, Any?>) {
        for (format in json.formats()) {
            (format["itag"] as? SimJson.Num)?.let { format["itag"] = it.text }
            (format["contentLength"] as? String)?.let { format["contentLength"] = SimJson.Num(it) }
            (format["approxDurationMs"] as? String)?.let { format["approxDurationMs"] = SimJson.Num(it) }
            for (key in listOf("width", "height", "bitrate", "averageBitrate", "fps")) {
                (format[key] as? SimJson.Num)?.let { format[key] = it.text }
            }
        }
        (json["videoDetails"] as? MutableMap<*, *>)?.let(SimJson::obj)?.let { details ->
            (details["lengthSeconds"] as? String)?.let { details["lengthSeconds"] = SimJson.Num(it) }
        }
    }

    private fun listsToMaps(json: MutableMap<String, Any?>) {
        json.streaming()?.let { data ->
            for (key in SimWorld.LIST_KEYS) {
                val list = (data[key] as? List<*>) ?: continue
                val map = LinkedHashMap<String, Any?>()
                list.map(SimJson::obj).forEachIndexed { index, format ->
                    val itag = (format["itag"] as SimJson.Num).text
                    map[if (itag in map) "$itag-$index" else itag] = format
                }
                data[key] = map
            }
        }
    }

    /** L15: the audio formats in a new list, the video formats where they were. */
    private fun splitAudioIntoNewList(json: MutableMap<String, Any?>) {
        json.streaming()?.let { data ->
            val list = SimJson.list(data["adaptiveFormats"] ?: return)
            val audio = list.filter { (SimJson.obj(it)["mimeType"] as? String)?.startsWith("audio/") == true }
            list.removeAll(audio)
            data["audioOnlyFormats"] = ArrayList(audio)
        }
    }

    /** Another video's format list (an end screen or related video), addresses of its own files. */
    private fun decoy(world: SimWorld, tag: String, video: String, seconds: Long): MutableMap<String, Any?> {
        val formats = listOf(137 to "video/mp4; codecs=\"avc1.640028\"", 140 to "audio/mp4; codecs=\"mp4a.40.2\"").map { (itag, mime) ->
            linkedMapOf<String, Any?>(
                "itag" to SimJson.Num(itag.toString()),
                "url" to world.extraFile("$tag-$itag", itag, mime, video, seconds),
                "mimeType" to mime,
                "contentLength" to (3_000_000L + itag).toString(),
                "approxDurationMs" to (seconds * 1_000).toString(),
            ).also { if (itag == 137) it["height"] = SimJson.Num("1080") }
        }
        return linkedMapOf("videoId" to video, "lengthSeconds" to seconds.toString(), "formats" to formats.toMutableList())
    }

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private val TERMINAL_REASONS = listOf(
        "age" to "Sign in to confirm your age",
        "login" to "Sign in to watch this video",
        "private" to "This video is private",
    )

    private const val NOT_ON_THIS_APP =
        """{"playabilityStatus":{"status":"UNPLAYABLE","reason":"The following content is not available on this app.",""" +
            """"errorScreen":{"playerErrorMessageRenderer":{"subreason":{"runs":[{"text":"Watch this content on the latest version of YouTube."}]}}}}}"""
    private const val UNKNOWN_VERSION = "1.09"
    private const val DECOY_SECONDS = 30L
    private const val NOISE_OBJECTS = 10_000
    private const val DEEP_LEVELS = 10
    private const val DURATION_FAULT_SECONDS = 30.0
}
