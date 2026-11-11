package io.github.zichen0116.pointa

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal class PageHaptics(context: Context) {
    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    fun click() {
        val motor = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            else VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
        motor.vibrate(effect, attributes)
    }
}
