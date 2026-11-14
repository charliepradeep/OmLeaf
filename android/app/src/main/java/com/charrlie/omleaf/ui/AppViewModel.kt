package com.charrlie.omleaf.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.charrlie.omleaf.data.AppSettings
import com.charrlie.omleaf.data.CustomPractice
import com.charrlie.omleaf.data.Repository
import com.charrlie.omleaf.domain.MeditationSettings
import com.charrlie.omleaf.domain.PlanRequest
import com.charrlie.omleaf.domain.SessionRecord
import com.charrlie.omleaf.domain.SoundSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the session screen needs to run. Set just before navigating to it. */
data class SessionSpec(
    val title: String,
    val request: PlanRequest,
    val sound: SoundSettings,
    val haptics: Boolean,
    val keepPlaying: Boolean = true,
)

class AppViewModel(private val repo: Repository) : ViewModel() {
    /** Null until the first read finishes, so the first-run note never flashes for returning users. */
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val custom: StateFlow<List<CustomPractice>> = repo.customPractices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<SessionRecord>> = repo.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var pending: SessionSpec? by mutableStateOf(null)

    fun saveSound(sound: SoundSettings) = viewModelScope.launch { repo.saveSound(sound) }
    fun saveKeepPlaying(on: Boolean) = viewModelScope.launch { repo.saveKeepPlaying(on) }
    fun saveReminder(on: Boolean, minuteOfDay: Int) = viewModelScope.launch { repo.saveReminder(on, minuteOfDay) }
    fun saveMeditation(m: MeditationSettings) = viewModelScope.launch { repo.saveMeditation(m) }
    fun saveHaptics(on: Boolean) = viewModelScope.launch { repo.saveHaptics(on) }
    fun saveCustom(item: CustomPractice) = viewModelScope.launch { repo.saveCustom(item) }
    fun deleteCustom(id: String) = viewModelScope.launch { repo.deleteCustom(id) }
    fun addSession(record: SessionRecord) = viewModelScope.launch { repo.addSession(record) }
    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }

    companion object {
        fun factory(repo: Repository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(repo) }
        }
    }
}
