package io.github.zichen0116.pointa.protocol

import java.net.URI
import java.util.Base64

internal val idPattern = Regex("[0-9a-f]{32}")
internal val pinPattern = Regex("[0-9a-f]{64}")
internal val secretPattern = Regex("[A-Za-z0-9_-]{43}")

internal fun validSecret(value: String): Boolean = secretPattern.matches(value) && runCatching {
    val bytes = Base64.getUrlDecoder().decode(value)
    bytes.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == value
}.getOrDefault(false)

/** Numeric private IPv4 only: no DNS, URLs, IPv6, octal notation, or public destinations. */
fun isLanAddress(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4 || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return false
    val octets = parts.map { it.toInt() }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 10 || octets[0] == 127 ||
        (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 192 && octets[1] == 168)
}

data class QrInvitation(
    val host: String,
    val port: Int,
    val serverId: String,
    val pairingId: String,
    val certificateSha256: String,
    val token: String,
) {
    init {
        require(isLanAddress(host) && port in 1..65535) { "invalid_address" }
        require(idPattern.matches(serverId) && idPattern.matches(pairingId)) { "invalid_identity" }
        require(pinPattern.matches(certificateSha256) && validSecret(token)) { "invalid_invitation" }
    }

    override fun toString() = "QrInvitation(host=$host, port=$port, token=<redacted>)"

    companion object {
        fun parse(text: String): QrInvitation {
            require(text.length in 1..1024 && text.none { it.isWhitespace() || it.isISOControl() }) { "invalid_qr" }
            val uri = try { URI(text) } catch (_: Exception) { throw IllegalArgumentException("invalid_qr") }
            require(uri.scheme == "pointa" && uri.rawAuthority == "pair" && uri.rawPath.isNullOrEmpty() &&
                uri.rawFragment == null) { "invalid_qr" }
            val fields = linkedMapOf<String, String>()
            for (field in (uri.rawQuery ?: throw IllegalArgumentException("invalid_qr")).split('&')) {
                val pair = field.split('=', limit = 2)
                require(pair.size == 2 && pair[0] !in fields) { "invalid_qr" }
                fields[pair[0]] = pair[1]
            }
            require(fields.keys == setOf("v", "host", "port", "id", "pair", "pin", "token") && fields["v"] == "1") { "invalid_qr" }
            val port = fields.getValue("port")
            require(port.matches(Regex("[1-9][0-9]{0,4}"))) { "invalid_port" }
            return QrInvitation(fields.getValue("host"), port.toInt(), fields.getValue("id"),
                fields.getValue("pair"), fields.getValue("pin"), fields.getValue("token"))
        }
    }
}
