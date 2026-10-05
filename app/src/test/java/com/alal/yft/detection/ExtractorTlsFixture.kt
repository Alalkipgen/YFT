package com.alal.yft.detection

import java.io.Closeable
import java.net.InetAddress
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/** A real verified local TLS connection; keys and response bodies stay inside the test. */
internal class ExtractorTlsFixture : Closeable {
    val server = MockWebServer()
    val client: OkHttpClient

    init {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server.useHttps(serverTls.sslSocketFactory(), false)
        val boundAddress = InetAddress.getByName("127.0.0.1")
        server.start(boundAddress, 0)
        client = OkHttpClient.Builder()
            // Loopback name/address ordering differs between runner operating systems.
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname == "localhost") {
                        listOf(boundAddress)
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
            })
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(false)
            .build()
    }

    /** Keep the verified certificate's host, independent of reverse-DNS names on the runner. */
    fun url(path: String): HttpUrl = server.url(path).newBuilder().host("localhost").build()

    override fun close() = server.shutdown()
}
