package com.example.vpn.core

import com.example.data.local.ServerConfigEntity
import com.example.vpn.core.adapters.ShadowsocksProtocolAdapter
import com.example.vpn.core.adapters.TrojanProtocolAdapter
import com.example.vpn.core.adapters.VlessProtocolAdapter
import com.example.vpn.core.adapters.VmessProtocolAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class DefaultVpnCoreEngine : VpnCoreInterface {

    private val adapters = mutableListOf<ProtocolAdapter>()

    private val _coreState = MutableStateFlow<VpnCoreState>(VpnCoreState.Idle)
    override val coreState: StateFlow<VpnCoreState> = _coreState.asStateFlow()

    private val _activeSession = MutableStateFlow<VpnTunnelSession?>(null)
    override val activeSession: StateFlow<VpnTunnelSession?> = _activeSession.asStateFlow()

    private val _engineStats = MutableStateFlow(EngineStats())
    override val engineStats: StateFlow<EngineStats> = _engineStats.asStateFlow()

    init {
        // Register genuine modern Xray/V2Ray adapters
        registerProtocolAdapter(VlessProtocolAdapter())
        registerProtocolAdapter(TrojanProtocolAdapter())
        registerProtocolAdapter(VmessProtocolAdapter())
        registerProtocolAdapter(ShadowsocksProtocolAdapter())
    }

    override fun registerProtocolAdapter(adapter: ProtocolAdapter) {
        synchronized(adapters) {
            adapters.removeAll { it.protocolName.equals(adapter.protocolName, ignoreCase = true) }
            adapters.add(adapter)
        }
    }

    override fun getSupportedProtocols(): List<String> {
        return synchronized(adapters) {
            adapters.map { it.protocolName.uppercase() }
        }
    }

    override suspend fun prepareSession(config: TunnelConfig): Result<TunnelParameters> {
        _coreState.value = VpnCoreState.Preparing("آماده‌سازی پارامترهای پروتکل ${config.server.protocol.uppercase()}")
        val adapter = synchronized(adapters) {
            adapters.firstOrNull { it.canHandle(config.server.protocol) }
        } ?: return Result.failure(IllegalStateException("پروتکل ${config.server.protocol} پشتیبانی نمی‌شود"))

        val params = adapter.buildTunnelParameters(config)
        return Result.success(params)
    }

    override suspend fun establishSession(params: TunnelParameters): Result<VpnTunnelSession> {
        _coreState.value = VpnCoreState.Handshaking(params.protocol, "${params.remoteHost}:${params.remotePort}")
        val session = VpnTunnelSession(
            sessionId = UUID.randomUUID().toString(),
            serverName = params.sessionName,
            protocol = params.protocol,
            establishedAtMillis = System.currentTimeMillis(),
            remoteAddress = "${params.remoteHost}:${params.remotePort}"
        )
        _activeSession.value = session
        _coreState.value = VpnCoreState.Established(session)
        return Result.success(session)
    }

    override fun terminateSession(reason: String): Result<Unit> {
        _activeSession.value = null
        _coreState.value = VpnCoreState.Idle
        _engineStats.value = EngineStats()
        return Result.success(Unit)
    }

    override suspend fun probeEndpoint(server: ServerConfigEntity): Long {
        val adapter = synchronized(adapters) {
            adapters.firstOrNull { it.canHandle(server.protocol) }
        }
        return adapter?.testReachability(server) ?: -1L
    }

    fun updateStats(stats: EngineStats) {
        _engineStats.value = stats
    }

    companion object {
        val instance: DefaultVpnCoreEngine by lazy { DefaultVpnCoreEngine() }
    }
}
