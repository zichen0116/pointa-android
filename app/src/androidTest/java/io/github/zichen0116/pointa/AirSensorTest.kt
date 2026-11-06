package io.github.zichen0116.pointa

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AirSensorTest {
    @Test fun realSensorSamplingStopsAndHidesOnRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val received = CountDownLatch(1)
        val samples = AtomicInteger()
        val hides = AtomicInteger()
        val held = AtomicBoolean()
        var controller: AirPointerController? = null
        try {
            instrumentation.runOnMainSync {
                controller = AirPointerController(instrumentation.targetContext,
                    move = { x, y -> assertTrue(x in 0.0..1.0 && y in 0.0..1.0); samples.incrementAndGet(); received.countDown() },
                    hide = { hides.incrementAndGet() }, changed = held::set, unavailable = {})
            }
            assumeTrue("Device does not provide rotation vectors", controller!!.available)
            instrumentation.runOnMainSync { controller!!.start() }
            assertTrue("Rotation sensor should deliver a sample", received.await(5, TimeUnit.SECONDS))
            assertTrue(held.get())
            instrumentation.runOnMainSync { controller!!.calibrate(); controller!!.stop() }
            val stoppedCount = samples.get()
            Thread.sleep(150)
            assertFalse(held.get())
            assertEquals(1, hides.get())
            assertEquals("Released sensor cannot send late movement", stoppedCount, samples.get())
        } finally { instrumentation.runOnMainSync { controller?.stop() } }
    }
}
