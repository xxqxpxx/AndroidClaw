package com.androidclaw.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.androidclaw.app.shortcuts.AppShortcuts
import com.androidclaw.app.ui.navigation.AppNavigation
import com.androidclaw.app.ui.theme.AndroidClawTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.filter { it.value }.keys.map { it.substringAfterLast('.') }
        val denied = results.filter { !it.value }.keys.map { it.substringAfterLast('.') }
        Log.i(TAG, "Permissions granted: $granted")
        if (denied.isNotEmpty()) Log.w(TAG, "Permissions denied: $denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestEssentialPermissions()

        val shortcutAction = intent?.action
        val startRoute = when (shortcutAction) {
            AppShortcuts.ACTION_NEW_CONVERSATION -> "new_conversation"
            AppShortcuts.ACTION_VOICE_INPUT -> "new_conversation_voice"
            AppShortcuts.ACTION_SEARCH -> "search"
            else -> null
        }

        setContent {
            AndroidClawTheme {
                AppNavigation(shortcutRoute = startRoute)
            }
        }
    }

    private fun requestEssentialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            Log.i(TAG, "Requesting ${needed.size} permissions: ${needed.map { it.substringAfterLast('.') }}")
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            Log.i(TAG, "All permissions already granted")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
