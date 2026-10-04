package com.alal.yft.detection

/** Hand-written JSON text for the offscreen host pages, so script text is escaped exactly once. */
internal object JsonText {
    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Appends [value] to [out] as a JSON string with every non-ASCII character escaped.
     *
     * Escaping by UTF-16 unit keeps the payload pure ASCII, so it survives any transport
     * encoding, and keeps even unpaired surrogates in a site's script byte-for-byte intact.
     */
    fun appendString(out: StringBuilder, value: String) {
        out.append('"')
        value.forEach { character ->
            when {
                character == '"' -> out.append("\\\"")
                character == '\\' -> out.append("\\\\")
                character == '\n' -> out.append("\\n")
                character == '\r' -> out.append("\\r")
                character == '\t' -> out.append("\\t")
                character.code < 0x20 || character.code > 0x7e -> {
                    val code = character.code
                    out.append("\\u")
                    out.append(HEX[code shr 12 and 0xf])
                    out.append(HEX[code shr 8 and 0xf])
                    out.append(HEX[code shr 4 and 0xf])
                    out.append(HEX[code and 0xf])
                }

                else -> out.append(character)
            }
        }
        out.append('"')
    }
}
