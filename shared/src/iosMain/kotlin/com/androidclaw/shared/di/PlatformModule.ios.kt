package com.androidclaw.shared.di

import com.androidclaw.shared.db.DatabaseDriverFactory
import com.androidclaw.shared.tools.DeviceActionBridge
import com.androidclaw.shared.tools.IosDeviceActionBridge
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
    single<DeviceActionBridge> { IosDeviceActionBridge() }
}
