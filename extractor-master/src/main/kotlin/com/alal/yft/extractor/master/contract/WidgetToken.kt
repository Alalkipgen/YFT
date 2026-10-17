/*
 * Provenance (Master R8, copied, not moved; main 34a41890):
 *   extractor-sites/.../x/XSyndication.kt  token, radixString, carry, DIGITS, HALF,
 *                                          MANTISSA_BITS, ZEROS_OR_POINT
 * Adapted: the same arithmetic, as the `{token}` of a contract endpoint.
 */
package com.alal.yft.extractor.master.contract

import kotlin.math.floor
import kotlin.math.max

/**
 * R8: X's embed-widget token for one post (`{token}` in X's contract endpoint). The widget's own
 * code computes it from the post ID, so the request is the widget's; nothing is signed or stored.
 */
internal object WidgetToken {
    /**
     * The embed widget's token: `((Number(id) / 1e15) * Math.PI).toString(36)` without its zeros
     * and point. [radixString] is JavaScript's own number-to-text in base 36, so the token is the
     * same text the widget sends.
     */
    fun token(postId: String): String {
        val value = (postId.toDouble() / 1e15) * Math.PI
        return radixString(value, 36).replace(ZEROS_OR_POINT, "")
    }

    /**
     * JavaScript's `Number.prototype.toString(radix)` for a finite double: the shortest digits
     * that read back as [value], rounded half to even, as V8's `DoubleToRadixCString` writes them.
     */
    fun radixString(value: Double, radix: Int): String {
        require(value.isFinite()) { "Only finite numbers have digits" }
        require(radix in 2..36)
        val negative = value < 0
        val magnitude = if (negative) -value else value
        var integer = floor(magnitude)
        var fraction = magnitude - integer
        // Fraction digits only as far as the double itself is precise.
        var delta = max(Double.MIN_VALUE, 0.5 * (Math.nextUp(magnitude) - magnitude))
        val fractionDigits = StringBuilder()
        if (fraction >= delta) {
            do {
                fraction *= radix
                delta *= radix
                val digit = fraction.toInt()
                fractionDigits.append(DIGITS[digit])
                fraction -= digit
                val roundsUp = fraction > HALF || (fraction == HALF && digit and 1 == 1)
                if (roundsUp && fraction + delta > 1) {
                    integer = carry(fractionDigits, integer, radix)
                    break
                }
            } while (fraction >= delta)
        }
        val integerDigits = StringBuilder()
        // Digits below the double's precision are written as zeros.
        while (Math.getExponent(integer / radix) - MANTISSA_BITS > 0) {
            integer /= radix
            integerDigits.append('0')
        }
        do {
            val remainder = integer % radix
            integerDigits.append(DIGITS[remainder.toInt()])
            integer = (integer - remainder) / radix
        } while (integer > 0)
        return buildString {
            if (negative) append('-')
            append(integerDigits.reverse())
            if (fractionDigits.isNotEmpty()) append('.').append(fractionDigits)
        }
    }

    /** Rounds the written fraction up by one last digit; returns the integer part after it. */
    private fun carry(digits: StringBuilder, integer: Double, radix: Int): Double {
        while (digits.isNotEmpty()) {
            val last = DIGITS.indexOf(digits.last())
            digits.setLength(digits.length - 1)
            if (last + 1 < radix) {
                digits.append(DIGITS[last + 1])
                return integer
            }
        }
        return integer + 1
    }

    private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val HALF = 0.5
    private const val MANTISSA_BITS = 52
    private val ZEROS_OR_POINT = Regex("0+|\\.")
}
