package io.github.zichen0116.pointa.protocol

import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ClientTest {
    @Test fun newTouchAfterReleaseIsNotClearedByAnOlderHide() {
        Fixture().use { f ->
            f.autoApprove = true
            f.client.connectQr(f.invitation())
            f.events.phase(ConnectionPhase.CONNECTED)
            repeat(100) { f.client.move(.1, .1); f.client.hide() }
            f.client.move(.9, .9)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            var latestArrived = false
            while (System.nanoTime() < deadline && !latestArrived) {
                val message = f.received.poll(100, TimeUnit.MILLISECONDS) ?: continue
                latestArrived = message.text("type") == "pointer.move" && message["x"]?.jsonPrimitive?.doubleOrNull == .9
            }
            assertTrue("The new touch must eventually be sent", latestArrived)
        }
    }
    @Test fun qrApprovalControlClockAndCooldownWork() {
        Fixture().use { f ->
            f.autoApprove = true
            f.client.connectQr(f.invitation())
            assertEquals(ConnectionPhase.CONNECTED, f.events.phase(ConnectionPhase.CONNECTED).phase)
            assertEquals(f.pin, f.events.paired.poll(3, TimeUnit.SECONDS)?.certificateSha256)
            f.client.move(0.2, 0.8)
            val move = f.message("pointer.move")
            assertTrue(kotlin.math.abs(move.number("ts") - f.time) < 3000)
            f.client.hide()
            val hide = f.message("pointer.hide")
            assertTrue(hide.number("seq") > move.number("seq"))
            repeat(10) { f.client.page(true) }
            f.message("slide.next")
            assertNotNull(f.events.actions.poll(3, TimeUnit.SECONDS))
            assertNull(f.received.poll(100, TimeUnit.MILLISECONDS))
            f.client.updateEffect(LaserEffect(size = 20))
            f.message("effect.update")
        }
    }

    @Test fun approvalCannotPrecedePending() {
        Fixture().use { f ->
            f.afterAuth = { socket -> socket.send(f.approved()) }
            f.client.connectQr(f.invitation())
            assertEquals("invalid_phase", f.events.phase(ConnectionPhase.FAILED).error)
            assertTrue(f.events.paired.isEmpty())
        }
    }

    @Test fun candidateTlsCannotBypassSrpOrReceiveCredentials() {
        Fixture().use { f ->
            f.afterAuth = { socket -> socket.send(Wire.message("pair.pending")) }
            f.client.pairCode("127.0.0.1", "012345", f.server.port)
            assertEquals("invalid_phase", f.events.phase(ConnectionPhase.FAILED).error)
            assertEquals("pair.srp.begin", f.received.poll(3, TimeUnit.SECONDS)?.text("type"))
            assertTrue(f.events.paired.isEmpty())
        }
    }

    @Test fun actualDerPinIsRequiredBeforeQrTokenLeavesClient() {
        Fixture().use { f ->
            f.client.connectQr(f.invitation().copy(certificateSha256 = "0".repeat(64)))
            assertEquals("certificate_changed", f.events.phase(ConnectionPhase.FAILED).error)
            assertTrue(f.received.isEmpty())
            assertTrue(f.events.paired.isEmpty())
        }
    }

    @Test fun storedIdentityIsCheckedBeforeCredentialLeavesClient() {
        Fixture().use { f ->
            f.client.reconnect(f.known().copy(serverId = "c".repeat(32)))
            assertEquals("identity_changed", f.events.phase(ConnectionPhase.FAILED).error)
            assertTrue(f.received.isEmpty())
            assertTrue(f.events.paired.isEmpty())
        }
    }

    @Test fun discoveredIdentityIsCheckedBeforeSrp() {
        Fixture().use { f ->
            f.client.pairCode(DiscoveredComputer("c".repeat(32), "PC", "127.0.0.1", f.server.port, f.pair, f.time), "012345")
            assertEquals("identity_changed", f.events.phase(ConnectionPhase.FAILED).error)
            assertTrue(f.received.isEmpty())
        }
    }

    @Test fun duplicateFieldsAndOversizedServerMessagesFailClosed() {
        listOf("{\"v\":1,\"v\":1}", "{\"v\":1,\"text\":\"${"a".repeat(4096)}\"}").forEach { message ->
            Fixture().use { f ->
                f.firstMessage = message
                f.client.connectQr(f.invitation())
                assertTrue(f.events.phase(ConnectionPhase.FAILED).error in setOf("duplicate_property", "message_too_large"))
                assertTrue(f.received.isEmpty())
            }
        }
    }

    @Test fun credentialPinInApprovalMustMatchTransport() {
        Fixture().use { f ->
            f.afterAuth = { socket ->
                socket.send(Wire.message("pair.pending"))
                socket.send(f.approved().replace(f.pin, "0".repeat(64)))
            }
            f.client.connectQr(f.invitation())
            assertEquals("certificate_changed", f.events.phase(ConnectionPhase.FAILED).error)
            assertTrue(f.events.paired.isEmpty())
        }
    }

    @Test fun resumeReceivesDisplayAndForgetIsDeliveredBeforeClose() {
        Fixture().use { f ->
            f.afterAuth = { it.send(f.ready()) }
            f.client.reconnect(f.known())
            f.events.phase(ConnectionPhase.CONNECTED)
            assertEquals(DisplayInfo(1920, 1080), f.events.displays.poll(3, TimeUnit.SECONDS))
            assertTrue(f.events.paired.isEmpty())
            f.client.forget()
            f.message("device.forget")
            f.events.phase(ConnectionPhase.DISCONNECTED)
        }
    }

    private class Events : PointaListener {
        val states = LinkedBlockingQueue<ConnectionState>()
        val paired = LinkedBlockingQueue<KnownComputer>()
        val actions = LinkedBlockingQueue<String>()
        val displays = LinkedBlockingQueue<DisplayInfo>()
        override fun onState(state: ConnectionState) { states.add(state) }
        override fun onPaired(computer: KnownComputer) { paired.add(computer) }
        override fun onActionResult(id: String, success: Boolean, error: String?) { actions.add(id) }
        override fun onDisplay(display: DisplayInfo?) { display?.let { displays.add(it) } }
        fun phase(phase: ConnectionPhase): ConnectionState {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline) {
                val state = states.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (state.phase == phase) return state
                if (state.phase == ConnectionPhase.FAILED) error("Unexpected failure ${state.error}")
            }
            error("Timed out waiting for $phase")
        }
    }

    private class Fixture : Closeable {
        val server = MockWebServer()
        private val cert = HeldCertificate.Builder().commonName("pointa-test").addSubjectAlternativeName("127.0.0.1").build()
        val pin = MessageDigest.getInstance("SHA-256").digest(cert.certificate.encoded).toHex()
        val id = "a".repeat(32)
        val pair = "b".repeat(32)
        private val device = "d".repeat(32)
        private val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
        val time = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        val events = Events()
        val client = PointaClient("Phone", events)
        val received = LinkedBlockingQueue<JsonObject>()
        @Volatile var autoApprove = false
        @Volatile var firstMessage: String? = null
        @Volatile var afterAuth: ((WebSocket) -> Unit)? = null

        init {
            val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
            server.useHttps(tls.sslSocketFactory(), false)
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(firstMessage ?: Wire.message("hello") {
                        put("serverId", id); put("name", "PC"); put("pairingId", pair)
                        put("time", time); put("heartbeatMs", 1000); put("maxMessageBytes", 4096)
                    })
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val data = Wire.parse(text)
                    val type = data.text("type")
                    if (type == "heartbeat") {
                        webSocket.send(Wire.message("heartbeat.ack") { put("time", time) })
                        return
                    }
                    received.add(data)
                    if (type.startsWith("pair.") || type == "auth.resume") {
                        if (autoApprove) {
                            webSocket.send(Wire.message("pair.pending"))
                            webSocket.send(approved())
                            webSocket.send(ready())
                        } else afterAuth?.invoke(webSocket)
                    }
                    if (type.startsWith("slide.")) webSocket.send(Wire.message("action.result") {
                        put("id", data.text("id")); put("success", true); put("error", JsonNull)
                    })
                    if (type == "device.forget") webSocket.close(1000, "forgotten")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            }))
            server.start()
        }

        fun invitation() = QrInvitation("127.0.0.1", server.port, id, pair, pin, secret)
        fun known() = KnownComputer(id, "PC", "127.0.0.1", server.port, pin, device, secret)
        fun approved() = Wire.message("pair.approved") {
            put("serverId", id); put("certificateSha256", pin); put("deviceId", device); put("credential", secret)
        }
        fun ready() = Wire.message("session.ready") {
            put("deviceId", device); put("time", time)
            putJsonObject("effect") { put("color", "#FF6475"); put("size", 16); put("trailMs", 500); put("smoothing", "standard") }
            putJsonObject("display") { put("width", 1920); put("height", 1080) }
        }
        fun message(type: String): JsonObject {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                val value = received.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (value.text("type") == type) return value
            }
            error("Missing $type")
        }
        override fun close() { client.close(); server.shutdown() }
    }
}
