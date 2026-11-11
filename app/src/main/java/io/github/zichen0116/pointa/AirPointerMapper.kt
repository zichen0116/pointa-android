package io.github.zichen0116.pointa

import kotlin.math.*

// Adapted from lan-laser-pen/example/orientation.ts at 1e643ed; MIT notice is bundled in assets/licenses.
internal class AirPointerMapper {
    data class Position(val x: Double = .5, val y: Double = .5)
    var position = Position()
        private set
    private var anchor = position
    private var reference: Array<DoubleArray>? = null
    private var tiltReference: DoubleArray? = null
    private var displayRotation = -1
    private var filteredX = 0.0
    private var filteredY = 0.0
    private var previousTime: Double? = null

    fun begin() { reference = null; tiltReference = null; anchor = position; filteredX = 0.0; filteredY = 0.0; previousTime = null }
    fun calibrate() { position = Position(); begin() }

    fun update(vector: FloatArray, sensitivity: Int, rotation: Int, timeMillis: Double): Position? {
        if (vector.size < 3 || vector.take(4).any { !it.isFinite() } || !timeMillis.isFinite()) return null
        var x = vector[0].toDouble(); var y = vector[1].toDouble(); var z = vector[2].toDouble()
        var w = if (vector.size >= 4) vector[3].toDouble() else sqrt((1 - x*x - y*y - z*z).coerceAtLeast(0.0))
        val length = sqrt(w*w + x*x + y*y + z*z)
        if (length < .000001) return null
        x /= length; y /= length; z /= length; w /= length
        screenChanged(rotation)
        fun rotate(v: DoubleArray) = doubleArrayOf(
            (1-2*(y*y+z*z))*v[0] + 2*(x*y-w*z)*v[1] + 2*(x*z+w*y)*v[2],
            2*(x*y+w*z)*v[0] + (1-2*(x*x+z*z))*v[1] + 2*(y*z-w*x)*v[2],
            2*(x*z-w*y)*v[0] + 2*(y*z+w*x)*v[1] + (1-2*(x*x+y*y))*v[2])
        val angle = rotation * PI / 2
        val forward = rotate(doubleArrayOf(sin(angle), cos(angle), 0.0))
        val frame = reference ?: arrayOf(forward, rotate(doubleArrayOf(cos(angle), -sin(angle), 0.0)),
            rotate(doubleArrayOf(0.0, 0.0, 1.0))).also { reference = it }
        fun dot(a: DoubleArray, b: DoubleArray) = a[0]*b[0] + a[1]*b[1] + a[2]*b[2]
        val depth = max(.001, dot(forward, frame[0]))
        return filtered(atan2(dot(forward, frame[1]), depth) * 180 / PI,
            -atan2(dot(forward, frame[2]), depth) * 180 / PI, sensitivity, timeMillis)
    }

    fun updateGravity(vector: FloatArray, sensitivity: Int, rotation: Int, timeMillis: Double): Position? {
        if (vector.size < 3 || vector.take(3).any { !it.isFinite() } || !timeMillis.isFinite()) return null
        val x = vector[0].toDouble(); val y = vector[1].toDouble(); val z = vector[2].toDouble()
        if (sqrt(x*x + y*y + z*z) !in 2.0..20.0) return null
        screenChanged(rotation)
        val beta = atan2(y, sqrt(x*x + z*z)) * 180 / PI
        val gamma = atan2(-x, z) * 180 / PI
        val origin = tiltReference ?: doubleArrayOf(beta, gamma).also { tiltReference = it }
        fun shortest(value: Double, from: Double) = (((value - from) % 360 + 540) % 360) - 180
        val dx = shortest(gamma, origin[1]); val dy = shortest(beta, origin[0]); val angle = rotation * PI / 2
        return filtered(dx*cos(angle) + dy*sin(angle), -dx*sin(angle) + dy*cos(angle), sensitivity, timeMillis)
    }

    private fun screenChanged(rotation: Int) { if (displayRotation != rotation) { displayRotation = rotation; calibrate() } }

    private fun filtered(x: Double, y: Double, sensitivity: Int, now: Double): Position? {
        val previous = previousTime
        if (previous != null && now < previous) return null
        val elapsed = if (previous == null) 16.0 else (now - previous).coerceIn(0.0, 1000.0)
        previousTime = now
        val weight = 1 - exp(-elapsed / 80)
        filteredX += weight * (x - filteredX); filteredY += weight * (y - filteredY)
        fun normalize(value: Double) = (sign(value) * max(0.0, abs(value) - .1) * (sensitivity.coerceIn(10, 100) / 60.0) / 25).coerceIn(-1.0, 1.0)
        position = Position((anchor.x + normalize(filteredX) / 2).coerceIn(0.0, 1.0), (anchor.y + normalize(filteredY) / 2).coerceIn(0.0, 1.0))
        return position
    }
}
