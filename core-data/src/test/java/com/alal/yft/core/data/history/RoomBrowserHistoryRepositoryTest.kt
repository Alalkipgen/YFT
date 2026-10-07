package com.alal.yft.core.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomBrowserHistoryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var history: RoomBrowserHistoryRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        history = RoomBrowserHistoryRepository(database.browserHistoryDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aPageOpenedAgainIsOneEntryWithAHigherCountAndTheNewTime() = runTest {
        assertTrue(history.record("https://example.com/news#top", "News", NOW - 2 * HOUR))
        assertTrue(history.record("https://www.youtube.com/", "YouTube", NOW - HOUR))
        assertTrue(history.record("https://example.com/news?utm_source=x", "News today", NOW))

        val pages = history.newest(10).first()

        assertEquals(
            listOf(
                BrowserHistoryEntry(NEWS, "News today", "example.com", NOW, 2),
                BrowserHistoryEntry(YOUTUBE, "YouTube", "youtube.com", NOW - HOUR, 1),
            ),
            pages,
        )
    }

    @Test
    fun pagesTheHistoryDoesNotKeepAreNotSaved() = runTest {
        assertFalse(history.record("about:blank", "", NOW))
        assertFalse(history.record("http://example.com/", "Plain", NOW))
        assertFalse(history.record("data:text/html,hi", "Data", NOW))

        assertEquals(0, database.browserHistoryDao().count())
    }

    @Test
    fun aPageWithoutItsOwnTitleIsListedByItsHostUntilItNamesItself() = runTest {
        history.record("https://m.youtube.com/watch?v=abc", null, NOW - 10)
        assertEquals("m.youtube.com", history.newest(1).first().single().title)

        history.rename("https://m.youtube.com/watch?v=abc#t", "A song - YouTube")
        // WebView gives the address as the title of a page without one.
        history.record("https://m.youtube.com/watch?v=abc", "m.youtube.com/watch?v=abc", NOW)

        val page = history.newest(1).first().single()
        assertEquals("A song - YouTube", page.title)
        assertEquals(2, page.visitCount)
        assertEquals(NOW, page.lastVisitedAtEpochMs)
    }

    @Test
    fun searchFindsTitlesHostsAndAddressesWithTheWordsTakenLiterally() = runTest {
        history.record("https://example.com/a", "Cat videos", NOW - 3)
        history.record("https://news.example.org/b", "Morning news", NOW - 2)
        history.record("https://example.net/deal", "100%_sure deal", NOW - 1)
        history.record("https://example.net/tips", "1000 tips", NOW)

        assertEquals(listOf("Cat videos"), history.search("  cat ", 10).first().titles())
        assertEquals(listOf("Morning news"), history.search("EXAMPLE.ORG", 10).first().titles())
        // "%" and "_" are the user's words, not LIKE wildcards ("1000 tips" would match).
        assertEquals(listOf("100%_sure deal"), history.search("0%_", 10).first().titles())
        assertEquals(4, history.search("", 10).first().size)
        val newestTwo = listOf("1000 tips", "100%_sure deal")
        assertEquals(newestTwo, history.search("example.net", 2).first().titles())
    }

    @Test
    fun oneDeleteRemovesOnePageAndClearRemovesAll() = runTest {
        history.record("https://example.com/a", "A", NOW - 1)
        history.record("https://example.com/b", "B", NOW)

        history.delete("https://example.com/a")
        assertEquals(listOf("B"), history.newest(10).first().map { it.title })

        history.clear()
        assertEquals(emptyList<BrowserHistoryEntry>(), history.newest(10).first())
    }

    @Test
    fun ninetyDaysAndFiveThousandPagesAreKeptTheOldestGoFirst() = runTest {
        val writable = database.openHelper.writableDatabase
        writable.beginTransaction()
        try {
            // 5,000 pages, one a minute before NOW - 1 day; page 0 is the newest.
            repeat(RoomBrowserHistoryRepository.MAX_PAGES) { index ->
                writable.execSQL(
                    "INSERT INTO browser_history VALUES (?, ?, 'example.com', ?, 1)",
                    arrayOf("https://example.com/$index", "Page $index", NOW - DAY - index * MIN),
                )
            }
            writable.execSQL(
                "INSERT INTO browser_history VALUES " +
                    "('https://old.example/', 'Old', 'old.example', ?, 3)",
                arrayOf(NOW - 91 * DAY),
            )
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
        assertEquals(5_001, database.browserHistoryDao().count())

        history.record("https://example.com/new", "New", NOW)

        val dao = database.browserHistoryDao()
        assertEquals(5_000, dao.count())
        assertEquals(null, dao.find("https://old.example/"))
        assertEquals(null, dao.find("https://example.com/4999"))
        assertEquals("Page 4998", dao.find("https://example.com/4998")?.title)
        assertEquals("New", history.newest(1).first().single().title)
    }

    private fun List<BrowserHistoryEntry>.titles() = map { it.title }

    private companion object {
        const val MIN = 60_000L
        const val HOUR = 60 * MIN
        const val DAY = 24 * HOUR
        const val NOW = 1_790_000_000_000L
        const val NEWS = "https://example.com/news"
        const val YOUTUBE = "https://www.youtube.com/"
    }
}
