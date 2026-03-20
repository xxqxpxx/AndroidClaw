package com.androidclaw.shared

actual fun getPlatform(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
