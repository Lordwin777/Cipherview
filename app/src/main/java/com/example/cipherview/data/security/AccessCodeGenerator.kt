package com.example.cipherview.data.security

import java.security.SecureRandom

/**
 * Generates and normalizes human-friendly, high-entropy Access Codes.
 * Excludes ambiguous characters (0, O, 1, I, L) to avoid transcription mistakes.
 */
object AccessCodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val CODE_LENGTH = 6
    private val secureRandom = SecureRandom()

    /**
     * Generates a new random Access Code, e.g. "X7K9P2".
     */
    fun generate(): String {
        val chars = CharArray(CODE_LENGTH)
        for (i in 0 until CODE_LENGTH) {
            chars[i] = ALPHABET[secureRandom.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }

    /**
     * Formats code with hyphen for easy readability, e.g. "X7K-9P2".
     */
    fun formatForDisplay(code: String): String {
        val clean = normalize(code)
        return if (clean.length == 6) {
            "${clean.substring(0, 3)}-${clean.substring(3)}"
        } else {
            clean
        }
    }

    /**
     * Normalizes user input by stripping spaces/hyphens and converting to uppercase.
     */
    fun normalize(input: String): String {
        return input.uppercase().replace("-", "").replace(" ", "").trim()
    }

    /**
     * Validates if a string is a valid Access Code structure.
     */
    fun isValid(input: String): Boolean {
        val clean = normalize(input)
        return clean.length == CODE_LENGTH && clean.all { it in ALPHABET }
    }
}
