package com.example.cipherview.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShareDirection {
    SENT,
    RECEIVED
}

@Serializable
enum class DocumentStatus(val label: String) {
    ACTIVE("Active"),
    EXPIRED("Expired"),
    VIEW_LIMIT_REACHED("Limit Reached"),
    REVOKED("Revoked")
}

@Serializable
data class NicknameHistoryItem(
    val nickname: String,
    val timestamp: Long,
    val isCurrent: Boolean
)

@Serializable
data class UserProfile(
    val currentNickname: String,
    val nicknameHistory: List<NicknameHistoryItem> = emptyList(),
    val deviceId: String
)

@Serializable
data class AccessHistoryEvent(
    val timestamp: Long,
    val eventType: String, // "CREATED", "SENT", "RECEIVED", "VIEWED", "REVOKED", "LIMIT_REACHED"
    val actorNickname: String,
    val note: String = ""
)

@Serializable
data class DocumentRestrictions(
    val expiresAt: Long? = null,
    val maxViews: Int? = null,
    val isRevoked: Boolean = false
)

@Serializable
data class SharedDocumentRecord(
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sharedByNickname: String, // Nickname at the time the document was created/shared
    val sharedWithNickname: String,
    val currentSenderNickname: String = sharedByNickname, // Tracks current nickname if updated
    val accessCode: String,
    val direction: ShareDirection,
    val status: DocumentStatus = DocumentStatus.ACTIVE,
    val viewsCount: Int = 0,
    val maxViews: Int? = null,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val packagePath: String, // Local storage path to the encrypted .cview package
    val accessHistory: List<AccessHistoryEvent> = emptyList()
) {
    /**
     * Determines current status based on timestamp and view count.
     */
    fun computeEffectiveStatus(currentTimeMillis: Long = System.currentTimeMillis()): DocumentStatus {
        if (status == DocumentStatus.REVOKED) return DocumentStatus.REVOKED
        if (expiresAt != null && currentTimeMillis > expiresAt) return DocumentStatus.EXPIRED
        if (maxViews != null && viewsCount >= maxViews) return DocumentStatus.VIEW_LIMIT_REACHED
        return DocumentStatus.ACTIVE
    }

    val isViewable: Boolean
        get() = computeEffectiveStatus() == DocumentStatus.ACTIVE
}
