package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.master.modules.youtube.StreamCheck.Verdict
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes.box
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes.el
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes.uint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 1.3 D4: the init check on L rows (guardrail 6), one test per case; bytes made in code. */
class InitCheckTest {
    private val avc = "video/mp4; codecs=\"avc1.640028\""
    private val aac = "audio/mp4; codecs=\"mp4a.40.2\""
    private val vp9 = "video/webm; codecs=\"vp9\""
    private val opus = "audio/webm; codecs=\"opus\""

    @Test
    fun `an MP4 row passes on its type, an ftyp start, no marker and the row's size`() {
        val bytes = InitBytes.mp4()

        val result = InitCheck.check(avc, ROW_SIZE, InitCheck.Observed("video/mp4", bytes, ROW_SIZE))

        assertEquals(InitCheck.Result(Verdict.PASS, InitCheck.Reason.OK), result)
        assertEquals(InitCheck.Result(Verdict.PASS, InitCheck.Reason.OK), check(aac, "audio/mp4", InitBytes.mp4(sample = "mp4a", handler = "soun")))
    }

    @Test
    fun `every MP4 start box the plan names passes`() {
        for (start in listOf("ftyp", "styp", "moof", "sidx")) {
            val bytes = box(start, ByteArray(24)) + box("mdat", ByteArray(64))
            assertEquals(start, InitCheck.Reason.OK, check(avc, "video/mp4", bytes).reason)
        }
        // A start box that runs to the end of the file (size 0) and one with a 64-bit size.
        val toEnd = InitBytes.u32(0) + "ftyp".latin1() + ByteArray(16)
        val extended = InitBytes.u32(1) + "ftyp".latin1() + InitBytes.u32(0) + InitBytes.u32(32) + ByteArray(16)
        assertEquals(InitCheck.Reason.OK, check(avc, "video/mp4", toEnd).reason)
        assertEquals(InitCheck.Reason.OK, check(avc, "video/mp4", extended).reason)
    }

    @Test
    fun `a WebM row passes when the bytes hold its whole Tracks element without encryption`() {
        val video = InitBytes.webm(codec = "V_VP9")
        val audio = InitBytes.webm(codec = "A_OPUS", unknownSegmentSize = true)

        assertEquals(InitCheck.Result(Verdict.PASS, InitCheck.Reason.OK), check(vp9, "video/webm", video))
        assertEquals(InitCheck.Result(Verdict.PASS, InitCheck.Reason.OK), check(opus, "audio/webm", audio))
        // Doc type "matroska", and a Void element between the header and the Segment.
        val matroska = InitBytes.webm(codec = "V_VP9", docType = "matroska", voidAfterHeader = true)
        assertEquals(InitCheck.Reason.OK, check(vp9, "video/webm", matroska).reason)
    }

    @Test
    fun `WebM compression is no encryption`() {
        val compressed = el(0x6D80, el(0x6240, uint(0x5031, 0) + uint(0x5032, 1) + uint(0x5033, 0) + el(0x5034, uint(0x4254, 3))))
        val emptyType = el(0x6D80, el(0x6240, el(0x5033, ByteArray(0)) + el(0x5034, uint(0x4254, 3))))

        assertEquals(InitCheck.Reason.OK, check(vp9, "video/webm", InitBytes.webm(codec = "V_VP9", encodings = compressed)).reason)
        assertEquals(InitCheck.Reason.OK, check(vp9, "video/webm", InitBytes.webm(codec = "V_VP9", encodings = emptyType)).reason)
    }

    @Test
    fun `a wrong content type fails`() {
        val cases = listOf(
            avc to "text/html; charset=utf-8",
            avc to "video/webm",
            avc to "audio/mp4",
            aac to "video/mp4",
            vp9 to "video/mp4",
            opus to "application/json",
        )
        for ((row, answer) in cases) {
            val bytes = if (row.contains("webm")) InitBytes.webm(codec = "V_VP9") else InitBytes.mp4()
            assertEquals("$row / $answer", InitCheck.Result(Verdict.FAIL, InitCheck.Reason.TYPE_MISMATCH), check(row, answer, bytes))
        }
    }

    @Test
    fun `a missing or generic content type is unknown`() {
        for (answer in listOf(null, "", " ", "application/octet-stream", "binary/octet-stream")) {
            assertEquals("$answer", InitCheck.Result(Verdict.UNKNOWN, InitCheck.Reason.TYPE_MISSING), check(avc, answer, InitBytes.mp4()))
        }
    }

