package com.alal.yft.extractor.master.modules.youtube.ladder

import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D5b: ShapeLocator, rung L step 1 (MASTER_KEY_PHASE1_3_PLAN.md §3 L) — what makes a
 * format, a video, a status, an HLS address and a DRM marker is read from values, under any key
 * names; the scan's bounds and its D2 flags. Small answers made here and the D1 simulator's 5
 * bases; offline, no live calls.
 */
class ShapeLocatorTest {
    private val vision = SimAsk(SimClient.VISION_OS, Sim.BASE_VERSION, visitor = false, sts = false)

    @Test
    fun `a format is read from values under any key names`() {
        val address = media(137, AVC_MIME)
        val renamed = linkedMapOf<String, Any?>("a" to "137", "b" to address, "c" to AVC_MIME, "h" to 1080)
        val scan = scan("x" to linkedMapOf("y" to listOf(renamed)))
        val found = scan.candidates.single()
        assertEquals("x.y[]", found.path)
        assertEquals(137, found.itag)
        assertEquals(address, found.address)
        assertNull(found.cipher)
        assertEquals(address, found.source)
        assertEquals(AVC_MIME, found.mime)
        assertEquals(212.0, found.durationSeconds!!, 0.0)
        assertFalse(found.canonicalKeys)
        assertEquals(0, scan.rejected)
        assertTrue(scan.complete)

        val canonical = scan("streamingData" to streaming(format(137, AVC_MIME), format(140, AAC_MIME))).candidates
        assertEquals(listOf(137, 140), canonical.map { it.itag })
        assertTrue(canonical.all { it.canonicalKeys && it.path == "streamingData.adaptiveFormats[]" })
        assertEquals("one list: one group, one family (its parent)", 1, canonical.map { it.group }.distinct().size)
        assertEquals(1, canonical.map { it.family }.distinct().size)
        assertTrue(canonical.first().familyNode!!.entries.containsKey("expiresInSeconds"))

        // An itag number under another key; no `dur` parameter, no duration.
        val numeric = scan("list" to listOf(linkedMapOf("n" to 18, "u" to media(18, MUXED_MIME, dur = null), "m" to MUXED_MIME))).candidates.single()
        assertEquals(18, numeric.itag)
        assertNull(numeric.durationSeconds)
        assertEquals("list[]", numeric.path)

        // The same address or mime twice in one object is one; a full mime wins over a bare type.
        val twice = linkedMapOf<String, Any?>("itag" to 137, "url" to address, "copy" to address, "mimeType" to AVC_MIME, "again" to AVC_MIME, "type" to "video/mp4")
        assertEquals(AVC_MIME, one(twice).candidates.single().mime)

        // The scan binds nothing: another video's format is listed too (ShapeNormalizer binds).
        val decoy = scan(
            ok(),
            details(),
            "streamingData" to streaming(format(137, AVC_MIME)),
            "endScreen" to linkedMapOf("videoId" to OTHER_VIDEO, "lengthSeconds" to "30", "list" to listOf(format(137, AVC_MIME, dur = "30.000", host = OTHER_HOST))),
        )
        assertEquals(listOf("streamingData.adaptiveFormats[]", "endScreen.list[]"), decoy.candidates.map { it.path })
        assertEquals(listOf("videoDetails"), decoy.videos.map { it.path })
    }

