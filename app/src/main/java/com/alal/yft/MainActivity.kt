package com.alal.yft

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

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme only styles the launch window; the app runs on Theme.Yft.
        setTheme(R.style.Theme_Yft)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YftApp(
                playback = playback,
                mediaDetails = mediaDetails,
                remoteThumbnails = remoteThumbnails,
                downloadThumbnails = downloadThumbnails,
            )
        }
        // Started here rather than in Application.onCreate so tests and background-only starts
        // do no file work; it runs once per process on the download scope.
        storageJanitor.launchOnce()
    }
}
