package com.androidclaw.app

import android.app.Application
import android.util.Log
import com.androidclaw.app.admin.DeviceAdminManager
import com.androidclaw.app.haptics.HapticManager
import com.androidclaw.app.platform.AndroidDeviceActionBridge
import com.androidclaw.app.settings.SettingsManager
import com.androidclaw.app.shortcuts.AppShortcuts
import com.androidclaw.shared.di.platformModule
import com.androidclaw.shared.di.sharedModule
import com.androidclaw.shared.tools.DeviceActionBridge
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class AndroidClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("AndroidClaw", "App starting")

        val appModule = module {
            single { SettingsManager(get()) }
            single { HapticManager(get(), get()) }
            single<DeviceActionBridge> { AndroidDeviceActionBridge(get()) }
            single { DeviceAdminManager(get()) }
        }

        startKoin {
            androidContext(this@AndroidClawApp)
            modules(sharedModule, platformModule, appModule)
        }

        AppShortcuts.setup(this)
    }
}
