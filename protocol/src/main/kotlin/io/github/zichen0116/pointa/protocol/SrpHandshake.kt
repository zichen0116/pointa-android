package io.github.zichen0116.pointa.protocol

import org.bouncycastle.crypto.agreement.srp.SRP6Client
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.digests.SHA256Digest
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class SrpHandshake(
    private val serverId: String,
    private val pairingId: String,
    private val certificatePin: String,
    private val client: SRP6Client = SRP6Client(),
) {
    init { client.init(SRP6StandardGroups.rfc5054_3072, SHA256Digest(), SecureRandom()) }

    fun proof(salt: String, b: String, code: String): Pair<String, String> {
        val publicB = integer(b, 384)
        ensure(publicB.signum() > 0 && publicB < SRP6StandardGroups.rfc5054_3072.n, "invalid_proof")
        val identity = "pointa-srp-v1\n$serverId\n$pairingId\n$certificatePin".toByteArray(Charsets.UTF_8)
        val password = code.toByteArray(Charsets.US_ASCII)
        val a = try { client.generateClientCredentials(hexBytes(salt, 16), identity, password) } finally { password.fill(0) }
        client.calculateSecret(publicB)
        return encode(a, 384) to encode(client.calculateClientEvidenceMessage(), 32)
    }

    fun confirm(m2: String): String {
        ensure(client.verifyServerEvidenceMessage(integer(m2, 32)), "invalid_proof")
        val key = hexBytes(encode(client.calculateSessionKey(), 32), 32)
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac.doFinal("pointa-srp-confirm-v1\n$pairingId".toByteArray(Charsets.UTF_8)).toHex()
        } finally { key.fill(0) }
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun hexBytes(text: String, bytes: Int): ByteArray {
    ensure(text.length == bytes * 2 && text.all { it in '0'..'9' || it in 'a'..'f' }, "invalid_proof")
    return ByteArray(bytes) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
internal fun integer(text: String, bytes: Int) = BigInteger(1, hexBytes(text, bytes))
internal fun encode(value: BigInteger, bytes: Int) = value.toString(16).padStart(bytes * 2, '0')
