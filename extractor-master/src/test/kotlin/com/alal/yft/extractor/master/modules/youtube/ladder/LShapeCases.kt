package com.alal.yft.extractor.master.modules.youtube.ladder

/**
 * Phase 1.3 D5b test helper: the simulator's L layout changes (SimCases L1–L16) as edits on a
 * [SimWorld], each with what rung L alone must give. The mutations are copied from SimCases
 * (which stays unchanged; its helpers are private) and do the same to an answer. The other rungs a
 * case needs (S for L5 and L14, H for L13) are not part of L's expectation.
 * D7 (2026-10-15) adds variants of L12 and L1: the copied parser's own DRM keys under moved or
 * renamed containers (L stops, as the parser would on them), and DRM-named player settings in
 * an answer without DRM (L reads the rows).
 */
internal object LShapeCases {
    enum class Expect {
        /** L rows equal to the unchanged answer's; with V `pass` as complete as the unchanged answer. */
        ROWS,

        /** The located rows all need the player script (cipher): S's work, no row L can check. */
        PROTECTED,

        /** A verdict that is not `OK` (sign-in, age, private, DRM): stop, the R6 chain decides. */
        STOP,

        /** Two bound groups under different parents: ambiguous, no L rows. */
        AMBIGUOUS,

        /** No formats left; the moved HLS address is located for H. */
        HLS_ONLY,
    }

    class Case(
        val name: String,
        val expect: Expect,
        /** The copied parser still reads every file (L6), so V passes in every V mode. */
        val readableByR0: Boolean = false,
        val setup: SimWorld.() -> Unit,
    ) {
        override fun toString(): String = name
    }

    private val TERMINAL_REASONS = listOf(
        "age" to "Sign in to confirm your age",
        "login" to "Sign in to watch this video",
        "private" to "This video is private",
    )

    val ALL: List<Case> = buildList {
        add(Case("L1", Expect.ROWS) { onAll(::moveStreamingData) })
        add(Case("L2", Expect.ROWS) { onAll(::renameLists) })
        add(Case("L3", Expect.ROWS) { onAll { json -> json.formats().forEach { it.rename("url", "streamUrl") } } })
        add(Case("L4 renamed", Expect.ROWS) { onAll { json -> json.formats().forEach { it.rename("mimeType", "mime") } } })
        add(Case("L4 split", Expect.ROWS) { onAll { json -> json.formats().forEach(::splitMime) } })
        add(
            Case("L5", Expect.PROTECTED) {
                visionCipher = true
                onAll { json -> json.formats().forEach { it.rename("signatureCipher", "streamCipher") } }
            },
        )
        add(Case("L6", Expect.ROWS, readableByR0 = true) { onAll(::driftTypes) })
        add(Case("L7", Expect.ROWS) { onAll(::listsToMaps) })
        add(
            Case("L8", Expect.ROWS) {
                onAll(::moveStreamingData)
                onAll { json -> json["endScreen"] = decoy(this, "l8", Sim.OTHER_VIDEO, DECOY_SECONDS) }
            },
        )
        add(
            Case("L9", Expect.AMBIGUOUS) {
                onAll(::moveStreamingData)
                onAll { json -> json["related"] = decoy(this, "l9", Sim.OTHER_VIDEO, lengthSeconds) }
            },
        )
        add(
            Case("L10", Expect.ROWS) {
                onAll(::moveStreamingData)
                onAll { json -> json.remove("playabilityStatus")?.let { status -> SimJson.obj(json["playback"])["status"] = status } }
            },
        )
        for ((name, reason) in TERMINAL_REASONS) {
            add(
                Case("L11 $name", Expect.STOP) {
                    onAll { json ->
                        json["playabilityStatus"] = SimJson.parse("""{"status":"LOGIN_REQUIRED","reason":"$reason"}""")
                        moveStreamingData(json)
                    }
                },
            )
        }
        add(
            Case("L12", Expect.STOP) {
                encrypted = true
                onAll { json -> json.formats().forEach { it["drmFamiliesV2"] = SimJson.parse("""["WIDEVINE"]""") } }
                onAll(::moveStreamingData)
            },
        )
        // D7: the copied parser's four DRM keys with a value, each under a moved or renamed container.
        add(
            Case("L12 params", Expect.STOP) {
                encrypted = true
                onAll { json -> json.streaming()?.let { it["drmParams"] = "CgQIARAB" } }
                onAll(::renameLists)
                onAll(::moveStreamingData)
            },
        )
        add(
            Case("L12 session", Expect.STOP) {
                encrypted = true
                onAll { json -> json["tracking"] = linkedMapOf<String, Any?>("drmSessionId" to "AB12CD34") }
                onAll(::moveStreamingData)
            },
        )
        add(
            Case("L12 families", Expect.STOP) {
                encrypted = true
                onAll { json -> json.formats().forEach { it["drmFamilies"] = SimJson.parse("""["WIDEVINE"]""") } }
                onAll(::listsToMaps)
            },
        )
        add(
            Case("L12 track", Expect.STOP) {
                encrypted = true
                onAll { json -> json.formats().forEach { it["drmTrackType"] = "DRM_TRACK_TYPE_HD" } }
                onAll(::renameLists)
            },
        )
        // D7: DRM-named player settings and a key-system list in an answer without DRM.
        add(
            Case("L1 config", Expect.ROWS) {
                onAll { json -> json["playerConfig"] = playerConfig() }
                onAll(::moveStreamingData)
            },
        )
        add(
            Case("L13", Expect.HLS_ONLY) {
                onAll(::removeFormats)
                onVision { json ->
                    val streaming = SimJson.obj(json["streamingData"])
                    val address = streaming.remove("hlsManifestUrl")
                    json["playback"] = linkedMapOf<String, Any?>("hls" to linkedMapOf<String, Any?>("manifestUrl" to address))
                }
            },
        )
        add(
            Case("L14", Expect.PROTECTED) {
                visionCipher = true
                pageLocated = true
                pageEdits += { html ->
                    html.replace("\"PLAYER_JS_URL\"", "\"PLAYER_SCRIPT_V2\"").replace("\"jsUrl\"", "\"scriptV2\"")
                        .replace("\"STS\"", "\"STS_V2\"").replace("\"signatureTimestamp\"", "\"stsV2\"")
                        .replace("\"VISITOR_DATA\"", "\"VISITOR_V2\"").replace("\"visitorData\"", "\"visitorV2\"")
                }
            },
        )
        add(Case("L15", Expect.ROWS) { onAll(::splitAudioIntoNewList) })
        add(
            Case("L16", Expect.ROWS) {
                onAll(::deepMove)
                onAll { json -> json["noise"] = noise(NOISE_OBJECTS) }
            },
        )
    }

