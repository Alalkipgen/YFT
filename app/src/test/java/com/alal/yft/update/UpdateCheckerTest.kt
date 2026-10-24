package com.alal.yft.update

import com.alal.yft.detection.ExtractorTlsFixture
import com.alal.yft.update.ReleaseFixtures.list
import com.alal.yft.update.ReleaseFixtures.release
import com.alal.yft.update.Updates.DAY
import com.alal.yft.update.Updates.HOUR
import com.alal.yft.update.Updates.START
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real checker against a local HTTPS server (a test-only certificate); no live network. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {
    private val tls = ExtractorTlsFixture()
    private val logs = mutableListOf<String>()
    private var now = START

    @After
    fun tearDown() = tls.close()

    private fun TestScope.checker(callTimeoutMillis: Long = 5_000) = UpdateChecker(
        client = tls.client,
        releasesUrl = tls.url("/repos/Alalkipgen/YFT/releases?per_page=10"),
        allowedHosts = setOf("localhost"),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        clock = { now },
        log = { logs += it },
        callTimeoutMillis = callTimeoutMillis,
    )

    private fun TestScope.controller(
        environment: UpdateEnvironment = Updates.release(),
        store: InMemoryUpdateStore = InMemoryUpdateStore(),
        checker: ReleaseCheck = checker(),
    ) = UpdateController(
        environment = environment,
        checker = checker,
        store = store,
        scope = backgroundScope,
        clock = { now },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun enqueue(vararg responses: MockResponse) = responses.forEach(tls.server::enqueue)

    @Test
    fun asksWithGitHubsHeadersOnlyAndNoCredentials() = runTest(UnconfinedTestDispatcher()) {
        enqueue(ok(list(release("v1.0.1"))))

        checker().check(installedCode = 1_000_000, testMode = false)

        val request = tls.server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/repos/Alalkipgen/YFT/releases?per_page=10", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertEquals(
            "https://api.github.com/repos/Alalkipgen/YFT/releases?per_page=10",
            UpdateChecker.RELEASES_URL,
        )
    }

    @Test
    fun aGoodAnswerIsAvailableOrUpToDate() = runTest(UnconfinedTestDispatcher()) {
        enqueue(ok(list(release("v1.0.1"), release("v1.0.0"))), ok(list(release("v1.0.0"))))

        val found = checker().check(installedCode = 1_000_000, testMode = false)
        val none = checker().check(installedCode = 1_000_000, testMode = false)

        assertEquals("1.0.1", (found as CheckResult.Available).update.version.name)
        assertEquals(CheckResult.UpToDate, none)
        assertEquals(listOf("Update check: localhost 200", "Update check: localhost 200"), logs)
    }

    @Test
    fun noServerAndADroppedConnectionAreOfflineAndNotCounted() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        repeat(2) { enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val store = InMemoryUpdateStore()
        val controller = controller(store = store)

        assertEquals(CheckResult.Offline, checker().check(1_000_000, false))
        controller.onAppOpened()
        assertEquals(2, tls.server.requestCount)
        tls.server.shutdown()
        assertEquals(CheckResult.Offline, checker().check(1_000_000, false))

        assertNull(store.record.lastAnswerAt)
        assertNull(controller.state.value.line)
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.all { it.startsWith("Update check offline: ") })
    }

    @Test
    fun aTimeoutIsOffline() = runTest(UnconfinedTestDispatcher()) {
        enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertEquals(CheckResult.Offline, checker(callTimeoutMillis = 300).check(1_000_000, false))
    }

    @Test
    fun rateLimitWithResetWaitsAndNotEvenCheckNowAsksBeforeIt() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val reset = (now + 2 * HOUR) / 1000
        enqueue(
            MockResponse().setResponseCode(403)
                .addHeader("x-ratelimit-remaining", "0")
                .addHeader("x-ratelimit-reset", reset.toString()),
            ok(list(release("v1.0.0"))),
        )
        val store = InMemoryUpdateStore()
        val controller = controller(store = store)

        controller.onAppOpened()
        assertEquals(reset * 1000, store.record.rateLimitUntil)
        assertEquals(START, store.record.lastAnswerAt)
        assertEquals(1, tls.server.requestCount)

        now += HOUR
        controller.checkNow()
        assertEquals(1, tls.server.requestCount)
        assertEquals(CheckLine.Busy(reset * 1000), controller.state.value.line)

        now += 2 * HOUR
        controller.checkNow()
        assertEquals(2, tls.server.requestCount)
        assertNull(store.record.rateLimitUntil)
        assertNull(controller.state.value.line)
    }

    @Test
    fun tooManyRequestsWithRetryAfter() = runTest(UnconfinedTestDispatcher()) {
        enqueue(MockResponse().setResponseCode(429).addHeader("retry-after", "120"))

        assertEquals(CheckResult.RateLimited(now + 120_000), checker().check(1_000_000, false))
    }

    @Test
    fun rateLimitsStayWithinADay() = runTest(UnconfinedTestDispatcher()) {
        enqueue(
            MockResponse().setResponseCode(403).addHeader("retry-after", "9999999999"),
            MockResponse().setResponseCode(403)
                .addHeader("x-ratelimit-remaining", "0")
                .addHeader("x-ratelimit-reset", ((now - HOUR) / 1000).toString()),
        )

        assertEquals(CheckResult.RateLimited(now + DAY), checker().check(1_000_000, false))
        assertEquals(CheckResult.RateLimited(now + 60_000), checker().check(1_000_000, false))
    }

    @Test
    fun forbiddenWithoutRateHeadersAndServerErrorsFail() = runTest(UnconfinedTestDispatcher()) {
        enqueue(
            MockResponse().setResponseCode(403),
            MockResponse().setResponseCode(403).addHeader("x-ratelimit-remaining", "12"),
            MockResponse().setResponseCode(500),
            MockResponse().setResponseCode(503),
            MockResponse().setResponseCode(404),
        )

        repeat(5) { assertEquals(CheckResult.Failed, checker().check(1_000_000, false)) }
    }

    @Test
    fun malformedOrOversizeAnswersFail() = runTest(UnconfinedTestDispatcher()) {
        val big = list(release("v1.0.1", body = "x".repeat(GitHubReleases.MAX_BYTES.toInt())))
        enqueue(ok("{"), ok("""{"message": "Moved"}"""), ok(big), ok(""))

        repeat(4) { assertEquals(CheckResult.Failed, checker().check(1_000_000, false)) }
    }

    @Test
    fun redirectsOnlyToAllowedHttpsHosts() = runTest(UnconfinedTestDispatcher()) {
        val elsewhere = tls.url("/moved").newBuilder().host("127.0.0.1").build()
        val plain = tls.url("/moved").newBuilder().scheme("http").build()
        enqueue(
            MockResponse().setResponseCode(301).addHeader("Location", tls.url("/moved")),
            ok(list(release("v1.0.1"))),
            MockResponse().setResponseCode(302).addHeader("Location", elsewhere),
            MockResponse().setResponseCode(302).addHeader("Location", plain),
        )

        val followed = checker().check(1_000_000, false)
        val offHost = checker().check(1_000_000, false)
        val http = checker().check(1_000_000, false)

        assertTrue(followed is CheckResult.Available)
        assertEquals(CheckResult.Failed, offHost)
        assertEquals(CheckResult.Failed, http)
        assertEquals(4, tls.server.requestCount)
        // Redirect addresses are never logged.
        assertTrue(logs.none { "moved" in it })
    }

    @Test
    fun tooManyRedirectsFail() = runTest(UnconfinedTestDispatcher()) {
        repeat(UpdateHttp.MAX_REDIRECTS + 1) {
            enqueue(MockResponse().setResponseCode(302).addHeader("Location", tls.url("/again")))
        }

        assertEquals(CheckResult.Failed, checker().check(1_000_000, false))
        assertEquals(UpdateHttp.MAX_REDIRECTS + 1, tls.server.requestCount)
    }

    @Test
    fun asksAtMostOnceADayOnOpenAndAgainWhenTheClockWentBack() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        repeat(3) { enqueue(ok(list(release("v1.0.0")))) }
        val controller = controller()

        controller.onAppOpened()
        assertEquals(1, tls.server.requestCount)
        now += 23 * HOUR
        controller.onAppOpened()
        assertEquals(1, tls.server.requestCount)
        now += HOUR
        controller.onAppOpened()
        assertEquals(2, tls.server.requestCount)
        // The last answer is dated after "now": the clock went back, so it asks again.
        now -= 60_000
        controller.onAppOpened()
        assertEquals(3, tls.server.requestCount)
        controller.onAppOpened()
        assertEquals(3, tls.server.requestCount)
    }

    @Test
    fun failedAnswersCountForTheDayButOfflineDoesNot() = runTest(UnconfinedTestDispatcher()) {
        enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
            MockResponse().setResponseCode(500),
            ok(list(release("v1.0.0"))),
        )
        val controller = controller()

        controller.onAppOpened()
        controller.onAppOpened()
        assertEquals(2, tls.server.requestCount)
        controller.onAppOpened()
        assertEquals(2, tls.server.requestCount)
    }

    @Test
    fun switchOffNoRequestOnOpenButCheckNowAsks() = runTest(UnconfinedTestDispatcher()) {
        enqueue(ok(list(release("v1.0.1"))))
        val controller = controller(store = InMemoryUpdateStore(UpdateRecord(autoCheck = false)))

        controller.onAppOpened()
        assertEquals(0, tls.server.requestCount)
        controller.checkNow()
        assertEquals(1, tls.server.requestCount)
        assertEquals("1.0.1", controller.state.value.found?.version?.name)
    }

    @Test
    fun testModeStartsWithTheSwitchOffSoOpeningAsksNothing() = runTest(UnconfinedTestDispatcher()) {
        enqueue(ok(list(release("v1.0.0-beta.1"))))
        val controller = controller(environment = Updates.test())

        controller.onAppOpened()
        assertEquals(0, tls.server.requestCount)
        controller.checkNow()
        assertEquals(1, tls.server.requestCount)
        assertEquals("1.0.0-beta.1", controller.state.value.found?.version?.name)
    }

    @Test
    fun policyOffNeverAsksAndHasNoRow() = runTest(UnconfinedTestDispatcher()) {
        enqueue(ok(list(release("v1.0.1"))))
        val store = InMemoryUpdateStore(UpdateRecord(autoCheck = true))
        val controller = controller(environment = Updates.off(), store = store)

        controller.onAppOpened()
        controller.checkNow()

        assertEquals(0, tls.server.requestCount)
        assertNull(controller.state.value.settingsRow())
        assertEquals(0, store.writes)
    }
}
