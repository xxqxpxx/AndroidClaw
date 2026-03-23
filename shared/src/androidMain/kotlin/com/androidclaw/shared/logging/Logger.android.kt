package com.androidclaw.shared.logging

import android.util.Log

actual object Logger {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    actual fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        // Crashlytics logging is handled by CrashLogger in androidApp
        CrashLogger.logException(tag, message, throwable)
    }
}

object CrashLogger {
    private var handler: ((String, String, Throwable?) -> Unit)? = null

    fun init(handler: (String, String, Throwable?) -> Unit) {
        this.handler = handler
    }

    fun logException(tag: String, message: String, throwable: Throwable?) {
        handler?.invoke(tag, message, throwable)
    }
}