    @Test
    fun `type parameters and case do not matter`() {
        assertEquals(InitCheck.Reason.OK, check(avc, "Video/MP4; charset=binary", InitBytes.mp4()).reason)
        assertEquals(InitCheck.Reason.OK, check("AUDIO/WEBM;codecs=\"opus\"", " audio/webm ", InitBytes.webm(codec = "A_OPUS")).reason)
    }

    @Test
    fun `wrong start bytes fail`() {
        val html = "<!DOCTYPE html><html><head></head><body>refused</body></html>".latin1()
        val mp4Cases = listOf(
            html,
            InitBytes.webm(codec = "V_VP9"),
            box("moov", ByteArray(32)),
            box("mdat", ByteArray(32)),
            box("free", ByteArray(32)),
            InitBytes.u32(4) + "ftyp".latin1() + ByteArray(16),
            box("ftyp", ByteArray(4)),
        )
        for ((index, bytes) in mp4Cases.withIndex()) {
            assertEquals("mp4 $index", InitCheck.Result(Verdict.FAIL, InitCheck.Reason.START_MISMATCH), check(avc, "video/mp4", bytes))
        }
        val webmCases = listOf(html, InitBytes.mp4(), InitBytes.webm(codec = "V_VP9", docType = "mkvx"))
        for ((index, bytes) in webmCases.withIndex()) {
            assertEquals("webm $index", InitCheck.Result(Verdict.FAIL, InitCheck.Reason.START_MISMATCH), check(vp9, "video/webm", bytes))
        }
    }

    @Test
    fun `too few bytes are unknown`() {
        for (bytes in listOf(ByteArray(0), InitBytes.mp4().copyOf(7), InitBytes.webm(codec = "V_VP9").copyOf(4))) {
            val row = if (bytes.size == 4) vp9 else avc
            val type = if (bytes.size == 4) "video/webm" else "video/mp4"
            assertEquals("${bytes.size}", InitCheck.Result(Verdict.UNKNOWN, InitCheck.Reason.TOO_SHORT), check(row, type, bytes))
        }
        // A 64-bit box size the bytes read do not hold.
        val extended = InitBytes.u32(1) + "ftyp".latin1() + InitBytes.u32(0)
        assertEquals(InitCheck.Reason.TOO_SHORT, check(avc, "video/mp4", extended).reason)
    }

    @Test
    fun `every MP4 encryption marker fails`() {
        for (marker in listOf("encv", "enca", "sinf", "schm", "tenc", "pssh", "senc")) {
            val bytes = InitBytes.ftyp() + box("moov", box("mvhd", ByteArray(100)) + box(marker, ByteArray(24)))
            assertEquals(marker, InitCheck.Result(Verdict.FAIL, InitCheck.Reason.ENCRYPTED), check(avc, "video/mp4", bytes))
        }
        // A whole CENC init segment: encv with sinf (frma, schm, schi/tenc) and a pssh box.
        assertEquals(InitCheck.Result(Verdict.FAIL, InitCheck.Reason.ENCRYPTED), check(avc, "video/mp4", InitBytes.mp4(encrypted = true)))
        assertEquals(
            InitCheck.Result(Verdict.FAIL, InitCheck.Reason.ENCRYPTED),
            check(aac, "audio/mp4", InitBytes.mp4(sample = "mp4a", handler = "soun", encrypted = true)),
        )
    }

    @Test
    fun `an encryption marker in a cut box still fails`() {
        val whole = InitBytes.mp4(encrypted = true)
        // The read ends inside moov: the box walk stops, the byte scan still sees the marker.
        val cut = whole.copyOf(whole.size - 40)

        assertEquals(InitCheck.Result(Verdict.FAIL, InitCheck.Reason.ENCRYPTED), check(avc, "video/mp4", cut))
        // The D1 world's encrypted MP4 stand-in (SimWorld at 81388f8): an ftyp start, then box names only.
        val standIn = "\u0000\u0000\u0000\u0018ftypdash\u0000\u0000\u0000\u0000iso6moovtrakencvsinfpssh".latin1()
        assertEquals(InitCheck.Result(Verdict.FAIL, InitCheck.Reason.ENCRYPTED), check(avc, "video/mp4", standIn))
        val plain = "\u0000\u0000\u0000\u0018ftypdash\u0000\u0000\u0000\u0000iso6moovtrak".latin1()
        assertEquals(InitCheck.Result(Verdict.PASS, InitCheck.Reason.OK), check(avc, "video/mp4", plain))
    }

