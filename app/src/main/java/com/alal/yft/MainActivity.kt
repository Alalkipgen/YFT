package com.alal.yft

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alal.yft.download.DownloadStorageJanitor
import com.alal.yft.feature.library.LibraryPlayback
import com.alal.yft.feature.library.MediaDetailsSource
import com.alal.yft.thumbnail.DownloadThumbnails
import com.alal.yft.thumbnail.RemoteThumbnails
import com.alal.yft.ui.YftApp
import com.alal.yft.ui.navigation.SharedLinks
import com.alal.yft.ui.navigation.TabRequests
import com.alal.yft.update.UpdateController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var storageJanitor: DownloadStorageJanitor

    @Inject
    lateinit var playback: LibraryPlayback

    @Inject
    lateinit var mediaDetails: MediaDetailsSource

    @Inject
    lateinit var remoteThumbnails: RemoteThumbnails

    @Inject
    lateinit var downloadThumbnails: DownloadThumbnails

    @Inject
    lateinit var updateController: UpdateController

    /** A download notification's tap asks for the Downloads tab (v1.0.2). */
    private val tabRequests = TabRequests()

    /** A link shared from another app, for Home's lookup (v1.0.2, item 4). */
    private val sharedLinks = SharedLinks()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme only styles the launch window; the app runs on Theme.Yft.
        setTheme(R.style.Theme_Yft)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity (rotation, a restart by Android) keeps the tab it showed and does
        // not look a shared link up again.
        if (savedInstanceState == null) {
            tabRequests.take(intent)
            sharedLinks.take(intent)
        }
        setContent {
            YftApp(
                playback = playback,
                mediaDetails = mediaDetails,
                remoteThumbnails = remoteThumbnails,
                downloadThumbnails = downloadThumbnails,
                tabRequests = tabRequests,
                sharedLinks = sharedLinks,
            )
        }
        // Started here rather than in Application.onCreate so tests and background-only starts
        // do no file work; it runs once per process on the download scope.
        storageJanitor.launchOnce()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A notification tapped while YFT is open or in the background (its single-top intent),
        // or a share that reached this copy of YFT.
        tabRequests.take(intent)
        sharedLinks.take(intent)
    }

    override fun onStart() {
        super.onStart()
        // The daily update check, only when the build and Check automatically allow it.
        updateController.onAppOpened()
    }
}