    @Test
    fun `an object with a media address that is not format-like is rejected`() {
        val address = media(137, AVC_MIME)
        val rejected = mapOf(
            "itag field differs" to linkedMapOf("itag" to 136, "url" to address, "mimeType" to AVC_MIME),
            "two addresses" to linkedMapOf("itag" to 137, "url" to address, "alt" to media(137, AVC_MIME, host = OTHER_HOST), "mimeType" to AVC_MIME),
            "two mimes" to linkedMapOf("itag" to 137, "url" to address, "mimeType" to AVC_MIME, "alt" to "video/mp4; codecs=\"avc1.4d401f\""),
            "no mime" to linkedMapOf("itag" to 137, "url" to address, "qualityLabel" to "1080p"),
            "a bare type alone" to linkedMapOf("itag" to 137, "url" to address, "type" to "video/mp4"),
            "two codec lists" to linkedMapOf("itag" to 137, "url" to address, "type" to "video/mp4", "codecs" to "avc1.640028", "alt" to "avc1.4d401f"),
            "the address names another type" to linkedMapOf("itag" to 137, "url" to media(137, AAC_MIME), "mimeType" to AVC_MIME),
            "an address and a cipher" to linkedMapOf("itag" to 137, "url" to address, "signatureCipher" to cipher(137, AVC_MIME), "mimeType" to AVC_MIME),
        )
        for ((label, format) in rejected) {
            val scan = one(format)
            assertTrue(label, scan.candidates.isEmpty())
            assertEquals(label, 1, scan.rejected)
        }

        // Not a media address at all: neither a candidate nor rejected.
        val ignored = listOf(
            "https://rr1---sn-shape.example.test/videoplayback?itag=137",
            "https://$HOST/videoplaybackx?itag=137",
            "http://$HOST/videoplayback?itag=137",
            "https://user@$HOST/videoplayback?itag=137",
            "https://$HOST/videoplayback?expire=4102444800",
            "https://$HOST/videoplayback?itag=1000",
            "https://$HOST/videoplayback?itag=0",
        )
        for (text in ignored) {
            val scan = one(linkedMapOf("itag" to 137, "url" to text, "mimeType" to AVC_MIME))
            assertTrue(scan.candidates.isEmpty())
            assertEquals(0, scan.rejected)
        }
    }

    @Test
    fun `a cipher and a split mime make one format`() {
        val signed = cipher(251, OPUS_MIME)
        val split = scan("p" to linkedMapOf("audio" to listOf(linkedMapOf("tag" to "251", "sc" to signed, "kind" to "audio/webm", "codec" to "opus"))))
        val found = split.candidates.single()
        assertNull(found.address)
        assertEquals(signed, found.cipher)
        assertEquals(signed, found.source)
        assertEquals(OPUS_MIME, found.mime)
        assertEquals("p.audio[]", found.path)
        assertFalse(found.canonicalKeys)

        for (key in listOf("signatureCipher", "cipher")) {
            val canonical = scan("streamingData" to linkedMapOf("formats" to listOf(linkedMapOf("itag" to 251, key to signed, "mimeType" to OPUS_MIME))))
            assertTrue(key, canonical.candidates.single().canonicalKeys)
        }

        val muxed = one(linkedMapOf("itag" to 18, "url" to media(18, MUXED_MIME), "type" to "video/mp4", "codecs" to "avc1.42001E, mp4a.40.2"))
        assertEquals(MUXED_MIME, muxed.candidates.single().mime)

        // Without `s`, or with a `url` that is not a media address, the text is not a cipher.
        val notCiphers = listOf(
            "sp=sig&url=" + enc(media(251, OPUS_MIME)),
            "s=&sp=sig&url=" + enc(media(251, OPUS_MIME)),
            "s=$SIGNATURE&url=" + enc("https://rr1---sn-shape.example.test/videoplayback?itag=251"),
            "s=$SIGNATURE&url=" + enc("https://$HOST/videoplayback?expire=4102444800") + "&note=googlevideo.com",
        )
        for (text in notCiphers) {
            val scan = one(linkedMapOf("itag" to 251, "signatureCipher" to text, "mimeType" to OPUS_MIME))
            assertTrue(scan.candidates.isEmpty())
            assertEquals(0, scan.rejected)
        }
    }

