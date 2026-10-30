package com.alal.yft.feature.quickdownload

import com.google.myanmartools.TransliterateZ2U
import com.google.myanmartools.ZawgyiDetector
import java.util.Locale
import kotlin.math.floor

/**
 * v1.0.2 item 25: a video title written in Zawgyi ([original]) and its Unicode form ([unicode]),
 * with the detector's [probability] that [original] is Zawgyi.
 */
data class ZawgyiTitle(
    val original: String,
    val unicode: String,
    val probability: Double,
) {
    /** The sheet's Lookup details line: "Title converted from Zawgyi (p=0.9999)". */
    val detailLine: String
        get() = "Title converted from Zawgyi (p=${ZawgyiTitles.probabilityText(probability)})"
}

/**
 * v1.0.2 item 25: a video title written in Zawgyi becomes Unicode, only when the detector is sure.
 *
 * A title is converted only when all of these hold: it has at least [MIN_MYANMAR_CODE_POINTS]
 * Myanmar code points (U+1000..U+109F), Google's `ZawgyiDetector` gives it a Zawgyi probability
 * of at least [MIN_PROBABILITY], and `TransliterateZ2U` changes it. Every other title stays
 * exactly as it is: a Unicode title never changes, and a short one (a mistyped Unicode word the
 * detector may misread) is kept by the length rule. Text outside the Myanmar block (Latin,
 * digits, emoji) is kept as it is. The detector's model is the library's own resource
 * (`com/google/myanmartools/zawgyiUnicodeModel.dat`), read once, and only for a title long
 * enough; the converter is made only for a title the detector is sure of. If either cannot be
 * made, no title is converted. A sheet asks again as its rows come, so the last answer is kept.
 */
object ZawgyiTitles {
    const val MIN_MYANMAR_CODE_POINTS = 10
    const val MIN_PROBABILITY = 0.999

    private val detector: ZawgyiDetector? by lazy {
        try {
            ZawgyiDetector()
        } catch (_: RuntimeException) {
            null
        }
    }

    private val converter: TransliterateZ2U? by lazy {
        try {
            TransliterateZ2U("Zawgyi to Unicode")
        } catch (_: RuntimeException) {
            null
        }
    }

    /** The last title asked about and its answer. */
    @Volatile
    private var last: Answer? = null

    /** [title] in Unicode, with the title it was; null when [title] stays exactly as it is. */
    fun unicode(title: String): ZawgyiTitle? {
        if (myanmarCodePoints(title) < MIN_MYANMAR_CODE_POINTS) return null
        last?.takeIf { it.title == title }?.let { return it.converted }
        return convert(title).also { last = Answer(title, it) }
    }

    private fun convert(title: String): ZawgyiTitle? {
        val probability = detector?.getZawgyiProbability(title) ?: return null
        // Also false for NaN.
        if (!(probability >= MIN_PROBABILITY)) return null
        val converted = converter?.convert(title)?.toString() ?: return null
        if (converted == title) return null
        return ZawgyiTitle(original = title, unicode = converted, probability = probability)
    }

    /** The detector's Zawgyi probability of [text] (negative infinity without Myanmar text). */
    internal fun probability(text: String): Double? = detector?.getZawgyiProbability(text)

    /** Myanmar code points: the block is in the BMP, so each is one UTF-16 char. */
    internal fun myanmarCodePoints(text: String): Int = text.count { it in '\u1000'..'\u109F' }

    /** [probability] with four decimals, never rounded up: 0.99996 is "0.9999", 1.0 "1.0000". */
    internal fun probabilityText(probability: Double): String =
        String.format(Locale.ROOT, "%.4f", floor(probability * PLACES) / PLACES)

    private const val PLACES = 10_000.0

    private class Answer(val title: String, val converted: ZawgyiTitle?)
}
