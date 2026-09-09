package com.example.cipherview.data.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-assurance cryptographic engine for CipherView.
 * Uses PBKDF2WithHmacSHA256 key derivation and AES-256-GCM authenticated encryption.
 */
object CryptoEngine {
    private val MAGIC_BYTES = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'I'.code.toByte(), 'E'.code.toByte(), 'W'.code.toByte(), 1)
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    sealed class DecryptResult {
        data class Success(
            val pdfBytes: ByteArray,
            val fileName: String,
            val senderNickname: String,
            val expiresAt: Long?,
            val maxViews: Int?
        ) : DecryptResult()

        object InvalidAccessCodeOrCorrupted : DecryptResult()
        data class InvalidPackageFormat(val reason: String = "Corrupted or invalid CipherView package format.") : DecryptResult()
        object RegularPdfNotEncrypted : DecryptResult()
        data class Expired(val expiredAt: Long) : DecryptResult()
    }

    data class PackageMetadata(
        val fileName: String,
        val senderNickname: String,
        val expiresAt: Long?,
        val maxViews: Int?,
        val fileSizeBytes: Long
    )

    fun isEncryptedPackage(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_BYTES.size) return false
        for (i in MAGIC_BYTES.indices) {
            if (bytes[i] != MAGIC_BYTES[i]) return false
        }
        return true
    }

    fun isPdf(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == '%'.code.toByte() &&
               bytes[1] == 'P'.code.toByte() &&
               bytes[2] == 'D'.code.toByte() &&
               bytes[3] == 'F'.code.toByte()
    }

    /**
     * Derives a 256-bit AES key from the user-facing Access Code and random salt.
     */
    fun deriveKey(accessCode: String, salt: ByteArray): SecretKey {
        val cleanCode = AccessCodeGenerator.normalize(accessCode)
        val spec = PBEKeySpec(cleanCode.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts a PDF file with AES-256-GCM using a key derived from the Access Code.
     * Packages the metadata, salt, IV, and ciphertext into a self-contained .cview bundle.
     */
    fun encryptDocument(
        pdfBytes: ByteArray,
        accessCode: String,
        fileName: String,
        senderNickname: String,
        expiresAt: Long? = null,
        maxViews: Int? = null
    ): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        val key = deriveKey(accessCode, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        // Include metadata as Associated Authenticated Data (AAD) to ensure it cannot be tampered with
        val aadData = "${fileName}|${senderNickname}|${expiresAt ?: 0}|${maxViews ?: -1}".toByteArray(Charsets.UTF_8)
        cipher.updateAAD(aadData)

        val ciphertext = cipher.doFinal(pdfBytes)

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Magic bytes
        dos.write(MAGIC_BYTES)
        // Salt & IV
        dos.write(salt)
        dos.write(iv)
        // Restrictions
        dos.writeLong(expiresAt ?: 0L)
        dos.writeInt(maxViews ?: -1)
        // Metadata strings
        dos.writeUTF(fileName)
        dos.writeUTF(senderNickname)
        // Ciphertext (includes 16-byte GCM auth tag at end)
        dos.writeInt(ciphertext.size)
        dos.write(ciphertext)
        dos.flush()

        return baos.toByteArray()
    }

    /**
     * Inspects the non-sensitive header of a .cview package without decrypting the payload.
     */
    fun inspectPackageHeader(packageBytes: ByteArray): PackageMetadata? {
        return try {
            val bais = ByteArrayInputStream(packageBytes)
            val dis = DataInputStream(bais)

            val magic = ByteArray(MAGIC_BYTES.size)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC_BYTES)) return null

            // Skip salt & iv
            dis.skipBytes(SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES)

            val expiresAtRaw = dis.readLong()
            val maxViewsRaw = dis.readInt()
            val fileName = dis.readUTF()
            val senderNickname = dis.readUTF()
            val ciphertextSize = dis.readInt()

            val expiresAt = if (expiresAtRaw > 0L) expiresAtRaw else null
            val maxViews = if (maxViewsRaw > 0) maxViewsRaw else null

            PackageMetadata(
                fileName = fileName,
                senderNickname = senderNickname,
                expiresAt = expiresAt,
                maxViews = maxViews,
                fileSizeBytes = ciphertextSize.toLong()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Authenticates and decrypts a .cview package using the provided Access Code.
     * Enforces expiration timestamps immediately.
     */
    fun decryptDocument(
        packageBytes: ByteArray,
        accessCode: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): DecryptResult {
        if (isPdf(packageBytes)) {
            return DecryptResult.RegularPdfNotEncrypted
        }
        if (!isEncryptedPackage(packageBytes)) {
            return DecryptResult.InvalidPackageFormat("Selected file is not a valid CipherView (.cview) encrypted package.")
        }

        val dis = try {
            val bais = ByteArrayInputStream(packageBytes)
            DataInputStream(bais)
        } catch (e: Exception) {
            return DecryptResult.InvalidPackageFormat("Unable to read package stream.")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val expiresAt: Long?
        val maxViews: Int?
        val fileName: String
        val senderNickname: String
        val ciphertext: ByteArray

        try {
            // Skip magic bytes since already validated
            dis.skipBytes(MAGIC_BYTES.size)
            dis.readFully(salt)
            dis.readFully(iv)

            val expiresAtRaw = dis.readLong()
            val maxViewsRaw = dis.readInt()
            fileName = dis.readUTF()
            senderNickname = dis.readUTF()

            expiresAt = if (expiresAtRaw > 0L) expiresAtRaw else null
            maxViews = if (maxViewsRaw > 0) maxViewsRaw else null

            // Expiration check
            if (expiresAt != null && currentTimeMillis > expiresAt) {
                return DecryptResult.Expired(expiresAt)
            }

            val ciphertextSize = dis.readInt()
            ciphertext = ByteArray(ciphertextSize)
            dis.readFully(ciphertext)
        } catch (e: Exception) {
            return DecryptResult.InvalidPackageFormat("Package header or payload structure is corrupted.")
        }

        return try {
            // Derive key from access code and salt
            val key = deriveKey(accessCode, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            // Verify AAD
            val aadData = "${fileName}|${senderNickname}|${expiresAt ?: 0}|${maxViews ?: -1}".toByteArray(Charsets.UTF_8)
            cipher.updateAAD(aadData)

            val plaintext = cipher.doFinal(ciphertext)

            DecryptResult.Success(
                pdfBytes = plaintext,
                fileName = fileName,
                senderNickname = senderNickname,
                expiresAt = expiresAt,
                maxViews = maxViews
            )
        } catch (e: AEADBadTagException) {
            DecryptResult.InvalidAccessCodeOrCorrupted
        } catch (e: javax.crypto.BadPaddingException) {
            DecryptResult.InvalidAccessCodeOrCorrupted
        } catch (e: javax.crypto.IllegalBlockSizeException) {
            DecryptResult.InvalidAccessCodeOrCorrupted
        } catch (e: java.security.GeneralSecurityException) {
            DecryptResult.InvalidAccessCodeOrCorrupted
        } catch (e: Exception) {
            DecryptResult.InvalidAccessCodeOrCorrupted
        }
    }
}
