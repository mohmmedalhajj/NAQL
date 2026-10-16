package com.example.model

data class NearbyDevice(
    val endpointId: String,
    val name: String,
    val serviceId: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val signalStrengthDbm: Int = -50,
    val discoveredAt: Long = System.currentTimeMillis()
)

data class ConnectionRequest(
    val endpointId: String,
    val endpointName: String,
    val authenticationDigits: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SessionState {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTION_PENDING_APPROVAL,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferSession(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val isIncoming: Boolean = false,
    val peerEndpointId: String = "",
    val peerDeviceName: String = "",
    val items: List<TransferItem> = emptyList(),
    val currentItemIndex: Int = 0,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val transferSpeedBytesPerSec: Long = 0L,
    val state: SessionState = SessionState.IDLE,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val errorMessage: String? = null,
    val authPin: String = ""
) {
    val overallProgress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() = "${TransferItem.formatBytes(transferSpeedBytesPerSec)} / ث"

    val estimatedTimeRemainingSec: Long
        get() {
            if (transferSpeedBytesPerSec <= 0 || transferredBytes >= totalBytes) return 0L
            val remainingBytes = totalBytes - transferredBytes
            return remainingBytes / transferSpeedBytesPerSec
        }
}