    @Test
    fun `WebM content encryption fails`() {
        val encryption = el(0x6D80, el(0x6240, uint(0x5031, 0) + uint(0x5032, 1) + uint(0x5033, 1) + el(0x5035, uint(0x47E1, 5) + el(0x47E2, ByteArray(16)))))
        val typeOnly = el(0x6D80, el(0x6240, uint(0x5033, 1)))
        val elementOnly = el(0x6D80, el(0x6240, el(0x5035, uint(0x47E1, 5))))

        for ((name, encodings) in listOf("full" to encryption, "type 1" to typeOnly, "ContentEncryption" to elementOnly)) {
            val bytes = InitBytes.webm(codec = "V_VP9", encodings = encodings)
            assertEquals(name, InitCheck.Result(Verdict.FAIL, InitCheck.Reason.ENCRYPTED), check(vp9, "video/webm", bytes))
        }
        // The second track is the encrypted one.
        val twoTracks = InitBytes.webm(codec = "A_OPUS", encodings = null, secondTrack = InitBytes.trackEntry(2, "A_OPUS", encryption))
        assertEquals(InitCheck.Reason.ENCRYPTED, check(opus, "audio/webm", twoTracks).reason)
    }

    @Test
    fun `a WebM start whose Tracks the bytes do not hold whole is unknown`() {
        val whole = InitBytes.webm(codec = "V_VP9")
        // The last match: SeekHead names the Tracks ID too.
        val tracksAt = InitBytes.lastIndexOf(whole, byteArrayOf(0x16, 0x54, 0xAE.toByte(), 0x6B))
        val cases = mapOf(
            "cut inside Tracks" to whole.copyOf(tracksAt + 9),
            "cut before Tracks" to whole.copyOf(tracksAt),
            "cluster first" to InitBytes.webm(codec = "V_VP9", clusterFirst = true),
            "no segment" to InitBytes.ebmlHeader("webm") + el(0x1549A966, uint(0x2AD7B1, 1_000_000)),
            "cut header" to InitBytes.ebmlHeader("webm").copyOf(12),
            "unknown-size element before Tracks" to InitBytes.ebmlHeader("webm") +
                InitBytes.unknownSize(0x18538067, InitBytes.unknownSize(0x1549A966, uint(0x2AD7B1, 1_000_000))),
        )
        for ((name, bytes) in cases) {
            assertEquals(name, InitCheck.Result(Verdict.UNKNOWN, InitCheck.Reason.TRACKS_UNREAD), check(vp9, "video/webm", bytes))
        }
    }

    @Test
    fun `the D1 world's WebM stand-ins are never a pass when encrypted and never a fail when not`() {
        // SimWorld at 81388f8 serves an EBML magic, a cut header and plain text: no Tracks to read.
        val start = "\u001AE\u00DF\u00A3\u0093B\u0082\u0088webm"
        val plain = check(opus, "audio/webm", (start + "moovtrak").latin1())
        val encrypted = check(opus, "audio/webm", (start + "ContentEncryption").latin1())

        assertNotEquals(Verdict.FAIL, plain.verdict)
        assertNotEquals(Verdict.PASS, encrypted.verdict)
        assertEquals(InitCheck.Reason.TRACKS_UNREAD, plain.reason)
    }

    @Test
    fun `the total size must equal the row's size when the row has one`() {
        val bytes = InitBytes.mp4()

        assertEquals(
            InitCheck.Result(Verdict.FAIL, InitCheck.Reason.SIZE_MISMATCH),
            InitCheck.check(avc, ROW_SIZE, InitCheck.Observed("video/mp4", bytes, ROW_SIZE - 1)),
        )
        assertEquals(
            InitCheck.Result(Verdict.UNKNOWN, InitCheck.Reason.SIZE_UNKNOWN),
            InitCheck.check(avc, ROW_SIZE, InitCheck.Observed("video/mp4", bytes, null)),
        )
        for (rowSize in listOf(null, 0L)) {
            for (total in listOf(null, ROW_SIZE)) {
                assertEquals(
                    "$rowSize / $total",
                    InitCheck.Result(Verdict.PASS, InitCheck.Reason.OK),
                    InitCheck.check(avc, rowSize, InitCheck.Observed("video/mp4", bytes, total)),
                )
            }
        }
    }

