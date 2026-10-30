package com.alal.yft.core.model.settings

/** Which variant Preview selects first. The user can always pick another one. */
enum class QualityPreference(
    /** The tallest picture this preference prefers, or null for no ceiling. */
    val maxHeight: Int?,
) {
    HIGHEST(maxHeight = null),
    UP_TO_1080P(maxHeight = 1_080),
    UP_TO_720P(maxHeight = 720),
    UP_TO_480P(maxHeight = 480),
    LOWEST(maxHeight = 0),
}

/** Where finished downloads are saved. */
enum class DownloadLocation {
    /** The shared Downloads collection on Android 10 and later, app storage before that. */
    SHARED_DOWNLOADS,

    /** Storage only YFT can read; removed when the app is uninstalled. */
    APP_STORAGE,

    /**
     * v1.0.2 item 28: the folder picked through Android's folder picker
     * ([DownloadPreferences.folder]), on every Android version YFT supports. A folder that can no
     * longer be used gives way to [SHARED_DOWNLOADS] (app storage before Android 10).
     */
    CHOSEN_FOLDER,
}

/**
 * v1.0.2 item 28: a folder picked through Android's folder picker: its document tree
 * ([treeUri], a `content://` address YFT keeps read and write access to) and its [name].
 */
data class SaveFolder(
    val treeUri: String,
    val name: String,
) {
    override fun toString(): String = "SaveFolder(name=$name)"
}

data class DownloadPreferences(
    val defaultQuality: QualityPreference = QualityPreference.UP_TO_720P,
    val location: DownloadLocation = DownloadLocation.SHARED_DOWNLOADS,
    /** Transfers run only on unmetered networks such as Wi-Fi. */
    val unmeteredOnly: Boolean = false,
    val maxConcurrentDownloads: Int = DEFAULT_CONCURRENT_DOWNLOADS,
    /** Ask before starting a download on a metered network such as mobile data. */
    val confirmOnMeteredNetwork: Boolean = true,
    /**
     * v1.0.2 item 28: the folder picked last; [DownloadLocation.CHOSEN_FOLDER] saves there and the
     * Library lists it. Another location leaves it here, as YFT keeps its access to the folder.
     */
    val folder: SaveFolder? = null,
) {
    init {
        require(maxConcurrentDownloads in CONCURRENT_DOWNLOAD_RANGE)
    }

    companion object {
        const val DEFAULT_CONCURRENT_DOWNLOADS: Int = 2
        val CONCURRENT_DOWNLOAD_RANGE: IntRange = 1..4
    }
}

/**
 * Chooses the variant a quality preference points at.
 *
 * Variants without a known height are treated as unknown rather than as low quality: they are
 * used only when no variant states its height. Among variants of equal height the higher bitrate
 * wins. A ceiling that every variant exceeds falls back to the smallest one, so a preference
 * never leaves the user without a selection.
 */
fun <T> QualityPreference.pick(
    variants: List<T>,
    height: (T) -> Int?,
    bitrate: (T) -> Long?,
): T? {
    if (variants.isEmpty()) return null
    val sized = variants.filter { height(it) != null }
    if (sized.isEmpty()) return variants.first()
    val byQuality = compareBy<T>({ height(it) ?: 0 }, { bitrate(it) ?: 0L })
    return when (this) {
        QualityPreference.HIGHEST -> sized.maxWithOrNull(byQuality)
        QualityPreference.LOWEST -> sized.minWithOrNull(byQuality)
        else -> {
            val ceiling = checkNotNull(maxHeight)
            sized.filter { (height(it) ?: 0) <= ceiling }.maxWithOrNull(byQuality)
                ?: sized.minWithOrNull(byQuality)
        }
    }
}
