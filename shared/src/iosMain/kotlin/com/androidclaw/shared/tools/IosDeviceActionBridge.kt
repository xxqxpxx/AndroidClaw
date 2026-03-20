package com.androidclaw.shared.tools

class IosDeviceActionBridge : DeviceActionBridge {
    override suspend fun setWifiEnabled(enabled: Boolean): Result<String> =
        Result.success("Wi-Fi settings: open Settings app on iOS")

    override suspend fun setBluetoothEnabled(enabled: Boolean): Result<String> =
        Result.success("Bluetooth settings: open Settings app on iOS")

    override suspend fun setFlashlightEnabled(enabled: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("Flashlight control not yet implemented on iOS"))

    override suspend fun setBrightness(level: Int): Result<String> =
        Result.failure(UnsupportedOperationException("Brightness control not yet implemented on iOS"))

    override suspend fun setVolume(stream: String, level: Int): Result<String> =
        Result.failure(UnsupportedOperationException("Volume control not yet implemented on iOS"))

    override suspend fun getDeviceInfo(): Result<String> =
        Result.success("iOS device - detailed info coming in future update")

    override suspend fun launchApp(packageName: String): Result<String> =
        Result.failure(UnsupportedOperationException("App launching not yet implemented on iOS"))

    override suspend fun listInstalledApps(): Result<String> =
        Result.failure(UnsupportedOperationException("App listing not available on iOS"))

    override suspend fun searchApps(query: String): Result<String> =
        Result.failure(UnsupportedOperationException("App search not available on iOS"))

    override suspend fun setClipboard(text: String): Result<String> =
        Result.failure(UnsupportedOperationException("Clipboard not yet implemented on iOS"))

    override suspend fun getClipboard(): Result<String> =
        Result.failure(UnsupportedOperationException("Clipboard not yet implemented on iOS"))

    override suspend fun getRecentNotifications(count: Int): Result<String> =
        Result.failure(UnsupportedOperationException("Notifications not available on iOS"))

    override suspend fun dismissNotification(key: String): Result<String> =
        Result.failure(UnsupportedOperationException("Notification dismissal not available on iOS"))

    override suspend fun setAlarm(hour: Int, minute: Int, label: String): Result<String> =
        Result.failure(UnsupportedOperationException("Alarm not yet implemented on iOS"))

    override suspend fun setTimer(seconds: Int, label: String): Result<String> =
        Result.failure(UnsupportedOperationException("Timer not yet implemented on iOS"))
}
