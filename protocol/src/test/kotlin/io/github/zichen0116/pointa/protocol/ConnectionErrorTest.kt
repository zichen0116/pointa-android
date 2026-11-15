package io.github.zichen0116.pointa.protocol

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionErrorTest {
    @Test fun separatesUnreachableRefusedTimeoutAndIdentityFailures() {
        assertEquals("network_unreachable", connectionError(ConnectException("Failed").apply { initCause(Exception("EHOSTUNREACH (No route to host)")) }))
        assertEquals("connection_refused", connectionError(ConnectException("ECONNREFUSED")))
        assertEquals("connect_timeout", connectionError(SocketTimeoutException()))
        assertEquals("invalid_certificate", connectionError(SSLException("handshake failed")))
        assertEquals("certificate_changed", connectionError(SSLException(CertificateException("certificate_changed"))))
    }
}
