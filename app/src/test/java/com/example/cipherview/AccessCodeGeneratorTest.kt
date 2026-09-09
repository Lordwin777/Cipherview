package com.example.cipherview

import com.example.cipherview.data.security.AccessCodeGenerator
import org.junit.Assert.*
import org.junit.Test

class AccessCodeGeneratorTest {

    @Test
    fun testGeneratedCodeProperties() {
        val codes = (1..50).map { AccessCodeGenerator.generate() }
        assertEquals(50, codes.size)

        for (code in codes) {
            assertEquals("Code must be 6 chars", 6, code.length)
            assertTrue("Code must be valid", AccessCodeGenerator.isValid(code))
            // Ensure ambiguous chars are excluded
            assertFalse("Code must not contain 0", code.contains('0'))
            assertFalse("Code must not contain O", code.contains('O'))
            assertFalse("Code must not contain 1", code.contains('1'))
            assertFalse("Code must not contain I", code.contains('I'))
            assertFalse("Code must not contain L", code.contains('L'))
        }
    }

    @Test
    fun testFormatForDisplay() {
        val code = "X7K9P2"
        val formatted = AccessCodeGenerator.formatForDisplay(code)
        assertEquals("X7K-9P2", formatted)
    }

    @Test
    fun testNormalize() {
        assertEquals("X7K9P2", AccessCodeGenerator.normalize("x7k-9p2"))
        assertEquals("X7K9P2", AccessCodeGenerator.normalize("  x7k 9p2  "))
        assertEquals("X7K9P2", AccessCodeGenerator.normalize("X7K9P2"))
    }
}
