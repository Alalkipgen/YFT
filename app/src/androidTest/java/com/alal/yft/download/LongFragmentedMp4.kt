package com.alal.yft.download

import java.io.File
import java.nio.ByteBuffer

/**
 * Makes a long fragmented MP4 out of a short one for the P27 timing test. The source's
 * `moof`/`mdat` pairs are written again and again, each copy with its fragment number and decode
 * time moved on, under one new `sidx` that gives the whole length. The test tracks' `tfhd` boxes
 * use default-base-is-moof, so the sample offsets inside each pair stay right. The source's
 * `mfra` is left out; its offsets would be wrong.
 */
object LongFragmentedMp4 {
    /** Writes [source]'s fragments into [target] until they last at least [minimumUs]. */
    fun write(source: ByteArray, minimumUs: Long, target: File) {
        val top = children(source, 0, source.size)
        val index = Sidx.read(source, top.single { it.type == "sidx" })
        val pairs = top.filter { it.type == "moof" || it.type == "mdat" }.chunked(2)
        check(pairs.all { it.size == 2 && it[0].type == "moof" && it[1].type == "mdat" }) {
            "The source is not moof/mdat pairs"
        }
        check(pairs.size == index.references.size) {
            "${pairs.size} fragments for ${index.references.size} references"
        }
        val period = index.references.sumOf { it.duration }
        val wanted = minimumUs * index.timescale
        val repeats = ((wanted + period * MICROS - 1) / (period * MICROS)).toInt()
        val count = repeats * pairs.size
        check(count in 1..MAX_REFERENCES) { "$count references do not fit in one sidx" }

        target.outputStream().buffered().use { output ->
            top.filter { it.type == "ftyp" || it.type == "moov" }
                .forEach { box -> output.write(source, box.offset, box.size) }
            output.write(index.repeated(pairs, repeats))
            for (round in 0 until repeats) {
                pairs.forEachIndexed { position, (moof, mdat) ->
                    val sequence = round * pairs.size + position + 1
                    output.write(shifted(source, moof, sequence, round * period))
                    output.write(source, mdat.offset, mdat.size)
                }
            }
        }
    }

    /** A copy of [moof] with fragment number [sequence] and its decode times moved by [shift]. */
    private fun shifted(source: ByteArray, moof: Box, sequence: Int, shift: Long): ByteArray {
        val copy = source.copyOfRange(moof.offset, moof.offset + moof.size)
        val buffer = ByteBuffer.wrap(copy)
        for (child in children(copy, moof.header, copy.size)) {
            when (child.type) {
                "mfhd" -> buffer.putInt(child.offset + FULL_BOX_HEADER, sequence)
                "traf" -> children(copy, child.offset + child.header, child.offset + child.size)
                    .filter { it.type == "tfdt" }
                    .forEach { tfdt ->
                        check(copy[tfdt.offset + BOX_HEADER].toInt() == 1) { "32-bit tfdt" }
                        val at = tfdt.offset + FULL_BOX_HEADER
                        buffer.putLong(at, buffer.getLong(at) + shift)
                    }
            }
        }
        return copy
    }

    private fun children(bytes: ByteArray, start: Int, end: Int): List<Box> {
        val buffer = ByteBuffer.wrap(bytes)
        val boxes = mutableListOf<Box>()
        var offset = start
        while (offset + BOX_HEADER <= end) {
            var size = buffer.getInt(offset).toLong() and UINT32
            var header = BOX_HEADER
            if (size == 1L) {
                size = buffer.getLong(offset + BOX_HEADER)
                header = BOX_HEADER * 2
            }
            if (size == 0L) size = (end - offset).toLong()
            check(size >= header && offset + size <= end) { "Bad box at $offset" }
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            boxes += Box(type, offset, size.toInt(), header)
            offset += size.toInt()
        }
        return boxes
    }

    private class Box(val type: String, val offset: Int, val size: Int, val header: Int)

    private class Reference(val duration: Long, val sap: Int)

    private class Sidx(
        val referenceId: Int,
        val timescale: Long,
        val references: List<Reference>,
    ) {
        /** A version-1 sidx for [repeats] rounds of [pairs], with the source's durations. */
        fun repeated(pairs: List<List<Box>>, repeats: Int): ByteArray {
            val count = repeats * pairs.size
            val size = FULL_BOX_HEADER + 8 + 16 + 4 + REFERENCE_BYTES * count
            val buffer = ByteBuffer.allocate(size)
            buffer.putInt(size)
            buffer.put("sidx".toByteArray(Charsets.US_ASCII))
            buffer.putInt(1 shl 24)
            buffer.putInt(referenceId)
            buffer.putInt(timescale.toInt())
            buffer.putLong(0L)
            buffer.putLong(0L)
            buffer.putShort(0)
            buffer.putShort(count.toShort())
            repeat(repeats) {
                pairs.forEachIndexed { position, (moof, mdat) ->
                    buffer.putInt(moof.size + mdat.size)
                    buffer.putInt(references[position].duration.toInt())
                    buffer.putInt(references[position].sap)
                }
            }
            return buffer.array()
        }

        companion object {
            fun read(bytes: ByteArray, box: Box): Sidx {
                val buffer = ByteBuffer.wrap(bytes)
                val version = bytes[box.offset + BOX_HEADER].toInt()
                var at = box.offset + FULL_BOX_HEADER
                val referenceId = buffer.getInt(at)
                val timescale = buffer.getInt(at + 4).toLong() and UINT32
                at += 8 + if (version == 0) 8 else 16
                val count = buffer.getShort(at + 2).toInt() and 0xffff
                at += 4
                val references = (0 until count).map { position ->
                    val entry = at + REFERENCE_BYTES * position
                    Reference(
                        duration = buffer.getInt(entry + 4).toLong() and UINT32,
                        sap = buffer.getInt(entry + 8),
                    )
                }
                return Sidx(referenceId, timescale, references)
            }
        }
    }

    private const val BOX_HEADER = 8
    private const val FULL_BOX_HEADER = 12
    private const val REFERENCE_BYTES = 12
    private const val MAX_REFERENCES = 0xffff
    private const val MICROS = 1_000_000L
    private const val UINT32 = 0xffffffffL
}
