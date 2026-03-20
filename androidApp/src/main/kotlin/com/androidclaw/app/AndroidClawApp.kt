package com.androidclaw.app

import android.app.Application
import com.androidclaw.shared.di.platformModule
import com.androidclaw.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AndroidClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AndroidClawApp)
            modules(sharedModule, platformModule)
        }
    }
}
