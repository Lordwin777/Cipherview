package com.example.cipherview.data.network

enum class TransportType(val displayName: String) {
    WIFI_NSD("Wi-Fi / LAN"),
    BLUETOOTH("Bluetooth"),
    MANUAL("Manual Connection")
}

data class DiscoveredPeer(
    val id: String,
    val nickname: String,
    val transportType: TransportType,
    val ipAddress: String? = null,
    val port: Int? = null,
    val bluetoothAddress: String? = null
)

enum class TransferStatus {
    IDLE,
    PREPARING,
    CONNECTING,
    TRANSFERRING,
    SUCCESS,
    FAILED
}

data class TransferProgress(
    val status: TransferStatus = TransferStatus.IDLE,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val peerNickname: String = "",
    val fileName: String = "",
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
