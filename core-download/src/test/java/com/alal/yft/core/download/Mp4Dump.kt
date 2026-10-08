package com.alal.yft.core.download

import java.nio.ByteBuffer

/**
 * Reads a merged MP4 back in the test (P35), independently of the stream copy's own reader:
 * the top-level boxes, and per track its headers, edit list and every sample from the tables.
 */
internal class Mp4Dump(private val bytes: ByteArray) {
    class Box(val type: String, val start: Int, val end: Int, val headerSize: Int, val size: Long)

    class DumpedSample(
        val offset: Long,
        val size: Int,
        val decodeTime: Long,
        val compositionOffset: Int,
        val sync: Boolean,
    )

    class DumpedTrack(
        val trackId: Int,
        val handler: String,
        val timescale: Long,
        val mediaDuration: Long,
        val trackDuration: Long,
        val width: Int,
        val height: Int,
        val edits: List<Pair<Long, Long>>,
        val sampleDescription: ByteArray,
        val samples: List<DumpedSample>,
        val chunkOffsets: List<Long>,
        val chunkSamples: List<Int>,
        val boxTypes: Set<String>,
        val cttsVersion: Int?,
    )

    private val view = ByteBuffer.wrap(bytes)

    /** The top-level boxes (a test may pass only the moov). */
    val top: List<Box> = run {
        val boxes = mutableListOf<Box>()
        var at = 0
        while (at < bytes.size) {
            var size = view.getInt(at).toLong() and 0xffffffffL
            var header = 8
            if (size == 1L) {
                size = view.getLong(at + 8)
                header = 16
            }
            val type = String(bytes, at + 4, 4, Charsets.ISO_8859_1)
            boxes += Box(type, at + header, (at + size).toInt(), header, size)
            at += size.toInt()
        }
        boxes
    }

    val movieTimescale: Long get() = moovChild("mvhd").let { u32(it.start + 12) }
    val movieDuration: Long get() = moovChild("mvhd").let { u32(it.start + 16) }

    val tracks: List<DumpedTrack> by lazy {
        children(top.single { it.type == "moov" }).filter { it.type == "trak" }.map(::track)
    }

    private fun moovChild(type: String): Box =
        children(top.single { it.type == "moov" }).single { it.type == type }

    private fun track(trak: Box): DumpedTrack {
        val boxes = children(trak)
        val tkhd = boxes.single { it.type == "tkhd" }
        val mdia = children(boxes.single { it.type == "mdia" })
        val mdhd = mdia.single { it.type == "mdhd" }
        val hdlr = mdia.single { it.type == "hdlr" }
        val minf = children(mdia.single { it.type == "minf" })
        val stbl = children(minf.single { it.type == "stbl" })
        val edits = boxes.firstOrNull { it.type == "edts" }?.let { edts ->
            val elst = children(edts).single { it.type == "elst" }
            val count = u32(elst.start + 4).toInt()
            List(count) { index ->
                val at = elst.start + 8 + 12 * index
                u32(at) to view.getInt(at + 4).toLong()
            }
        }.orEmpty()
        fun table(type: String) = stbl.firstOrNull { it.type == type }
        val stsz = table("stsz")!!
        val count = u32(stsz.start + 8).toInt()
        val fixed = u32(stsz.start + 4).toInt()
        val sizes = List(count) { if (fixed != 0) fixed else u32(stsz.start + 12 + 4 * it).toInt() }
        val durations = expand(table("stts")!!, count)
        val ctts = table("ctts")
        val offsets = ctts?.let { expand(it, count) } ?: List(count) { 0 }
        val stss = table("stss")?.let { box ->
            List(u32(box.start + 4).toInt()) { u32(box.start + 8 + 4 * it).toInt() - 1 }.toSet()
        }
        val stco = table("stco")
        val co64 = table("co64")
        val chunkOffsets = if (stco != null) {
            List(u32(stco.start + 4).toInt()) { u32(stco.start + 8 + 4 * it) }
        } else {
            List(u32(co64!!.start + 4).toInt()) { view.getLong(co64.start + 8 + 8 * it) }
        }
        val stsc = table("stsc")!!
        val stscEntries = List(u32(stsc.start + 4).toInt()) { index ->
            val at = stsc.start + 8 + 12 * index
            u32(at).toInt() to u32(at + 4).toInt()
        }
        val chunkSamples = chunkOffsets.indices.map { chunk ->
            stscEntries.last { it.first <= chunk + 1 }.second
        }
        val samples = mutableListOf<DumpedSample>()
        var sample = 0
        var decodeTime = 0L
        chunkOffsets.forEachIndexed { chunk, start ->
            var position = start
            repeat(chunkSamples[chunk]) {
                samples += DumpedSample(
                    offset = position,
                    size = sizes[sample],
                    decodeTime = decodeTime,
                    compositionOffset = offsets[sample],
                    sync = stss?.contains(sample) ?: true,
                )
                position += sizes[sample]
                decodeTime += durations[sample]
                sample += 1
            }
        }
        check(sample == count) { "stsc covers $sample of $count samples" }
        val stsd = stbl.single { it.type == "stsd" }
        return DumpedTrack(
            trackId = view.getInt(tkhd.start + 12),
            handler = String(bytes, hdlr.start + 8, 4, Charsets.ISO_8859_1),
            timescale = u32(mdhd.start + 12),
            mediaDuration = u32(mdhd.start + 16),
            trackDuration = u32(tkhd.start + 20),
            width = view.getInt(tkhd.start + 76),
            height = view.getInt(tkhd.start + 80),
            edits = edits,
            sampleDescription = bytes.copyOfRange(stsd.start - 8, stsd.end),
            samples = samples,
            chunkOffsets = chunkOffsets,
            chunkSamples = chunkSamples,
            boxTypes = stbl.map { it.type }.toSet(),
            cttsVersion = ctts?.let { bytes[it.start].toInt() },
        )
    }

    /** Run-length entries (count, value) expanded to one value per sample. */
    private fun expand(box: Box, count: Int): List<Int> {
        val values = mutableListOf<Int>()
        repeat(u32(box.start + 4).toInt()) { index ->
            val at = box.start + 8 + 8 * index
            repeat(u32(at).toInt()) { values += view.getInt(at + 4) }
        }
        check(values.size == count) { "${box.type} covers ${values.size} of $count samples" }
        return values
    }

    fun children(box: Box): List<Box> {
        val boxes = mutableListOf<Box>()
        var at = box.start
        while (at < box.end) {
            val size = view.getInt(at)
            val type = String(bytes, at + 4, 4, Charsets.ISO_8859_1)
            boxes += Box(type, at + 8, at + size, 8, size.toLong())
            at += size
        }
        return boxes
    }

    private fun u32(at: Int): Long = view.getInt(at).toLong() and 0xffffffffL
}
