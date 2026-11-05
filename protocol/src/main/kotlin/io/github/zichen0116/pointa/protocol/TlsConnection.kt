package io.github.zichen0116.pointa.protocol

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import java.net.Proxy
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** One connection only. A missing pin is allowed exclusively for an explicit SRP attempt. */
internal class TlsConnection(expectedPin: String?) {
    val actualPin = AtomicReference<String>()
    val client: OkHttpClient

    init {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("client_certificate_not_supported")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || authType.isBlank()) throw CertificateException("invalid_certificate")
                val certificate = chain.first()
                certificate.checkValidity()
                val strongKey = when (val key = certificate.publicKey) {
                    is RSAPublicKey -> key.modulus.bitLength() >= 2048
                    is ECPublicKey -> key.params.curve.field.fieldSize >= 256
                    else -> false
                }
                val signature = certificate.sigAlgName.uppercase().replace("-", "")
                if (!strongKey || "MD5" in signature || "SHA1" in signature || "NONE" in signature)
                    throw CertificateException("invalid_certificate")
                val pin = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).toHex()
                if (expectedPin != null && !MessageDigest.isEqual(pin.toByteArray(), expectedPin.toByteArray()))
                    throw CertificateException("certificate_changed")
                if (!actualPin.compareAndSet(null, pin) && actualPin.get() != pin)
                    throw CertificateException("certificate_changed")
            }
        }
        val tls = SSLContext.getInstance("TLS")
        tls.init(null, arrayOf(trust), null)
        client = OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trust)
            // Identity is the exact DER pin, or certificate-bound SRP before any credential is accepted.
            .hostnameVerifier { _, _ -> actualPin.get() != null }
            .connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build()))
            .protocols(listOf(Protocol.HTTP_1_1))
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    fun release() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}
