package com.androidclaw.shared.logging

import platform.Foundation.NSLog

actual object Logger {
    actual fun d(tag: String, message: String) {
        NSLog("D/$tag: $message")
    }

    actual fun i(tag: String, message: String) {
        NSLog("I/$tag: $message")
    }

    actual fun w(tag: String, message: String) {
        NSLog("W/$tag: $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        NSLog("E/$tag: $message${if (throwable != null) " | ${throwable.message}" else ""}")
    }
}
