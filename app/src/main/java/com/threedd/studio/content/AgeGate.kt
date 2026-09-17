package com.threedd.studio.content

import android.content.Context
import com.threedd.studio.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException

/**
 * Age verification for the mature module. The date of birth is validated properly and the
 * unlock state is persisted; the gate fails closed whenever the stored value is unreadable.
 */
class AgeGate(private val context: Context, private val store: SettingsStore) {

    sealed interface Result {
        data class Unlocked(val age: Int) : Result
        data object Underage : Result
        data object InvalidDate : Result
        data object FutureDate : Result
    }

    val isUnlocked: Flow<Boolean> = store.snapshot.map { it.ageVerified }

    /** Parses yyyy-MM-dd, rejects impossible/future dates and applies the 18-year rule. */
    fun verify(dateOfBirth: String, minimumAge: Int = 18): Result {
        val cleaned = dateOfBirth.trim()
        val parsed = try {
            LocalDate.parse(cleaned)
        } catch (e: DateTimeParseException) {
            return Result.InvalidDate
        }
        val today = LocalDate.now()
        if (parsed.isAfter(today)) return Result.FutureDate
        val age = Period.between(parsed, today).years
        return if (age >= minimumAge) Result.Unlocked(age) else Result.Underage
    }

    suspend fun commit(result: Result, dateOfBirth: String) {
        when (result) {
            is Result.Unlocked -> store.setAgeVerified(true, dateOfBirth.trim())
            else -> store.setAgeVerified(false, "")
        }
    }

    suspend fun lock() = store.setAgeVerified(false, "")

    fun currentRating(unlocked: Boolean): ContentRating =
        if (unlocked) ContentRating.MATURE else ContentRating.GENERAL
}
