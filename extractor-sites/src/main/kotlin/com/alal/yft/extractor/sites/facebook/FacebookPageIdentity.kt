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

    /**
     * Desktop Safari, for the second page request that reads the AVC ladder (P4).
     *
     * Facebook picks a video's DASH ladder by browser: a Chromium identity gets AV1 and VP9
     * tracks only, which phones before Android 14 cannot merge into an MP4, while Safari gets
     * AVC tracks every phone merges (sandbox live check of a public reel, 2026-10-05: tagset
     * `r2av1-r1gen2vp9` against `basic_gen2`).
     */
    const val AVC_LADDER_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Safari/605.1.15"

    /** Desktop Chrome with the browser's engine version for a phone identity, else unchanged. */
    fun forPageRequest(userAgent: String?): String? {
        if (userAgent == null || !MOBILE_MARK.containsMatchIn(userAgent)) return userAgent
        val version = CHROME_VERSION.find(userAgent)?.groupValues?.get(1)
            ?: DEFAULT_CHROME_VERSION
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$version Safari/537.36"
    }
}
