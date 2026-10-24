package com.example.vpn.core.adapters

import com.example.data.local.ServerConfigEntity
import com.example.data.prober.ServerProber
import com.example.vpn.core.ProtocolAdapter
import com.example.vpn.core.TunnelConfig
import com.example.vpn.core.TunnelParameters

class VlessProtocolAdapter : ProtocolAdapter {
    override val protocolName: String = "vless"

    override fun canHandle(protocol: String): Boolean =
        protocol.equals(protocolName, ignoreCase = true)

    override suspend fun testReachability(server: ServerConfigEntity, timeoutMs: Int): Long =
        ServerProber.probe(server, timeoutMs)

    override fun buildTunnelParameters(config: TunnelConfig): TunnelParameters {
        val s = config.server
        return TunnelParameters(
            sessionName = "Javadsolvpn VLESS [${s.securityType.uppercase()}]",
            remoteHost = s.host,
            remotePort = s.port,
            protocol = "VLESS",
            security = s.securityType,
            transport = s.transportType,
            mtu = config.mtu,
            dnsServers = config.dnsServers,
            virtualIp = config.virtualIpV4,
            routingPrefix = config.virtualPrefixV4
        )
    }
}

class TrojanProtocolAdapter : ProtocolAdapter {
    override val protocolName: String = "trojan"

    override fun canHandle(protocol: String): Boolean =
        protocol.equals(protocolName, ignoreCase = true)

    override suspend fun testReachability(server: ServerConfigEntity, timeoutMs: Int): Long =
        ServerProber.probe(server, timeoutMs)

    override fun buildTunnelParameters(config: TunnelConfig): TunnelParameters {
        val s = config.server
        return TunnelParameters(
            sessionName = "Javadsolvpn Trojan TLS",
            remoteHost = s.host,
            remotePort = s.port,
            protocol = "TROJAN",
            security = "tls",
            transport = s.transportType,
            mtu = config.mtu,
            dnsServers = config.dnsServers,
            virtualIp = config.virtualIpV4,
            routingPrefix = config.virtualPrefixV4
        )
    }
}

class VmessProtocolAdapter : ProtocolAdapter {
    override val protocolName: String = "vmess"

    override fun canHandle(protocol: String): Boolean =
        protocol.equals(protocolName, ignoreCase = true)

    override suspend fun testReachability(server: ServerConfigEntity, timeoutMs: Int): Long =
        ServerProber.probe(server, timeoutMs)

    override fun buildTunnelParameters(config: TunnelConfig): TunnelParameters {
        val s = config.server
        return TunnelParameters(
            sessionName = "Javadsolvpn VMess AEAD",
            remoteHost = s.host,
            remotePort = s.port,
            protocol = "VMESS",
            security = s.securityType,
            transport = s.transportType,
            mtu = config.mtu,
            dnsServers = config.dnsServers,
            virtualIp = config.virtualIpV4,
            routingPrefix = config.virtualPrefixV4
        )
    }
}

class ShadowsocksProtocolAdapter : ProtocolAdapter {
    override val protocolName: String = "ss"

    override fun canHandle(protocol: String): Boolean =
        protocol.equals(protocolName, ignoreCase = true) || protocol.equals("shadowsocks", ignoreCase = true)

    override suspend fun testReachability(server: ServerConfigEntity, timeoutMs: Int): Long =
        ServerProber.probe(server, timeoutMs)

    override fun buildTunnelParameters(config: TunnelConfig): TunnelParameters {
        val s = config.server
        return TunnelParameters(
            sessionName = "Javadsolvpn Shadowsocks AEAD",
            remoteHost = s.host,
            remotePort = s.port,
            protocol = "SS",
            security = "cipher",
            transport = "tcp",
            mtu = config.mtu,
            dnsServers = config.dnsServers,
            virtualIp = config.virtualIpV4,
            routingPrefix = config.virtualPrefixV4
        )
    }
}
