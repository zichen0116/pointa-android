package io.github.zichen0116.pointa.protocol

import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

internal fun connectionError(failure: Throwable): String {
    val causes = generateSequence(failure) { it.cause }.take(8).toList()
    return when {
        causes.any { it.message == "certificate_changed" } -> "certificate_changed"
        causes.any { it is SSLException || it is CertificateException } -> "invalid_certificate"
        causes.any { it is NoRouteToHostException || it.message.orEmpty().contains("EHOSTUNREACH") || it.message.orEmpty().contains("ENETUNREACH") } -> "network_unreachable"
        causes.any { it is SocketTimeoutException } -> "connect_timeout"
        causes.any { it.message.orEmpty().contains("ECONNREFUSED") } -> "connection_refused"
        else -> "network_error"
    }
}
