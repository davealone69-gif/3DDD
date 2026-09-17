package com.threedd.studio

import com.threedd.studio.content.AgeVerification
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeVerificationTest {

    private val today = LocalDate.of(2026, 6, 15)

    @Test
    fun `adult over eighteen is unlocked with the right age`() {
        val result = AgeVerification.verify("1990-03-02", today = today)
        assertTrue(result is AgeVerification.Result.Unlocked)
        assertEquals(36, (result as AgeVerification.Result.Unlocked).age)
    }

    @Test
    fun `exactly eighteen on the day passes`() {
        val result = AgeVerification.verify("2008-06-15", today = today)
        assertTrue(result is AgeVerification.Result.Unlocked)
        assertEquals(18, (result as AgeVerification.Result.Unlocked).age)
    }

    @Test
    fun `one day short of eighteen fails`() {
        assertEquals(AgeVerification.Result.Underage, AgeVerification.verify("2008-06-16", today = today))
    }

    @Test
    fun `future dates are rejected`() {
        assertEquals(AgeVerification.Result.FutureDate, AgeVerification.verify("2030-01-01", today = today))
    }

    @Test
    fun `malformed input fails closed`() {
        assertEquals(AgeVerification.Result.InvalidDate, AgeVerification.verify("", today = today))
        assertEquals(AgeVerification.Result.InvalidDate, AgeVerification.verify("not-a-date", today = today))
        assertEquals(AgeVerification.Result.InvalidDate, AgeVerification.verify("15/06/1990", today = today))
        assertEquals(AgeVerification.Result.InvalidDate, AgeVerification.verify("1990-13-45", today = today))
    }

    @Test
    fun `implausibly old dates are rejected rather than treated as adults`() {
        assertEquals(AgeVerification.Result.ImplausibleDate, AgeVerification.verify("1800-01-01", today = today))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertTrue(AgeVerification.verify("  1990-03-02  ", today = today) is AgeVerification.Result.Unlocked)
    }
}
