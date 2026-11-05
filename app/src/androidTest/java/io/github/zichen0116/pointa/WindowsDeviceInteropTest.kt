package io.github.zichen0116.pointa

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.zichen0116.pointa.protocol.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WindowsDeviceInteropTest {
    @Test fun androidSrpKeystoreResumeAndControlReachRealWindows() {
        val encoded = InstrumentationRegistry.getArguments().getString("pointa.fixture")
        assumeTrue("Run scripts/device-smoke.ps1 for Windows interoperability", encoded != null)
        val fixture = JSONObject(String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
        val states = LinkedBlockingQueue<ConnectionState>()
        val paired = LinkedBlockingQueue<KnownComputer>()
        val actions = LinkedBlockingQueue<Boolean>()
        val store = SecureDeviceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        fun phase(expected: ConnectionPhase): ConnectionState {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline) {
                val state = states.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (state.phase == expected) return state
                if (state.phase == ConnectionPhase.FAILED) error("Unexpected connection error: ${state.error}")
            }
            error("Missing connection phase: $expected")
        }
        var id: String? = null
        PointaClient("Android emulator", object : PointaListener {
            override fun onState(state: ConnectionState) { states.add(state) }
            override fun onPaired(computer: KnownComputer) { paired.add(computer) }
            override fun onActionResult(id: String, success: Boolean, error: String?) { actions.add(success) }
        }).use { client ->
            try {
                client.pairCode("10.0.2.2", fixture.getString("code"), fixture.getInt("port"))
                phase(ConnectionPhase.CONNECTED)
                val computer = paired.poll(2, TimeUnit.SECONDS) ?: error("No authenticated computer")
                id = computer.serverId
                assertEquals(fixture.getString("certificateSha256"), computer.certificateSha256)
                store.remember(computer)
                assertEquals(computer, store.load().first { it.serverId == id })
                client.move(.25, .75); client.hide(); client.page(true)
                assertEquals(true, actions.poll(5, TimeUnit.SECONDS))
                client.disconnect(); phase(ConnectionPhase.DISCONNECTED)
                Thread.sleep(200)
                client.reconnect(store.load().first { it.serverId == id })
                phase(ConnectionPhase.CONNECTED)
                client.page(false)
                assertEquals(true, actions.poll(5, TimeUnit.SECONDS))
                client.forget(); phase(ConnectionPhase.DISCONNECTED)
                client.reconnect(computer)
                assertEquals("invalid_credential", phase(ConnectionPhase.FAILED).error)
            } finally { id?.let(store::forget) }
        }
    }
}
