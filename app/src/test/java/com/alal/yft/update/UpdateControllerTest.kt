package com.alal.yft.update

import com.alal.yft.update.Updates.DAY
import com.alal.yft.update.Updates.HOUR
import com.alal.yft.update.Updates.START
import com.alal.yft.update.Updates.found
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateControllerTest {
    private val check = FakeReleaseCheck()
    private var now = START

    private fun TestScope.controller(
        environment: UpdateEnvironment = Updates.release(),
        store: InMemoryUpdateStore = InMemoryUpdateStore(),
    ) = UpdateController(
        environment = environment,
        checker = check,
        store = store,
        scope = backgroundScope,
        clock = { now },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private val UpdateController.row get() = state.value.settingsRow()

    @Test
    fun theRowSaysNotCheckedYetUntilAGoodAnswer() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        assertEquals(UpdatesRow("Not checked yet", isNew = false), controller.row)

        check.next = CheckResult.Offline
        controller.checkNow()
        assertEquals(UpdatesRow("Not checked yet", isNew = false), controller.row)
        check.next = CheckResult.Failed
        controller.checkNow()
        assertEquals(UpdatesRow("Not checked yet", isNew = false), controller.row)
        check.next = CheckResult.RateLimited(now + 60_000)
        controller.checkNow()
        assertEquals(UpdatesRow("Not checked yet", isNew = false), controller.row)

        now += HOUR
        check.next = CheckResult.UpToDate
        controller.checkNow()
        assertEquals(UpdatesRow("Up to date", isNew = false), controller.row)
        assertEquals(now, controller.state.value.lastGoodAt)

        check.next = CheckResult.Available(found())
        controller.checkNow()
        assertEquals(UpdatesRow("v1.0.1 available", isNew = true), controller.row)
    }

    @Test
    fun testModeMarksTheOfferAndAsksWithCodeZero() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(environment = Updates.test(code = 3))
        check.next = CheckResult.Available(found("v1.0.0-beta.1"))

        controller.checkNow()

        assertEquals(0L, check.lastInstalledCode)
        assertEquals(true, check.lastTestMode)
        assertEquals(UpdatesRow("v1.0.0-beta.1 available (test)", isNew = true), controller.row)
        assertFalse(controller.state.value.autoCheck)
    }

    @Test
    fun theReleaseAsksWithItsOwnCodeAndChecksAutomaticallyByDefault() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val controller = controller(environment = Updates.release(code = 1_000_000))

        assertTrue(controller.state.value.autoCheck)
        controller.onAppOpened()

        assertEquals(1, check.calls)
        assertEquals(1_000_000L, check.lastInstalledCode)
        assertEquals(false, check.lastTestMode)
    }

    @Test
    fun checkNowShowsCheckingThenItsLine() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        val answers = listOf(
            CheckResult.Offline to CheckLine.NoConnection,
            CheckResult.Failed to CheckLine.CouldNotCheck,
            CheckResult.RateLimited(now + 2 * HOUR) to CheckLine.Busy(now + 2 * HOUR),
        )

        answers.forEach { (answer, line) ->
            val gate = CompletableDeferred<CheckResult>()
            check.gate = gate
            now += 3 * HOUR
            controller.checkNow()
            assertTrue(controller.state.value.checking)
            assertNull(controller.state.value.line)
            gate.complete(answer)
            assertFalse(controller.state.value.checking)
            assertEquals(line, controller.state.value.line)
        }
    }

    @Test
    fun theTimesAndTheLinesReadAsThePlanSays() {
        val times = UpdateTimes(
            now = START + 9 * HOUR + 35 * 60_000,
            is24Hour = true,
            zone = java.util.TimeZone.getTimeZone("UTC"),
        )
        val twelve = times.copy(is24Hour = false)

        assertEquals("No connection.", CheckLine.NoConnection.text(times))
        assertEquals("Couldn't check. Try again later.", CheckLine.CouldNotCheck.text(times))
        assertEquals(
            "GitHub is busy. Try again after 22:10.",
            CheckLine.Busy(START + 10 * HOUR + 10 * 60_000).text(times),
        )
        assertEquals(
            "GitHub is busy. Try again after 10:10 PM.",
            CheckLine.Busy(START + 10 * HOUR + 10 * 60_000).text(twelve),
        )
        assertEquals("Last checked today, 21:35", times.lastChecked(times.now))
        assertEquals("Last checked yesterday, 21:35", times.lastChecked(times.now - DAY))
        assertEquals("Last checked 22 Oct, 21:35", times.lastChecked(times.now - 3 * DAY))
        assertEquals("Last checked 25 Oct 2025, 21:35", times.lastChecked(times.now - 365 * DAY))
        assertEquals("Last checked today, 9:35 PM", twelve.lastChecked(times.now))
    }

    @Test
    fun aGoodAnswerClearsAnEarlierProblemLine() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        check.next = CheckResult.Offline
        controller.checkNow()
        assertEquals(CheckLine.NoConnection, controller.state.value.line)

        check.next = CheckResult.UpToDate
        controller.checkNow()

        assertNull(controller.state.value.line)
    }

    @Test
    fun theDailyCheckLeavesNoProblemLine() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        check.next = CheckResult.Failed

        controller.onAppOpened()

        assertEquals(1, check.calls)
        assertNull(controller.state.value.line)
    }

    @Test
    fun oneCheckAtATime() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        val gate = CompletableDeferred<CheckResult>()
        check.gate = gate

        controller.onAppOpened()
        controller.checkNow()
        controller.checkNow()
        controller.onAppOpened()
        assertEquals(1, check.calls)

        // A tap during the daily check gets that check's answer and line.
        gate.complete(CheckResult.Offline)
        assertEquals(CheckLine.NoConnection, controller.state.value.line)
        assertFalse(controller.state.value.checking)
    }

    @Test
    fun theScreenOpensByItselfOncePerFoundVersion() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore()
        val controller = controller(store = store)
        check.next = CheckResult.Available(found("v1.0.1"))

        controller.onAppOpened()
        assertEquals("1.0.1", controller.state.value.autoOpen?.version?.name)

        controller.markOpened("1.0.1")
        assertNull(controller.state.value.autoOpen)
        assertEquals("1.0.1", store.record.openedVersion)

        now += DAY
        controller.onAppOpened()
        assertNull(controller.state.value.autoOpen)

        // A newer version opens it again; the New badge stays meanwhile.
        check.next = CheckResult.Available(found("v1.0.2"))
        now += DAY
        controller.onAppOpened()
        assertEquals("1.0.2", controller.state.value.autoOpen?.version?.name)
        assertEquals(UpdatesRow("v1.0.2 available", isNew = true), controller.row)
    }

    @Test
    fun aVersionFoundOnCheckNowCountsAsOpened() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore()
        val controller = controller(store = store)
        check.next = CheckResult.Available(found("v1.0.1"))

        controller.checkNow()

        assertNull(controller.state.value.autoOpen)
        assertEquals("1.0.1", store.record.openedVersion)
        assertEquals(UpdatesRow("v1.0.1 available", isNew = true), controller.row)
    }

    @Test
    fun deferredUntilTheNextOpen() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        check.next = CheckResult.Available(found("v1.0.1"))
        controller.onAppOpened()

        controller.deferAutoOpen()
        assertNull(controller.state.value.autoOpen)

        controller.onAppOpened()
        assertEquals("1.0.1", controller.state.value.autoOpen?.version?.name)
        assertEquals(1, check.calls)
    }

    @Test
    fun withTheSwitchOffTheScreenNeverOpensByItself() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore(UpdateRecord(found = found("v1.0.1")))
        val controller = controller(store = store)
        assertEquals("1.0.1", controller.state.value.autoOpen?.version?.name)

        controller.setAutoCheck(false)

        assertNull(controller.state.value.autoOpen)
        assertEquals(false, store.record.autoCheck)
        assertEquals(UpdatesRow("v1.0.1 available", isNew = true), controller.row)
    }

    @Test
    fun theFoundUpdateIsKeptAndShownWithoutARequest() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore(
            UpdateRecord(lastAnswerAt = now, lastGoodAt = now, found = found("v1.0.1")),
        )
        val controller = controller(environment = Updates.release(code = 1_000_000), store = store)

        controller.onAppOpened()

        assertEquals(0, check.calls)
        assertEquals("1.0.1", controller.state.value.found?.version?.name)
    }

    @Test
    fun theFoundUpdateIsDroppedOnceInstalled() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore(
            UpdateRecord(lastAnswerAt = now, lastGoodAt = now, found = found("v1.0.1")),
        )
        val controller = controller(environment = Updates.release(code = 1_000_100), store = store)

        assertNull(controller.state.value.found)
        assertNull(store.record.found)
        assertEquals(UpdatesRow("Up to date", isNew = false), controller.row)
    }

    @Test
    fun anUpToDateAnswerDropsTheFoundUpdate() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore(UpdateRecord(found = found("v1.0.1")))
        val controller = controller(store = store)
        check.next = CheckResult.UpToDate

        controller.checkNow()

        assertNull(store.record.found)
        assertEquals(UpdatesRow("Up to date", isNew = false), controller.row)
    }

    @Test
    fun theReleaseDropsAStoredPrerelease() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore(UpdateRecord(found = found("v1.0.1-beta.1")))

        val controller = controller(environment = Updates.release(code = 1_000_000), store = store)

        assertNull(controller.state.value.found)
    }

    @Test
    fun aRateLimitFarAheadMeansTheClockWentBack() = runTest(UnconfinedTestDispatcher()) {
        val store = InMemoryUpdateStore(UpdateRecord(rateLimitUntil = now + 3 * DAY))
        val controller = controller(store = store)

        controller.checkNow()

        assertEquals(1, check.calls)
    }

    @Test
    fun theInstalledNameIsShown() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller(environment = Updates.test(name = "1.0.0-beta.3-debug"))

        assertEquals("1.0.0-beta.3-debug", controller.state.value.installedName)
        assertTrue(controller.state.value.testMode)
    }
}
