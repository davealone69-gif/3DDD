package com.threedd.studio.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.threedd.studio.data.model.QualityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_settings")

@Singleton
class SettingsStore @Inject constructor(private val context: Context) {

    private object Keys {
        val QUALITY = stringPreferencesKey("quality")
        val MUSIC = booleanPreferencesKey("music_enabled")
        val SFX = booleanPreferencesKey("sfx_enabled")
        val VOLUME = floatPreferencesKey("volume")
        val LOOP_ANIM = booleanPreferencesKey("loop_animation")
        val ANIM_SPEED = floatPreferencesKey("animation_speed")
        val AGE_VERIFIED = booleanPreferencesKey("age_verified")
        val AGE_DOB = stringPreferencesKey("age_dob")
    }

    data class Snapshot(
        val quality: QualityPreset = QualityPreset.BALANCED,
        val musicEnabled: Boolean = true,
        val sfxEnabled: Boolean = true,
        val volume: Float = 0.7f,
        val loopAnimation: Boolean = true,
        val animationSpeed: Float = 1.0f,
        val ageVerified: Boolean = false,
        val ageDob: String = ""
    )

    val snapshot: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            quality = p[Keys.QUALITY]?.let { runCatching { QualityPreset.valueOf(it) }.getOrNull() }
                ?: QualityPreset.BALANCED,
            musicEnabled = p[Keys.MUSIC] ?: true,
            sfxEnabled = p[Keys.SFX] ?: true,
            volume = p[Keys.VOLUME] ?: 0.7f,
            loopAnimation = p[Keys.LOOP_ANIM] ?: true,
            animationSpeed = p[Keys.ANIM_SPEED] ?: 1.0f,
            ageVerified = p[Keys.AGE_VERIFIED] ?: false,
            ageDob = p[Keys.AGE_DOB] ?: ""
        )
    }

    suspend fun setQuality(preset: QualityPreset) = context.dataStore.edit { it[Keys.QUALITY] = preset.name }
    suspend fun setMusicEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.MUSIC] = enabled }
    suspend fun setSfxEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SFX] = enabled }
    suspend fun setVolume(value: Float) = context.dataStore.edit { it[Keys.VOLUME] = value.coerceIn(0f, 1f) }
    suspend fun setLoopAnimation(loop: Boolean) = context.dataStore.edit { it[Keys.LOOP_ANIM] = loop }
    suspend fun setAnimationSpeed(speed: Float) = context.dataStore.edit { it[Keys.ANIM_SPEED] = speed.coerceIn(0.1f, 3f) }

    suspend fun setAgeVerified(verified: Boolean, dob: String) = context.dataStore.edit {
        it[Keys.AGE_VERIFIED] = verified
        it[Keys.AGE_DOB] = dob
    }
}
