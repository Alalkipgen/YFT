package com.alal.yft.update

import com.alal.yft.update.Updates.found
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.0.2: Home's update card (`IN_APP_UPDATE_PLAN.md` §10) says what Settings' New says. */
class HomeUpdateCardTest {
    private fun state(mode: UpdateMode, found: FoundUpdate?, lastGoodAt: Long? = null) =
        UpdateState(
            mode = mode,
            installedName = "1.0.2",
            autoCheck = true,
            found = found,
            lastGoodAt = lastGoodAt,
        )

    @Test
    fun aFoundVersionGetsTheCardWithItsLabel() {
        assertEquals(
            HomeUpdateCard("Update available", "v1.0.3 · See what's new"),
            state(UpdateMode.RELEASE, found("v1.0.3")).homeCard(),
        )
    }

    @Test
    fun testModeMarksItsOfferAsTheScreenDoes() {
        assertEquals(
            HomeUpdateCard("Update available", "v1.0.3-rc.1 (test) · See what's new"),
            state(UpdateMode.TEST, found("v1.0.3-rc.1")).homeCard(),
        )
    }

    @Test
    fun noCardWithoutAFindOrInABuildThatNeverChecks() {
        assertNull(state(UpdateMode.RELEASE, found = null).homeCard())
        assertNull(state(UpdateMode.RELEASE, found = null, lastGoodAt = Updates.START).homeCard())
        assertNull(state(UpdateMode.TEST, found = null).homeCard())
        assertNull(state(UpdateMode.OFF, found("v1.0.3")).homeCard())
    }
}
