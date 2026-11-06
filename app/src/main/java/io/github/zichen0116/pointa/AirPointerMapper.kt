package io.github.zichen0116.pointa

import kotlin.math.*

/** Relative quaternion mapping avoids Euler-angle jumps when the phone is held upright. */
internal class AirPointerMapper {
    data class Position(val x: Double = .5, val y: Double = .5)
    var position = Position()
        private set
    private var origin: DoubleArray? = null
    private var anchor = position
    private var displayRotation = -1

    fun begin() { origin = null; anchor = position }
    fun calibrate() { position = Position(); begin() }

    fun update(vector: FloatArray, sensitivity: Int, rotation: Int): Position? {
        if (vector.size < 3 || vector.take(4).any { !it.isFinite() }) return null
        val x = vector[0].toDouble(); val y = vector[1].toDouble(); val z = vector[2].toDouble()
        val w = if (vector.size >= 4) vector[3].toDouble() else sqrt((1 - x * x - y * y - z * z).coerceAtLeast(0.0))
        val length = sqrt(w * w + x * x + y * y + z * z)
        if (length < 1e-6) return null
        val q = doubleArrayOf(w / length, x / length, y / length, z / length)
        if (displayRotation != rotation) { displayRotation = rotation; begin() }
        val o = origin ?: run { origin = q; return position }
        val rw = o[0] * q[0] + o[1] * q[1] + o[2] * q[2] + o[3] * q[3]
        val rx = o[0] * q[1] - o[1] * q[0] - o[2] * q[3] + o[3] * q[2]
        val ry = o[0] * q[2] + o[1] * q[3] - o[2] * q[0] - o[3] * q[1]
        val rz = o[0] * q[3] - o[1] * q[2] + o[2] * q[1] - o[3] * q[0]
        val magnitude = sqrt(rx * rx + ry * ry + rz * rz)
        val angle = 2 * atan2(magnitude, abs(rw))
        if (angle > PI / 2) return position // Large turns require recentering rather than flipping across the screen.
        val factor = if (magnitude < 1e-8) 0.0 else angle / magnitude * if (rw < 0) -1 else 1
        val lx = rx * factor; val ly = ry * factor; val lz = rz * factor
        // Gravity-aligned yaw works with either an upright or a flat phone; pitch follows the screen's right axis.
        val yaw = 2 * (o[1] * o[3] - o[0] * o[2]) * lx + 2 * (o[2] * o[3] + o[0] * o[1]) * ly +
            (1 - 2 * (o[1] * o[1] + o[2] * o[2])) * lz
        val pitch = when (rotation) { 1 -> ly; 2 -> -lx; 3 -> -ly; else -> lx }
        fun deadZone(value: Double) = sign(value) * (abs(value) - .0025).coerceAtLeast(0.0)
        val gain = sensitivity.coerceIn(10, 100) / 60.0 / (PI / 3)
        position = Position((anchor.x - deadZone(yaw) * gain).coerceIn(0.0, 1.0),
            (anchor.y - deadZone(pitch) * gain).coerceIn(0.0, 1.0))
        return position
    }
}