    @Test
    fun `a fail found by any step wins over an unknown`() {
        val encrypted = InitBytes.mp4(encrypted = true)

        assertEquals(InitCheck.Reason.ENCRYPTED, InitCheck.check(avc, ROW_SIZE, InitCheck.Observed(null, encrypted, null)).reason)
        assertEquals(InitCheck.Reason.SIZE_MISMATCH, InitCheck.check(avc, ROW_SIZE, InitCheck.Observed(null, InitBytes.mp4(), 1L)).reason)
        assertEquals(InitCheck.Reason.TYPE_MISMATCH, InitCheck.check(avc, ROW_SIZE, InitCheck.Observed("text/html", ByteArray(3), null)).reason)
        assertEquals(InitCheck.Reason.START_MISMATCH, check(vp9, null, InitBytes.mp4()).reason)
        assertEquals(Verdict.FAIL, InitCheck.check(vp9, ROW_SIZE, InitCheck.Observed("video/webm", whole(), ROW_SIZE + 1)).verdict)
    }

    @Test
    fun `a row type the check cannot read is unknown`() {
        for (row in listOf(null, "", "video/3gpp; codecs=\"mp4v.20.3\"", "application/x-mpegURL", "video/mp2t")) {
            assertEquals("$row", InitCheck.Result(Verdict.UNKNOWN, InitCheck.Reason.ROW_TYPE_UNSUPPORTED), check(row, "video/mp4", InitBytes.mp4()))
        }
    }

    @Test
    fun `the total is read only from a whole Content-Range`() {
        assertEquals(123_456L, InitCheck.totalOf("bytes 0-4095/123456"))
        assertEquals(10L, InitCheck.totalOf(" Bytes 0-0/10 "))
        for (header in listOf(null, "", "bytes */123456", "bytes 0-4095/*", "bytes 0-9/5", "bytes 9-0/50", "items 0-1/5", "bytes 0-1/5, 7-9/10")) {
            assertNull("$header", InitCheck.totalOf(header))
        }
    }

    @Test
    fun `a result and the observed bytes show no value`() {
        val observed = InitCheck.Observed("video/mp4", "SECRETBYTES".latin1(), ROW_SIZE)
        val result = InitCheck.check(avc, ROW_SIZE, observed)

        assertFalse(observed.toString().contains("SECRET") || observed.toString().contains("video/mp4"))
        assertFalse(result.toString().contains("SECRET") || result.toString().contains("$ROW_SIZE"))
    }

    private fun check(row: String?, contentType: String?, bytes: ByteArray): InitCheck.Result =
        InitCheck.check(row, null, InitCheck.Observed(contentType, bytes, null))

    private fun whole(): ByteArray = InitBytes.webm(codec = "V_VP9")

    private companion object {
        const val ROW_SIZE = 1_234_567L
    }
}

/** ISO BMFF boxes and EBML elements for the init check's tests, made in code. */
internal object InitBytes {
    fun u32(value: Long): ByteArray = ByteArray(4) { ((value ushr (24 - 8 * it)) and 0xFF).toByte() }

    fun box(type: String, payload: ByteArray): ByteArray = u32(8L + payload.size) + type.latin1() + payload

    fun ftyp(): ByteArray = box("ftyp", "dash".latin1() + u32(0) + "iso6".latin1() + "avc1".latin1() + "mp41".latin1())

    /** An init segment: ftyp, then moov with one track whose sample entry is [sample] (or encv/enca). */
    fun mp4(sample: String = "avc1", handler: String = "vide", encrypted: Boolean = false): ByteArray {
        val entryType = if (!encrypted) sample else if (handler == "vide") "encv" else "enca"
        val protection = if (!encrypted) ByteArray(0) else box(
            "sinf",
            box("frma", sample.latin1()) + box("schm", u32(0) + "cenc".latin1() + u32(0x10000)) +
                box("schi", box("tenc", ByteArray(24))),
        )
        val entry = box(entryType, ByteArray(78) + protection)
        val stsd = box("stsd", u32(0) + u32(1) + entry)
        val hdlr = box("hdlr", u32(0) + u32(0) + handler.latin1() + ByteArray(13))
        val trak = box("trak", box("tkhd", ByteArray(84)) + box("mdia", box("mdhd", ByteArray(24)) + hdlr + box("minf", box("stbl", stsd))))
        val pssh = if (encrypted) box("pssh", u32(0) + ByteArray(16) + u32(0)) else ByteArray(0)
        return ftyp() + box("moov", box("mvhd", ByteArray(100)) + pssh + trak) + box("sidx", ByteArray(24))
    }