    @Test
    fun `video, status, HLS and DRM objects are read from values`() {
        val videos = scan(
            "a" to linkedMapOf("id" to VIDEO, "lengthSeconds" to "212"),
            "b" to linkedMapOf("ref" to VIDEO, "lengthSeconds" to 212),
            "c" to linkedMapOf("id" to OTHER_VIDEO, "lengthSeconds" to "30"),
            "d" to linkedMapOf("id" to VIDEO, "lengthSeconds" to "0"),
            "e" to linkedMapOf("id" to VIDEO, "duration" to "212"),
            "f" to linkedMapOf("id" to VIDEO, "lengthSeconds" to "2:12"),
            VIDEO to linkedMapOf("id" to VIDEO, "lengthSeconds" to "212"),
        ).videos
        assertEquals(listOf("a" to 212L, "b" to 212L, "*" to 212L), videos.map { it.path to it.lengthSeconds })
        assertTrue(scan("a" to linkedMapOf("id" to VIDEO, "lengthSeconds" to "212"), id = null).videos.isEmpty())

        val statuses = scan(
            "a" to linkedMapOf("status" to "OK"),
            "b" to linkedMapOf("x" to "OK", "y" to "LOGIN_REQUIRED"),
            "c" to linkedMapOf("y" to "UNPLAYABLE", "x" to "OK"),
            "d" to linkedMapOf("status" to "Ok", "note" to "fine"),
        ).statuses
        assertEquals(listOf("a" to "OK", "b" to "LOGIN_REQUIRED", "c" to "UNPLAYABLE"), statuses.map { it.path to it.value })

        val single = scan("streamingData" to linkedMapOf("hlsManifestUrl" to HLS_VARIANT))
        assertEquals(HLS_VARIANT, single.hls)
        val same = scan("a" to linkedMapOf("u" to HLS_VARIANT), "b" to listOf(HLS_VARIANT))
        assertEquals(listOf(HLS_VARIANT), same.hlsAddresses)
        assertEquals(HLS_VARIANT, same.hls)
        val two = scan("a" to linkedMapOf("u" to HLS_VARIANT), "b" to linkedMapOf("u" to HLS_PLAYLIST))
        assertEquals(listOf(HLS_VARIANT, HLS_PLAYLIST), two.hlsAddresses)
        assertNull("two HLS addresses: none is the one", two.hls)
        val notHls = listOf(
            "http://manifest.googlevideo.com/api/manifest/hls_variant/id/1/file/index.m3u8",
            "https://manifest.googlevideo.com/api/manifest/dash/id/1/file/index.mpd",
            "https://rr1---sn-shape.googlevideo.com/api/manifest/hls_variant/id/1/file/index.m3u8",
            "https://manifest.googlevideo.com.example.test/api/manifest/hls_variant/id/1/file/index.m3u8",
            "https://manifest.googlevideo.com:8443x/api/manifest/hls_variant/id/1",
        )
        assertTrue(scan("a" to notHls).hlsAddresses.isEmpty())

        val marked = listOf(
            "streamingData" to linkedMapOf("drmParams" to "x"),
            "f" to linkedMapOf("drmFamiliesV2" to listOf("A")),
            "f" to linkedMapOf("someDrmInfo" to linkedMapOf("k" to 1)),
            "f" to linkedMapOf("isDRM" to true),
            "f" to listOf("WIDEVINE"),
            "f" to linkedMapOf("t" to "PLAYREADY"),
            "f" to linkedMapOf("t" to "DRM_TRACK_TYPE_AUDIO"),
        )
        for (top in marked) assertTrue(top.toString(), scan(top).drm)
        val unmarked = listOf(
            "f" to linkedMapOf("drmFamilies" to emptyList<String>()),
            "f" to linkedMapOf("drmParams" to " "),
            "f" to linkedMapOf("drm" to false, "isDrm" to null, "drmCount" to 0),
            "f" to linkedMapOf("hydrometer" to "1"),
            "f" to linkedMapOf("t" to "widevine.example"),
        )
        for (top in unmarked) assertFalse(top.toString(), scan(top).drm)
    }

    @Test
    fun `bounds - a deep cut is complete only when nothing judged can hide below it`() {
        // The root is depth 0; nest(n, x) puts x at depth n + 1. Containers at depth 16 are not opened.
        val found = scan("deep" to nest(14, format(137, AVC_MIME)))
        assertEquals("deep" + ".k".repeat(14), found.candidates.single().path)
        assertFalse(found.depthCut)
        assertTrue(found.complete)

        val hidden = scan("deep" to nest(15, format(137, AVC_MIME)))
        assertTrue(hidden.candidates.isEmpty())
        assertTrue(hidden.depthCut)
        assertFalse("a media address below the cut", hidden.complete)

        val harmless = scan(ok(), details(), "deep" to nest(20, linkedMapOf("note" to "x", "list" to listOf(1, 2))))
        assertTrue(harmless.depthCut)
        assertTrue("nothing judged below the cut", harmless.complete)
        val parsed = ShapeLocator.parse(answer(ok(), details(), "deep" to nest(20, "x")))!!
        assertFalse("no raw answer to count in", ShapeLocator.scan(parsed, VIDEO).complete)

        val below = mapOf(
            "a status word" to linkedMapOf("status" to "LOGIN_REQUIRED"),
            "the video ID" to linkedMapOf("ref" to VIDEO),
            "a media host" to linkedMapOf("note" to "rr1---sn-shape.googlevideo.com"),
            "a media host in a key" to linkedMapOf("rr1---sn-shape.GoogleVideo.com" to 1),
        )
        for ((label, inner) in below) {
            val scan = scan(ok(), details(), "deep" to nest(20, inner))
            assertTrue(label, scan.depthCut)
            assertFalse(label, scan.complete)
        }
    }

