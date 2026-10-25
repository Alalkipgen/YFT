package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadTaskStore
import com.alal.yft.core.download.DownloadTransferDispatcher
import com.alal.yft.core.download.QueueTransferResult
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.feature.settings.YouTubeSessionReset
import com.alal.yft.feature.settings.YouTubeSessionResetResult
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Item 7 Track B3: "Reset YouTube session" (the screen asks first) runs the reset once and says
 * in the snackbar what went and what to do next; the downloads stay as they were.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelYouTubeResetTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aFullResetSaysHowManyCookiesWentAndToReopenThePage() = runTest {
        val reset = FakeReset(YouTubeSessionResetResult(cookiesRemoved = 5, cookiesLeft = 0))
        val viewModel = DownloadsViewModel(queue(Store(listOf(refused("v3")))), reset)
        val messages = messagesOf(viewModel)
        runCurrent()

        viewModel.resetYouTubeSession()
        runCurrent()

        assertEquals(1, reset.calls)
        assertEquals(
            listOf(
                "YouTube session reset (5 cookies removed). Reopen the page in the browser, " +
                    "then download again.",
            ),
            messages,
        )
        // The refused download stays, still asking for a fresh link.
        val row = viewModel.uiState.value.rows.single()
        assertEquals("v3", row.id)
        assertEquals(DownloadTaskStatus.NEEDS_REFRESH, row.status)
        assertEquals(DownloadFailureReason.YOUTUBE_REFUSED, row.failureReason)
    }

    @Test
    fun aCookieThatStayedIsNamedWithWhatRemovesIt() = runTest {
        val reset = FakeReset(YouTubeSessionResetResult(cookiesRemoved = 4, cookiesLeft = 1))
        val viewModel = DownloadsViewModel(queue(Store(emptyList())), reset)
        val messages = messagesOf(viewModel)
        runCurrent()

        viewModel.resetYouTubeSession()
        runCurrent()

        assertEquals(
            listOf(
                "YouTube session partly reset: 1 cookie stayed, which Clear browsing data in " +
                    "Settings removes (it signs you out of every site in YFT).",
            ),
            messages,
        )
    }

    @Test
    fun withoutYouTubeCookiesTheResetSaysNoneWent() = runTest {
        val reset = FakeReset(YouTubeSessionResetResult(cookiesRemoved = 0, cookiesLeft = 0))
        val viewModel = DownloadsViewModel(queue(Store(emptyList())), reset)
        val messages = messagesOf(viewModel)
        runCurrent()

        viewModel.resetYouTubeSession()
        runCurrent()

        assertEquals(
            listOf(
                "YouTube session reset (no cookies removed). Reopen the page in the browser, " +
                    "then download again.",
            ),
            messages,
        )
    }

    @Test
    fun aResetThatFailsSaysSo() = runTest {
        val reset = YouTubeSessionReset { throw IllegalStateException("no WebView") }
        val viewModel = DownloadsViewModel(queue(Store(emptyList())), reset)
        val messages = messagesOf(viewModel)
        runCurrent()

        viewModel.resetYouTubeSession()
        runCurrent()

        assertEquals(listOf(YOUTUBE_RESET_FAILED), messages)
    }

    private fun TestScope.messagesOf(viewModel: DownloadsViewModel): List<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch { viewModel.messages.collect { messages += it } }
        return messages
    }

    private fun queue(store: DownloadTaskStore): DownloadQueue = DownloadQueue(
        store = store,
        transferDispatcher = NeverFinishing,
        scope = CoroutineScope(mainDispatcherRule.dispatcher),
        maxConcurrentDownloads = 1,
        clock = { 1_000 },
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    /** A download YouTube refused while its link had time left (B1). */
    private fun refused(id: String): StoredDownloadTask = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = DownloadTaskStatus.NEEDS_REFRESH,
        planType = DownloadPlanType.DIRECT,
        totalBytes = 100,
        downloadedBytes = 10,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        destinationUri = "content://media/external/downloads/7",
        preferredSegmentCount = 1,
        requiresLinkRefresh = true,
        failureReason = DownloadFailureReason.YOUTUBE_REFUSED,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = 100,
            entityTag = null,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, 99, 10)),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private class FakeReset(private val result: YouTubeSessionResetResult) : YouTubeSessionReset {
        var calls = 0

        override suspend fun reset(): YouTubeSessionResetResult {
            calls += 1
            return result
        }
    }

    private class Store(initial: List<StoredDownloadTask>) : DownloadTaskStore {
        private val tasks = LinkedHashMap<String, StoredDownloadTask>().apply {
            initial.forEach { put(it.id, it) }
        }

        override suspend fun loadAll(): List<StoredDownloadTask> = tasks.values.toList()

        override suspend fun save(task: StoredDownloadTask) {
            tasks[task.id] = task
        }

        override suspend fun delete(id: String) {
            tasks.remove(id)
        }
    }

    private object NeverFinishing : DownloadTransferDispatcher {
        override suspend fun transfer(
            plan: DownloadPlan,
            metadata: RemoteFileMetadata?,
            destination: DownloadDestination,
            resumeFrom: TransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (TransferCheckpoint) -> Unit,
        ): QueueTransferResult = awaitCancellation()

        override suspend fun discard(plan: DownloadPlan) = Unit
    }
}
