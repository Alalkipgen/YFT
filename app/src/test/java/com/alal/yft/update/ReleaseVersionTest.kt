package com.alal.yft.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun stableTagsReadWithOrWithoutTheV() {
        assertEquals(1_000_000L, ReleaseVersion.parse("v1.0.0")?.ruleCode)
        assertEquals(1_000_000L, ReleaseVersion.parse("1.0.0")?.ruleCode)
        assertEquals(1_000_100L, ReleaseVersion.parse("v1.0.1")?.ruleCode)
        assertEquals(1_010_000L, ReleaseVersion.parse("v1.1.0")?.ruleCode)
        assertEquals(1_020_300L, ReleaseVersion.parse("v1.2.3")?.ruleCode)
        assertEquals(99_999_900L, ReleaseVersion.parse("v99.99.99")?.ruleCode)
        assertEquals(ReleaseVersion.parse("v1.0.1"), ReleaseVersion.parse("1.0.1"))
    }

    @Test
    fun theNameAndTheAssetHaveNoVAndTheLabelHasOne() {
        val version = checkNotNull(ReleaseVersion.parse("v1.0.1"))

        assertEquals("1.0.1", version.name)
        assertEquals("v1.0.1", version.label)
        assertEquals("video-downloader-1.0.1.apk", version.apkName)
        assertEquals("video-downloader-1.0.1.apk", ReleaseVersion.parse("1.0.1")?.apkName)
        assertEquals(
            "video-downloader-1.0.0-beta.1.apk",
            ReleaseVersion.parse("v1.0.0-beta.1")?.apkName,
        )
    }

    @Test
    fun badTagsDoNotRead() {
        listOf(
            "",
            "v",
            "vv1.0.0",
            "V1.0.0",
            "1.0",
            "1",
            "1.0.0.0",
            "1.100.0",
            "1.0.100",
            "1000.0.0",
            "01.0.0",
            "1.00.0",
            "1.0.00",
            "a.b.c",
            "1.0.0a",
            "v1.0.0 ",
            " v1.0.0",
            "1.0.0-",
            "1.0.0-beta..1",
            "1.0.0-beta_1",
            "1.0.0+build.1",
            "release-1.0.0",
            "v1.0.0-" + "x".repeat(60),
        ).forEach { tag -> assertNull(tag, ReleaseVersion.parse(tag)) }
    }

    @Test
    fun prereleasesHaveAVersionButNoRuleCode() {
        val beta = checkNotNull(ReleaseVersion.parse("v1.0.0-beta.1"))

        assertEquals(ReleaseVersion(1, 0, 0, "beta.1"), beta)
        assertEquals("1.0.0-beta.1", beta.name)
        assertFalse(beta.isStable)
        assertNull(beta.ruleCode)
        assertEquals(1_000_000L, beta.baseCode)
        assertTrue(checkNotNull(ReleaseVersion.parse("1.0.1")).isStable)
        assertEquals("rc.1", ReleaseVersion.parse("1.0.0-rc.1")?.prerelease)
    }

    @Test
    fun versionsCompareInSemanticVersionOrder() {
        val ordered = listOf(
            "0.9.9",
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.10",
            "1.0.0-rc.1",
            "1.0.0",
            "1.0.1-beta.1",
            "1.0.1",
            "1.1.0",
            "2.0.0",
        ).map { checkNotNull(ReleaseVersion.parse(it)) }

        assertEquals(ordered, ordered.shuffled(java.util.Random(7)).sorted())
        assertEquals(
            0,
            checkNotNull(ReleaseVersion.parse("v1.0.0")).compareTo(ReleaseVersion(1, 0, 0)),
        )
    }
}
