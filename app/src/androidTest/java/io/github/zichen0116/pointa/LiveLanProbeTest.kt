package io.github.zichen0116.pointa

import androidx.test.platform.app.InstrumentationRegistry
import io.github.zichen0116.pointa.protocol.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in probe using saved trust; it sends heartbeats only and does not edit app data. */
class LiveLanProbeTest {
    @Test(timeout = 90_000) fun savedTrustMaintainsARealLanSession() {
        val arguments = InstrumentationRegistry.getArguments()
        val host = arguments.getString("pointa.liveHost")
        val name = arguments.getString("pointa.liveComputer")
        assumeTrue("An explicit live LAN target is required", host != null && name != null)
        require(isLanAddress(host!!))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        android.util.Log.i("PointaProbe", "Loading saved trust")
        val computer = SecureDeviceStore(context).load().single { it.name == name }
        android.util.Log.i("PointaProbe", "Saved trust loaded")
        val events = LinkedBlockingQueue<ConnectionState>()
        val client = PointaClient("Pointa network check", object : PointaListener {
            override fun onState(state: ConnectionState) { android.util.Log.i("PointaProbe", "${state.phase}: ${state.error}"); events.add(state) }
            override fun onPaired(computer: KnownComputer) { error("A saved-trust probe must not create a pairing") }
        })
        try {
            client.reconnect(computer.copy(host = host))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            var connected = false
            while (System.nanoTime() < deadline && !connected) {
                val state = events.poll(1, TimeUnit.SECONDS) ?: continue
                assertNotEquals("Handshake failed: ${state.error}", ConnectionPhase.FAILED, state.phase)
                connected = state.phase == ConnectionPhase.CONNECTED
            }
            assertTrue("Live LAN handshake did not complete", connected)
            android.util.Log.i("PointaProbe", "Checking heartbeats for 45 seconds")
            repeat(45) {
                val state = events.poll(1, TimeUnit.SECONDS)
                assertNull("Live session ended: ${state?.phase} / ${state?.error}", state)
            }
        } finally { client.disconnect(); client.close() }
    }
}
