package com.alal.yft.extractor.master.social

/**
 * Signed recipe config (signed, data only): a
 * ladder rung the config in use turns off (`rungsOff`: the names of
 * [com.alal.yft.extractor.master.recipes.RemoteRecipeConfig.RUNGS]) is not run. Its line says so
 * ([line]: `<the rung's label>: off (config); not run`) and the ladder goes on as when the rung
 * gives nothing. The Social ladders' capture rungs — F3, T3, X3, I3 and VK's V3 (`vk V3` in Lookup details), the visible tab's own playback — have
 * their own labels ([CAPTURES]); with one off the engine asks no capture, and the lookup's
 * answer is the ladder's lines only ([captureOff]). A build without the config's key: nothing is
 * ever off, as before.
 */
object RungsOff {
    /** The end of an off rung's line. */
    const val SUFFIX = ": off (config); not run"

    /** The capture rungs' labels, by site (their lines only when the config turns them off). */
    internal val CAPTURES = mapOf(
        FacebookLadder.SITE_ID to FacebookLadder.F3,
        TikTokLadder.SITE_ID to TikTokLadder.T3,
        XLadder.SITE_ID to XLadder.X3,
        InstagramLadder.SITE_ID to InstagramLadder.I3,
        VkLadder.SITE_ID to VkLadder.V3,
    )

    /** The line of the rung [label] names when the config turns it off. */
    internal fun line(label: String): String = "$label$SUFFIX"

    /** Whether [details] hold a capture rung's off line: the tab's own playback was not asked. */
    fun captureOff(details: List<String>): Boolean {
        val lines = CAPTURES.values.map(::line).toSet()
        return details.any { it.trim() in lines }
    }
}
