package com.threedd.studio.content

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException

/**
 * The age-check itself, separated from storage so the rule is directly unit testable.
 * Fails closed: anything unparseable or impossible is a rejection, never a pass.
 */
object AgeVerification {

    sealed interface Result {
        data class Unlocked(val age: Int) : Result
        data object Underage : Result
        data object InvalidDate : Result
        data object FutureDate : Result
        data object ImplausibleDate : Result
    }

    /** Rejects dates older than a human lifespan so a typo like 0001-01-01 cannot pass. */
    const val MAX_AGE = 120

    fun verify(dateOfBirth: String, minimumAge: Int = 18, today: LocalDate = LocalDate.now()): Result {
        val cleaned = dateOfBirth.trim()
        if (cleaned.isEmpty()) return Result.InvalidDate
        val parsed = try {
            LocalDate.parse(cleaned)
        } catch (e: DateTimeParseException) {
            return Result.InvalidDate
        }
        if (parsed.isAfter(today)) return Result.FutureDate
        val age = Period.between(parsed, today).years
        if (age > MAX_AGE) return Result.ImplausibleDate
        return if (age >= minimumAge) Result.Unlocked(age) else Result.Underage
    }
}
