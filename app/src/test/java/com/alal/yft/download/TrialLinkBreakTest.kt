package com.alal.yft.download

import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadRequestTag
import com.alal.yft.download.policy.DownloadNetworkStatus
import com.alal.yft.feature.downloads.DownloadSpeedMeter
import com.alal.yft.feature.downloads.DownloadsViewModel
import com.alal.yft.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

/**
 * Track E (E-D9): the trial's "Break this download's link" refuses its download's current link
 * alone, without the network, and stops a body still being read; outside the trial it does
 * nothing at all.
 */
class TrialLinkBreakTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = MockWebServer()

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun `outside the trial the downloads keep their own client and no link breaks`() {
        val client = OkHttpClient()
        val tool = TrialLinkBreak(available = false)
        assertSame(client, tool.around(client))

        tool.breakLink(TASK)
        server.enqueue(MockResponse().setBody("ok"))
        val wired = client.newBuilder().addInterceptor(tool).build()

        assertEquals(200, wired.code(tagged(TASK, link = 0)))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a broken link meets the tool's 403 while its fresh link and the rest pass`() {
        val tool = TrialLinkBreak(available = true)
        val client = tool.around(OkHttpClient())
        repeat(4) { server.enqueue(MockResponse().setBody("ok")) }

        assertEquals(200, client.code(tagged(TASK, link = 0)))
        tool.breakLink(TASK)

        assertEquals(403, client.code(tagged(TASK, link = 0)))
        assertEquals(1, server.requestCount)
        assertEquals(200, client.code(tagged(TASK, link = 1)))
        assertEquals(200, client.code(tagged(OTHER, link = 0)))
        assertEquals(200, client.code(untagged()))
        assertEquals(403, client.code(tagged(TASK, link = 0)))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `a break before any request breaks the first link and a second break the fresh one`() {
        val tool = TrialLinkBreak(available = true)
        val client = tool.around(OkHttpClient())
        repeat(2) { server.enqueue(MockResponse().setBody("ok")) }

        tool.breakLink(TASK)
        assertEquals(403, client.code(tagged(TASK, link = 0)))
        assertEquals(0, server.requestCount)

        assertEquals(200, client.code(tagged(TASK, link = 1)))
        tool.breakLink(TASK)
        assertEquals(403, client.code(tagged(TASK, link = 1)))
        assertEquals(200, client.code(tagged(TASK, link = 2)))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a body still being read stops once its link breaks`() {
        val tool = TrialLinkBreak(available = true)
        val client = tool.around(OkHttpClient())
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(BODY_BYTES))))

        client.newCall(tagged(TASK, link = 0)).execute().use { response ->
            val body = checkNotNull(response.body).source()
            assertEquals(READ_FIRST, body.readByteArray(READ_FIRST.toLong()).size)

            tool.breakLink(TASK)

            assertThrows(IOException::class.java) { body.readByteArray() }
        }
    }

    @Test
    fun `Downloads' break reaches the trial's tool`() {
        val tool = TrialLinkBreak(available = true)
        val viewModel = DownloadsViewModel(
            queue = DownloadQueue(
                store = MemoryTaskStore(),
                transferDispatcher = EndlessTransfers(),
                scope = CoroutineScope(mainDispatcherRule.dispatcher),
                clock = { RenewalFixtures.NOW },
                ioDispatcher = mainDispatcherRule.dispatcher,
            ),
            networkStatus = DownloadNetworkStatus.AlwaysAllowed,
            storageSource = DownloadStorageSource.None,
            speeds = DownloadSpeedMeter(),
            health = InMemoryBackgroundHealthStore(),
            systemStatus = BackgroundSystemStatus.Unlimited,
            serviceStarter = object : DownloadServiceStarter {
                override fun start() = Unit
            },
            trialBreak = tool,
        )

        viewModel.breakLink(TASK)

        assertEquals(403, tool.around(OkHttpClient()).code(tagged(TASK, link = 0)))
        assertEquals(0, server.requestCount)
    }

    private fun OkHttpClient.code(request: Request): Int =
        newCall(request).execute().use { it.code }

    private fun tagged(taskId: String, link: Int): Request = Request.Builder()
        .url(server.url("/file.mp4"))
        .tag(DownloadRequestTag::class.java, DownloadRequestTag(taskId, link))
        .build()

    private fun untagged(): Request = Request.Builder().url(server.url("/page")).build()

    private companion object {
        const val TASK = "task-1"
        const val OTHER = "task-2"
        const val BODY_BYTES = 1_048_576
        const val READ_FIRST = 1_024
    }
}