    @Test
    fun `bounds - node, size and depth limits`() {
        val filled = scan("list" to List(ShapeLocator.MAX_NODES - 2) { 0 })
        assertTrue(filled.complete)
        assertEquals(ShapeLocator.MAX_NODES, filled.nodes)
        val over = scan("list" to List(ShapeLocator.MAX_NODES - 1) { 0 })
        assertFalse(over.complete)
        assertEquals(ShapeLocator.MAX_NODES, over.nodes)
        val formatAfterCap = scan("list" to List(ShapeLocator.MAX_NODES) { 0 }, "streamingData" to streaming(format(137, AVC_MIME)))
        assertFalse(formatAfterCap.complete)
        assertTrue(formatAfterCap.candidates.isEmpty())

        assertNull("over 4 MiB", ShapeLocator.parse(" ".repeat(ShapeLocator.MAX_ANSWER_CHARS - 1) + "{}x"))
        assertTrue(ShapeLocator.parse(" ".repeat(ShapeLocator.MAX_ANSWER_CHARS - 2) + "{}") != null)
        assertNull("not JSON", ShapeLocator.parse("{\"a\":"))
        assertNull("deeper than the copied parser reads", ShapeLocator.parse("[".repeat(70) + "]".repeat(70)))
    }

    @Test
    fun `moved and partial flags for D2's judge`() {
        val canonical = scan(ok(), details(), "streamingData" to streaming(format(137, AVC_MIME), format(140, AAC_MIME)))
        assertFalse(canonical.moved)
        assertFalse(canonical.partial)
        assertEquals(setOf("streamingData.adaptiveFormats[]"), canonical.formatPaths)

        val moved = scan(ok(), details(), "playback" to linkedMapOf("streamingData" to streaming(format(137, AVC_MIME))))
        assertTrue(moved.moved)
        assertFalse(moved.partial)
        assertEquals(setOf("playback.streamingData.adaptiveFormats[]"), moved.formatPaths)

        val renamed = linkedMapOf<String, Any?>("itag" to 137, "url" to media(137, AVC_MIME), "mime" to AVC_MIME)
        val split = linkedMapOf<String, Any?>("itag" to 140, "url" to media(140, AAC_MIME), "mimeType" to "audio/mp4", "codecs" to "mp4a.40.2")
        for (format in listOf(renamed, split)) {
            val partial = scan(ok(), details(), "streamingData" to streaming(format))
            assertFalse(partial.moved)
            assertTrue(partial.partial)
        }
        assertFalse("no formats: neither", scan(ok(), details()).let { it.moved || it.partial })
    }

    @Test
    fun `the simulator's bases - every format in the canonical lists, as the copied parser reads it`() {
        for (base in Sim.BASES) {
            val text = SimJson.write(SimWorld(base).answer(vision))
            val scan = ShapeLocator.scan(ShapeLocator.parse(text)!!, Sim.VIDEO_ID, text)
            val root = SimJson.parse(text)
            val listed = listOf("formats", "adaptiveFormats").sumOf { (SimJson.at(root, "streamingData", it) as? List<*>)?.size ?: 0 }
            assertTrue(base, scan.complete)
            assertFalse(base, scan.depthCut || scan.drm || scan.moved || scan.partial)
            assertEquals(base, 0, scan.rejected)
            assertEquals(base, listed, scan.candidates.size)
            assertTrue(base, scan.candidates.all { it.canonicalKeys && it.path in ShapeLocator.CANONICAL_LISTS })
            assertEquals(base, "playabilityStatus" to "OK", scan.statuses.first().let { it.path to it.value })
            assertEquals(base, listOf("videoDetails"), scan.videos.map { it.path })
        }
    }

