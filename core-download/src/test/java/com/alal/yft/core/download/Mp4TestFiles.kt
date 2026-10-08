package com.alal.yft.core.download

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Small MP4 tracks built in the test (P35): fragmented (`moov` with `mvex`, then `moof` +
 * `mdat` pairs, as YouTube's and Facebook's DASH files) or plain (`mdat`, then `moov` with
 * `stbl` tables). Every sample has its own random bytes, so a misplaced one shows.
 */
internal object Mp4TestFiles {
    class Sample(
        val bytes: ByteArray,
        val duration: Int,
        val compositionOffset: Int = 0,
        val sync: Boolean = true,
        /** The sample's size; a virtual sample of a test's huge file has no bytes. */
        val size: Int = bytes.size,
    )

    /** An edit list entry: segment duration in movie ticks (1000 a second), media time. */
    class Edit(val duration: Long, val mediaTime: Long)

    class Track(
        val video: Boolean,
        val timescale: Int,
        val fragments: List<List<Sample>>,
        val firstDecodeTime: Long = 0,
        val edits: List<Edit> = emptyList(),
        val sampleEntry: String = if (video) "avc1" else "mp4a",
        /** Boxes added to the moov, the traf or the sample entry ("pssh", "senc", "sinf"). */
        val extraMoov: List<ByteArray> = emptyList(),
        val extraTraf: List<ByteArray> = emptyList(),
        val extraEntry: List<ByteArray> = emptyList(),
        val tracks: Int = 1,
        /** Each fragment's tfdt, when it does not simply follow on. */
        val decodeTimes: List<Long>? = null,
    ) {
        val samples: List<Sample> get() = fragments.flatten()
    }

    fun video(
        fragments: Int = 3,
        perFragment: Int = 30,
        timescale: Int = 15_360,
        random: Random = Random(1),
    ): Track = Track(
        video = true,
        timescale = timescale,
        fragments = List(fragments) {
            List(perFragment) { index ->
                // I P B B …: each I frame starts a fragment; B frames have larger offsets.
                Sample(
                    bytes = random.nextBytes(200 + random.nextInt(800)),
                    duration = timescale / 30,
                    compositionOffset = if (index % 3 == 0) timescale / 30 else 2 * timescale / 30,
                    sync = index == 0,
                )
            }
        },
    )

    fun audio(
        fragments: Int = 3,
        perFragment: Int = 43,
        timescale: Int = 44_100,
        random: Random = Random(2),
    ): Track = Track(
        video = false,
        timescale = timescale,
        fragments = List(fragments) {
            List(perFragment) { Sample(random.nextBytes(100 + random.nextInt(300)), 1_024) }
        },
    )

