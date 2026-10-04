package com.alal.yft.extractor.sites.facebook

/**
 * The identity of the adapter's own page request.
 *
 * Facebook answers a phone identity with its mobile page: the video sits in HTML attributes with
 * an inline DASH manifest, and a share link resolves only through a script, so this adapter found
 * no delivery fields and reported a changed page format (owner's phone, P2). A desktop identity
 * gets the page whose JSON payloads carry the delivery fields, and share links redirect to the
 * reel (sandbox live check, 2026-10-04). Only the page request changes: media requests keep the
 * browser's own identity.
 */
internal object FacebookPageIdentity {
    private val MOBILE_MARK = Regex("""\b(Android|Mobile|iPhone|iPad)\b""")
    private val CHROME_VERSION = Regex("""Chrome/(\d+(?:\.\d+)*)""")
    private const val DEFAULT_CHROME_VERSION = "130.0.0.0"

    /** Desktop Chrome with the browser's engine version for a phone identity, else unchanged. */
    fun forPageRequest(userAgent: String?): String? {
        if (userAgent == null || !MOBILE_MARK.containsMatchIn(userAgent)) return userAgent
        val version = CHROME_VERSION.find(userAgent)?.groupValues?.get(1)
            ?: DEFAULT_CHROME_VERSION
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$version Safari/537.36"
    }
}
