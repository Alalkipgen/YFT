package com.alal.yft

import android.app.Application
import com.alal.yft.diagnostics.CrashReportHandler
import com.alal.yft.diagnostics.CrashReportStore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YftApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReportHandler.install(CrashReportStore(this))
    }
}
