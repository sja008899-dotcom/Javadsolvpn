package com.example.data.local

import java.security.MessageDigest
import java.util.Locale

/**
 * Fingerprint for deduplication only. Never log the input fields because they may contain secrets.
 * Do not identify a server only by host:port: different credentials/transports may be valid.
 */
object ConfigFingerprint {
    fun of(server: ServerConfigEntity): String {
        val canonical = listOf(
            server.protocol.trim().lowercase(Locale.ROOT),
            server.host.trim().trim('[', ']').lowercase(Locale.ROOT),
            server.port.toString(),
            server.uuidOrPassword.trim(),
            server.flow.trim(),
            server.securityType.trim().lowercase(Locale.ROOT),
            server.transportType.trim().lowercase(Locale.ROOT),
            server.sni.trim().lowercase(Locale.ROOT),
            server.publicKey.trim(),
            server.shortId.trim(),
            server.fingerprint.trim().lowercase(Locale.ROOT),
            server.path.trim(),
            server.serviceName.trim(),
            server.alpn.trim(),
            server.headerType.trim().lowercase(Locale.ROOT),
            server.alterId.toString()
        ).joinToString("\u001f")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
