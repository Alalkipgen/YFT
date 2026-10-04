package com.alal.yft.feature.about

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceNoticesTest {
    @Test
    fun bundledNoticesMatchTheThirdPartyNoticesDocument() {
        val document = listOf("../docs/THIRD_PARTY_NOTICES.md", "docs/THIRD_PARTY_NOTICES.md")
            .map(::File)
            .first(File::isFile)
            .readText()

        OpenSourceNotices.bundled.forEach { notice ->
            val row = document.lines().firstOrNull { line ->
                line.startsWith("| ${notice.name}") && "| ${notice.version} |" in line
            }
            assertTrue("${notice.name} ${notice.version} missing from the document", row != null)
            assertTrue("${notice.name} license differs", "| ${notice.license}" in row!!)
        }
    }

    @Test
    fun noticesHaveUniqueIdsAndCarryTheirLicenseText() {
        val notices = OpenSourceNotices.all

        assertEquals(notices.size, notices.map(OpenSourceNotice::id).toSet().size)
        notices.forEach { notice ->
            assertTrue(notice.id, notice.noticeText.isNotBlank())
        }
        val meriyah = notices.single { it.id == "meriyah" }
        assertTrue("Copyright (c) 2019 and later, KFlash and others." in meriyah.noticeText)
        val astring = notices.single { it.id == "astring" }
        assertTrue("Copyright (c) 2015, David Bonnet" in astring.noticeText)
        val font = notices.single { it.id == "plus-jakarta-sans" }
        assertTrue("Copyright 2020 The Plus Jakarta Sans Project Authors" in font.noticeText)
        assertTrue("SIL OPEN FONT LICENSE Version 1.1" in font.noticeText)
        assertTrue("Apache License" in notices.single { it.id == "material-symbols" }.noticeText)
        val icons = notices.single { it.id == "simple-icons" }.noticeText
        assertTrue("CC0 1.0" in icons)
        assertTrue("YFT is not affiliated with them." in icons)
    }
}
