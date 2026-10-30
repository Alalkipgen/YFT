package com.alal.yft.feature.quickdownload

import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.UNICODE_TITLE
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.ZAWGYI_TITLE
import com.google.myanmartools.TransliterateZ2U
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.2 item 25: a title written in Zawgyi becomes Unicode only when it has at least ten Myanmar
 * code points and Google's detector is sure (p ≥ 0.999); every other title stays exactly as it
 * is. The probabilities named here are the detector's, Myanmar Tools 1.2.0.
 */
class ZawgyiTitlesTest {
    @Test
    fun aZawgyiTitleBecomesItsUnicodeForm() {
        val title = ZawgyiTitles.unicode(ZAWGYI_TITLE)!!

        assertEquals(UNICODE_TITLE, title.unicode)
        assertEquals(
            "1019 103C 1014 103A 1019 102C 0020 101E 102E 1001 103B 1004 103A 1038 0020 " +
                "1021 101E 1005 103A 1019 103B 102C 1038",
            codePoints(title.unicode),
        )
        assertEquals(ZAWGYI_TITLE, title.original)
        assertEquals(1.0, title.probability, 0.0)
        assertEquals("Title converted from Zawgyi (p=1.0000)", title.detailLine)
    }

    @Test
    fun latinDigitsAndPunctuationAroundItStayAsTheyAre() {
        val title = ZawgyiTitles.unicode("$ZAWGYI_TITLE | Official MV 2024")!!

        assertEquals("$UNICODE_TITLE | Official MV 2024", title.unicode)
    }

    @Test
    fun askingAgainGivesTheSameAnswer() {
        // A sheet asks again as its rows come; the last answer is kept, one title at a time.
        val first = ZawgyiTitles.unicode(ZAWGYI_TITLE)

        assertEquals(first, ZawgyiTitles.unicode(ZAWGYI_TITLE))
        assertNull(ZawgyiTitles.unicode(UNICODE_TITLE))
        assertNull(ZawgyiTitles.unicode(UNICODE_TITLE))
        assertEquals(first, ZawgyiTitles.unicode(ZAWGYI_TITLE))
        assertEquals(UNICODE_TITLE, first?.unicode)
    }

    @Test
    fun aUnicodeTitleNeverChanges() {
        // The converter alone would change every one of them; the detector keeps them all: its
        // Zawgyi probability is at most 3.4e-6 (ဒါဟာအချစ်လား), as low as 4.2e-31.
        val converter = TransliterateZ2U("Zawgyi to Unicode")
        UNICODE_TITLES.forEach { title ->
            assertNotEquals(title, title, converter.convert(title).toString())
            assertTrue(title, ZawgyiTitles.myanmarCodePoints(title) >= 10)
            assertTrue(title, ZawgyiTitles.probability(title)!! < 0.00001)
            assertNull(title, ZawgyiTitles.unicode(title))
        }
    }

    @Test
    fun aTitleWithoutMyanmarLettersNeverChanges() {
        listOf(
            "Ocean waves",
            "สวัสดีครับ",
            "नमस्ते दुनिया",
            "I \u2764\uFE0F Yangon \uD83C\uDFB6",
            "",
            " ",
        ).forEach { title ->
            assertEquals(title, 0, ZawgyiTitles.myanmarCodePoints(title))
            assertNull(title, ZawgyiTitles.unicode(title))
        }
    }

