package com.alal.yft.core.download

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * D1: remuxes the pieces of an HLS download into an MP4 in plain Kotlin, without re-encoding:
 * MPEG-TS with H.264 video and AAC (ADTS) sound becomes an MP4 (an M4A for sound alone), packed
 * AAC sound an M4A. Samples go straight into the destination as they are read, in chunks of
 * about a second per track, so the phone needs no room beyond the pieces and the file; `moov`
 * ([StreamCopyMoov], as the P35 stream copy writes it) follows them. Anything it does not expect
 * — another codec, encryption, a broken packet, frame or parameter set — is "not supported"
 * ([StreamCopyUnsupported]) and the caller keeps the file in its own format.
 */
internal object TsMp4Remuxer : HlsRemuxer {
    override suspend fun remux(
        pieces: List<HlsPiece>,
        kind: HlsMediaKind,
        audioOnly: Boolean,
        output: SeekableDownloadOutput,
    ): Long {
        if (pieces.isEmpty()) unsupported("no pieces")
        return try {
            val writer = RemuxMp4Writer(output)
            when (kind) {
                HlsMediaKind.TRANSPORT_STREAM -> transportStream(pieces, audioOnly, writer)
                HlsMediaKind.ADTS_AUDIO -> packedAudio(pieces, writer)
                else -> unsupported("${kind.name.lowercase()} is not remuxed")
            }
            writer.finish()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (notSupported: StreamCopyUnsupported) {
            throw notSupported
        } catch (error: IOException) {
            throw error
        } catch (error: RuntimeException) {
            unsupported("failed, ${error.javaClass.simpleName}")
        } catch (error: OutOfMemoryError) {
            unsupported("failed, out of memory")
        }
    }

    private suspend fun transportStream(
        pieces: List<HlsPiece>,
        audioOnly: Boolean,
        writer: RemuxMp4Writer,
    ) {
        val timeline = TsTimeline()
        val video = if (audioOnly) null else AvcSampleBuilder(writer, timeline)
        val audio = AdtsSampleBuilder(writer, timeline)
        val demuxer = TsDemuxer(
            wantVideo = !audioOnly,
            onVideo = { pts, dts, data, offset, length ->
                video?.accessUnit(pts, dts, data, offset, length)
            },
            onAudio = { pts, data, offset, length -> audio.pes(pts, data, offset, length) },
        )
        val block = ByteArray(TS_PACKET_BYTES * PACKETS_PER_READ)
        pieces.forEachIndexed { index, piece ->
            currentCoroutineContext().ensureActive()
            val start = HlsMediaSniffer.transportStreamStart(piece.file, piece.start)
                ?: unsupported("piece ${index + 1} is not MPEG-TS")
            RandomAccessFile(piece.file, "r").use { input ->
                input.seek(start)
                var filled = 0
                while (true) {
                    val read = input.read(block, filled, block.size - filled)
                    if (read < 0) break
                    filled += read
                    val packets = filled / TS_PACKET_BYTES
                    for (packet in 0 until packets) demuxer.packet(block, packet * TS_PACKET_BYTES)
                    val rest = filled - packets * TS_PACKET_BYTES
                    System.arraycopy(block, packets * TS_PACKET_BYTES, block, 0, rest)
                    filled = rest
                    currentCoroutineContext().ensureActive()
                }
                // A cut packet at the end of a piece holds nothing whole: it is left out.
            }
        }
        demuxer.finish()
        video?.finish()
        audio.finish()
        if (demuxer.videoSelected && video != null && video.samples == 0) {
            unsupported("no video keyframe")
        }
        if (!demuxer.videoSelected && audio.samples == 0) unsupported("no sound")
    }

    private suspend fun packedAudio(pieces: List<HlsPiece>, writer: RemuxMp4Writer) {
        val audio = AdtsSampleBuilder(writer, timeline = null)
        val block = ByteArray(PACKED_AUDIO_READ_BYTES)
        for (piece in pieces) {
            currentCoroutineContext().ensureActive()
            RandomAccessFile(piece.file, "r").use { input ->
                input.seek(piece.start)
                while (true) {
                    val read = input.read(block)
                    if (read < 0) break
                    audio.data(block, 0, read)
                }
            }
        }
        audio.finish()
        if (audio.samples == 0) unsupported("no sound")
    }

    private const val PACKETS_PER_READ = 2_048
    private const val PACKED_AUDIO_READ_BYTES = 256 * 1_024
}

/**
 * D1: the packets of an MPEG-TS stream, read in order: the first program of the PAT, its first
 * H.264 video ([wantVideo]) and its first AAC (ADTS) sound from the first PMT, each PES of those
 * two handed on whole with its 90 kHz times. Another video or sound codec, scrambled packets or
 * a SAMPLE-AES stream are not supported, so a video is never written without its picture or its
 * sound; other streams (ID3 metadata, cues) are left out.
 */
internal class TsDemuxer(
    private val wantVideo: Boolean,
    private val onVideo: (pts: Long?, dts: Long?, data: ByteArray, at: Int, length: Int) -> Unit,
    private val onAudio: (pts: Long?, data: ByteArray, at: Int, length: Int) -> Unit,
) {
    private var pmtPid = -1
    private var videoPid = -1
    private var audioPid = -1
    private var selected = false
    private val videoPes = PesBuffer { data, length -> pes(data, length, video = true) }
    private val audioPes = PesBuffer { data, length -> pes(data, length, video = false) }

    /** Whether the program's H.264 video is read (a PMT named one and video is wanted). */
    val videoSelected: Boolean get() = videoPid >= 0

    /** The 188-byte packet at [at] of [data]. */
    fun packet(data: ByteArray, at: Int) {
        if (data[at].u8() != TS_SYNC) unsupported("lost MPEG-TS sync")
        val second = data[at + 1].u8()
        if (second and 0x80 != 0) unsupported("MPEG-TS transport error")
        val pid = (second and 0x1f shl 8) or data[at + 2].u8()
        val unitStart = second and 0x40 != 0
        val fourth = data[at + 3].u8()
        val control = fourth shr 4 and 0x03
        if (control and 0x01 == 0 || pid == NULL_PID) return
        if (fourth and 0xc0 != 0) unsupported("scrambled MPEG-TS")
        var payload = at + 4
        if (control and 0x02 != 0) payload += 1 + data[at + 4].u8()
        val end = at + TS_PACKET_BYTES
        if (payload >= end) return
        when (pid) {
            PAT_PID -> if (unitStart) pat(data, payload, end)
            pmtPid -> if (unitStart) pmt(data, payload, end)
            videoPid -> videoPes.add(data, payload, end, unitStart)
            audioPid -> audioPes.add(data, payload, end, unitStart)
        }
    }

    /** Hands on the last PES of each stream. */
    fun finish() {
        videoPes.flush()
        audioPes.flush()
        if (!selected) unsupported("no PMT")
    }

    private fun pat(data: ByteArray, payload: Int, end: Int) {
        if (pmtPid >= 0) return
        val section = sectionOf(data, payload, end, TABLE_PAT, MIN_PAT_SECTION) ?: return
        var at = section.first + PAT_PROGRAMS
        while (at + 4 <= section.last) {
            val program = data[at].u8() shl 8 or data[at + 1].u8()
            val pid = data[at + 2].u8() and 0x1f shl 8 or data[at + 3].u8()
            if (program != 0) {
                pmtPid = pid
                return
            }
            at += 4
        }
    }

    private fun pmt(data: ByteArray, payload: Int, end: Int) {
        if (selected) return
        val section = sectionOf(data, payload, end, TABLE_PMT, MIN_PMT_SECTION) ?: return
        val programInfo = data[section.first + 10].u8() and 0x0f shl 8 or
            data[section.first + 11].u8()
        var at = section.first + PMT_STREAMS + programInfo
        val streams = mutableListOf<Pair<Int, Int>>()
        while (at + 5 <= section.last) {
            val type = data[at].u8()
            val pid = data[at + 1].u8() and 0x1f shl 8 or data[at + 2].u8()
            val info = data[at + 3].u8() and 0x0f shl 8 or data[at + 4].u8()
            streams += type to pid
            at += 5 + info
        }
        if (streams.any { (type, _) -> type in ENCRYPTED_TYPES }) unsupported("SAMPLE-AES")
        if (wantVideo) {
            val video = streams.firstOrNull { (type, _) -> type == TYPE_H264 }
            if (video == null && streams.any { (type, _) -> type in VIDEO_TYPES }) {
                unsupported("video codec")
            }
            videoPid = video?.second ?: -1
        }
        val audio = streams.firstOrNull { (type, _) -> type == TYPE_ADTS }
        if (audio == null && streams.any { (type, _) -> type in AUDIO_TYPES }) {
            unsupported("sound codec")
        }
        audioPid = audio?.second ?: -1
        if (videoPid < 0 && audioPid < 0) unsupported("no video or sound")
        if (videoPid == audioPid) unsupported("one PID for video and sound")
        selected = true
    }

    /** The section of a PSI table: its first byte and where its CRC starts; null when cut. */
    private fun sectionOf(
        data: ByteArray,
        payload: Int,
        end: Int,
        tableId: Int,
        minLength: Int,
    ): IntRange? {
        val first = payload + 1 + data[payload].u8()
        if (first + 3 > end || data[first].u8() != tableId) return null
        val length = data[first + 1].u8() and 0x0f shl 8 or data[first + 2].u8()
        if (length < minLength || first + 3 + length > end) return null
        return first..(first + 3 + length - CRC_BYTES)
    }

    private fun pes(data: ByteArray, length: Int, video: Boolean) {
        if (
            length < PES_HEADER || data[0].toInt() != 0 || data[1].toInt() != 0 ||
            data[2].toInt() != 1
        ) {
            unsupported("broken PES")
        }
        val flags = data[7].u8() shr 6
        val payload = PES_HEADER + data[8].u8()
        if (payload > length) unsupported("broken PES")
        val pts = if (flags and 0x02 != 0 && payload >= PES_HEADER + 5) time(data, 9) else null
        val dts = if (flags == 0x03 && payload >= PES_HEADER + 10) time(data, 14) else null
        if (video) {
            onVideo(pts, dts, data, payload, length - payload)
        } else {
            onAudio(pts, data, payload, length - payload)
        }
    }

    private fun time(data: ByteArray, at: Int): Long =
        (data[at].u8().toLong() shr 1 and 0x07 shl 30) or
            (data[at + 1].u8().toLong() shl 22) or
            (data[at + 2].u8().toLong() shr 1 shl 15) or
            (data[at + 3].u8().toLong() shl 7) or
            (data[at + 4].u8().toLong() shr 1)

    /** The packets of one PES, from the packet that starts it until the next one starts. */
    private class PesBuffer(private val emit: (ByteArray, Int) -> Unit) {
        private val bytes = GrowableBytes(INITIAL_PES_BYTES)
        private var started = false

        fun add(data: ByteArray, from: Int, end: Int, unitStart: Boolean) {
            if (unitStart) {
                flush()
                started = true
            }
            if (!started) return
            if (bytes.length + (end - from) > MAX_PES_BYTES) unsupported("PES too large")
            bytes.append(data, from, end - from)
        }

        fun flush() {
            if (started && bytes.length > 0) emit(bytes.data, bytes.length)
            bytes.clear()
        }
    }

    private companion object {
        const val PAT_PID = 0
        const val NULL_PID = 0x1fff
        const val TABLE_PAT = 0x00
        const val TABLE_PMT = 0x02
        const val MIN_PAT_SECTION = 9
        const val MIN_PMT_SECTION = 13
        const val PAT_PROGRAMS = 8
        const val PMT_STREAMS = 12
        const val CRC_BYTES = 4
        const val PES_HEADER = 9
        const val TYPE_H264 = 0x1b
        const val TYPE_ADTS = 0x0f
        const val INITIAL_PES_BYTES = 256 * 1_024
        const val MAX_PES_BYTES = 32 * 1_024 * 1_024

        /** MPEG-1/2 video, MPEG-4 part 2, H.264, MVC, HEVC, AVS, VC-1. */
        val VIDEO_TYPES = setOf(0x01, 0x02, 0x10, 0x1b, 0x20, 0x24, 0x42, 0xea)

        /** MPEG audio, ADTS, LATM, AC-3, E-AC-3, DTS, TrueHD. */
        val AUDIO_TYPES = setOf(0x03, 0x04, 0x0f, 0x11, 0x81, 0x82, 0x83, 0x87, 0x8a)

        /** SAMPLE-AES H.264, AAC, AC-3 and E-AC-3. */
        val ENCRYPTED_TYPES = setOf(0xdb, 0xcf, 0xc1, 0xc2)
    }
}

/**
 * D1: 90 kHz MPEG times (33 bits) made one line: each is taken where it lies closest to the one
 * before it, so a stream that crosses the wrap (about every 26.5 hours) keeps counting.
 */
internal class TsTimeline {
    private var reference: Long? = null

