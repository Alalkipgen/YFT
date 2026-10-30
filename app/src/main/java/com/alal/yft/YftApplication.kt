package com.alal.yft

import android.app.Application
import com.alal.yft.detection.Av1MergeSwitch
import com.alal.yft.diagnostics.CrashReportHandler
import com.alal.yft.diagnostics.CrashReportStore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YftApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReportHandler.install(CrashReportStore(this))
        // v1.0.2 item 26: a debug build's AV1 merge switch, read at each sheet and download.
        Av1MergeSwitch.install(this)
    }
}