    /** An EBML element: [id] with its marker bits, the shortest size, [payload]. */
    fun el(id: Long, payload: ByteArray): ByteArray = idBytes(id) + sizeBytes(payload.size.toLong()) + payload

    /** An element of unknown size (live Segments and Clusters). */
    fun unknownSize(id: Long, payload: ByteArray): ByteArray = idBytes(id) + byteArrayOf(0xFF.toByte()) + payload

    fun uint(id: Long, value: Long): ByteArray {
        var length = 1
        while (length < 8 && value ushr (8 * length) != 0L) length++
        return el(id, ByteArray(length) { ((value ushr (8 * (length - 1 - it))) and 0xFF).toByte() })
    }

    fun str(id: Long, text: String): ByteArray = el(id, text.latin1())

    fun ebmlHeader(docType: String): ByteArray = el(
        0x1A45DFA3,
        uint(0x4286, 1) + uint(0x42F7, 1) + uint(0x42F2, 4) + uint(0x42F3, 8) + str(0x4282, docType) +
            uint(0x4287, 4) + uint(0x4285, 2),
    )

    fun trackEntry(number: Long, codec: String, encodings: ByteArray?): ByteArray =
        el(0xAE, uint(0xD7, number) + uint(0x73C5, number * 1_000) + uint(0x83, if (codec.startsWith("V_")) 1 else 2) +
            str(0x86, codec) + (encodings ?: ByteArray(0)))

    /** A WebM DASH start: header, Segment (SeekHead, Void, Info, Tracks, Cues, a Cluster start). */
    fun webm(
        codec: String,
        encodings: ByteArray? = null,
        docType: String = "webm",
        unknownSegmentSize: Boolean = false,
        voidAfterHeader: Boolean = false,
        clusterFirst: Boolean = false,
        secondTrack: ByteArray? = null,
    ): ByteArray {
        val seekHead = el(0x114D9B74, el(0x4DBB, el(0x53AB, idBytes(0x1654AE6B)) + uint(0x53AC, 120)))
        val void = el(0xEC, ByteArray(40))
        val info = el(0x1549A966, uint(0x2AD7B1, 1_000_000) + str(0x4D80, "yft-test") + el(0x4489, ByteArray(8)))
        val tracks = el(0x1654AE6B, trackEntry(1, codec, encodings) + (secondTrack ?: ByteArray(0)))
        val cues = el(0x1C53BB6B, el(0xBB, uint(0xB3, 0) + el(0xB7, uint(0xF7, 1) + uint(0xF1, 300))))
        val cluster = unknownSize(0x1F43B675, uint(0xE7, 0) + el(0xA3, ByteArray(200) { (it * 7).toByte() }))
        val children = if (clusterFirst) seekHead + info + cluster + tracks else seekHead + void + info + tracks + cues + cluster
        val segment = if (unknownSegmentSize) unknownSize(0x18538067, children) else el(0x18538067, children)
        return ebmlHeader(docType) + (if (voidAfterHeader) el(0xEC, ByteArray(4)) else ByteArray(0)) + segment
    }

    fun lastIndexOf(bytes: ByteArray, part: ByteArray): Int =
        (0..bytes.size - part.size).last { at -> part.indices.all { bytes[at + it] == part[it] } }

    private fun idBytes(id: Long): ByteArray {
        val length = when {
            id <= 0xFF -> 1
            id <= 0xFFFF -> 2
            id <= 0xFFFFFF -> 3
            else -> 4
        }
        return ByteArray(length) { ((id ushr (8 * (length - 1 - it))) and 0xFF).toByte() }
    }

    private fun sizeBytes(size: Long): ByteArray {
        var length = 1
        while (length < 8 && size >= (1L shl (7 * length)) - 1) length++
        val marked = size or (1L shl (7 * length))
        return ByteArray(length) { ((marked ushr (8 * (length - 1 - it))) and 0xFF).toByte() }
    }
}

private fun String.latin1(): ByteArray = toByteArray(Charsets.ISO_8859_1)
