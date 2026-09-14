package com.example.exercisetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InputRulesTest {

    @Test
    fun ordinaryUsernamesAreAllowed() {
        assertNull(Username.problemWith("Sam_99"))
        assertNull(Username.problemWith("Big Lifter"))
        assertNull(Username.problemWith("j.doe-2"))
    }

    @Test
    fun shortUsernamesAreRejected() {
        assertNotNull(Username.problemWith("ab"))
    }

    @Test
    fun lookAlikeCharactersAreRejected() {
        assertNotNull(Username.problemWith("Zoë"))
        assertNotNull(Username.problemWith("Аdmin")) // Cyrillic А
        assertNotNull(Username.problemWith("hi!"))
    }

    @Test
    fun namesThatCouldPassForStaffAreReserved() {
        assertNotNull(Username.problemWith("Admin"))
        assertNotNull(Username.problemWith("ad min"))
        assertNotNull(Username.problemWith("Official_Sam"))
        assertNotNull(Username.problemWith("Exercise Tracker"))
    }

    @Test
    fun cleanTrimsCollapsesAndCaps() {
        assertEquals("Big Lifter", Username.clean("  Big    Lifter  ", "uid12345"))
        assertEquals(Username.MAX_LENGTH, Username.clean("x".repeat(40), "uid12345").length)
    }

    @Test
    fun blankNamesFallBackToTheDefault() {
        assertEquals("Athlete 9F2A", Username.clean("   ", "abc9f2a"))
        assertEquals("Athlete 9F2A", Username.defaultFor("abc9f2a"))
    }

    @Test
    fun passwordsNeedEightCharactersWithALetterAndANumber() {
        assertNotNull(PasswordRules.problemWith("short1"))
        assertNotNull(PasswordRules.problemWith("longpassword"))
        assertNotNull(PasswordRules.problemWith("12345678"))
        assertNull(PasswordRules.problemWith("goodpass1"))
    }
}