    fun unwrap(raw: Long): Long {
        val last = reference
        val value = if (last == null) {
            raw
        } else {
            var candidate = raw + last - Math.floorMod(last, WRAP)
            if (candidate - last > HALF) {
                candidate -= WRAP
            } else if (last - candidate > HALF) {
                candidate += WRAP
            }
            candidate
        }
        reference = value
        return value
    }

    private companion object {
        const val WRAP = 1L shl 33
        const val HALF = 1L shl 32
    }
}

/**
 * D1: H.264 access units (Annex B, one per PES) as MP4 samples: each NAL unit with a 4-byte
 * length instead of its start code; access unit delimiters and filler data are left out. The
 * first sample is the first IDR picture with its parameter sets (anything before it cannot be
 * shown); the parameter sets seen until then become the `avcC`.
 */
internal class AvcSampleBuilder(
    private val writer: RemuxMp4Writer,
    private val timeline: TsTimeline,
) {
    private val sequenceSets = mutableListOf<ByteArray>()
    private val pictureSets = mutableListOf<ByteArray>()
    private val sample = GrowableBytes(INITIAL_SAMPLE_BYTES)
    private var track: RemuxTrack? = null

    val samples: Int get() = track?.count ?: 0

    fun accessUnit(pts: Long?, dts: Long?, data: ByteArray, offset: Int, length: Int) {
        val end = offset + length
        sample.clear()
        var idr = false
        var nal = AnnexB.nextUnit(data, offset, end)
        if (nal < 0) {
            if (length == 0) return
            unsupported("H.264 without start codes")
        }
        while (nal in 0 until end) {
            val next = AnnexB.nextUnit(data, nal, end)
            var nalEnd = if (next < 0) end else next - 3
            while (nalEnd > nal && data[nalEnd - 1].toInt() == 0) nalEnd -= 1
            if (nalEnd > nal) {
                val type = data[nal].u8() and 0x1f
                if (type != NAL_DELIMITER && type != NAL_FILLER) {
                    if (type == NAL_IDR) idr = true
                    if (track == null && type == NAL_SPS) remember(sequenceSets, data, nal, nalEnd)
                    if (track == null && type == NAL_PPS) remember(pictureSets, data, nal, nalEnd)
                    sample.appendInt(nalEnd - nal)
                    sample.append(data, nal, nalEnd - nal)
                }
            }
            if (next < 0) break
            nal = next
        }
        if (sample.length == 0) return
        val current = track ?: run {
            if (!idr || sequenceSets.isEmpty() || pictureSets.isEmpty()) return
            if (pts == null && dts == null) unsupported("no video times")
            writer.videoTrack().also { track = it }
        }
        val decode = (dts ?: pts)?.let(timeline::unwrap)
        val presented = pts?.let(timeline::unwrap)
        val offsetTicks = if (decode != null && presented != null) presented - decode else 0L
        if (abs(offsetTicks) > MAX_COMPOSITION_TICKS) unsupported("composition offsets")
        writer.add(current, decode, sample.data, 0, sample.length, offsetTicks.toInt(), idr)
    }

    /** Gives the track its `stsd` and size from the first sequence parameter set. */
    fun finish() {
        val current = track ?: return
        val sps = AvcSps.parse(sequenceSets.first())
        current.sampleDescription = AvcSps.sampleDescription(sequenceSets, pictureSets, sps)
        current.width = sps.width
        current.height = sps.height
    }

    private fun remember(sets: MutableList<ByteArray>, data: ByteArray, from: Int, to: Int) {
        if (to - from > MAX_PARAMETER_SET_BYTES) unsupported("H.264 parameter set")
        val set = data.copyOfRange(from, to)
        if (sets.none { it.contentEquals(set) }) {
            if (sets.size == MAX_PARAMETER_SETS) unsupported("H.264 parameter sets")
            sets += set
        }
    }

    private companion object {
        const val NAL_IDR = 5
        const val NAL_SPS = 7
        const val NAL_PPS = 8
        const val NAL_DELIMITER = 9
        const val NAL_FILLER = 12
        const val INITIAL_SAMPLE_BYTES = 256 * 1_024
        const val MAX_PARAMETER_SET_BYTES = 0xffff
        const val MAX_PARAMETER_SETS = 31

        /** Ten seconds at 90 kHz. */
        const val MAX_COMPOSITION_TICKS = 900_000L
    }
}

/** D1: start codes of an H.264 byte stream (Annex B). */
internal object AnnexB {
    /** The index after the next `00 00 01` in [from] until [end], or -1. */
    fun nextUnit(data: ByteArray, from: Int, end: Int): Int {
        var at = from
        while (at + 2 < end) {
            val third = data[at + 2].toInt()
            when {
                third == 1 -> {
                    if (data[at + 1].toInt() == 0 && data[at].toInt() == 0) return at + 3
                    at += 3
                }
                third == 0 -> at += 1
                else -> at += 3
            }
        }
        return -1
    }
}

/**
 * D1: what the MP4 needs of an H.264 sequence parameter set: its profile, level, chroma format
 * and bit depths for the `avcC`, and the picture's size after cropping.
 */
internal class AvcSps(
    val profile: Int,
    val compatibility: Int,
    val level: Int,
    val chromaFormat: Int,
    val bitDepthLumaMinus8: Int,
    val bitDepthChromaMinus8: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun parse(nal: ByteArray): AvcSps {
            if (nal.size < 4) unsupported("H.264 SPS")
            val bits = BitReader(unescape(nal, 1))
            val profile = bits.bits(8)
            val compatibility = bits.bits(8)
            val level = bits.bits(8)
            bits.ue()
            var chromaFormat = 1
            var separatePlanes = false
            var lumaDepth = 0
            var chromaDepth = 0
            if (profile in CHROMA_PROFILES) {
                chromaFormat = bits.ue()
                if (chromaFormat > 3) unsupported("H.264 SPS")
                if (chromaFormat == 3) separatePlanes = bits.flag()
                lumaDepth = bits.ue()
                chromaDepth = bits.ue()
                if (lumaDepth > 6 || chromaDepth > 6) unsupported("H.264 SPS")
                bits.flag()
                if (bits.flag()) {
                    repeat(if (chromaFormat != 3) 8 else 12) { list ->
                        if (bits.flag()) skipScalingList(bits, if (list < 6) 16 else 64)
                    }
                }
            }
            bits.ue()
            when (bits.ue()) {
                0 -> bits.ue()
                1 -> {
                    bits.flag()
                    bits.se()
                    bits.se()
                    val cycle = bits.ue()
                    if (cycle > 255) unsupported("H.264 SPS")
                    repeat(cycle) { bits.se() }
                }
            }
            bits.ue()
            bits.flag()
            val widthInMbs = bits.ue() + 1
            val heightInUnits = bits.ue() + 1
            val frameMbsOnly = bits.flag()
            if (!frameMbsOnly) bits.flag()
            bits.flag()
            var left = 0
            var right = 0
            var top = 0
            var bottom = 0
            if (bits.flag()) {
                left = bits.ue()
                right = bits.ue()
                top = bits.ue()
                bottom = bits.ue()
            }
            val fields = if (frameMbsOnly) 1 else 2
            val monochrome = chromaFormat == 0 || separatePlanes
            val cropX = if (monochrome || chromaFormat == 3) 1 else 2
            val cropY = (if (monochrome || chromaFormat != 1) 1 else 2) * fields
            val width = widthInMbs * 16 - cropX * (left + right)
            val height = fields * heightInUnits * 16 - cropY * (top + bottom)
            if (width !in 1..MAX_SIZE || height !in 1..MAX_SIZE) unsupported("H.264 size")
            return AvcSps(
                profile = profile,
                compatibility = compatibility,
                level = level,
                chromaFormat = chromaFormat,
                bitDepthLumaMinus8 = lumaDepth,
                bitDepthChromaMinus8 = chromaDepth,
                width = width,
                height = height,
            )
        }

        /** `stsd` with one `avc1` entry of [sps]'s size and an `avcC` of every set. */
        fun sampleDescription(
            sequenceSets: List<ByteArray>,
            pictureSets: List<ByteArray>,
            sps: AvcSps,
        ): ByteArray {
            val avcc = ByteArrayOutputStream()
            avcc.write(1)
            avcc.write(sps.profile)
            avcc.write(sps.compatibility)
            avcc.write(sps.level)
            avcc.write(0xff)
            avcc.write(0xe0 or sequenceSets.size)
            for (set in sequenceSets) {
                avcc.write(set.size shr 8)
                avcc.write(set.size and 0xff)
                avcc.write(set)
            }
            avcc.write(pictureSets.size)
            for (set in pictureSets) {
                avcc.write(set.size shr 8)
                avcc.write(set.size and 0xff)
                avcc.write(set)
            }
            if (sps.profile in AVCC_EXTENSION_PROFILES) {
                avcc.write(0xfc or sps.chromaFormat)
                avcc.write(0xf8 or sps.bitDepthLumaMinus8)
                avcc.write(0xf8 or sps.bitDepthChromaMinus8)
                avcc.write(0)
            }
            val entry = ByteBuffer.allocate(VISUAL_ENTRY_BYTES)
                .put(ByteArray(6))
                .putShort(1)
                .put(ByteArray(16))
                .putShort(sps.width.toShort())
                .putShort(sps.height.toShort())
                .putInt(DPI_72)
                .putInt(DPI_72)
                .putInt(0)
                .putShort(1)
                .put(ByteArray(32))
                .putShort(DEPTH_24)
                .putShort(-1)
                .array()
            val avc1 = box("avc1", entry, box("avcC", avcc.toByteArray()))
            return box("stsd", ByteBuffer.allocate(8).putInt(0).putInt(1).array(), avc1)
        }

        /** The RBSP of a NAL unit from [from] on: emulation prevention bytes removed. */
        private fun unescape(nal: ByteArray, from: Int): ByteArray {
            val out = ByteArray(nal.size - from)
            var length = 0
            var zeros = 0
            for (index in from until nal.size) {
                val value = nal[index].u8()
                if (zeros >= 2 && value == 3) {
                    zeros = 0
                    continue
                }
                out[length++] = value.toByte()
                zeros = if (value == 0) zeros + 1 else 0
            }
            return out.copyOf(length)
        }

        private fun skipScalingList(bits: BitReader, size: Int) {
            var last = 8
            var next = 8
            repeat(size) {
                if (next != 0) next = (last + bits.se() + 256) % 256
                if (next != 0) last = next
            }
        }

        private fun box(type: String, vararg payloads: ByteArray): ByteArray {
            val size = BOX_HEADER + payloads.sumOf { it.size }
            val out = ByteBuffer.allocate(size).putInt(size)
                .put(type.toByteArray(Charsets.ISO_8859_1))
            payloads.forEach { out.put(it) }
            return out.array()
        }

        private val CHROMA_PROFILES =
            setOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)
        private val AVCC_EXTENSION_PROFILES = setOf(100, 110, 122, 144)
        private const val MAX_SIZE = 16_384
        private const val VISUAL_ENTRY_BYTES = 78
        private const val DPI_72 = 0x00480000
        private const val DEPTH_24: Short = 0x18
    }
}

