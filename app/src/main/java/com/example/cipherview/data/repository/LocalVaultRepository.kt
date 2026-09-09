package com.example.cipherview.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.cipherview.data.model.*
import com.example.cipherview.data.security.CryptoEngine
import com.example.cipherview.data.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Local-first, zero-cloud repository for managing local identity, nickname history,
 * encrypted document storage, access controls, and audit logs.
 */
class LocalVaultRepository(private val context: Context) {
    private val keystoreManager = KeystoreManager()
    private val prefs: SharedPreferences = context.getSharedPreferences("cipherview_vault_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val vaultDir = File(context.filesDir, "vault").apply {
        if (!exists()) mkdirs()
    }

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _documents = MutableStateFlow<List<SharedDocumentRecord>>(emptyList())
    val documents: StateFlow<List<SharedDocumentRecord>> = _documents.asStateFlow()

    init {
        loadProfile()
        loadDocuments()
    }

    private fun loadProfile() {
        val encryptedBase64 = prefs.getString("encrypted_profile", null)
        if (encryptedBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val decryptedJson = keystoreManager.decrypt(encryptedBytes)
                if (decryptedJson.isNotEmpty()) {
                    val profile = json.decodeFromString<UserProfile>(decryptedJson)
                    _userProfile.value = profile
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _userProfile.value = null
    }

    private fun saveProfile(profile: UserProfile) {
        _userProfile.value = profile
        try {
            val jsonString = json.encodeToString(profile)
            val encryptedBytes = keystoreManager.encrypt(jsonString)
            val base64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            prefs.edit().putString("encrypted_profile", base64).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Initializes identity on first run with chosen nickname.
     */
    fun setupInitialNickname(nickname: String) {
        val cleanNickname = nickname.trim()
        val deviceId = "cview-" + UUID.randomUUID().toString().substring(0, 8)
        val historyItem = NicknameHistoryItem(
            nickname = cleanNickname,
            timestamp = System.currentTimeMillis(),
            isCurrent = true
        )
        val profile = UserProfile(
            currentNickname = cleanNickname,
            nicknameHistory = listOf(historyItem),
            deviceId = deviceId
        )
        saveProfile(profile)
    }

    /**
     * Updates the user's nickname while maintaining the full historical record.
     * Previous document activities retain the nickname used at the time.
     */
    fun updateNickname(newNickname: String) {
        val clean = newNickname.trim()
        val current = _userProfile.value ?: return
        if (clean.equals(current.currentNickname, ignoreCase = true)) return

        val now = System.currentTimeMillis()
        val updatedHistory = current.nicknameHistory.map {
            it.copy(isCurrent = false)
        } + NicknameHistoryItem(
            nickname = clean,
            timestamp = now,
            isCurrent = true
        )

        val updatedProfile = current.copy(
            currentNickname = clean,
            nicknameHistory = updatedHistory
        )
        saveProfile(updatedProfile)

        // Update local sent records with the current sender nickname while preserving the historical snapshot
        val updatedDocs = _documents.value.map { doc ->
            if (doc.direction == ShareDirection.SENT) {
                doc.copy(currentSenderNickname = clean)
            } else {
                doc
            }
        }
        saveDocuments(updatedDocs)
    }

    private fun loadDocuments() {
        val encryptedBase64 = prefs.getString("encrypted_documents", null)
        if (encryptedBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val decryptedJson = keystoreManager.decrypt(encryptedBytes)
                if (decryptedJson.isNotEmpty()) {
                    val list = json.decodeFromString<List<SharedDocumentRecord>>(decryptedJson)
                    // Auto-prune any orphaned or invalid files (e.g. non-cview files)
                    val validList = list.filter { doc ->
                        val f = File(doc.packagePath)
                        if (!f.exists()) false
                        else {
                            val head = try {
                                val b = ByteArray(6)
                                java.io.FileInputStream(f).use { it.read(b) }
                                b
                            } catch (e: Exception) { ByteArray(0) }
                            val isCview = CryptoEngine.isEncryptedPackage(head)
                            if (!isCview) {
                                try { f.delete() } catch (e: Exception) {}
                            }
                            isCview
                        }
                    }
                    _documents.value = validList
                    if (validList.size != list.size) {
                        saveDocuments(validList)
                    }
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _documents.value = emptyList()
    }

    private fun saveDocuments(docs: List<SharedDocumentRecord>) {
        _documents.value = docs
        try {
            val jsonString = json.encodeToString(docs)
            val encryptedBytes = keystoreManager.encrypt(jsonString)
            val base64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            prefs.edit().putString("encrypted_documents", base64).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stores an encrypted .cview package in local vault and creates a SENT record.
     */
    suspend fun saveSentDocument(
        fileName: String,
        fileSizeBytes: Long,
        sharedWithNickname: String,
        accessCode: String,
        encryptedPackageBytes: ByteArray,
        expiresAt: Long? = null,
        maxViews: Int? = null
    ): SharedDocumentRecord = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val packageFile = File(vaultDir, "$id.cview")
        packageFile.writeBytes(encryptedPackageBytes)

        val now = System.currentTimeMillis()
        val myNick = _userProfile.value?.currentNickname ?: "Me"

        val record = SharedDocumentRecord(
            id = id,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            sharedByNickname = myNick,
            sharedWithNickname = sharedWithNickname,
            currentSenderNickname = myNick,
            accessCode = accessCode,
            direction = ShareDirection.SENT,
            status = DocumentStatus.ACTIVE,
            viewsCount = 0,
            maxViews = maxViews,
            createdAt = now,
            expiresAt = expiresAt,
            packagePath = packageFile.absolutePath,
            accessHistory = listOf(
                AccessHistoryEvent(
                    timestamp = now,
                    eventType = "ENCRYPTED_AND_STORED",
                    actorNickname = myNick,
                    note = "Encrypted with AES-256-GCM. Package stored in local vault."
                )
            )
        )

        val updated = listOf(record) + _documents.value
        saveDocuments(updated)
        record
    }

    /**
     * Stores an incoming .cview package and creates a RECEIVED record.
     */
    suspend fun saveReceivedDocument(
        encryptedPackageBytes: ByteArray,
        sharedWithNickname: String = ""
    ): SharedDocumentRecord = withContext(Dispatchers.IO) {
        if (CryptoEngine.isPdf(encryptedPackageBytes)) {
            throw IllegalArgumentException("The selected file is a standard unencrypted PDF. Use 'Share Document' to encrypt it with an Access Code.")
        }
        if (!CryptoEngine.isEncryptedPackage(encryptedPackageBytes)) {
            throw IllegalArgumentException("The selected file is not a valid CipherView (.cview) encrypted package.")
        }

        val metadata = CryptoEngine.inspectPackageHeader(encryptedPackageBytes)
            ?: throw IllegalArgumentException("CipherView package header is corrupted or incomplete.")

        val id = UUID.randomUUID().toString()
        val packageFile = File(vaultDir, "$id.cview")
        packageFile.writeBytes(encryptedPackageBytes)

        val now = System.currentTimeMillis()
        val myNick = _userProfile.value?.currentNickname ?: "Me"

        val fileName = metadata.fileName
        val senderNick = metadata.senderNickname
        val expiresAt = metadata.expiresAt
        val maxViews = metadata.maxViews

        val record = SharedDocumentRecord(
            id = id,
            fileName = fileName,
            fileSizeBytes = packageFile.length(),
            sharedByNickname = senderNick,
            sharedWithNickname = if (sharedWithNickname.isNotBlank()) sharedWithNickname else myNick,
            currentSenderNickname = senderNick,
            accessCode = "", // Recipient enters this out-of-band
            direction = ShareDirection.RECEIVED,
            status = DocumentStatus.ACTIVE,
            viewsCount = 0,
            maxViews = maxViews,
            createdAt = now,
            expiresAt = expiresAt,
            packagePath = packageFile.absolutePath,
            accessHistory = listOf(
                AccessHistoryEvent(
                    timestamp = now,
                    eventType = "RECEIVED",
                    actorNickname = myNick,
                    note = "Encrypted package received from $senderNick"
                )
            )
        )

        val updated = listOf(record) + _documents.value
        saveDocuments(updated)
        record
    }

    /**
     * Deletes a document from the vault and removes its package file.
     */
    fun deleteDocument(documentId: String): Boolean {
        val docs = _documents.value.toMutableList()
        val record = docs.find { it.id == documentId } ?: return false
        docs.removeAll { it.id == documentId }
        saveDocuments(docs)
        try {
            val file = File(record.packagePath)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    /**
     * Records a view event, increments view count, and updates status if limit reached.
     */
    fun recordDocumentView(documentId: String): SharedDocumentRecord? {
        val docs = _documents.value.toMutableList()
        val index = docs.indexOfFirst { it.id == documentId }
        if (index == -1) return null

        val current = docs[index]
        val now = System.currentTimeMillis()
        val newViews = current.viewsCount + 1
        val myNick = _userProfile.value?.currentNickname ?: "User"

        val limitReached = current.maxViews != null && newViews >= current.maxViews
        val newStatus = if (limitReached) DocumentStatus.VIEW_LIMIT_REACHED else current.computeEffectiveStatus(now)

        val updatedEvent = AccessHistoryEvent(
            timestamp = now,
            eventType = if (limitReached) "LIMIT_REACHED" else "VIEWED",
            actorNickname = myNick,
            note = if (current.maxViews != null) "View $newViews of ${current.maxViews}" else "View $newViews"
        )

        val updatedRecord = current.copy(
            viewsCount = newViews,
            status = newStatus,
            accessHistory = current.accessHistory + updatedEvent
        )

        docs[index] = updatedRecord
        saveDocuments(docs)
        return updatedRecord
    }

    /**
     * Revokes access to a document.
     */
    fun revokeDocument(documentId: String): SharedDocumentRecord? {
        val docs = _documents.value.toMutableList()
        val index = docs.indexOfFirst { it.id == documentId }
        if (index == -1) return null

        val current = docs[index]
        val now = System.currentTimeMillis()
        val myNick = _userProfile.value?.currentNickname ?: "Sender"

        val updatedEvent = AccessHistoryEvent(
            timestamp = now,
            eventType = "REVOKED",
            actorNickname = myNick,
            note = "Access revoked by sender"
        )

        val updatedRecord = current.copy(
            status = DocumentStatus.REVOKED,
            accessHistory = current.accessHistory + updatedEvent
        )

        docs[index] = updatedRecord
        saveDocuments(docs)
        return updatedRecord
    }

    /**
     * Retrieves the raw encrypted bytes for a document.
     */
    fun getEncryptedPackageBytes(record: SharedDocumentRecord): ByteArray? {
        val file = File(record.packagePath)
        return if (file.exists()) file.readBytes() else null
    }

    fun getDocumentById(id: String): SharedDocumentRecord? {
        return _documents.value.find { it.id == id }
    }
}
