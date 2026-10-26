package com.example.vpn.diagnostic

enum class DiagnosticStepState {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

data class DiagnosticStepItem(
    val title: String,
    val description: String,
    val state: DiagnosticStepState = DiagnosticStepState.PENDING,
    val detailMessage: String = "",
    val errorCode: String? = null
)

data class DiagnosticLog(
    val timestamp: String,
    val tag: String,
    val message: String,
    val isError: Boolean = false
)

data class SafeModeDiagnosticReport(
    val isRunning: Boolean = false,
    val overallPassed: Boolean? = null,
    val fdNumber: Int = -1,
    val isFdBound: Boolean = false,
    val coreVersion: String = "",
    val coreReturnCode: Long? = null,
    val handshakeResponseCode: Int = -1,
    val handshakeLatencyMs: Long = -1L,
    val targetServerName: String = "",
    val tcpReachable: Boolean = false,
    val tcpPingMs: Long = -1L,
    val internetCode204Passed: Boolean = false,
    val internetLatencyMs: Long = -1L,
    val nextCandidateId: Long? = null,
    val nextCandidateName: String? = null,
    val steps: List<DiagnosticStepItem> = emptyList(),
    val logs: List<DiagnosticLog> = emptyList(),
    val summary: String = ""
)
