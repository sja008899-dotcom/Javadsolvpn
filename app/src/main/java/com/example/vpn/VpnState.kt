package com.example.vpn

enum class VpnStatus {
    IDLE,
    CHECKING_NETWORK,
    SEARCHING_CANDIDATES,
    CONNECTING,
    CONNECTED,
    SWITCHING_CANDIDATE,
    RECONNECTING,
    DISCONNECTING,
    ERROR
}

data class TrafficStats(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val downloadSpeedKbps: Double = 0.0,
    val uploadSpeedKbps: Double = 0.0,
    val durationSeconds: Long = 0L,
    val pingMs: Long = -1L
) {
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedDownload: String
        get() = formatBytes(bytesIn)

    val formattedUpload: String
        get() = formatBytes(bytesOut)

    val formattedDownloadSpeed: String
        get() = formatSpeed(downloadSpeedKbps)

    val formattedUploadSpeed: String
        get() = formatSpeed(uploadSpeedKbps)

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
                bytes >= 1024 -> String.format("%.0f KB", bytes.toDouble() / 1024)
                else -> "$bytes B"
            }
        }

        fun formatSpeed(kbps: Double): String {
            return when {
                kbps >= 1024 -> String.format("%.2f MB/s", kbps / 1024)
                else -> String.format("%.1f KB/s", kbps)
            }
        }
    }
}