    @Test
    fun fewerThanTenMyanmarCodePointsStayAsTheyAreEvenWhenTheDetectorSaysZawgyi() {
        // ျမန္မာ (p = 0.99996) and ေရႊျမန္မာ (p = 0.9999999) are Zawgyi the detector is sure
        // of, but too short to trust; ေကျးရွာ typed with ေ first (p = 0.504, the owner's example
        // of a word a detector misreads) and အချစ်ရူး (p = 6.4e-8) are Unicode.
        assertTrue(ZawgyiTitles.probability(ZAWGYI_SIX)!! >= ZawgyiTitles.MIN_PROBABILITY)
        assertTrue(ZawgyiTitles.probability(ZAWGYI_NINE)!! >= ZawgyiTitles.MIN_PROBABILITY)
        listOf(ZAWGYI_SIX to 6, ZAWGYI_NINE to 9, KYEI_YWA to 7, ACHIT_YU to 8)
            .forEach { (title, count) ->
                assertEquals(title, count, ZawgyiTitles.myanmarCodePoints(title))
                assertNull(title, ZawgyiTitles.unicode(title))
            }

        // Ten are enough: ေမာင္ေမာင္ (p = 1.0) is မောင်မောင်.
        assertEquals(10, ZawgyiTitles.myanmarCodePoints(ZAWGYI_TEN))
        assertEquals(MAUNG_MAUNG, ZawgyiTitles.unicode(ZAWGYI_TEN)?.unicode)
    }

    @Test
    fun theDetailLineShowsTheProbabilityWithFourDecimalsNeverRoundedUp() {
        assertEquals("1.0000", ZawgyiTitles.probabilityText(1.0))
        assertEquals("0.9999", ZawgyiTitles.probabilityText(0.99996))
        assertEquals("0.9990", ZawgyiTitles.probabilityText(0.99905))
        assertEquals(
            "Title converted from Zawgyi (p=0.9999)",
            ZawgyiTitle(original = "a", unicode = "b", probability = 0.99999).detailLine,
        )
    }

    private fun codePoints(text: String): String =
        text.map { it.code.toString(16).uppercase().padStart(4, '0') }.joinToString(" ")

    private companion object {
        /** ျမန္မာ ("Myanmar") in Zawgyi: 6 code points. */
        const val ZAWGYI_SIX = "\u103B\u1019\u1014\u1039\u1019\u102C"

        /** ေရႊျမန္မာ ("Golden Myanmar") in Zawgyi: 9 code points. */
        const val ZAWGYI_NINE = "\u1031\u101B\u108A\u103B\u1019\u1014\u1039\u1019\u102C"

        /** ေမာင္ေမာင္ ("Maung Maung") in Zawgyi: 10 code points. */
        const val ZAWGYI_TEN = "\u1031\u1019\u102C\u1004\u1039\u1031\u1019\u102C\u1004\u1039"

        /** [ZAWGYI_TEN] in Unicode: မောင်မောင် (p = 1.8e-19). */
        const val MAUNG_MAUNG = "\u1019\u1031\u102C\u1004\u103A\u1019\u1031\u102C\u1004\u103A"

        /** ကျေးရွာ ("village") typed with ေ first: 7 code points. */
        const val KYEI_YWA = "\u1031\u1000\u103B\u1038\u101B\u103D\u102C"

        /** အချစ်ရူး, one of the owner's Unicode titles (FileNamesTest): 8 code points. */
        const val ACHIT_YU = "\u1021\u1001\u103B\u1005\u103A\u101B\u1030\u1038"

        /**
         * Unicode titles with ten Myanmar code points or more: the owner's (FileNamesTest:
         * ထွဏ်းထွဏ်း p = 1.1e-11, ဒါဟာအချစ်လား 3.4e-6, ဇော်ပိုင် ချောစုခင် 4.2e-31),
         * [UNICODE_TITLE] (4.0e-19), မြန်မာပြည် (9.0e-14), ရွှေမြန်မာ (3.7e-8) and [MAUNG_MAUNG].
         */
        val UNICODE_TITLES = listOf(
            "\u1011\u103D\u100F\u103A\u1038\u1011\u103D\u100F\u103A\u1038",
            "\u1012\u102B\u101F\u102C\u1021\u1001\u103B\u1005\u103A\u101C\u102C\u1038",
            "\u1007\u1031\u102C\u103A\u1015\u102D\u102F\u1004\u103A " +
                "\u1001\u103B\u1031\u102C\u1005\u102F\u1001\u1004\u103A",
            UNICODE_TITLE,
            "\u1019\u103C\u1014\u103A\u1019\u102C\u1015\u103C\u100A\u103A",
            "\u101B\u103D\u103E\u1031\u1019\u103C\u1014\u103A\u1019\u102C",
            MAUNG_MAUNG,
        )
    }
}