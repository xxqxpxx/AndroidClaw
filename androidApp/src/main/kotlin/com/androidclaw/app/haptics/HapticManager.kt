package com.androidclaw.app.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.androidclaw.app.settings.SettingsManager

/**
 * Centralized haptic feedback manager.
 * Provides typed haptic patterns for different UI events.
 * Respects the user's haptic feedback preference.
 */
class HapticManager(
    context: Context,
    private val settings: SettingsManager
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun tick() = vibrate(HapticPattern.TICK)
    fun click() = vibrate(HapticPattern.CLICK)
    fun heavyClick() = vibrate(HapticPattern.HEAVY_CLICK)
    fun success() = vibrate(HapticPattern.SUCCESS)
    fun error() = vibrate(HapticPattern.ERROR)
    fun messageSent() = vibrate(HapticPattern.CLICK)
    fun messageReceived() = vibrate(HapticPattern.TICK)
    fun toolExecuted() = vibrate(HapticPattern.TICK)
    fun voiceStart() = vibrate(HapticPattern.HEAVY_CLICK)
    fun voiceStop() = vibrate(HapticPattern.CLICK)

    private fun vibrate(pattern: HapticPattern) {
        if (!settings.hapticFeedback.value) return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (pattern) {
                HapticPattern.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticPattern.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticPattern.HEAVY_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticPattern.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), -1)
                HapticPattern.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 50, 30, 50, 30, 50), -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val duration = when (pattern) {
                HapticPattern.TICK -> 10L
                HapticPattern.CLICK -> 20L
                HapticPattern.HEAVY_CLICK -> 40L
                HapticPattern.SUCCESS -> 30L
                HapticPattern.ERROR -> 60L
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private enum class HapticPattern {
        TICK, CLICK, HEAVY_CLICK, SUCCESS, ERROR
    }
}