/** D1: reads an H.264 RBSP bit by bit, with Exp-Golomb codes; reading past its end fails. */
internal class BitReader(private val data: ByteArray) {
    private var position = 0

    fun flag(): Boolean = bits(1) == 1

    fun bits(count: Int): Int {
        var value = 0
        repeat(count) {
            if (position >= data.size * 8) unsupported("H.264 SPS cut short")
            val bit = data[position shr 3].u8() shr (7 - (position and 7)) and 1
            value = value shl 1 or bit
            position += 1
        }
        return value
    }

    fun ue(): Int {
        var zeros = 0
        while (!flag()) {
            zeros += 1
            if (zeros > 31) unsupported("H.264 SPS code")
        }
        if (zeros == 0) return 0
        return ((1L shl zeros) - 1 + bits(zeros).toLong()).coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun se(): Int {
        val code = ue()
        return if (code and 1 == 1) (code + 1) / 2 else -(code / 2)
    }
}

/**
 * D1: AAC in ADTS frames as MP4 samples: every raw frame without its header, timed from its PES
 * (the first frame that starts in a PES at the PES's time, the next ones 1024 samples on) or,
 * for packed sound without a [timeline], one after another. A frame cut between two PES is
 * joined first. The `stsd` holds the AudioSpecificConfig of the headers, as the HLS merge's
 * [AdtsTrackReader] writes it.
 */
internal class AdtsSampleBuilder(
    private val writer: RemuxMp4Writer,
    private val timeline: TsTimeline?,
) {
    private val carry = GrowableBytes(INITIAL_CARRY_BYTES)
    private var format = -1
    private var rate = 0
    private var track: RemuxTrack? = null

    val samples: Int get() = track?.count ?: 0

    /** One PES of sound: the frames that start in it begin at [pts] (90 kHz). */
    fun pes(pts: Long?, data: ByteArray, offset: Int, length: Int) {
        val newFrom = carry.length
        carry.append(data, offset, length)
        frames(newFrom, pts?.let { raw -> checkNotNull(timeline).unwrap(raw) })
    }

    /** Packed sound: more of the joined frames. */
    fun data(data: ByteArray, offset: Int, length: Int) {
        carry.append(data, offset, length)
        frames(Int.MAX_VALUE, null)
    }

    fun finish() {
        val current = track ?: return
        val objectType = (format shr 7 and 0x03) + 1
        val frequencyIndex = format shr 3 and 0x0f
        val channels = format and 0x07
        val bitrate = current.payloadBytes * BITS_PER_BYTE * rate /
            (current.count.toLong() * SAMPLES_PER_FRAME)
        current.sampleDescription = AdtsTrackReader.sampleDescription(
            objectType = objectType,
            frequencyIndex = frequencyIndex,
            channelConfiguration = channels,
            rate = rate,
            largestFrame = current.largestSample,
            bitrate = bitrate,
        )
    }

    private fun frames(newFrom: Int, pts: Long?) {
        val bytes = carry.data
        var at = 0
        var newFrames = 0L
        while (carry.length - at >= HEADER_BYTES) {
            val second = bytes[at + 1].u8()
            if (bytes[at].u8() != 0xff || second and 0xf6 != 0xf0) unsupported("lost ADTS sync")
            val third = bytes[at + 2].u8()
            val fourth = bytes[at + 3].u8()
            val headerBytes = if (second and 1 == 1) HEADER_BYTES else CRC_HEADER_BYTES
            val frameBytes = (fourth and 0x03 shl 11) or (bytes[at + 4].u8() shl 3) or
                (bytes[at + 5].u8() shr 5)
            if (frameBytes <= headerBytes) unsupported("broken ADTS frame size")
            if (carry.length - at < frameBytes) break
            if (bytes[at + 6].u8() and 0x03 != 0) unsupported("several AAC blocks in an ADTS frame")
            val channelBits = (third and 0x01 shl 2) or (fourth shr 6)
            val frameFormat = (third shr 6 shl 7) or (third shr 2 and 0x0f shl 3) or channelBits
            val current = track ?: start(frameFormat)
            if (frameFormat != format) unsupported("ADTS format changes")
            val proposed = if (pts != null && at >= newFrom) {
                rescale(pts, MPEG_CLOCK, rate.toLong()) + newFrames * SAMPLES_PER_FRAME
            } else {
                null
            }
            if (at >= newFrom) newFrames += 1
            if (current.count == 0 && proposed == null && timeline != null) {
                unsupported("no sound times")
            }
            val payload = frameBytes - headerBytes
            writer.add(current, proposed, bytes, at + headerBytes, payload, 0, true)
            at += frameBytes
        }
        carry.drop(at)
    }

    private fun start(frameFormat: Int): RemuxTrack {
        val frequencyIndex = frameFormat shr 3 and 0x0f
        if (frequencyIndex >= SAMPLE_RATES.size) unsupported("ADTS rate")
        rate = SAMPLE_RATES[frequencyIndex]
        if (rate > MAX_ENTRY_RATE) unsupported("ADTS rate")
        if (frameFormat and 0x07 == 0) unsupported("ADTS channels")
        format = frameFormat
        return writer.audioTrack(rate).also { track = it }
    }

    private companion object {
        const val HEADER_BYTES = 7
        const val CRC_HEADER_BYTES = 9
        const val INITIAL_CARRY_BYTES = 64 * 1_024
        const val SAMPLES_PER_FRAME = 1_024
        const val BITS_PER_BYTE = 8
        const val MPEG_CLOCK = 90_000L
        const val MAX_ENTRY_RATE = 65_535
        val SAMPLE_RATES = intArrayOf(
            96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025,
            8_000, 7_350,
        )
    }
}

/**
 * D1: one track of the remuxed MP4 while it is written: each sample's size, duration,
 * composition offset, sync flag and place in the file, its chunks, and the chunk still gathered
 * in memory. Times are the track's own ticks; a time that jumps back, or ahead by more than ten
 * seconds (a discontinuity), is moved to where the samples before it end, and so are the times
 * after it.
 */
internal class RemuxTrack(
    val kind: StreamCopyTrackKind,
    val timescale: Long,
    /** A sample's duration until the track's own samples tell. */
    private val defaultDuration: Int,
    /** A proposed time this close to where the samples before it end is taken as that place. */
    private val snapTicks: Long,
) {
    var count = 0
        private set
    var offsets = LongArray(INITIAL_SAMPLES)
        private set
    var sizes = IntArray(INITIAL_SAMPLES)
        private set
    var durations = IntArray(INITIAL_SAMPLES)
        private set
    var compositionOffsets = IntArray(INITIAL_SAMPLES)
        private set
    var sync = BooleanArray(INITIAL_SAMPLES)
        private set

    var firstTime = 0L
        private set
    var lastTime = 0L
        private set
    var largestSample = 0
        private set
    var payloadBytes = 0L
        private set

    var sampleDescription: ByteArray? = null
    var width = 0
    var height = 0

    val pending = GrowableBytes(INITIAL_CHUNK_BYTES)
    var pendingFirst = 0
        private set
    var pendingStart = 0L
        private set

    var chunkFirst = IntArray(INITIAL_CHUNKS)
        private set
    var chunkSamples = IntArray(INITIAL_CHUNKS)
        private set
    var chunkOffsets = LongArray(INITIAL_CHUNKS)
        private set
    var chunkCount = 0
        private set

    private var rebase = 0L

    /** Where a sample proposed at [proposed] ticks (null: unknown) goes on the timeline. */
    fun place(proposed: Long?): Long {
        if (count == 0) return proposed ?: 0L
        val expected = lastTime + lastDuration()
        val time = proposed?.plus(rebase) ?: return expected
        if (abs(time - expected) <= snapTicks) return expected
        if (time > lastTime && time - lastTime <= MAX_GAP_SECONDS * timescale) return time
        rebase += expected - time
        return expected
    }

    /** Adds a sample at [time] ([place]) to the chunk gathered in memory. */
    fun add(
        time: Long,
        data: ByteArray,
        offset: Int,
        length: Int,
        compositionOffset: Int,
        isSync: Boolean,
    ) {
        if (length <= 0) unsupported("empty sample")
        if (count == offsets.size) grow()
        if (count == 0) {
            firstTime = time
        } else {
            val duration = time - lastTime
            if (duration <= 0 || duration > Int.MAX_VALUE) unsupported("sample times")
            durations[count - 1] = duration.toInt()
        }
        if (pending.length == 0) {
            pendingFirst = count
            pendingStart = time
        }
        sizes[count] = length
        compositionOffsets[count] = compositionOffset
        sync[count] = isSync
        lastTime = time
        largestSample = maxOf(largestSample, length)
        payloadBytes += length
        pending.append(data, offset, length)
        count += 1
    }

    /** Records the gathered chunk as written at [position]. */
    fun chunkWritten(position: Long) {
        if (chunkCount == chunkFirst.size) {
            val size = chunkCount * 2
            chunkFirst = chunkFirst.copyOf(size)
            chunkSamples = chunkSamples.copyOf(size)
            chunkOffsets = chunkOffsets.copyOf(size)
        }
        chunkFirst[chunkCount] = pendingFirst
        chunkSamples[chunkCount] = count - pendingFirst
        chunkOffsets[chunkCount] = position
        chunkCount += 1
        var at = position
        for (sample in pendingFirst until count) {
            offsets[sample] = at
            at += sizes[sample]
        }
        pending.clear()
        pendingFirst = count
    }

    /** The last sample lasts as long as the one before it. */
    fun closeLastSample() {
        if (count > 0) durations[count - 1] = lastDuration()
    }

    private fun lastDuration(): Int = if (count >= 2) durations[count - 2] else defaultDuration

    private fun grow() {
        val size = offsets.size * 2
        offsets = offsets.copyOf(size)
        sizes = sizes.copyOf(size)
        durations = durations.copyOf(size)
        compositionOffsets = compositionOffsets.copyOf(size)
        sync = sync.copyOf(size)
    }

    private companion object {
        const val INITIAL_SAMPLES = 4_096
        const val INITIAL_CHUNKS = 256
        const val INITIAL_CHUNK_BYTES = 1_024 * 1_024
        const val MAX_GAP_SECONDS = 10L
    }
}

/**
 * D1: the remuxed MP4 in [output]: `ftyp` (an M4A's for sound alone) and a 64-bit `mdat` whose
 * size is set at the end, the chunks of both tracks as they fill (about a second or
 * [maxChunkBytes] each), then `moov`.
 */
internal class RemuxMp4Writer(
    private val output: SeekableDownloadOutput,
    private val maxChunkBytes: Long = StreamCopyLayout.MAX_CHUNK_BYTES,
    private val maxSamples: Int = Mp4TrackReader.MAX_SAMPLES,
) {
    private var video: RemuxTrack? = null
    private var audio: RemuxTrack? = null
    private var position = PAYLOAD_START
    private var samples = 0

    init {
        output.write(0, StreamCopyLayout.FTYP, 0, StreamCopyLayout.FTYP.size)
        output.write(StreamCopyLayout.FTYP.size.toLong(), mdatHeader(0L), 0, LARGE_BOX_HEADER)
    }

    fun videoTrack(): RemuxTrack {
        check(video == null)
        return RemuxTrack(StreamCopyTrackKind.VIDEO, MPEG_CLOCK, VIDEO_DURATION, snapTicks = 0L)
            .also { video = it }
    }

    fun audioTrack(rate: Int): RemuxTrack {
        check(audio == null)
        return RemuxTrack(
            StreamCopyTrackKind.AUDIO,
            rate.toLong(),
            AUDIO_FRAME,
            snapTicks = AUDIO_FRAME / 2L,
        ).also { audio = it }
    }

    /** One sample of [track], proposed at [proposed] ticks (null: right after the last one). */
    fun add(
        track: RemuxTrack,
        proposed: Long?,
        data: ByteArray,
        offset: Int,
        length: Int,
        compositionOffset: Int,
        isSync: Boolean,
    ) {
        if (samples == maxSamples) unsupported("too many samples")
        val time = track.place(proposed)
        if (
            track.pending.length > 0 &&
            (
                time - track.pendingStart >= track.timescale ||
                    track.pending.length + length > maxChunkBytes
                )
        ) {
            flush(track)
        }
        track.add(time, data, offset, length, compositionOffset, isSync)
        samples += 1
    }

    /** Writes the last chunks, the `mdat` size, the `ftyp` and `moov`; returns the length. */
    fun finish(): Long {
        val tracks = listOfNotNull(video, audio).filter { it.count > 0 }
        if (tracks.isEmpty()) unsupported("no samples")
        tracks.filter { it.pending.length > 0 }
            .sortedBy { ticksToMicros(it.pendingStart - it.firstTime, it.timescale) }
            .forEach(::flush)
        val payloadEnd = position
        val ftyp = if (tracks.any { it.kind == StreamCopyTrackKind.VIDEO }) {
            StreamCopyLayout.FTYP
        } else {
            M4A_FTYP
        }
        output.write(0, ftyp, 0, ftyp.size)
        val mdatSize = payloadEnd - ftyp.size
        output.write(ftyp.size.toLong(), mdatHeader(mdatSize), 0, LARGE_BOX_HEADER)
        val origin = tracks.minOf { rescale(it.firstTime, it.timescale, MPEG_CLOCK) }
        val placed = tracks.map { track ->
            track.closeLastSample()
            val description = track.sampleDescription ?: unsupported("no sample description")
            val count = track.count
            val offsets = track.compositionOffsets.copyOf(count)
            val syncs = track.sync.copyOf(count)
            val mp4Track = Mp4Track(
                kind = track.kind,
                timescale = track.timescale,
                language = 0,
                sampleDescription = description,
                nalLengthSize = if (track.kind == StreamCopyTrackKind.VIDEO) 4 else null,
                width = track.width shl 16,
                height = track.height shl 16,
                matrix = null,
                editShift = 0L,
                firstDecodeTime = (
                    track.firstTime - rescale(origin, MPEG_CLOCK, track.timescale)
                    ).coerceAtLeast(0L),
                count = count,
                offsets = track.offsets.copyOf(count),
                sizes = track.sizes.copyOf(count),
                durations = track.durations.copyOf(count),
                compositionOffsets = offsets.takeIf { all -> all.any { it != 0 } },
                sync = syncs.takeIf { all -> all.any { !it } },
            )
            mp4Track to PlacedChunks(
                firstSample = track.chunkFirst,
                samples = track.chunkSamples,
                offsets = track.chunkOffsets,
                count = track.chunkCount,
            )
        }
        val moov = StreamCopyMoov.build(StreamCopyLayout.placed(placed))
        val sink = BoxSink(BoxOutput(output), ByteBuffer.allocate(MOOV_BUFFER_BYTES), payloadEnd)
        moov.write(sink)
        sink.flush()
        val end = payloadEnd + moov.size
        output.setLength(end)
        return end
    }

    private fun flush(track: RemuxTrack) {
        val bytes = track.pending
        output.write(position, bytes.data, 0, bytes.length)
        val written = bytes.length
        track.chunkWritten(position)
        position += written
    }

    private fun mdatHeader(size: Long): ByteArray = ByteBuffer.allocate(LARGE_BOX_HEADER)
        .putInt(1)
        .put("mdat".toByteArray(Charsets.ISO_8859_1))
        .putLong(size)
        .array()

    /** The `moov` sink's positioned writes, into the destination (a heap buffer's bytes). */
    private class BoxOutput(private val output: SeekableDownloadOutput) : StreamCopyOutput {
        override fun write(source: ByteBuffer, position: Long) {
            val count = source.remaining()
            output.write(position, source.array(), source.arrayOffset() + source.position(), count)
            source.position(source.limit())
        }

        override fun truncate(size: Long) = output.setLength(size)
    }

    companion object {
        const val MPEG_CLOCK = 90_000L
        const val VIDEO_DURATION = 3_000
        const val AUDIO_FRAME = 1_024
        private const val MOOV_BUFFER_BYTES = 64 * 1_024

        /** `ftyp` of sound alone: M4A, minor version 512, compatible M4A, isom, iso2, mp41. */
        val M4A_FTYP: ByteArray = ByteBuffer.allocate(32).apply {
            putInt(32)
            put("ftypM4A ".toByteArray(Charsets.ISO_8859_1))
            putInt(512)
            put("M4A isomiso2mp41".toByteArray(Charsets.ISO_8859_1))
        }.array()

        /** `ftyp` (32 bytes) and the 16-byte `mdat` header come first. */
        private const val PAYLOAD_START = 32L + LARGE_BOX_HEADER
    }
}

/** D1: a byte array that grows as bytes are added, and drops bytes from its front. */
internal class GrowableBytes(initial: Int) {
    var data = ByteArray(initial)
        private set
    var length = 0
        private set

    fun append(source: ByteArray, offset: Int, count: Int) {
        ensure(count)
        System.arraycopy(source, offset, data, length, count)
        length += count
    }

    fun appendInt(value: Int) {
        ensure(4)
        data[length] = (value ushr 24).toByte()
        data[length + 1] = (value ushr 16).toByte()
        data[length + 2] = (value ushr 8).toByte()
        data[length + 3] = value.toByte()
        length += 4
    }

    fun drop(count: Int) {
        if (count <= 0) return
        System.arraycopy(data, count, data, 0, length - count)
        length -= count
    }

    fun clear() {
        length = 0
    }

    private fun ensure(extra: Int) {
        val needed = length.toLong() + extra
        if (needed > Int.MAX_VALUE - 8) throw OutOfMemoryError("Buffer too large")
        if (needed > data.size) {
            val size = maxOf(data.size * 2L, needed).coerceAtMost(Int.MAX_VALUE - 8L)
            data = data.copyOf(size.toInt())
        }
    }
}
