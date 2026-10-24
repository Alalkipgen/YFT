package com.alal.yft.update

import java.util.Locale

/**
 * A release's `SHA256SUMS` asset (§4.4 step 3): `sha256sum`'s lines, `<hex>  <name>` or
 * `<hex> *<name>`, CRLF or LF, either hex case. Only the line for exactly the APK's name counts;
 * two lines for it that disagree mean no checksum at all.
 */
internal object Sha256Sums {
    /** The file is a few lines; anything longer is not a checksum list. */
    const val MAX_BYTES = 16 * 1024L

    /** The APK's SHA-256 in uppercase hex, or null when the list has no single answer for it. */
    fun find(text: String, fileName: String): String? {
        var found: String? = null
        for (raw in text.split('\n')) {
            val match = LINE.matchEntire(raw.removeSuffix("\r")) ?: continue
            if (match.groupValues[2] != fileName) continue
            val hash = match.groupValues[1].uppercase(Locale.ROOT)
            if (found != null && found != hash) return null
            found = hash
        }
        return found
    }

    private val LINE = Regex("""([0-9A-Fa-f]{64})(?: {2}| \*)(\S.*)""")
}
