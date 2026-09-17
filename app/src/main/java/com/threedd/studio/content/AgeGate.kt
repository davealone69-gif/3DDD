package com.threedd.studio.content

import android.content.Context
import com.threedd.studio.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Age verification for the mature module. The date of birth is validated properly and the
 * unlock state is persisted; the gate fails closed whenever the stored value is unreadable.
 */
class AgeGate(private val context: Context, private val store: SettingsStore) {

    val isUnlocked: Flow<Boolean> = store.snapshot.map { it.ageVerified }

    fun verify(dateOfBirth: String, minimumAge: Int = 18): AgeVerification.Result =
        AgeVerification.verify(dateOfBirth, minimumAge)

    suspend fun commit(result: AgeVerification.Result, dateOfBirth: String) {
        when (result) {
            is AgeVerification.Result.Unlocked -> store.setAgeVerified(true, dateOfBirth.trim())
            else -> store.setAgeVerified(false, "")
        }
    }

    suspend fun lock() = store.setAgeVerified(false, "")

    fun currentRating(unlocked: Boolean): ContentRating =
        if (unlocked) ContentRating.MATURE else ContentRating.GENERAL
}
