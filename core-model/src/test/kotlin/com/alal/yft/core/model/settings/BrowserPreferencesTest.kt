package com.alal.yft.core.model.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserPreferencesTest {
    @Test
    fun `Google is the search engine until the user picks another`() {
        assertEquals(SearchEngine.GOOGLE, BrowserPreferences().searchEngine)
        assertEquals(
            listOf("Google", "DuckDuckGo", "Bing"),
            SearchEngine.entries.map(SearchEngine::displayName),
        )
    }
}