    /** L16's filler: [count] small objects beside the moved formats. */
    fun noise(count: Int): MutableList<Any?> = MutableList(count) { linkedMapOf<String, Any?>("k" to it, "v" to "x") }

    /**
     * D7: player settings with DRM names and no DRM, of the kind that stopped L on live visionOS
     * answers (the live key is not known; names and values made up): a DRM-named settings object
     * and a list of key systems.
     */
    fun playerConfig(): MutableMap<String, Any?> = linkedMapOf(
        "audioConfig" to linkedMapOf<String, Any?>("loudnessDb" to SimJson.Num("-3.5"), "enablePerFormatLoudness" to true),
        "mediaCommonConfig" to linkedMapOf<String, Any?>("keySystems" to mutableListOf<Any?>("FAIRPLAY")),
        "webDrmConfig" to linkedMapOf<String, Any?>("widevineServiceCert" to "Q0VSVA", "useCobaltWidevine" to true),
    )

    // --- copied from SimCases (same edits) ---

    private fun SimWorld.onVision(edit: (MutableMap<String, Any?>) -> Unit) {
        edits += { client, json -> if (client == SimClient.VISION_OS) edit(json) }
    }

    private fun SimWorld.onAll(edit: (MutableMap<String, Any?>) -> Unit) {
        edits += { _, json -> edit(json) }
    }

    private fun MutableMap<String, Any?>.streaming(): MutableMap<String, Any?>? =
        (this["streamingData"] as? MutableMap<*, *>)?.let(SimJson::obj)

    private fun MutableMap<String, Any?>.formats(): List<MutableMap<String, Any?>> = streaming()?.let { data ->
        SimWorld.LIST_KEYS.flatMap { key -> (data[key] as? List<*>).orEmpty().map(SimJson::obj) }
    }.orEmpty()

    private fun MutableMap<String, Any?>.rename(from: String, to: String) {
        if (containsKey(from)) this[to] = remove(from)
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

    fun moveStreamingData(json: MutableMap<String, Any?>) {
        val data = json.remove("streamingData") ?: return
        json["playback"] = linkedMapOf<String, Any?>("streamingData" to data)
    }

    /** L16: the formats moved 10 levels deep, beside the noise. */
    fun deepMove(json: MutableMap<String, Any?>) {
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

    private const val DECOY_SECONDS = 30L
    const val NOISE_OBJECTS = 10_000
    private const val DEEP_LEVELS = 10
}
