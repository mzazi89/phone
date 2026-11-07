package com.mzazi.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {

    @Test
    fun mapsNamesToKeypadDigits() {
        assertEquals("25423", T9.digitsFor("Alice"))
        assertEquals("6279", T9.digitsFor("Mary"))
        assertEquals("", T9.digitsFor(""))
        assertEquals("22", T9.digitsFor("2b"))
    }

    @Test
    fun normalizesNumbers() {
        assertEquals("0712345678", T9.normalizeNumber("0712 345 678"))
        assertEquals("0712345678", T9.normalizeNumber("(0712) 345-678"))
        assertEquals("+254712345678", T9.normalizeNumber("+254 712 345 678"))
    }

    @Test
    fun matchesByNumberSubstring() {
        assertTrue(T9.matches("0712", "Mary Wanjiku", "0712 345 678"))
        assertTrue(T9.matches("345", "Mary Wanjiku", "0712 345 678"))
        assertFalse(T9.matches("999", "Mary Wanjiku", "0712 345 678"))
    }

    @Test
    fun matchesByNameThroughT9() {
        // "mary" -> 6279
        assertTrue(T9.matches("6279", "Mary Wanjiku", "0712 345 678"))
        assertTrue(T9.matches("627", "Mary Wanjiku", "0712 345 678"))
        assertFalse(T9.matches("6279", "Peter Otieno", "0733 000 111"))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(T9.matches("", "Anyone", "0700 000 000"))
    }

    @Test
    fun formatsLocalNumbersInGroups() {
        assertEquals("0712 345 678", T9.formatNumber("0712345678"))
        assertEquals("712 345 678", T9.formatNumber("712345678"))
        assertEquals("+254 712 345 678", T9.formatNumber("+254712345678"))
        assertEquals("112", T9.formatNumber("112"))
    }

    @Test
    fun buildsInitialsForAvatars() {
        assertEquals("MW", T9.initials("Mary Wanjiku"))
        assertEquals("M", T9.initials("Mary"))
        assertEquals("#", T9.initials("  "))
    }
}
