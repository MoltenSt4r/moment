package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Multi-layered tactile feedback engine for Moment.
 * Provides distinct physical sensations for pull tension, checkmark lock,
 * deep-pull partner switching, side-swipe exit, replies, and reactions.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private var lastTensionTickBucket: Int = -1

    fun resetPullTracking() {
        lastTensionTickBucket = -1
    }

    /**
     * Progressive subtle ticks while pulling down towards the checkmark threshold (0f..1f).
     */
    fun onPullProgress(progressToCheckmark: Float, view: View? = null) {
        val bucket = (progressToCheckmark.coerceIn(0f, 0.99f) * 6).toInt()
        if (bucket > 0 && bucket != lastTensionTickBucket) {
            lastTensionTickBucket = bucket
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibratePredefined(VibrationEffect.EFFECT_TICK, fallbackMs = 8L)
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    /**
     * Crisp lock-in pulse when the top pull indicator transforms into a Checkmark (✓).
     */
    fun onCheckmarkArmed(view: View? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        vibrateWaveform(longArrayOf(0, 18, 35, 28), intArrayOf(0, 190, 0, 255), fallbackMs = 35L)
    }

    /**
     * Distinct energetic double-pulse when pulling deeper below the checkmark to switch partners.
     */
    fun onDeepPullNextPartnerArmed(view: View? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_DOUBLE_CLICK, fallbackMs = 30L)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Sweeping release vibration when swiping left or right after the checkmark to exit to main menu.
     */
    fun onSideSwipeExitArmed(view: View? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        vibrateWaveform(longArrayOf(0, 24, 40, 14), intArrayOf(0, 240, 0, 120), fallbackMs = 30L)
    }

    fun onStartMatchmaking() {
        vibrateWaveform(longArrayOf(0, 20, 45, 32), intArrayOf(0, 180, 0, 255), fallbackMs = 35L)
    }

    fun onHandshakeConnected() {
        vibrateWaveform(longArrayOf(0, 14, 50, 22, 45, 30), intArrayOf(0, 140, 0, 200, 0, 255), fallbackMs = 45L)
    }

    fun onReplySnapped() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 14L)
        } else {
            vibrateOneShot(14L, 180)
        }
    }

    fun onReactionToggled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_TICK, fallbackMs = 10L)
        } else {
            vibrateOneShot(10L, 150)
        }
    }

    fun onSessionShredded() {
        vibrateWaveform(longArrayOf(0, 16, 30, 16, 30, 12), intArrayOf(0, 220, 0, 150, 0, 90), fallbackMs = 30L)
    }

    private fun vibratePredefined(effectId: Int, fallbackMs: Long) {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(effectId))
            } else {
                vibrateOneShot(fallbackMs, 180)
            }
        } catch (_: Exception) {
        }
    }

    private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray, fallbackMs: Long) {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vib.hasAmplitudeControl()) {
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vib.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(fallbackMs)
            }
        } catch (_: Exception) {
        }
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }
}
