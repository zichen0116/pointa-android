package io.github.zichen0116.pointa

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AirPointerMapperTest {
    private val identity = floatArrayOf(0f, 0f, 0f, 1f)
    private fun rotate(axis: Int, radians: Double) = floatArrayOf(0f, 0f, 0f, cos(radians / 2).toFloat()).also { it[axis] = sin(radians / 2).toFloat() }

    @Test fun matchesOriginalJavascriptOrientationAndGravityStreams() {
        val rows = javaClass.getResourceAsStream("/air-reference.csv")!!.bufferedReader().use { it.readLines().drop(1) }.map { it.split(',') }
        for (stream in rows.groupBy { it[0] }.values) {
            val mapper = AirPointerMapper()
            for (row in stream) {
                val vector = (4..7).map { row[it].toFloat() }.toFloatArray()
                val actual = (if (row[2].toBoolean()) mapper.updateGravity(vector, 60, row[1].toInt(), row[3].toDouble())
                    else mapper.update(vector, 60, row[1].toInt(), row[3].toDouble()))!!
                assertEquals(row[0], row[8].toDouble(), actual.x, .00001)
                assertEquals(row[0], row[9].toDouble(), actual.y, .00001)
            }
        }
        assertEquals(128, rows.size)
    }

    @Test fun calibrationAndQuaternionSignRemainStable() {
        val mapper = AirPointerMapper()
        val upright = rotate(0, PI/2)
        assertEquals(AirPointerMapper.Position(), mapper.update(upright, 60, 0, 0.0))
        assertEquals(AirPointerMapper.Position(), mapper.update(upright.map { -it }.toFloatArray(), 60, 0, 16.0))
        mapper.calibrate()
        assertEquals(AirPointerMapper.Position(), mapper.update(identity, 60, 0, 32.0))
    }

    @Test fun holdResumesFromPreviousPointAndRotationRecenters() {
        val mapper = AirPointerMapper(); mapper.update(identity, 60, 0, 0.0)
        val last = mapper.update(rotate(2,.4), 60, 0, 200.0)
        assertTrue(last!!.x < .5)
        mapper.begin()
        assertEquals(last, mapper.update(rotate(2,.8), 60, 0, 300.0))
        assertEquals(AirPointerMapper.Position(), mapper.update(rotate(2,.8), 60, 1, 400.0))
    }

    @Test fun lowPassIsIndependentOfSamplingRateAndRejectsOldSamples() {
        fun sample(step: Int): Double {
            val mapper = AirPointerMapper(); mapper.update(identity, 60, 0, 0.0)
            for (time in step..160 step step) mapper.update(rotate(2,.3), 60, 0, time.toDouble())
            assertNull(mapper.update(identity, 60, 0, 159.0))
            return mapper.position.x
        }
        assertEquals(sample(10), sample(20), .00001)
    }

    @Test fun deadZoneSensitivityClampingAndInvalidSensors() {
        val mapper = AirPointerMapper(); mapper.update(identity, 60, 0, 0.0)
        assertEquals(AirPointerMapper.Position(), mapper.update(rotate(0,.001), 60, 0, 1000.0))
        assertEquals(0.0, mapper.update(rotate(2,1.0), 100, 0, 2000.0)!!.x, .00001)
        assertNull(mapper.update(floatArrayOf(Float.NaN,0f,0f),60,0,3000.0))
        assertNull(mapper.update(floatArrayOf(0f,0f,0f,0f),60,0,3000.0))
        assertNull(mapper.updateGravity(floatArrayOf(0f,0f,0f),60,0,3000.0))
        assertNull(mapper.updateGravity(floatArrayOf(0f,0f,25f),60,0,3000.0))
    }
}
