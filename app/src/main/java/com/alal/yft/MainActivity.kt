package com.alal.yft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alal.yft.download.DownloadStorageJanitor
import com.alal.yft.ui.YftApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var storageJanitor: DownloadStorageJanitor

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme only styles the launch window; the app runs on Theme.Yft.
        setTheme(R.style.Theme_Yft)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YftApp() }
        // Started here rather than in Application.onCreate so tests and background-only starts
        // do no file work; it runs once per process on the download scope.
        storageJanitor.launchOnce()
    }
}
