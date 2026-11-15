package io.github.zichen0116.pointa.protocol

import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/** No automatic retries. Callbacks are serialized on the protocol thread, never the UI thread. */
class PointaClient(deviceName: String, private val listener: PointaListener, private val diagnostic: (String) -> Unit = {}) : Closeable {
    private val name = deviceName.trim().also {
        require(it.length in 1..64 && it.none(Char::isISOControl)) { "invalid_name" }
    }
    private enum class Stage { NONE, HELLO, SRP_CHALLENGE, SRP_SERVER, PENDING, APPROVAL, READY, CONNECTED, FORGETTING }
    private data class Attempt(val host: String, val port: Int, val qr: QrInvitation? = null,
                               var code: String? = null, val known: KnownComputer? = null,
                               val discovered: DiscoveredComputer? = null)
    private data class Move(val generation: Long, val x: Double, val y: Double)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "pointa-protocol").apply { isDaemon = true }
    }
    private var stage = Stage.NONE
    private var attempt: Attempt? = null
    private var tls: TlsConnection? = null
    private var socket: WebSocket? = null
    private var srp: SrpHandshake? = null
    private var serverId = ""
    private var pairingId = ""
    private var computerName = ""
    private var deviceId = ""
    private var generation = 0L
    @Volatile private var currentGeneration = 0L
    @Volatile private var connected = false
    @Volatile private var closed = false
    private val pendingMove = AtomicReference<Move>()
    private val pointerOrder = AtomicLong()
    private var startedAt = 0L
    private var deadline = 0L
    private var serverTime = 0L
    private var timeAnchor = 0L
    private var lastMessage = 0L
    private var lastHeartbeat = 0L
    private var lastSlide = Long.MIN_VALUE / 2
    private var lastEffect = Long.MIN_VALUE / 2
    private var sequence = 0L
    private val actions = mutableSetOf<String>()
    private var shownDisplay: DisplayInfo? = null
    private var ticker: ScheduledFuture<*>? = null

    fun connectQr(invitation: QrInvitation) = post { start(Attempt(invitation.host, invitation.port, qr = invitation)) }

    fun pairCode(host: String, code: String, port: Int = 32145) {
        require(isLanAddress(host) && port in 1..65535) { "invalid_address" }
        require(code.matches(Regex("[0-9]{6}"))) { "invalid_code" }
        post { start(Attempt(host, port, code = code)) }
    }

    fun pairCode(computer: DiscoveredComputer, code: String) {
        require(isLanAddress(computer.host) && computer.port in 1..65535) { "invalid_address" }
        require(code.matches(Regex("[0-9]{6}"))) { "invalid_code" }
        require(idPattern.matches(computer.serverId) && idPattern.matches(computer.pairingId)) { "invalid_identity" }
        post { start(Attempt(computer.host, computer.port, code = code, discovered = computer)) }
    }

    fun reconnect(device: KnownComputer) {
        require(isLanAddress(device.host) && device.port in 1..65535) { "invalid_address" }
        require(idPattern.matches(device.serverId) && idPattern.matches(device.deviceId) &&
            pinPattern.matches(device.certificateSha256) && validSecret(device.credential)) { "invalid_credential" }
        post { start(Attempt(device.host, device.port, known = device)) }
    }

    fun move(x: Double, y: Double) {
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0) { "invalid_pointer" }
        val token = currentGeneration
        if (connected && !closed) { pointerOrder.incrementAndGet(); pendingMove.set(Move(token, x, y)) }
    }

    fun hide() {
        val order = pointerOrder.incrementAndGet()
        pendingMove.set(null)
        post { if (stage == Stage.CONNECTED && pointerOrder.get() == order) control("pointer.hide") }
    }

    fun page(next: Boolean) = postConnected {
        val now = monotonicMillis()
        if (now - lastSlide < 180) return@postConnected
        lastSlide = now
        val id = UUID.randomUUID().toString()
        if (actions.size >= 32) { fail("network_timeout"); return@postConnected }
        actions.add(id)
        control(if (next) "slide.next" else "slide.prev") { put("id", id) }
    }

    fun updateEffect(effect: LaserEffect) {
        require(effect.isValid()) { "invalid_effect" }
        postConnected {
            val now = monotonicMillis()
            if (now - lastEffect < 250) { listener.onEffectRejected("effect_cooldown"); return@postConnected }
            lastEffect = now
            control("effect.update") {
                put("color", effect.color); put("size", effect.size)
                put("trailMs", effect.trailMs); put("smoothing", effect.smoothing)
            }
        }
    }

    fun forget() = postConnected {
        pendingMove.set(null)
        if (stage == Stage.CONNECTED) {
            control("device.forget")
            connected = false
            stage = Stage.FORGETTING
            deadline = monotonicMillis() + 2000
        } else disconnectInternal()
    }

    fun disconnect() {
        connected = false
        pendingMove.set(null)
        post { disconnectInternal() }
    }

    override fun close() {
        if (closed) return
        closed = true
        connected = false
        pendingMove.set(null)
        try {
            executor.execute {
                guarded { cleanup(); emit(ConnectionPhase.DISCONNECTED) }
                executor.shutdown()
            }
        } catch (_: RejectedExecutionException) { }
    }

    private fun start(value: Attempt) {
        cleanup()
        diagnostic("Connecting to ${value.host}:${value.port}")
        attempt = value
        computerName = value.known?.name ?: value.host
        startedAt = monotonicMillis()
        stage = Stage.HELLO
        ticker = executor.scheduleAtFixedRate({ guarded { tick() } }, 0, 16_666_667, TimeUnit.NANOSECONDS)
        deadline = startedAt + 15_000
        emit(ConnectionPhase.CONNECTING)
        val connection = TlsConnection(value.qr?.certificateSha256 ?: value.known?.certificateSha256)
        tls = connection
        val token = generation
        socket = connection.client.newWebSocket(Request.Builder().url("wss://${value.host}:${value.port}/ws").build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) = callback(token) { receive(text) }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = callback(token) { fail("text_required") }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = callback(token) {
                    diagnostic("Transport failure: " + generateSequence(t) { it.cause }.take(5).joinToString(" / ") { "${it.javaClass.simpleName}: ${it.message?.take(180)}" })
                    if (stage == Stage.FORGETTING) disconnectInternal() else fail(connectionError(t))
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = callback(token) {
                    webSocket.close(code, null)
                    if (stage == Stage.FORGETTING) disconnectInternal() else fail("network_closed")
                }
            })
    }

    private fun receive(text: String) {
        val data = Wire.parse(text)
        ensure(data.number("v") == 1L, "unsupported_version")
        val type = data.text("type", 32)
        if (type == "error") { fail(data.text("code", 64)); return }
        when (stage) {
            Stage.HELLO -> {
                ensure(type == "hello", "invalid_phase")
                val value = attempt!!
                serverId = data.text("serverId", 32); pairingId = data.text("pairingId", 32)
                ensure(idPattern.matches(serverId) && idPattern.matches(pairingId), "invalid_identity")
                value.discovered?.let {
                    ensure(serverId == it.serverId, "identity_changed")
                    // Discovery is only a locator: use the current SRP session after the code refreshes.
                }
                computerName = data.text("name", 64)
                ensure(data.number("heartbeatMs") == 1000L && data.number("maxMessageBytes") == 4096L, "unsupported_protocol")
                ensure(tls?.actualPin?.get() != null, "invalid_certificate")
                syncTime(data.number("time"))
                when {
                    value.known != null -> {
                        ensure(serverId == value.known.serverId, "identity_changed")
                        deviceId = value.known.deviceId
                        send("auth.resume") { put("deviceId", deviceId); put("credential", value.known.credential) }
                        next(Stage.READY)
                    }
                    value.qr != null -> {
                        ensure(serverId == value.qr.serverId, "identity_changed")
                        ensure(pairingId == value.qr.pairingId, "pairing_expired")
                        send("pair.qr") { put("name", name); put("pairingId", pairingId); put("token", value.qr.token) }
                        next(Stage.PENDING)
                        emit(ConnectionPhase.PAIRING)
                    }
                    else -> {
                        srp = SrpHandshake(serverId, pairingId, tls!!.actualPin.get())
                        send("pair.srp.begin") { put("name", name); put("pairingId", pairingId) }
                        next(Stage.SRP_CHALLENGE)
                        emit(ConnectionPhase.PAIRING)
                    }
                }
            }
            Stage.SRP_CHALLENGE -> {
                ensure(type == "pair.srp.challenge", "invalid_phase")
                ensure(data.text("group") == "rfc5054-3072" && data.text("hash") == "SHA-256", "unsupported_protocol")
                val proof = srp!!.proof(data.text("salt", 32), data.text("b", 768), attempt!!.code!!)
                attempt!!.code = null
                send("pair.srp.proof") { put("a", proof.first); put("m1", proof.second) }
                next(Stage.SRP_SERVER)
            }
            Stage.SRP_SERVER -> {
                ensure(type == "pair.srp.server", "invalid_phase")
                val confirmation = srp!!.confirm(data.text("m2", 64))
                srp = null
                send("pair.srp.confirm") { put("confirmation", confirmation) }
                next(Stage.PENDING)
            }
            Stage.PENDING -> {
                ensure(type == "pair.pending", "invalid_phase")
                next(Stage.APPROVAL, 30_000)
                emit(ConnectionPhase.WAITING_APPROVAL)
            }
            Stage.APPROVAL -> {
                ensure(type == "pair.approved", "invalid_phase")
                ensure(data.text("serverId", 32) == serverId, "identity_changed")
                val pin = data.text("certificateSha256", 64)
                ensure(pin == tls!!.actualPin.get(), "certificate_changed")
                deviceId = data.text("deviceId", 32)
                val credential = data.text("credential", 43)
                ensure(idPattern.matches(deviceId) && validSecret(credential), "invalid_credential")
                val value = attempt!!
                val paired = KnownComputer(serverId, computerName, value.host, value.port, pin, deviceId, credential, System.currentTimeMillis())
                next(Stage.READY)
                listener.onPaired(paired)
            }
            Stage.READY -> {
                ensure(type == "session.ready", "invalid_phase")
                ensure(data.text("deviceId", 32) == deviceId, "identity_changed")
                val effect = data.effect(); val display = data.display()
                syncTime(data.number("time"))
                stage = Stage.CONNECTED; connected = true
                lastMessage = monotonicMillis(); lastHeartbeat = lastMessage - 1000
                attempt = null
                listener.onEffect(effect)
                shownDisplay = display; listener.onDisplay(display)
                emit(ConnectionPhase.CONNECTED)
            }
            Stage.CONNECTED -> {
                when (type) {
                    "heartbeat.ack" -> {
                        syncTime(data.number("time"), ((monotonicMillis() - lastHeartbeat) / 2).coerceIn(0, 500))
                        val display = data.display()
                        if (display != shownDisplay) { shownDisplay = display; listener.onDisplay(display) }
                    }
                    "action.result" -> {
                        val id = data.text("id", 36)
                        ensure(actions.remove(id), "invalid_action_id")
                        val success = data.boolean("success")
                        val error = if (data["error"] == JsonNull) null else data.text("error", 256)
                        listener.onActionResult(id, success, error)
                    }
                    "effect.applied" -> listener.onEffect(data.effect())
                    "effect.rejected" -> listener.onEffectRejected(data.text("error", 64))
                    else -> throw ProtocolFailure("invalid_phase")
                }
                lastMessage = monotonicMillis()
            }
            Stage.FORGETTING -> Unit
            else -> throw ProtocolFailure("invalid_phase")
        }
    }

    private fun next(value: Stage, timeout: Long = 15_000) { stage = value; deadline = monotonicMillis() + timeout }

    private fun tick() {
        if (stage == Stage.NONE) return
        val now = monotonicMillis()
        if (stage == Stage.FORGETTING) { if (now >= deadline) disconnectInternal(); return }
        if (stage != Stage.CONNECTED) {
            if (now >= deadline || now - startedAt >= 60_000) fail("authentication_timeout")
            return
        }
        if (now - lastMessage > 4500) { fail("network_timeout"); return }
        if (now - lastHeartbeat >= 1000) { lastHeartbeat = now; control("heartbeat") }
        pendingMove.getAndSet(null)?.let {
            if (it.generation == generation) control("pointer.move") { put("x", it.x); put("y", it.y); put("active", true) }
        }
    }

    private fun syncTime(time: Long, transit: Long = 0) {
        ensure(time in 1..Long.MAX_VALUE - 100_000, "invalid_time")
        serverTime = time + transit
        timeAnchor = monotonicMillis()
    }

    private fun control(type: String, fields: JsonObjectBuilder.() -> Unit = {}) {
        ensure(stage == Stage.CONNECTED, "invalid_phase")
        ensure(sequence < Long.MAX_VALUE, "invalid_sequence")
        sequence++
        send(type) { put("seq", sequence); put("ts", serverTime + monotonicMillis() - timeAnchor); fields() }
    }

    private fun send(type: String, fields: JsonObjectBuilder.() -> Unit = {}) {
        val value = socket ?: throw ProtocolFailure("network_closed")
        ensure(value.queueSize() <= 16_384, "network_slow")
        ensure(value.send(Wire.message(type, fields)), "network_closed")
    }

    private fun disconnectInternal() {
        if (stage == Stage.CONNECTED) runCatching { control("pointer.hide") }
        cleanup(graceful = true)
        emit(ConnectionPhase.DISCONNECTED)
    }

    private fun fail(error: String) { diagnostic("Failure at $stage: $error"); cleanup(); emit(ConnectionPhase.FAILED, error) }

    private fun cleanup(graceful: Boolean = false) {
        ticker?.cancel(false); ticker = null
        generation++; currentGeneration = generation
        pointerOrder.incrementAndGet()
        connected = false; pendingMove.set(null); stage = Stage.NONE
        val previous = socket
        socket = null
        if (graceful && previous != null) {
            previous.close(1000, "client disconnected")
            executor.schedule({ previous.cancel() }, 1, TimeUnit.SECONDS)
        } else previous?.cancel()
        tls?.release(); tls = null; srp = null; attempt?.code = null; attempt = null
        sequence = 0; actions.clear(); shownDisplay = null
        lastSlide = Long.MIN_VALUE / 2; lastEffect = Long.MIN_VALUE / 2
    }

    private fun emit(phase: ConnectionPhase, error: String? = null) = listener.onState(ConnectionState(phase, computerName, error))
    private fun postConnected(block: () -> Unit) {
        val token = currentGeneration
        if (!connected || closed) return
        post { if (token == generation && stage == Stage.CONNECTED) block() }
    }
    private fun post(block: () -> Unit) {
        if (closed) return
        try { executor.execute { if (!closed) guarded(block) } } catch (_: RejectedExecutionException) { }
    }
    private fun callback(token: Long, block: () -> Unit) = post { if (token == generation && stage != Stage.NONE) block() }
    private fun guarded(block: () -> Unit) {
        try { block() } catch (error: ProtocolFailure) { fail(error.code) }
        catch (error: Exception) { diagnostic("Protocol exception: ${error.javaClass.simpleName}"); fail("invalid_message") }
    }
    private fun monotonicMillis() = System.nanoTime() / 1_000_000
}
