package com.androidclaw.app

import android.app.Application
import android.util.Log
import com.androidclaw.app.admin.DeviceAdminManager
import com.androidclaw.app.audio.SoundManager
import com.androidclaw.app.haptics.HapticManager
import com.androidclaw.app.llm.ModelDownloadManager
import com.androidclaw.app.llm.OnDeviceLlmEngine
import com.androidclaw.app.platform.AndroidDeviceActionBridge
import com.androidclaw.app.settings.SettingsManager
import com.androidclaw.app.shortcuts.AppShortcuts
import com.androidclaw.shared.di.platformModule
import com.androidclaw.shared.di.sharedModule
import com.androidclaw.shared.logging.CrashLogger
import com.androidclaw.shared.tools.DeviceActionBridge
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class AndroidClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "App starting")

        initCrashlytics()

        val appModule = module {
            single { SettingsManager(get()) }
            single { HapticManager(get(), get()) }
            single<DeviceActionBridge> { AndroidDeviceActionBridge(get()) }
            single { DeviceAdminManager(get()) }
            single { OnDeviceLlmEngine(get()) }
            single { ModelDownloadManager(get()) }
            single { SoundManager(get()) }
        }

        startKoin {
            androidContext(this@AndroidClawApp)
            modules(sharedModule, platformModule, appModule)
        }

        AppShortcuts.setup(this)
        Log.i(TAG, "App initialized successfully")
    }

    private fun initCrashlytics() {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)

            // Wire CrashLogger so shared module errors go to Crashlytics
            CrashLogger.init { tag, message, throwable ->
                crashlytics.log("$tag: $message")
                if (throwable != null) {
                    crashlytics.recordException(throwable)
                }
            }

            // Catch uncaught exceptions and log them
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
                Log.e(TAG, "UNCAUGHT EXCEPTION on ${thread.name}", exception)
                crashlytics.log("Uncaught exception on ${thread.name}")
                crashlytics.recordException(exception)
                defaultHandler?.uncaughtException(thread, exception)
            }

            Log.i(TAG, "Crashlytics initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Crashlytics", e)
        }
    }

    companion object {
        private const val TAG = "AndroidClaw"
    }
}
