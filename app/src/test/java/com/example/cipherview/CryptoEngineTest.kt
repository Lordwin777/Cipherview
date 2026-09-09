package com.example.cipherview

import com.example.cipherview.data.security.AccessCodeGenerator
import com.example.cipherview.data.security.CryptoEngine
import org.junit.Assert.*
import org.junit.Test

class CryptoEngineTest {

    @Test
    fun testEncryptionAndDecryptionRoundTrip() {
        val samplePdf = "Sample PDF Confidential Content for CipherView".toByteArray(Charsets.UTF_8)
        val accessCode = "X7K9P2"
        val fileName = "Confidential_Doc.pdf"
        val sender = "Void"
        val maxViews = 3

        val encryptedPackage = CryptoEngine.encryptDocument(
            pdfBytes = samplePdf,
            accessCode = accessCode,
            fileName = fileName,
            senderNickname = sender,
            maxViews = maxViews
        )

        assertNotNull(encryptedPackage)
        assertTrue(encryptedPackage.size > samplePdf.size)

        // Decrypt with correct code
        val result = CryptoEngine.decryptDocument(encryptedPackage, accessCode)
        assertTrue("Expected Success result", result is CryptoEngine.DecryptResult.Success)

        val success = result as CryptoEngine.DecryptResult.Success
        assertArrayEquals(samplePdf, success.pdfBytes)
        assertEquals(fileName, success.fileName)
        assertEquals(sender, success.senderNickname)
        assertEquals(maxViews, success.maxViews)
    }

    @Test
    fun testDecryptionWithFormattedAccessCode() {
        val samplePdf = "Secret text".toByteArray(Charsets.UTF_8)
        val code = "A3B7C9"

        val encrypted = CryptoEngine.encryptDocument(
            pdfBytes = samplePdf,
            accessCode = code,
            fileName = "doc.pdf",
            senderNickname = "Shadow"
        )

        // User enters code formatted with hyphen or lowercase
        val resultHyphen = CryptoEngine.decryptDocument(encrypted, "a3b-7c9")
        assertTrue(resultHyphen is CryptoEngine.DecryptResult.Success)
        assertArrayEquals(samplePdf, (resultHyphen as CryptoEngine.DecryptResult.Success).pdfBytes)

        val resultSpace = CryptoEngine.decryptDocument(encrypted, "A3B 7C9")
        assertTrue(resultSpace is CryptoEngine.DecryptResult.Success)
    }

    @Test
    fun testDecryptionWithWrongAccessCode() {
        val samplePdf = "Secret text".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoEngine.encryptDocument(
            pdfBytes = samplePdf,
            accessCode = "X7K9P2",
            fileName = "doc.pdf",
            senderNickname = "Void"
        )

        val result = CryptoEngine.decryptDocument(encrypted, "WRONG9")
        assertTrue(
            "Expected InvalidAccessCodeOrCorrupted, got $result",
            result is CryptoEngine.DecryptResult.InvalidAccessCodeOrCorrupted
        )
    }

    @Test
    fun testExpirationPolicyEnforced() {
        val samplePdf = "Time sensitive doc".toByteArray(Charsets.UTF_8)
        val pastTime = System.currentTimeMillis() - 5000L // Expired 5 seconds ago

        val encrypted = CryptoEngine.encryptDocument(
            pdfBytes = samplePdf,
            accessCode = "EXP123",
            fileName = "urgent.pdf",
            senderNickname = "Lord",
            expiresAt = pastTime
        )

        val result = CryptoEngine.decryptDocument(encrypted, "EXP123")
        assertTrue("Expected Expired result", result is CryptoEngine.DecryptResult.Expired)
    }

    @Test
    fun testTamperedPayloadFails() {
        val samplePdf = "Integrity critical doc".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoEngine.encryptDocument(
            pdfBytes = samplePdf,
            accessCode = "INT999",
            fileName = "report.pdf",
            senderNickname = "Void"
        )

        // Corrupt last byte
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        val result = CryptoEngine.decryptDocument(encrypted, "INT999")
        assertTrue(
            "Tampered payload must fail authentication",
            result is CryptoEngine.DecryptResult.InvalidAccessCodeOrCorrupted
        )
    }

    @Test
    fun testInspectPackageHeaderWithoutAccessCode() {
        val samplePdf = "Document for preview".toByteArray(Charsets.UTF_8)
        val fileName = "Project_Report.pdf"
        val sender = "Void"
        val maxViews = 5

        val encrypted = CryptoEngine.encryptDocument(
            pdfBytes = samplePdf,
            accessCode = "PRV123",
            fileName = fileName,
            senderNickname = sender,
            maxViews = maxViews
        )

        val metadata = CryptoEngine.inspectPackageHeader(encrypted)
        assertNotNull(metadata)
        assertEquals(fileName, metadata?.fileName)
        assertEquals(sender, metadata?.senderNickname)
        assertEquals(maxViews, metadata?.maxViews)
    }
}
