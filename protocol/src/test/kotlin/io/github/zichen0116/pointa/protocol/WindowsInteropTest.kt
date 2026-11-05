package io.github.zichen0116.pointa.protocol

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class WindowsInteropTest {
    @Test fun realWindowsQrSrpResumeRevocationAndPointerLifecycle() {
        val dll = System.getenv("POINTA_INTEROP_HOST")
        assumeTrue("Set POINTA_INTEROP_HOST to the built test-only Windows host", dll != null && File(dll).isFile)
        val port = ServerSocket(0).use { it.localPort }
        val process = ProcessBuilder(System.getenv("POINTA_DOTNET") ?: "dotnet", dll!!, "--port", port.toString()).start()
        val output = LinkedBlockingQueue<JsonObject>()
        val input = process.outputStream.bufferedWriter()
        thread(isDaemon = true) { process.inputStream.bufferedReader().forEachLine { line -> runCatching { Wire.parse(line) }.getOrNull()?.let(output::add) } }
        thread(isDaemon = true) { process.errorStream.bufferedReader().forEachLine { /* Never echo pairing fixtures or credentials into test reports. */ } }
        fun command(command: String, fields: JsonObjectBuilder.() -> Unit = {}) {
            input.write(buildJsonObject { put("command", command); fields() }.toString()); input.newLine(); input.flush()
        }
        fun event(name: String, predicate: (JsonObject) -> Boolean = { true }): JsonObject {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
            while (System.nanoTime() < deadline) {
                val value = output.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (value.text("event") == name && predicate(value)) return value
            }
            error("Missing Windows test event: $name")
        }
        val events = Events()
        val client = PointaClient("Android JVM interop", events)
        try {
            val fixture = event("ready")
            client.connectQr(QrInvitation.parse(fixture.text("qr", 1024)))
            events.waitFor(ConnectionPhase.CONNECTED)
            val qrDevice = events.paired.poll(5, TimeUnit.SECONDS) ?: error("Missing QR credential")
            client.move(.2, .8)
            event("pointer") { it.boolean("active") }
            client.hide()
            event("pointer") { !it.boolean("active") }
            client.page(true)
            assertEquals(1L, event("slide").number("count"))
            client.disconnect()
            events.waitFor(ConnectionPhase.DISCONNECTED)
            event("state") { it["connectedDeviceId"] == JsonNull }
            client.reconnect(qrDevice)
            events.waitFor(ConnectionPhase.CONNECTED)
            command("state")
            val snapshot = event("snapshot")
            assertEquals(1L, snapshot.number("slides"))
            assertFalse(snapshot.getValue("pointer").jsonObject.boolean("active"))
            command("display") { put("width", 1080); put("height", 1920) }
            assertEquals(DisplayInfo(1080, 1920), events.displays.poll(3, TimeUnit.SECONDS))
            command("revoke") { put("deviceId", qrDevice.deviceId) }
            events.waitFor(ConnectionPhase.FAILED)
            client.reconnect(qrDevice)
            assertEquals("invalid_credential", events.waitFor(ConnectionPhase.FAILED).error)
            command("refresh")
            val codeFixture = event("fixture")
            client.pairCode("127.0.0.1", codeFixture.text("code", 6), port)
            events.waitFor(ConnectionPhase.CONNECTED)
            val srpDevice = events.paired.poll(5, TimeUnit.SECONDS) ?: error("Missing SRP credential")
            assertEquals(codeFixture.text("certificateSha256", 64), srpDevice.certificateSha256)
            assertEquals(qrDevice.serverId, srpDevice.serverId)
            client.updateEffect(LaserEffect("#32B894", 20, 800, "high"))
            assertEquals(20L, event("effect").getValue("effect").jsonObject.number("size"))
            client.forget()
            events.waitFor(ConnectionPhase.DISCONNECTED)
            client.reconnect(srpDevice)
            assertEquals("invalid_credential", events.waitFor(ConnectionPhase.FAILED).error)
        } finally {
            client.close()
            runCatching { command("quit") }
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        assertEquals("Windows test host stopped cleanly", 0, process.exitValue())
    }

    private class Events : PointaListener {
        val states = LinkedBlockingQueue<ConnectionState>()
        val paired = LinkedBlockingQueue<KnownComputer>()
        val displays = LinkedBlockingQueue<DisplayInfo>()
        override fun onState(state: ConnectionState) { states.add(state) }
        override fun onPaired(computer: KnownComputer) { paired.add(computer) }
        override fun onDisplay(display: DisplayInfo?) { display?.let(displays::add) }
        fun waitFor(phase: ConnectionPhase): ConnectionState {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
            while (System.nanoTime() < deadline) {
                val state = states.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (state.phase == phase) return state
                if (state.phase == ConnectionPhase.FAILED) error("Unexpected protocol failure: ${state.error}")
            }
            error("Missing client phase: $phase")
        }
    }
}
