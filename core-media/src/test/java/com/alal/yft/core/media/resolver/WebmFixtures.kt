package com.alal.yft.core.media.resolver

/** Builds small but valid WebM (Matroska) element trees for the header parser and resolver tests. */
internal object WebmFixtures {
    fun file(
        width: Int = 1920,
        height: Int = 1080,
        video: Boolean = true,
        audio: Boolean = true,
        videoCodec: String = "V_VP9",
        audioCodec: String = "A_OPUS",
        framesPerSecond: Int = 30,
        durationMillis: Long = 61_000,
        docType: String = "webm",
    ): ByteArray {
        val ebml = element(0x1A45DFA3, text(0x4282, docType))
        val info = element(
            0x1549A966,
            uint(0x2AD7B1, 1_000_000L) + float(0x4489, durationMillis.toDouble()),
        )
        val tracks = element(
            0x1654AE6B,
            buildList {
                if (video) {
                    add(
                        track(
                            number = 1,
                            type = 1,
                            codecId = videoCodec,
                            defaultDurationNs = 1_000_000_000L / framesPerSecond,
                            video = element(0xE0, uint(0xB0, width.toLong()) + uint(0xBA, height.toLong())),
                        ),
                    )
                }
                if (audio) {
                    add(
                        track(
                            number = 2,
                            type = 2,
                            codecId = audioCodec,
                            defaultDurationNs = null,
                            video = element(0xE1, uint(0x9F, 2) + uint(0xB5, 48_000)),
                        ),
                    )
                }
            }.fold(ByteArray(0)) { all, entry -> all + entry },
        )
        return ebml + element(0x18538067, info + tracks)
    }

    private fun track(
        number: Int,
        type: Int,
        codecId: String,
        defaultDurationNs: Long?,
        video: ByteArray,
    ): ByteArray = element(
        0xAE,
        uint(0xD7, number.toLong()) +
            uint(0x83, type.toLong()) +
            text(0x86, codecId) +
            (defaultDurationNs?.let { uint(0x23E383, it) } ?: ByteArray(0)) +
            video,
    )

    private fun element(id: Int, body: ByteArray): ByteArray = id(id) + size(body.size) + body

    private fun id(value: Int): ByteArray {
        var length = 1
        while (length < 4 && value ushr (8 * length) != 0) length += 1
        return ByteArray(length) { index -> (value ushr (8 * (length - 1 - index))).toByte() }
    }

    private fun size(value: Int): ByteArray {
        var length = 1
        while (length < 8 && value >= (1 shl (7 * length)) - 1) length += 1
        val bytes = ByteArray(length)
        var remaining = value
        for (index in length - 1 downTo 0) {
            bytes[index] = remaining.toByte()
            remaining = remaining ushr 8
        }
        bytes[0] = (bytes[0].toInt() or (0x80 ushr (length - 1))).toByte()
        return bytes
    }

    private fun uint(id: Int, value: Long): ByteArray = element(id, minimal(value))

    private fun text(id: Int, value: String): ByteArray = element(id, value.toByteArray())

    private fun float(id: Int, value: Double): ByteArray =
        element(id, ByteArray(8) { index -> (value.toRawBits() ushr (8 * (7 - index))).toByte() })

    private fun minimal(value: Long): ByteArray {
        var length = 1
        while (length < 8 && value ushr (8 * length) != 0L) length += 1
        return ByteArray(length) { index -> (value ushr (8 * (length - 1 - index))).toByte() }
    }
}
