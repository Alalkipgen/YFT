package com.alal.yft.update

import android.content.SharedPreferences

/** What the updater keeps between app opens (§4.3). Times are epoch millis. */
data class UpdateRecord(
    /** Check automatically; null = the build's default (on in the release, off in test mode). */
    val autoCheck: Boolean? = null,
    /** GitHub's last answer of any kind: the 24 h gate. */
    val lastAnswerAt: Long? = null,
    /** The last good answer (up to date, or an update found): "Last checked". */
    val lastGoodAt: Long? = null,
    /** The update found last, shown again without a request until it is installed. */
    val found: FoundUpdate? = null,
    /** No request before this time (GitHub's rate limit). */
    val rateLimitUntil: Long? = null,
    /** The last version the Updates screen opened for ("1.0.1"); it opens once per version. */
    val openedVersion: String? = null,
)

interface UpdateStore {
    fun read(): UpdateRecord

    fun write(record: UpdateRecord)
}

/**
 * The record in the app's private preferences file [FILE] (keys `update_*`), apart from the
 * settings DataStore so core-data stays as it is; like every app file it is never backed up
 * (`allowBackup` false, `data_extraction_rules.xml`).
 * A found update that no longer reads (another tag or a link off GitHub) is dropped.
 */
class PreferencesUpdateStore(private val preferences: SharedPreferences) : UpdateStore {
    override fun read(): UpdateRecord = UpdateRecord(
        autoCheck = if (preferences.contains(AUTO_CHECK)) bool(AUTO_CHECK) else null,
        lastAnswerAt = long(LAST_ANSWER_AT),
        lastGoodAt = long(LAST_GOOD_AT),
        found = readFound(),
        rateLimitUntil = long(RATE_LIMIT_UNTIL),
        openedVersion = string(OPENED_VERSION),
    )

    override fun write(record: UpdateRecord) {
        val editor = preferences.edit()
        editor.putOrRemove(AUTO_CHECK, record.autoCheck)
        editor.putOrRemove(LAST_ANSWER_AT, record.lastAnswerAt)
        editor.putOrRemove(LAST_GOOD_AT, record.lastGoodAt)
        editor.putOrRemove(RATE_LIMIT_UNTIL, record.rateLimitUntil)
        editor.putOrRemove(OPENED_VERSION, record.openedVersion)
        val found = record.found
        editor.putOrRemove(FOUND_TAG, found?.version?.name)
        editor.putOrRemove(FOUND_PRERELEASE, found?.prerelease)
        editor.putOrRemove(FOUND_APK_URL, found?.apkUrl)
        editor.putOrRemove(FOUND_APK_SIZE, found?.apkSize)
        editor.putOrRemove(FOUND_SUMS_URL, found?.sumsUrl)
        editor.putOrRemove(FOUND_NOTES, found?.notes)
        editor.putOrRemove(FOUND_PUBLISHED_AT, found?.publishedAt)
        editor.apply()
    }

    private fun readFound(): FoundUpdate? {
        val version = string(FOUND_TAG)?.let(ReleaseVersion::parse) ?: return null
        val apkUrl = string(FOUND_APK_URL)?.takeIf(GitHubReleases::isReleaseDownload) ?: return null
        val apkSize = long(FOUND_APK_SIZE)?.takeIf { it > 0 } ?: return null
        return FoundUpdate(
            version = version,
            code = version.ruleCode,
            prerelease = bool(FOUND_PRERELEASE) || !version.isStable,
            apkUrl = apkUrl,
            apkSize = apkSize,
            sumsUrl = string(FOUND_SUMS_URL)?.takeIf(GitHubReleases::isReleaseDownload),
            notes = GitHubReleases.notes(string(FOUND_NOTES)),
            publishedAt = string(FOUND_PUBLISHED_AT),
        )
    }

    // A value of another type (an older or a damaged file) reads as missing.
    private fun bool(key: String): Boolean =
        runCatching { preferences.getBoolean(key, false) }.getOrDefault(false)

    private fun long(key: String): Long? = if (preferences.contains(key)) {
        runCatching { preferences.getLong(key, 0) }.getOrNull()
    } else {
        null
    }

    private fun string(key: String): String? =
        runCatching { preferences.getString(key, null) }.getOrNull()

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: Any?) {
        when (value) {
            null -> remove(key)
            is Boolean -> putBoolean(key, value)
            is Long -> putLong(key, value)
            is String -> putString(key, value)
            else -> error("Unsupported value for $key")
        }
    }

    companion object {
        const val FILE = "update"
        const val AUTO_CHECK = "update_auto_check"
        const val LAST_ANSWER_AT = "update_last_answer_at"
        const val LAST_GOOD_AT = "update_last_good_at"
        const val RATE_LIMIT_UNTIL = "update_rate_limit_until"
        const val OPENED_VERSION = "update_opened_version"
        const val FOUND_TAG = "update_found_version"
        const val FOUND_PRERELEASE = "update_found_prerelease"
        const val FOUND_APK_URL = "update_found_apk_url"
        const val FOUND_APK_SIZE = "update_found_apk_size"
        const val FOUND_SUMS_URL = "update_found_sums_url"
        const val FOUND_NOTES = "update_found_notes"
        const val FOUND_PUBLISHED_AT = "update_found_published_at"
    }
}
