package io.github.zichen0116.pointa.protocol

import org.bouncycastle.crypto.agreement.srp.SRP6Client
import org.bouncycastle.crypto.agreement.srp.SRP6Server
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class ProtocolTest {
    private val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })
    private val qr = "pointa://pair?v=1&host=192.168.1.7&port=32145&id=${"a".repeat(32)}&pair=${"b".repeat(32)}&pin=${"c".repeat(64)}&token=$token"

    @Test fun qrParsesExactContractAndRedactsSecret() {
        val parsed = QrInvitation.parse(qr)
        assertEquals("192.168.1.7", parsed.host)
        assertEquals(32145, parsed.port)
        assertEquals(token, parsed.token)
        assertFalse(parsed.toString().contains(token))
    }

    @Test fun qrRejectsAmbiguousOrUnsafeDestinations() {
        val invalid = listOf(qr + "&host=10.0.0.1", qr + "&extra=1", qr + "#fragment", " $qr", qr.replace("pointa://", "https://"),
            qr.replace("pair?", "pair/?"), qr.replace("v=1", "v=2"), qr.replace("32145", "65536"), qr.replace("32145", "032145"),
            qr.replace("192.168.1.7", "8.8.8.8"), qr.replace("192.168.1.7", "localhost"), qr.replace("192.168.1.7", "192.168.01.7"),
            qr.replace("192.168.1.7", "%31%39%32.168.1.7"), qr.replace("id=aaaa", "id=AAAA"), qr.replace(token, token + "="),
            qr.replace(token, token.dropLast(1) + "9"))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { QrInvitation.parse(it) } }
    }

    @Test fun lanAddressesCannotTriggerDnsOrPublicConnections() {
        listOf("10.0.2.2", "127.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.2.4").forEach { assertTrue(it, isLanAddress(it)) }
        listOf("0.0.0.0", "8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.1.1", "localhost", "127.1", "127.0.0.256", "0177.0.0.1", "::1", "1.1.1.1/path", "10.0.0.1 ").forEach { assertFalse(it, isLanAddress(it)) }
    }

    @Test fun wireRejectsDuplicatesDepthOversizeAndWrongTypes() {
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"v\":1,\"v\":1}") }
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"v\":1,\"\\u0076\":1}") }
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"a\":" + "[".repeat(8) + "0" + "]".repeat(8) + "}") }
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"a\":\"${"中".repeat(1500)}\"}") }
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"v\":\"1\"}").number("v") }
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"v\":1.0}").number("v") }
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"v\":9223372036854775808}").number("v") }
        assertEquals(1, Wire.parse("{\"v\":1,\"nested\":{\"v\":2},\"text\":\"{\\\"v\\\":4}\"}").number("v"))
    }

    @Test fun effectAndDisplayBoundsAreCheckedBeforeIntegerConversion() {
        assertFalse(LaserEffect(size = 41).isValid())
        assertFalse(LaserEffect(trailMs = -1).isValid())
        assertFalse(LaserEffect(color = "red").isValid())
        assertFalse(LaserEffect(smoothing = "other").isValid())
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"effect\":{\"size\":4294967312,\"trailMs\":500,\"color\":\"#FF6475\",\"smoothing\":\"standard\"}}").effect() }
        assertThrows(ProtocolFailure::class.java) { Wire.parse("{\"display\":{\"width\":0,\"height\":1080}}").display() }
        assertNull(Wire.parse("{\"display\":null}").display())
        assertNull(Wire.parse("{}").display())
        assertEquals(DisplayInfo(1920, 1080), Wire.parse("{\"display\":{\"width\":1920,\"height\":1080}}").display())
    }

    @Test fun discoveryUsesDatagramSourceAndRejectsInvalidHints() {
        val data = "{\"v\":1,\"type\":\"pointa.discovery\",\"serverId\":\"${"0".repeat(32)}\",\"name\":\"PC\",\"port\":32145,\"pairingId\":\"${"1".repeat(32)}\",\"expiresAt\":1793800000000,\"host\":\"8.8.8.8\"}"
        assertEquals("10.0.0.7", LanDiscovery.parse(data, "10.0.0.7").host)
        assertThrows(ProtocolFailure::class.java) { LanDiscovery.parse(data, "8.8.8.8") }
        assertThrows(ProtocolFailure::class.java) { LanDiscovery.parse(data.replace("32145", "4294999441"), "10.0.0.7") }
    }

    @Test fun srpMatchesIndependentSha2563072Vector() {
        val serverId = "0".repeat(32); val pair = "1".repeat(32); val pin = "2".repeat(64)
        val salt = "00112233445566778899aabbccddeeff"
        val server = fixedServer(serverId, pair, pin, salt)
        val b = server.generateServerCredentials()
        val client = SrpHandshake(serverId, pair, pin, object : SRP6Client() {
            override fun selectPrivateValue(): BigInteger = BigInteger("11".repeat(32), 16)
        })
        val (a, m1) = client.proof(salt, encode(b, 384), "012345")
        assertEquals("554d1db9c9d367c73c139993a6aec6bc5f4d7ab233f954bb85067f7fdee8c4b2", MessageDigest.getInstance("SHA-256").digest(hexBytes(a, 384)).toHex())
        assertEquals("9afd74118c41160c09c237bb008f375c839a1bd30d450098a98671637db5d106", MessageDigest.getInstance("SHA-256").digest(hexBytes(encode(b, 384), 384)).toHex())
        assertEquals("48e237e1cc5ab614a709bab910db6a2d8b548293d6c5ecf8cafd9713d735f45b", m1)
        server.calculateSecret(integer(a, 384))
        assertTrue(server.verifyClientEvidenceMessage(integer(m1, 32)))
        val m2 = encode(server.calculateServerEvidenceMessage(), 32)
        assertEquals("b462a694651dcc0315844f6b8909d2191d12a55a59b4594e72da6f09184e890a", m2)
        assertEquals("a0805d78735aed8ed9cf8c25cb88f0fe6e0cdf757e1339e8cd31ea519d5d048b", encode(server.calculateSessionKey(), 32))
        assertEquals("0a2b0732b1dea7c770cb1ef01a803e60313a40710b6a6dc9872ad78f55720179", client.confirm(m2))
    }

    @Test fun srpRejectsZeroOrOutOfRangePublicAndUnverifiedServer() {
        val client = SrpHandshake("0".repeat(32), "1".repeat(32), "2".repeat(64))
        assertThrows(ProtocolFailure::class.java) { client.proof("00".repeat(16), "00".repeat(384), "012345") }
        assertThrows(ProtocolFailure::class.java) { client.proof("00".repeat(16), encode(SRP6StandardGroups.rfc5054_3072.n, 384), "012345") }
        val server = fixedServer("0".repeat(32), "1".repeat(32), "2".repeat(64), "00".repeat(16))
        client.proof("00".repeat(16), encode(server.generateServerCredentials(), 384), "012345")
        assertThrows(ProtocolFailure::class.java) { client.confirm("00".repeat(32)) }
    }

    @Test fun srpBindsActualCertificateAndLeadingZeroPassword() {
        val server = fixedServer("0".repeat(32), "1".repeat(32), "2".repeat(64), "00".repeat(16))
        val client = SrpHandshake("0".repeat(32), "1".repeat(32), "3".repeat(64))
        val proof = client.proof("00".repeat(16), encode(server.generateServerCredentials(), 384), "012345")
        server.calculateSecret(integer(proof.first, 384))
        assertFalse(server.verifyClientEvidenceMessage(integer(proof.second, 32)))
    }

    private fun fixedServer(id: String, pair: String, pin: String, salt: String): SRP6Server {
        val generator = SRP6VerifierGenerator()
        generator.init(SRP6StandardGroups.rfc5054_3072, SHA256Digest())
        val verifier = generator.generateVerifier(hexBytes(salt, 16), "pointa-srp-v1\n$id\n$pair\n$pin".toByteArray(), "012345".toByteArray())
        return object : SRP6Server() {
            override fun selectPrivateValue(): BigInteger = BigInteger("22".repeat(32), 16)
        }.also { it.init(SRP6StandardGroups.rfc5054_3072, verifier, SHA256Digest(), SecureRandom()) }
    }
}
