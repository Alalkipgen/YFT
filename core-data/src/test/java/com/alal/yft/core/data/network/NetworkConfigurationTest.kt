package com.alal.yft.core.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigurationTest {
    @Test
    fun usesBoundedTimeoutsDefaultTlsAndNoLoggingInterceptors() {
        val client = NetworkConfiguration.createClient()

        assertEquals(20_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
    }
}
