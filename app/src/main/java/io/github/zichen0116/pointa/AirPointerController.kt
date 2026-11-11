package io.github.zichen0116.pointa

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal class AirPointerController(context: Context, private val move: (Double, Double) -> Unit,
    private val hide: () -> Unit, private val changed: (Boolean) -> Unit, private val unavailable: () -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available: Boolean get() = sensor != null
    val usesTilt: Boolean get() = sensor?.type in setOf(Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)
    private val mapper = AirPointerMapper()
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var lastSample = 0L
    var sensitivity = 60
    var rotation = 0
    private val watchdog = object : Runnable {
        override fun run() {
            if (!active) return
            if (SystemClock.elapsedRealtime() - lastSample > 500) { stop(); unavailable() }
            else handler.postDelayed(this, 250)
        }
    }

    fun start() {
        if (active) return
        if (sensor == null || !manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) { unavailable(); return }
        mapper.begin()
        active = true
        lastSample = SystemClock.elapsedRealtime()
        changed(true)
        handler.postDelayed(watchdog, 500)
    }

    fun stop() {
        manager.unregisterListener(this)
        handler.removeCallbacks(watchdog)
        if (active) { active = false; hide(); changed(false) }
    }

    fun calibrate() {
        mapper.calibrate()
        if (active) move(.5, .5)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        val age = SystemClock.elapsedRealtimeNanos() - event.timestamp
        if (age !in 0..250_000_000L) return
        val time = event.timestamp / 1_000_000.0
        val point = (if (usesTilt) mapper.updateGravity(event.values, sensitivity, rotation, time)
            else mapper.update(event.values, sensitivity, rotation, time)) ?: return
        lastSample = SystemClock.elapsedRealtime()
        move(point.x, point.y)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}
