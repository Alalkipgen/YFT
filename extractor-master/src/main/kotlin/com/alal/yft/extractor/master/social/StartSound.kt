package com.alal.yft.extractor.master.social

/**
 * VK's pack proof (the owner's retest of 7e101aa4, 2026-10-26): a sound file's codec from the
 * first bytes V read of it ([SocialRowCheck.START_BYTES]), for a sound row that states none: VK's
 * pieces' sound file ([VkPieceFiles]) names no codecs, and the phone's merge gate
 * (`AudioVideoMuxCompatibility`) takes an MP4 sound only as `mp4a.40.…`, so the 1080p row's
 * download read "This video and its audio cannot be combined on this phone" though the sheet
 * offered it. ISO BMFF only, inside the file's `moov`, the first sound track (its handler `soun`,
 * or, with no handler in the bytes, a track whose sample entry is a sound's): its `stsd` entry
 * `mp4a`, then the entry's `esds` (the MPEG-4 elementary stream descriptor): the decoder
 * configuration's object type (`0x40` MPEG-4 audio) and the audio specific configuration's audio
 * object type (2 AAC LC, 5 HE-AAC, 29 HE-AAC v2), as the codec string names them
 * (`mp4a.40.2`). An `mp4a` entry whose `esds` the bytes do not hold, or hold cut, names
 * `mp4a.40.2`, the plain AAC every VK sound file carries; an entry of another kind (`Opus`,
 * `ac-3`, `fLaC`) names its own type lowercased, so the gate refuses it as before. None for a
 * `moov` past the bytes, a box the bytes hold only in part, a picture-only file and bytes that are
 * no ISO BMFF. At most [MAX_BOXES] boxes. Pure: never decodes, plays or asks anything, and nothing
 * is logged.
 */
internal object StartSound {
    private const val MAX_BOXES = 256

    /** The plain AAC codec, named when an `mp4a` entry's `esds` is past the bytes. */
    const val AAC_LC = "mp4a.40.2"

    /** The sound sample entries whose type names the codec as is (lowercased). */
    private val OTHER_SOUND = setOf("opus", "ac-3", "ec-3", "flac", "alac", "mp3", "samr", "sawb", ".mp3", "ulaw", "alaw", "twos", "sowt", "lpcm")

    /** One track's facts, as far as the bytes go. */
    private class Track {
        var handler: String? = null
        var codec: String? = null
    }

