package com.alal.yft.extractor.master.android

/**
 * R5: the codec policy the document-start script `yft-master-codecs.js` enforces in the page's
 * player. The app builds it from the same rules as `DeviceMergeSupport`: AVC/AAC always,
 * VP9/Opus WebM merges from Android 10, AV1 only while AV1 merges are on (they are off). The
 * script only narrows answers, never touches DRM queries and is off on YouTube hosts.
 */
data class CodecSteering(val vp9: Boolean, val av1: Boolean = false) {
    /** [template] (the asset's text) with this policy in place of the default one. */
    fun script(template: String): String {
        val start = template.indexOf(POLICY_START)
        val end = template.indexOf(POLICY_END, start + 1)
        require(start >= 0 && end > start) { "codec policy marker missing" }
        return template.substring(0, start) + "{ vp9: $vp9, av1: $av1 }" +
            template.substring(end + POLICY_END.length)
    }

    companion object {
        const val ASSET = "yft-master-codecs.js"

        /** Every frame's origin; the script itself stays off on YouTube hosts. */
        val ORIGINS = setOf("*")

        private const val POLICY_START = "/*YFT_CODEC_POLICY*/"
        private const val POLICY_END = "/*END*/"
    }
}