    /** A fragmented file: ftyp, moov, then a moof and an mdat per fragment. */
    fun fragmented(track: Track): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ftyp())
        out.write(moov(track, fragmented = true, plainTables = null))
        var decodeTime = track.firstDecodeTime
        track.fragments.forEachIndexed { index, samples ->
            val time = track.decodeTimes?.get(index) ?: decodeTime
            out.write(fragment(track, index + 1, time, samples))
            decodeTime = time + samples.sumOf { it.duration.toLong() }
        }
        return out.toByteArray()
    }

    /** A plain file: ftyp, mdat with every sample, then moov with chunks of [perChunk]. */
    fun plain(track: Track, perChunk: Int = 7): ByteArray {
        val samples = track.samples
        val ftyp = ftyp()
        val data = samples.fold(ByteArray(0)) { all, sample -> all + sample.bytes }
        val mdat = box("mdat", data)
        val chunks = samples.indices.chunked(perChunk)
        var position = (ftyp.size + 8).toLong()
        val offsets = chunks.map { chunk ->
            position.also { position += chunk.sumOf { samples[it].bytes.size } }
        }
        val tables = listOf(
            fullBox("stts", 0, 0, runs(samples.map { it.duration })),
            fullBox("ctts", 0, 0, runs(samples.map { it.compositionOffset })),
            fullBox(
                "stss",
                0,
                0,
                ints(samples.indices.filter { samples[it].sync }.map { it + 1 }, count = true),
            ),
            fullBox("stsc", 0, 0, stsc(chunks.map { it.size })),
            fullBox("stsz", 0, 0, ints(listOf(0, samples.size) + samples.map { it.bytes.size })),
            fullBox("stco", 0, 0, ints(offsets.map { it.toInt() }, count = true)),
        )
        return ftyp + mdat + moov(track, fragmented = false, plainTables = tables)
    }

    fun box(type: String, vararg payloads: ByteArray): ByteArray {
        val payload = payloads.fold(ByteArray(0)) { all, part -> all + part }
        return ByteBuffer.allocate(8 + payload.size)
            .putInt(8 + payload.size)
            .put(type.toByteArray(Charsets.ISO_8859_1))
            .put(payload)
            .array()
    }

    fun fullBox(type: String, version: Int, flags: Int, vararg payloads: ByteArray): ByteArray =
        box(type, int((version shl 24) or flags), *payloads)

    fun int(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    fun long(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()

    fun short(value: Int): ByteArray = ByteBuffer.allocate(2).putShort(value.toShort()).array()

    private fun ints(values: List<Int>, count: Boolean = false): ByteArray {
        val all = if (count) listOf(values.size) + values else values
        val buffer = ByteBuffer.allocate(4 * all.size)
        all.forEach(buffer::putInt)
        return buffer.array()
    }

    /** stts and ctts entries: sample count and value of each run. */
    private fun runs(values: List<Int>): ByteArray {
        val runs = mutableListOf<Pair<Int, Int>>()
        values.forEach { value ->
            val last = runs.lastOrNull()
            if (last != null && last.second == value) {
                runs[runs.size - 1] = last.first + 1 to value
            } else {
                runs += 1 to value
            }
        }
        return ints(listOf(runs.size) + runs.flatMap { listOf(it.first, it.second) })
    }

    private fun stsc(perChunk: List<Int>): ByteArray {
        val entries = mutableListOf<Int>()
        var count = 0
        perChunk.forEachIndexed { index, samples ->
            if (index == 0 || samples != perChunk[index - 1]) {
                entries += listOf(index + 1, samples, 1)
                count += 1
            }
        }
        return ints(listOf(count) + entries)
    }

    private fun ftyp(): ByteArray =
        box("ftyp", "dash".toByteArray(), int(0), "iso6avc1mp41".toByteArray())

    private fun moov(track: Track, fragmented: Boolean, plainTables: List<ByteArray>?): ByteArray {
        val traks = (1..track.tracks).map { id -> trak(track, id, plainTables) }
        val mvhd = fullBox(
            "mvhd",
            0,
            0,
            int(0),
            int(0),
            int(1_000),
            int(0),
            int(0x00010000),
            short(0x0100),
            ByteArray(10),
            MATRIX,
            ByteArray(24),
            int(track.tracks + 1),
        )
        val mvex = if (fragmented) {
            box(
                "mvex",
                *(1..track.tracks).map { id ->
                    fullBox("trex", 0, 0, int(id), int(1), int(0), int(0), int(0x01010000))
                }.toTypedArray(),
            )
        } else {
            ByteArray(0)
        }
        return box("moov", mvhd, *traks.toTypedArray(), mvex, *track.extraMoov.toTypedArray())
    }

    private fun trak(track: Track, id: Int, plainTables: List<ByteArray>?): ByteArray {
        val width = if (track.video) 160 shl 16 else 0
        val height = if (track.video) 90 shl 16 else 0
        val tkhd = fullBox(
            "tkhd",
            0,
            3,
            int(0),
            int(0),
            int(id),
            int(0),
            int(0),
            ByteArray(8),
            short(0),
            short(0),
            short(if (track.video) 0 else 0x0100),
            short(0),
            MATRIX,
            int(width),
            int(height),
        )
        val edts = if (track.edits.isEmpty()) {
            ByteArray(0)
        } else {
            box(
                "edts",
                fullBox(
                    "elst",
                    0,
                    0,
                    int(track.edits.size),
                    *track.edits.map { edit ->
                        int(edit.duration.toInt()) + int(edit.mediaTime.toInt()) + int(0x00010000)
                    }.toTypedArray(),
                ),
            )
        }
        val mdhd = fullBox(
            "mdhd",
            0,
            0,
            int(0),
            int(0),
            int(track.timescale),
            int(0),
            short(0x15c7),
            short(0),
        )
        val handler = if (track.video) "vide" else "soun"
        val hdlr = fullBox(
            "hdlr",
            0,
            0,
            int(0),
            handler.toByteArray(),
            ByteArray(12),
            "Test\u0000".toByteArray(),
        )
        val header = if (track.video) {
            fullBox("vmhd", 0, 1, ByteArray(8))
        } else {
            fullBox("smhd", 0, 0, ByteArray(4))
        }
        val dinf = box("dinf", fullBox("dref", 0, 0, int(1), fullBox("url ", 0, 1)))
        val stsd = fullBox("stsd", 0, 0, int(1), sampleEntry(track))
        val tables = plainTables ?: listOf(
            fullBox("stts", 0, 0, int(0)),
            fullBox("stsc", 0, 0, int(0)),
            fullBox("stsz", 0, 0, int(0), int(0)),
            fullBox("stco", 0, 0, int(0)),
        )
        val stbl = box("stbl", stsd, *tables.toTypedArray())
        return box(
            "trak",
            tkhd,
            edts,
            box("mdia", mdhd, hdlr, box("minf", header, dinf, stbl)),
        )
    }

    private fun sampleEntry(track: Track): ByteArray {
        val extra = track.extraEntry.toTypedArray()
        return if (track.video) {
            val fixed = ByteBuffer.allocate(78)
            fixed.position(6)
            fixed.putShort(1)
            fixed.position(24)
            fixed.putShort(160)
            fixed.putShort(90)
            fixed.putInt(0x00480000)
            fixed.putInt(0x00480000)
            fixed.putInt(0)
            fixed.putShort(1)
            fixed.position(74)
            fixed.putShort(0x18)
            fixed.putShort(-1)
            val avcC = box(
                "avcC",
                byteArrayOf(1, 0x42, 0xc0.toByte(), 0x0d, 0xff.toByte(), 0xe1.toByte()),
                short(4),
                byteArrayOf(0x67, 0x42, 0xc0.toByte(), 0x0d),
                byteArrayOf(1),
                short(3),
                byteArrayOf(0x68, 0xce.toByte(), 0x3c),
            )
            box(track.sampleEntry, fixed.array(), avcC, *extra)
        } else {
            val fixed = ByteBuffer.allocate(28)
            fixed.position(6)
            fixed.putShort(1)
            fixed.position(16)
            fixed.putShort(2)
            fixed.putShort(16)
            fixed.position(24)
            fixed.putInt(track.timescale shl 16)
            val esds = fullBox(
                "esds",
                0,
                0,
                byteArrayOf(3, 25, 0, 1, 0, 4, 17, 0x40, 0x15, 0, 0, 0, 0, 1, 0xf4.toByte(), 0),
                byteArrayOf(0, 1, 0xf4.toByte(), 0, 5, 2, 0x12, 0x10, 6, 1, 2),
            )
            box(track.sampleEntry, fixed.array(), esds, *extra)
        }
    }

    private fun fragment(
        track: Track,
        sequence: Int,
        decodeTime: Long,
        samples: List<Sample>,
    ): ByteArray {
        val data = samples.fold(ByteArray(0)) { all, sample -> all + sample.bytes }
        return moof(track, sequence, decodeTime, samples) + box("mdat", data)
    }

    /** A moof whose samples follow it in an mdat with a header of [mdatHeaderBytes]. */
    fun moof(
        track: Track,
        sequence: Int,
        decodeTime: Long,
        samples: List<Sample>,
        mdatHeaderBytes: Int = 8,
    ): ByteArray {
        val negative = samples.any { it.compositionOffset < 0 }
        val flags = 0x001 or 0x100 or 0x200 or 0x400 or 0x800
        val trunSize = 8 + 4 + 4 + 4 + samples.size * 16
        val tfhd = fullBox("tfhd", 0, 0x020000, int(1))
        val tfdt = fullBox("tfdt", 1, 0, long(decodeTime))
        val extraTraf = track.extraTraf.fold(ByteArray(0)) { all, part -> all + part }
        val trafSize = 8 + tfhd.size + tfdt.size + trunSize + extraTraf.size
        val mfhd = fullBox("mfhd", 0, 0, int(sequence))
        val moofSize = 8 + mfhd.size + trafSize
        val entries = ByteBuffer.allocate(samples.size * 16)
        samples.forEach { sample ->
            entries.putInt(sample.duration)
            entries.putInt(sample.size)
            entries.putInt(if (sample.sync) 0x02000000 else 0x01010000)
            entries.putInt(sample.compositionOffset)
        }
        val trun = fullBox(
            "trun",
            if (negative) 1 else 0,
            flags,
            int(samples.size),
            int(moofSize + mdatHeaderBytes),
            entries.array(),
        )
        check(trun.size == trunSize)
        val moof = box("moof", mfhd, box("traf", tfhd, tfdt, trun, extraTraf))
        check(moof.size == moofSize)
        return moof
    }

    /** ftyp and moov of [track], without its fragments. */
    fun header(track: Track): ByteArray =
        ftyp() + moov(track, fragmented = true, plainTables = null)

    private val MATRIX: ByteArray = ByteBuffer.allocate(36)
        .putInt(0x00010000).putInt(0).putInt(0)
        .putInt(0).putInt(0x00010000).putInt(0)
        .putInt(0).putInt(0).putInt(0x40000000)
        .array()
}
