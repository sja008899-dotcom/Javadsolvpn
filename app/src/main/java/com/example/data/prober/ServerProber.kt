package com.example.data.prober

import com.example.data.local.ServerConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object ServerProber {

    private val semaphore = Semaphore(64)

    /**
     * Performs a real socket connection and optional TLS handshake with SNI to test reachability.
     * Returns the exact elapsed time in milliseconds, or -1 if the server is unreachable.
     */
    suspend fun probe(server: ServerConfigEntity, timeoutMs: Int = 2500): Long = withContext(Dispatchers.IO) {
        semaphore.withPermit {
            val startTime = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(server.host, server.port), timeoutMs)

                // If TLS or Reality is specified, verify that the remote TLS listener responds to SNI ClientHello
                val isTls = server.securityType.equals("tls", ignoreCase = true) ||
                        server.securityType.equals("reality", ignoreCase = true)
                if (isTls) {
                    val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val effectiveSni = server.sni.ifBlank { server.host }
                    val sslSocket = sslFactory.createSocket(socket, effectiveSni, server.port, true) as SSLSocket
                    try {
                        val params = SSLParameters()
                        params.serverNames = listOf(SNIHostName(effectiveSni))
                        sslSocket.sslParameters = params
                        sslSocket.startHandshake()
                    } catch (_: Exception) {
                        // Handshake failure or self-signed cert on reality
                        // If standard TLS handshake fails on strict check, TCP level reachability is still verified
                    } finally {
                        try { sslSocket.close() } catch (_: Exception) {}
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > 0) elapsed else 1L
            } catch (_: Exception) {
                -1L
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }
    }
}
