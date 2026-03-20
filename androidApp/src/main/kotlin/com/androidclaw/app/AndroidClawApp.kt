package com.androidclaw.app

import android.app.Application
import com.androidclaw.app.platform.AndroidDeviceActionBridge
import com.androidclaw.shared.di.platformModule
import com.androidclaw.shared.di.sharedModule
import com.androidclaw.shared.tools.DeviceActionBridge
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class AndroidClawApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val appModule = module {
            single<DeviceActionBridge> { AndroidDeviceActionBridge(get()) }
        }

        startKoin {
            androidContext(this@AndroidClawApp)
            modules(sharedModule, platformModule, appModule)
        }
    }
}
