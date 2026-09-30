package com.alal.yft.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun hasStablePersistenceNames() {
        assertEquals(listOf("SYSTEM", "LIGHT", "DARK"), ThemeMode.entries.map(ThemeMode::name))
    }
}
