package com.charrlie.omleaf.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.charrlie.omleaf.domain.BreathPattern
import com.charrlie.omleaf.domain.Category
import com.charrlie.omleaf.domain.ChantSound
import com.charrlie.omleaf.domain.Kind
import com.charrlie.omleaf.domain.MeditationSettings
import com.charrlie.omleaf.domain.MeditationStyle
import com.charrlie.omleaf.domain.Practice
import com.charrlie.omleaf.domain.SessionRecord
import com.charrlie.omleaf.domain.SoundSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.store by preferencesDataStore(name = "omleaf")

@Serializable
data class CustomPractice(
    val id: String,
    val name: String,
    val inhale: Double,
    val holdIn: Double,
    val exhale: Double,
    val holdOut: Double,
    val rounds: Int,
    val chant: String = ChantSound.NONE.name,
) {
    fun toPractice() = Practice(
        id = id,
        name = name,
        sanskrit = "Your own",
        category = Category.CUSTOM,
        summary = "A pattern you made yourself.",
        howTo = emptyList(),
        caution = null,
        kind = Kind.RHYTHM,
        pattern = BreathPattern(inhale, holdIn, exhale, holdOut),
        rounds = rounds,
        chant = runCatching { ChantSound.valueOf(chant) }.getOrDefault(ChantSound.NONE),
    )
}

@Serializable
private data class StoredSession(val epochDay: Long, val practice: String, val seconds: Int, val rounds: Int)

data class AppSettings(
    val sound: SoundSettings = SoundSettings(),
    val haptics: Boolean = true,
    val acknowledgedNote: Boolean = false,
    val meditation: MeditationSettings = MeditationSettings(),
)

/** Everything is stored on this phone with DataStore. Nothing leaves the device. */
class Repository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val frequency = doublePreferencesKey("frequency")
        val chimes = booleanPreferencesKey("chimes")
        val guide = booleanPreferencesKey("guide")
        val drone = booleanPreferencesKey("drone")
        val volume = floatPreferencesKey("volume")
        val haptics = booleanPreferencesKey("haptics")
        val acknowledged = booleanPreferencesKey("acknowledged")
        val custom = stringPreferencesKey("custom")
        val history = stringPreferencesKey("history")
        val medMinutes = intPreferencesKey("med_minutes")
        val medStyle = stringPreferencesKey("med_style")
        val medBellEvery = intPreferencesKey("med_bell_every")
        val medBells = booleanPreferencesKey("med_bells")
    }

    val settings: Flow<AppSettings> = context.store.data.map { p ->
        val d = SoundSettings()
        AppSettings(
            sound = SoundSettings(
                frequencyHz = p[Keys.frequency] ?: d.frequencyHz,
                chimes = p[Keys.chimes] ?: d.chimes,
                guideTone = p[Keys.guide] ?: d.guideTone,
                drone = p[Keys.drone] ?: d.drone,
                volume = p[Keys.volume] ?: d.volume,
            ).sanitized(),
            haptics = p[Keys.haptics] ?: true,
            acknowledgedNote = p[Keys.acknowledged] ?: false,
            meditation = MeditationSettings(
                minutes = p[Keys.medMinutes] ?: MeditationSettings().minutes,
                style = runCatching { MeditationStyle.valueOf(p[Keys.medStyle].orEmpty()) }.getOrDefault(MeditationStyle.DRONE),
                bellEveryMinutes = p[Keys.medBellEvery] ?: 0,
                bells = p[Keys.medBells] ?: true,
            ).sanitized(),
        )
    }

    val customPractices: Flow<List<CustomPractice>> = context.store.data.map { p ->
        decode(p[Keys.custom], CustomPractice.serializer())
    }

    val history: Flow<List<SessionRecord>> = context.store.data.map { p ->
        decode(p[Keys.history], StoredSession.serializer()).map { SessionRecord(it.epochDay, it.practice, it.seconds, it.rounds) }
    }

    private fun <T> decode(raw: String?, s: kotlinx.serialization.KSerializer<T>): List<T> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(ListSerializer(s), raw) }.getOrDefault(emptyList())

    suspend fun saveSound(sound: SoundSettings) {
        val s = sound.sanitized()
        context.store.edit {
            it[Keys.frequency] = s.frequencyHz
            it[Keys.chimes] = s.chimes
            it[Keys.guide] = s.guideTone
            it[Keys.drone] = s.drone
            it[Keys.volume] = s.volume
        }
    }

    suspend fun saveMeditation(m: MeditationSettings) {
        val s = m.sanitized()
        context.store.edit {
            it[Keys.medMinutes] = s.minutes
            it[Keys.medStyle] = s.style.name
            it[Keys.medBellEvery] = s.bellEveryMinutes
            it[Keys.medBells] = s.bells
        }
    }

    suspend fun saveHaptics(on: Boolean) { context.store.edit { it[Keys.haptics] = on } }

    suspend fun acknowledgeNote() { context.store.edit { it[Keys.acknowledged] = true } }

    suspend fun saveCustom(item: CustomPractice) {
        context.store.edit { p ->
            val list = decode(p[Keys.custom], CustomPractice.serializer()).filterNot { it.id == item.id } + item
            p[Keys.custom] = json.encodeToString(ListSerializer(CustomPractice.serializer()), list)
        }
    }

    suspend fun deleteCustom(id: String) {
        context.store.edit { p ->
            val list = decode(p[Keys.custom], CustomPractice.serializer()).filterNot { it.id == id }
            p[Keys.custom] = json.encodeToString(ListSerializer(CustomPractice.serializer()), list)
        }
    }

    suspend fun addSession(record: SessionRecord) {
        context.store.edit { p ->
            val list = (decode(p[Keys.history], StoredSession.serializer()) + StoredSession(record.epochDay, record.practice, record.seconds, record.rounds))
                .takeLast(MAX_HISTORY)
            p[Keys.history] = json.encodeToString(ListSerializer(StoredSession.serializer()), list)
        }
    }

    suspend fun clearHistory() { context.store.edit { it.remove(Keys.history) } }

    private companion object { const val MAX_HISTORY = 1000 }
}
