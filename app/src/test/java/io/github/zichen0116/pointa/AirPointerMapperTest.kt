package io.github.zichen0116.pointa

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AirPointerMapperTest {
    private val identity = floatArrayOf(0f, 0f, 0f, 1f)
    private fun rotate(axis: Int, radians: Double) = floatArrayOf(0f, 0f, 0f, cos(radians / 2).toFloat()).also { it[axis] = sin(radians / 2).toFloat() }

    @Test fun uprightOriginIsStableAndQuaternionSignDoesNotMatter() {
        val mapper = AirPointerMapper()
        val upright = rotate(0, PI / 2)
        assertEquals(AirPointerMapper.Position(), mapper.update(upright, 60, 0))
        assertEquals(AirPointerMapper.Position(), mapper.update(upright.map { -it }.toFloatArray(), 60, 0))
    }
    @Test fun yawAndPitchFollowIndependentAxesAndLandscapeRemaps() {
        val mapper = AirPointerMapper()
        mapper.update(identity, 60, 0)
        val yaw = mapper.update(rotate(2, .2), 60, 0)!!
        assertTrue(yaw.x < .5); assertEquals(.5, yaw.y, .0001)
        mapper.calibrate(); mapper.update(identity, 60, 0)
        assertTrue(mapper.update(rotate(0, .2), 60, 0)!!.y < .5)
        mapper.calibrate(); mapper.update(identity, 60, 1)
        val landscape = mapper.update(rotate(1, .2), 60, 1)!!
        assertTrue(landscape.y < .5); assertEquals(.5, landscape.x, .0001)
    }
    @Test fun activationContinuesAndCalibrationReturnsToCenter() {
        val mapper = AirPointerMapper()
        mapper.update(identity, 60, 0)
        val last = mapper.update(rotate(2, .2), 60, 0)
        mapper.begin()
        assertEquals(last, mapper.update(rotate(2, .8), 60, 0))
        mapper.calibrate()
        assertEquals(AirPointerMapper.Position(), mapper.update(rotate(1, .8), 60, 0))
    }
    @Test fun sensitivityBoundsDeadZoneAndInvalidSamplesAreSafe() {
        val low = AirPointerMapper(); val high = AirPointerMapper()
        low.update(identity, 10, 0); high.update(identity, 100, 0)
        assertTrue(low.update(rotate(2, .2), 10, 0)!!.x > high.update(rotate(2, .2), 100, 0)!!.x)
        high.calibrate(); high.update(identity, 100, 0)
        assertEquals(AirPointerMapper.Position(), high.update(rotate(0, .001), 100, 0))
        assertEquals(0.0, high.update(rotate(2, 1.0), 100, 0)!!.x, .0001)
        val last = high.position
        assertEquals(last, high.update(rotate(1, PI), 100, 0))
        assertNull(high.update(floatArrayOf(Float.NaN, 0f, 0f), 60, 0))
        assertNull(high.update(floatArrayOf(0f, 0f, 0f, 0f), 60, 0))
    }
    @Test fun uprightPhoneYawMatchesFlatPhoneYaw() {
        val flat = AirPointerMapper(); flat.update(identity, 60, 0)
        val upright = AirPointerMapper(); upright.update(rotate(0, PI / 2), 60, 0)
        val half = sqrt(.5)
        val rotated = floatArrayOf((half * cos(.1)).toFloat(), (half * sin(.1)).toFloat(),
            (half * sin(.1)).toFloat(), (half * cos(.1)).toFloat())
        val a = flat.update(rotate(2, .2), 60, 0)!!
        val b = upright.update(rotated, 60, 0)!!
        assertEquals(a.x, b.x, .0001); assertEquals(a.y, b.y, .0001)
        assertTrue(b.x < .5)
    }
}
