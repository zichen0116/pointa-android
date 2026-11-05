package io.github.zichen0116.pointa.protocol

data class KnownComputer(
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int = 32145,
    val certificateSha256: String,
    val deviceId: String,
    val credential: String,
    val lastConnectedAt: Long = 0,
) {
    override fun toString() = "KnownComputer(name=$name, host=$host, credentials=<redacted>)"
}

data class LaserEffect(
    val color: String = "#FF6475",
    val size: Int = 16,
    val trailMs: Int = 500,
    val smoothing: String = "standard",
) {
    fun isValid() = color.matches(Regex("#[0-9a-fA-F]{6}")) && size in 6..40 &&
        trailMs in 0..1200 && smoothing in setOf("low", "standard", "high")
}

data class DisplayInfo(val width: Int, val height: Int)

enum class ConnectionPhase { DISCONNECTED, CONNECTING, PAIRING, WAITING_APPROVAL, CONNECTED, FAILED }

data class ConnectionState(val phase: ConnectionPhase, val name: String = "", val error: String? = null)

data class DiscoveredComputer(
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int,
    val pairingId: String,
    val expiresAt: Long,
)

/** Callbacks run on a dedicated protocol thread; implementations must not block. */
interface PointaListener {
    fun onState(state: ConnectionState)
    /** Only called after authentication and explicit Windows approval. Persist using Keystore. */
    fun onPaired(computer: KnownComputer)
    fun onActionResult(id: String, success: Boolean, error: String?) {}
    fun onEffect(effect: LaserEffect) {}
    fun onDisplay(display: DisplayInfo?) {}
    fun onEffectRejected(error: String) {}
}
