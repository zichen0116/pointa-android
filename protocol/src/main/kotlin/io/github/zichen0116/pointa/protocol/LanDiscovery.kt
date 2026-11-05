package io.github.zichen0116.pointa.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

object LanDiscovery {
    /** Blocking bounded IO. Discovery identities remain untrusted until TLS/SRP authentication. */
    fun discover(timeoutMillis: Int = 1500): List<DiscoveredComputer> {
        require(timeoutMillis in 100..10_000)
        val results = linkedMapOf<String, DiscoveredComputer>()
        val destinations = linkedSetOf("255.255.255.255")
        NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp && !it.isLoopback }?.forEach { network ->
            network.interfaceAddresses.filter { it.address is Inet4Address && isLanAddress(it.address.hostAddress!!) }
                .mapNotNullTo(destinations) { it.broadcast?.hostAddress }
        }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val request = "POINTA_DISCOVER_V1\n".toByteArray(Charsets.US_ASCII)
            for (host in destinations) runCatching {
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName(host), 32146))
            }
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
            while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted) {
                socket.soTimeout = ((deadline - System.nanoTime()) / 1_000_000).coerceIn(1, 200).toInt()
                val packet = DatagramPacket(ByteArray(4097), 4097)
                try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                if (packet.address !is Inet4Address || packet.length > 4096) continue
                val computer = runCatching { parse(packet.data.copyOf(packet.length).toString(Charsets.UTF_8), packet.address.hostAddress!!) }.getOrNull() ?: continue
                results["${computer.serverId}@${computer.host}:${computer.port}"] = computer
                if (results.size >= 64) break
            }
        }
        return results.values.toList()
    }

    internal fun parse(message: String, host: String): DiscoveredComputer {
        ensure(isLanAddress(host), "invalid_address")
        val data = Wire.parse(message)
        ensure(data.number("v") == 1L && data.text("type") == "pointa.discovery")
        val id = data.text("serverId", 32); val pair = data.text("pairingId", 32)
        ensure(idPattern.matches(id) && idPattern.matches(pair), "invalid_identity")
        val port = data.number("port"); ensure(port in 1..65535, "invalid_port")
        val expiresAt = data.number("expiresAt"); ensure(expiresAt > 0, "invalid_expiry")
        return DiscoveredComputer(id, data.text("name", 64), host, port.toInt(), pair, expiresAt)
    }
}
