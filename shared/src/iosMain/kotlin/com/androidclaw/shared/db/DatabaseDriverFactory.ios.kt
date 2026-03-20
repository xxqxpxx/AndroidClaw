package com.androidclaw.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.androidclaw.db.AndroidClawDb

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(AndroidClawDb.Schema, "androidclaw.db")
    }
}
