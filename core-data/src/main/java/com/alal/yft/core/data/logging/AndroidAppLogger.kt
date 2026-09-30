package com.alal.yft.core.data.logging

import android.util.Log
import com.alal.yft.core.model.logging.AppLogger
import com.alal.yft.core.model.logging.SensitiveValueRedactor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppLogger @Inject constructor() : AppLogger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, SensitiveValueRedactor.redact(message))
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, SensitiveValueRedactor.redact(message))
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, SensitiveValueRedactor.redact(message))
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        val failureType = throwable?.javaClass?.simpleName?.let { " [$it]" }.orEmpty()
        Log.e(tag, SensitiveValueRedactor.redact(message + failureType))
    }
}
