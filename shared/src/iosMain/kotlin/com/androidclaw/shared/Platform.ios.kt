package com.androidclaw.shared

import platform.UIKit.UIDevice

actual fun getPlatform(): String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
