package com.alal.yft.core.browser.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageProbeBudgetTest {
    @Test
    fun deduplicatesSignedVariantsAndEnforcesPageBudget() {
        val page = "https://example.test/watch"
        val budget = PageProbeBudget(maxUniqueUrls = 2)
        budget.beginPage(page)

        assertTrue(budget.tryAcquire(page, "https://cdn.test/stream?quality=720&token=one"))
        assertFalse(budget.tryAcquire(page, "https://cdn.test/stream?token=two&quality=720"))
        assertTrue(budget.tryAcquire(page, "https://cdn.test/other?quality=1080"))
        assertFalse(budget.tryAcquire(page, "https://cdn.test/third"))
    }

    @Test
    fun navigationResetsBudgetAndRejectsStalePageRequests() {
        val first = "https://example.test/one"
        val second = "https://example.test/two"
        val budget = PageProbeBudget(maxUniqueUrls = 1)
        budget.beginPage(first)
        assertTrue(budget.tryAcquire(first, "https://cdn.test/one"))

        budget.beginPage(second)

        assertFalse(budget.tryAcquire(first, "https://cdn.test/stale"))
        assertTrue(budget.tryAcquire(second, "https://cdn.test/new"))
    }
}