package com.alal.yft.detection

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.alal.yft.BuildConfig
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import java.util.Locale

/**
 * v1.0.2 item 26 (debug only): AV1 merges as a test switch, off by default. AV1 merges are off
 * ([AudioVideoMuxCompatibility.AV1_MP4_ENABLED], P4: the API 34 emulator's muxer failed AV1 + AAC),
 * so Facebook's AV1-only sizes and YouTube's AV1-only 2K/4K are not offered. A debug build
 * ([built]) has the census screen's "AV1 merges (test)" box; while it is on, Android 14 or newer
 * ([AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK]) merges AV1 + AAC into one MP4: the sheet keeps
 * those rows (with an AV1 decoder, [DeviceMergeSupport]) and the download's gates let them through
 * (`DownloadPlanFactory`, the merge engine). The release and both previews have no switch
 * ([built] false: the preferences are never read), so AV1 stays off there until the owner's phone
 * proves it, as its own later commit.
 */
internal object Av1MergeSwitch {
    /** The app preferences that hold the switch. */
    const val PREFERENCES = "yft_av1_merges"

    /** The switch's key in [PREFERENCES]. */
    const val KEY = "av1_merges_test"

    /** Whether this build has the switch: a debug build only, never the release or a preview. */
    val built: Boolean get() = BuildConfig.AV1_MERGE_SWITCH

    @Volatile
    private var installed: SharedPreferences? = null

    /** Whether the switch is on: off by default, and always off in a build without it. */
    fun on(context: Context, built: Boolean = this.built): Boolean =
        built && preferences(context).getBoolean(KEY, false)

    /** Sets the switch (the census screen's box). */
    fun set(context: Context, on: Boolean) {
        preferences(context).edit().putBoolean(KEY, on).apply()
    }

    /**
     * Keeps the app's preferences for [merges], read at each sheet and download (the app's
     * start); a build without the switch keeps none, so [merges] is the release's constant.
     */
    fun install(context: Context, built: Boolean = this.built) {
        installed = if (built) preferences(context) else null
    }

    /** Whether AV1 merges are on now: the release's constant, else (debug) the switch. */
    fun merges(): Boolean =
        AudioVideoMuxCompatibility.AV1_MP4_ENABLED || installed?.getBoolean(KEY, false) == true

    /** A download's Details line about the phone's AV1 decoders, only while AV1 merges are on. */
    fun detailLine(): String? = if (merges()) Av1Decoders.line else null

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/**
 * v1.0.2 item 26: whether the phone has an AV1 decoder, from its decoder list (MediaCodecList),
 * hardware or software, for one Details line: "AV1 decoder: hardware (c2.qti.av1.decoder)",
 * "AV1 decoder: software (c2.android.av1.decoder)", both, or "AV1 decoder: none".
 */
internal object Av1Decoders {
    const val MIME_TYPE = "video/av01"
    const val LABEL = "AV1 decoder:"

    /** One AV1 decoder: its name and whether it runs on the phone's hardware. */
    data class Decoder(val name: String, val hardware: Boolean)

    /** The phone's line, read once (the decoder list does not change while the app runs). */
    val line: String by lazy { line(installed()) }

    /** The line for [decoders]: the first hardware one, then the first software one; or none. */
    fun line(decoders: List<Decoder>): String {
        val hardware = decoders.firstOrNull { it.hardware }
        val software = decoders.firstOrNull { !it.hardware }
        val kinds = listOfNotNull(
            hardware?.let { "hardware (${it.name})" },
            software?.let { "software (${it.name})" },
        )
        return "$LABEL ${kinds.joinToString(", ").ifEmpty { "none" }}"
    }

    /** "hardware", "software", "hardware/software" or "none": the kinds alone, for a log line. */
    fun kinds(decoders: List<Decoder>): String = listOfNotNull(
        "hardware".takeIf { decoders.any { it.hardware } },
        "software".takeIf { decoders.any { !it.hardware } },
    ).joinToString("/").ifEmpty { "none" }

    /** The phone's AV1 decoders; a failing codec list counts as none. */
    fun installed(): List<Decoder> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MIME_TYPE, ignoreCase = true) }
            }
            .map { info -> Decoder(info.name, hardware(info)) }
    }.getOrDefault(emptyList())

    /**
     * Android 10's own answer where the decoder states one; else (older phones, or a decoder that
     * states neither) by name: Android's own software decoders are `c2.android.*`/`OMX.google.*`.
     */
    private fun hardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (info.isHardwareAccelerated) return true
            if (info.isSoftwareOnly) return false
        }
        return !isSoftwareName(info.name)
    }

    /** Whether [name] is one of Android's own software decoders (`c2.android.*`, `OMX.google.*`). */
    fun isSoftwareName(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return SOFTWARE_PREFIXES.any(lower::startsWith)
    }

    private val SOFTWARE_PREFIXES = listOf("c2.android.", "omx.google.", "c2.google.")
}
