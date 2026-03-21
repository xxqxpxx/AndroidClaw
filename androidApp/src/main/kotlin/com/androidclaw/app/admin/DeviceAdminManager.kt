package com.androidclaw.app.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceAdminManager(private val context: Context) {

    companion object {
        private const val TAG = "AndroidClaw.AdminMgr"
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ClawDeviceAdminReceiver.getComponentName(context)

    val isAdminActive: Boolean
        get() = devicePolicyManager.isAdminActive(adminComponent)

    fun requestAdminActivation(): Intent {
        Log.i(TAG, "Requesting device admin activation")
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "AndroidClaw needs device admin access to lock the screen, manage passwords, and control device security features on your behalf."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun lockScreen(): Result<String> = runCatching {
        if (!isAdminActive) {
            return Result.failure(IllegalStateException("Device admin not active. Please enable it in Settings."))
        }
        devicePolicyManager.lockNow()
        Log.i(TAG, "Screen locked")
        "Screen locked successfully"
    }

    fun setMaxFailedPasswordAttempts(attempts: Int): Result<String> = runCatching {
        if (!isAdminActive) {
            return Result.failure(IllegalStateException("Device admin not active"))
        }
        devicePolicyManager.setMaximumFailedPasswordsForWipe(adminComponent, attempts)
        Log.i(TAG, "Max failed password attempts set to $attempts")
        "Maximum failed password attempts set to $attempts before wipe"
    }

    fun setCameraDisabled(disabled: Boolean): Result<String> = runCatching {
        if (!isAdminActive) {
            return Result.failure(IllegalStateException("Device admin not active"))
        }
        devicePolicyManager.setCameraDisabled(adminComponent, disabled)
        Log.i(TAG, "Camera ${if (disabled) "disabled" else "enabled"}")
        "Camera ${if (disabled) "disabled" else "enabled"}"
    }

    fun setMaxScreenLockTimeout(timeoutMs: Long): Result<String> = runCatching {
        if (!isAdminActive) {
            return Result.failure(IllegalStateException("Device admin not active"))
        }
        devicePolicyManager.setMaximumTimeToLock(adminComponent, timeoutMs)
        val seconds = timeoutMs / 1000
        Log.i(TAG, "Max screen lock timeout set to ${seconds}s")
        "Maximum screen lock timeout set to ${seconds} seconds"
    }

    fun getDeviceAdminStatus(): String = buildString {
        appendLine("Device Admin Status:")
        appendLine("  Active: $isAdminActive")
        if (isAdminActive) {
            appendLine("  Component: $adminComponent")
            try {
                val maxTime = devicePolicyManager.getMaximumTimeToLock(adminComponent)
                if (maxTime > 0) appendLine("  Max lock timeout: ${maxTime / 1000}s")
            } catch (_: Exception) {}
        }
    }.trim()
}