    fun codec(bytes: ByteArray): String? {
        var boxes = 0
        var found: String? = null

        fun u8(at: Int): Int = bytes[at].toInt() and 255

        fun u32(at: Int): Long = (0..3).fold(0L) { n, i -> (n shl 8) or (bytes[at + i].toLong() and 255) }

        fun u64(at: Int): Long? {
            if (at + 8 > bytes.size) return null
            val high = u32(at)
            if (high > 0x7fffffff) return null
            return (high shl 32) or u32(at + 4)
        }

        fun type(at: Int): String = String(bytes, at, 4, Charsets.US_ASCII)

        /**
         * An MPEG-4 descriptor's size at [at] (one to four bytes of seven bits, the top bit a
         * continuation), with the position after it; null past [end].
         */
        fun descriptorSize(at: Int, end: Int): Pair<Int, Int>? {
            var size = 0
            var position = at
            repeat(4) {
                if (position >= end) return null
                val byte = u8(position++)
                size = (size shl 7) or (byte and 0x7f)
                if (byte and 0x80 == 0) return size to position
            }
            return null
        }

        /**
         * `esds` ([body] to [end]): the ES descriptor (tag 3), its decoder configuration (tag 4:
         * the object type), the decoder specific information (tag 5: the audio object type).
         */
        fun esds(body: Int, end: Int): String? {
            var at = body + 4 // The full box's version and flags.
            if (at >= end || u8(at) != 0x03) return null
            val es = descriptorSize(at + 1, end) ?: return null
            at = es.second
            if (at + 3 > end) return null
            val flags = u8(at + 2)
            at += 3
            if (flags and 0x80 != 0) at += 2 // streamDependenceFlag: dependsOn_ES_ID.
            if (flags and 0x40 != 0) { // URL_Flag: a length-prefixed URL.
                if (at >= end) return null
                at += 1 + u8(at)
            }
            if (flags and 0x20 != 0) at += 2 // OCRstreamFlag: OCR_ES_Id.
            if (at >= end || u8(at) != 0x04) return null
            val decoder = descriptorSize(at + 1, end) ?: return null
            at = decoder.second
            if (at + 13 > end) return null
            val objectType = u8(at)
            at += 13
            val prefix = "mp4a.%02x".format(objectType)
            if (objectType != 0x40) return prefix
            if (at >= end || u8(at) != 0x05) return AAC_LC
            val specific = descriptorSize(at + 1, end) ?: return AAC_LC
            at = specific.second
            if (at + 1 > end) return AAC_LC
            var audioObjectType = u8(at) ushr 3
            if (audioObjectType == 31) {
                if (at + 2 > end) return AAC_LC
                // The escape: six more bits, 32 added.
                audioObjectType = 32 + (((u8(at) and 0x07) shl 3) or (u8(at + 1) ushr 5))
            }
            return "$prefix.$audioObjectType"
        }

        /** `stsd`: the first entry, a sound one; an `mp4a` entry's `esds` names the codec. */
        fun sampleEntry(track: Track, body: Int, end: Int) {
            val entry = body + 8
            if (entry + 36 > end || u32(body + 4) < 1) return
            val size = u32(entry)
            if (size < 36 || size > end - entry) return
            val entryType = type(entry + 4)
            val entryEnd = entry + size.toInt()
            when {
                entryType == "mp4a" -> {
                    track.codec = AAC_LC
                    // The entry's own boxes after the audio sample entry's 28 bytes (a QuickTime
                    // version 1 entry carries 16 more; the walk skips the unknown).
                    var at = entry + 36
                    while (at + 8 <= entryEnd && ++boxes <= MAX_BOXES) {
                        val childSize = u32(at)
                        if (childSize < 8 || childSize > entryEnd - at) {
                            // Not a box (a version 1 entry's extra fields): try the next word.
                            at += 4
                            continue
                        }
                        if (type(at + 4) == "esds") {
                            track.codec = esds(at + 8, at + childSize.toInt()) ?: AAC_LC
                            return
                        }
                        at += childSize.toInt()
                    }
                }
                entryType.lowercase() in OTHER_SOUND -> track.codec = entryType.trim().lowercase()
            }
        }

        fun soundOf(track: Track): String? = when (track.handler) {
            "soun" -> track.codec
            null -> track.codec
            else -> null
        }

        /** The boxes from [begin] to [limit] (the bytes' end for a box they hold only in part). */
        fun walk(begin: Int, limit: Int, depth: Int, track: Track?) {
            var at = begin
            while (found == null && at + 8 <= limit && ++boxes <= MAX_BOXES) {
                val size = u32(at)
                val header = if (size == 1L) 16 else 8
                if (at + header > limit) return
                val length = when (size) {
                    1L -> u64(at + 8) ?: return
                    0L -> (limit - at).toLong()
                    else -> size
                }
                if (length < header) return
                val whole = length <= limit - at
                val end = if (whole) at + length.toInt() else limit
                val body = at + header
                val name = type(at + 4)
                when {
                    depth == 0 -> if (name == "moov") walk(body, end, 1, null)
                    depth == 1 -> if (name == "trak") {
                        val each = Track()
                        walk(body, end, 2, each)
                        found = soundOf(each)
                    }
                    track != null -> when (name) {
                        "mdia" -> if (depth == 2) walk(body, end, 3, track)
                        "hdlr" -> if (depth == 3 && whole && body + 12 <= end) track.handler = type(body + 8)
                        "minf" -> if (depth == 3) walk(body, end, 4, track)
                        "stbl" -> if (depth == 4) walk(body, end, 5, track)
                        "stsd" -> if (depth == 5 && whole) sampleEntry(track, body, end)
                    }
                }
                // Nothing after a box the bytes hold only in part is in them.
                if (!whole) return
                at = end
            }
        }

        walk(0, bytes.size, 0, null)
        return found
    }
}