    @Test
    fun `codec helpers read the families the copied parser splits`() {
        for (codec in listOf("avc1.640028", "avc3.4d401f", "vp9", "vp09.00.51.08", "av01.0.08M.08", "hev1.1.6.L93.B0", "dvh1.05.01")) {
            assertTrue(codec, ShapeLocator.isVideoCodec(codec))
            assertFalse(codec, ShapeLocator.isAudioCodec(codec))
        }
        for (codec in listOf("mp4a.40.2", "opus", "vorbis", "ec-3", "ac-3", "flac", "mp3")) {
            assertTrue(codec, ShapeLocator.isAudioCodec(codec))
            assertFalse(codec, ShapeLocator.isVideoCodec(codec))
        }
        for (codec in listOf("text", "wvtt", "stpp", "")) {
            assertFalse(codec, ShapeLocator.isVideoCodec(codec) || ShapeLocator.isAudioCodec(codec))
        }
        assertEquals(listOf("avc1.42001E", "mp4a.40.2"), ShapeLocator.codecsOf(MUXED_MIME))
        assertEquals(emptyList<String>(), ShapeLocator.codecsOf("video/mp4"))
    }

    @Test
    fun `toString shows counts and key paths only`() {
        val scan = scan(
            ok(),
            details(),
            VIDEO to linkedMapOf("list" to listOf(format(137, AVC_MIME), linkedMapOf("itag" to 251, "signatureCipher" to cipher(251, OPUS_MIME), "mimeType" to OPUS_MIME))),
            "streamingData" to linkedMapOf("hlsManifestUrl" to HLS_VARIANT),
            "a-b" to linkedMapOf("137" to listOf(format(140, AAC_MIME))),
        )
        assertEquals(setOf("*.list[]", "*.*[]"), scan.formatPaths)
        assertEquals(HLS_VARIANT, scan.hls)
        val printed = listOf<Any>(scan) + scan.candidates + scan.videos + scan.statuses
        for (item in printed) {
            val text = item.toString()
            for (secret in listOf("googlevideo", "http", "videoplayback", VIDEO, SIGNATURE, "expire", "&")) assertFalse(text, text.contains(secret))
        }
    }

    // --- small answers ---

    private fun scan(vararg top: Pair<String, Any?>, id: String? = VIDEO): ShapeLocator.Scan {
        val text = answer(*top)
        return ShapeLocator.scan(ShapeLocator.parse(text)!!, id, text)
    }

    private fun one(format: Map<String, Any?>): ShapeLocator.Scan = scan("list" to listOf(format))

    private fun nest(levels: Int, inner: Any?): Any? = (1..levels).fold(inner) { acc, _ -> linkedMapOf<String, Any?>("k" to acc) }

    private fun enc(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

    private fun media(itag: Int, mime: String, dur: String? = "212.000", host: String = HOST): String =
        "https://$host/videoplayback?expire=4102444800&itag=$itag&mime=" + enc(mime.substringBefore(';')) + (dur?.let { "&dur=$it" } ?: "")

    private fun cipher(itag: Int, mime: String): String = "s=$SIGNATURE&sp=sig&url=" + enc(media(itag, mime))

    private fun format(itag: Int, mime: String, dur: String? = "212.000", host: String = HOST): MutableMap<String, Any?> =
        linkedMapOf("itag" to itag, "url" to media(itag, mime, dur, host), "mimeType" to mime)

    private fun streaming(vararg formats: Map<String, Any?>) = linkedMapOf<String, Any?>("expiresInSeconds" to "21540", "adaptiveFormats" to formats.toList())

    private fun ok() = "playabilityStatus" to linkedMapOf<String, Any?>("status" to "OK")

    private fun details() = "videoDetails" to linkedMapOf<String, Any?>("videoId" to VIDEO, "title" to "Title", "lengthSeconds" to "212")

    private fun answer(vararg top: Pair<String, Any?>): String = SimJson.write(linkedMapOf(*top))

    private companion object {
        const val VIDEO = "Yft0Shape02"
        const val OTHER_VIDEO = "Other000000"
        const val HOST = "rr1---sn-shape.googlevideo.com"
        const val OTHER_HOST = "rr9---sn-decoy.googlevideo.com"
        const val SIGNATURE = "SIGVALUE0123456789"
        const val AVC_MIME = "video/mp4; codecs=\"avc1.640028\""
        const val AAC_MIME = "audio/mp4; codecs=\"mp4a.40.2\""
        const val OPUS_MIME = "audio/webm; codecs=\"opus\""
        const val MUXED_MIME = "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\""
        const val HLS_VARIANT = "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/4102444800/id/1/file/index.m3u8"
        const val HLS_PLAYLIST = "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/4102444800/id/1/itag/96/file/index.m3u8"
    }
}
