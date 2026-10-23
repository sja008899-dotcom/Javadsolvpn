package com.example.vpn.core

import com.example.data.local.ServerConfigEntity
import kotlinx.coroutines.flow.StateFlow

sealed class VpnCoreState {
    object Idle : VpnCoreState()
    data class Preparing(val message: String) : VpnCoreState()
    data class Handshaking(val protocol: String, val endpoint: String) : VpnCoreState()
    data class Established(val session: VpnTunnelSession) : VpnCoreState()
    data class Reconnecting(val reason: String) : VpnCoreState()
    data class Failed(val error: String, val canRetry: Boolean = true) : VpnCoreState()
}

data class TunnelConfig(
    val server: ServerConfigEntity,
    val mtu: Int = 1500,
    val dnsServers: List<String> = listOf("1.1.1.1", "1.0.0.1"),
    val virtualIpV4: String = "10.8.0.2",
    val virtualPrefixV4: Int = 24,
    val routeAllTraffic: Boolean = true,
    val enableUdp: Boolean = true
)

data class TunnelParameters(
    val sessionName: String,
    val remoteHost: String,
    val remotePort: Int,
    val protocol: String,
    val security: String,
    val transport: String,
    val mtu: Int,
    val dnsServers: List<String>,
    val virtualIp: String,
    val routingPrefix: Int
)

data class VpnTunnelSession(
    val sessionId: String,
    val serverName: String,
    val protocol: String,
    val establishedAtMillis: Long,
    val remoteAddress: String
)

data class EngineStats(
    val totalBytesIn: Long = 0L,
    val totalBytesOut: Long = 0L,
    val currentDownloadKbps: Double = 0.0,
    val currentUploadKbps: Double = 0.0,
    val activeConnections: Int = 0,
    val packetLossPercentage: Double = 0.0
)

/**
 * ProtocolAdapter allows pluggable implementation of VLESS, Trojan, VMess,
 * Shadowsocks, OpenVPN, WireGuard, and future transports.
 */
interface ProtocolAdapter {
    val protocolName: String
    fun canHandle(protocol: String): Boolean
    suspend fun testReachability(server: ServerConfigEntity, timeoutMs: Int = 3000): Long
    fun buildTunnelParameters(config: TunnelConfig): TunnelParameters
}

/**
 * Unified Core Interface for device VPN and proxy network management.
 */
interface VpnCoreInterface {
    val coreState: StateFlow<VpnCoreState>
    val activeSession: StateFlow<VpnTunnelSession?>
    val engineStats: StateFlow<EngineStats>

    fun registerProtocolAdapter(adapter: ProtocolAdapter)
    fun getSupportedProtocols(): List<String>
    suspend fun prepareSession(config: TunnelConfig): Result<TunnelParameters>
    suspend fun establishSession(params: TunnelParameters): Result<VpnTunnelSession>
    fun terminateSession(reason: String): Result<Unit>
    suspend fun probeEndpoint(server: ServerConfigEntity): Long
}
