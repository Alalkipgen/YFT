package com.alal.yft.update

/**
 * A release's version, read from its tag: `v1.0.1` and `1.0.1` both read as 1.0.1 (the
 * workflow's tags have the `v`; one made by hand may not). Stable versions have the versionCode
 * rule's code, X·1000000 + Y·10000 + Z·100 with Y and Z at most 99 (v1.0.0 = 1000000); a
 * prerelease has none — its code sits below its version's by convention, which YFT cannot
 * compute from the tag.
 */
data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
) : Comparable<ReleaseVersion> {
    init {
        require(major in 0..MAX_MAJOR && minor in 0..MAX_PART && patch in 0..MAX_PART)
        require(prerelease == null || PRERELEASE.matches(prerelease))
    }

    /** "1.0.1" or "1.0.1-beta.1": the versionName and the APK's name have no `v`. */
    val name: String
        get() = "$major.$minor.$patch" + prerelease?.let { "-$it" }.orEmpty()

    /** "v1.0.1": what the screen shows. */
    val label: String
        get() = "v$name"

    /** The release asset the workflow uploads: `video-downloader-1.0.1.apk`. */
    val apkName: String
        get() = "video-downloader-$name.apk"

    val isStable: Boolean
        get() = prerelease == null

    /** The rule's versionCode for a stable version; null for a prerelease. */
    val ruleCode: Long?
        get() = if (prerelease == null) baseCode else null

    /** The rule's code of this version without its prerelease part (1.0.0-rc.1 → 1000000). */
    val baseCode: Long
        get() = major * MAJOR_STEP + minor * MINOR_STEP + patch * PATCH_STEP

    /** Semantic-version order: 1.0.0-beta.2 < 1.0.0-beta.10 < 1.0.0-rc.1 < 1.0.0 < 1.0.1. */
    override fun compareTo(other: ReleaseVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        val mine = prerelease ?: return if (other.prerelease == null) 0 else 1
        val theirs = other.prerelease ?: return -1
        val left = mine.split('.')
        val right = theirs.split('.')
        for (index in 0 until minOf(left.size, right.size)) {
            val order = compareIdentifiers(left[index], right[index])
            if (order != 0) return order
        }
        return compareValues(left.size, right.size)
    }

    override fun toString(): String = label

    companion object {
        const val MAX_MAJOR = 999
        const val MAX_PART = 99
        const val MAX_TAG_LENGTH = 64
        private const val MAJOR_STEP = 1_000_000L
        private const val MINOR_STEP = 10_000L
        private const val PATCH_STEP = 100L
        private val TAG = Regex(
            """v?(0|[1-9]\d{0,2})\.(0|[1-9]\d?)\.(0|[1-9]\d?)""" +
                """(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?""",
        )
        private val PRERELEASE = Regex("""[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*""")
        private val NUMERIC = Regex("""\d+""")

        /** The version of a tag or versionName, or null when it does not follow the scheme. */
        fun parse(tag: String): ReleaseVersion? {
            if (tag.length > MAX_TAG_LENGTH) return null
            val match = TAG.matchEntire(tag) ?: return null
            val (major, minor, patch, prerelease) = match.destructured
            return ReleaseVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                prerelease = prerelease.ifEmpty { null },
            )
        }

        private fun compareIdentifiers(left: String, right: String): Int {
            val leftNumeric = NUMERIC.matches(left)
            val rightNumeric = NUMERIC.matches(right)
            return when {
                leftNumeric && rightNumeric ->
                    compareValues(left.trimStart('0').length, right.trimStart('0').length)
                        .takeIf { it != 0 }
                        ?: left.trimStart('0').compareTo(right.trimStart('0'))
                leftNumeric -> -1
                rightNumeric -> 1
                else -> left.compareTo(right)
            }
        }
    }
}
