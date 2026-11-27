package com.example.bluetoothtracker.ui.language

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.bluetoothtracker.core.AppLanguage
import com.example.bluetoothtracker.core.LocaleHelper
import com.example.bluetoothtracker.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isFromSettings: Boolean = savedStateHandle.get<Boolean>("isFromSettings") ?: false

    /** Currently active application language when this screen opened. */
    val appliedLanguage: AppLanguage = LocaleHelper.currentLanguage()

    /** Highlighted pending selection; does NOT apply locale until Continue is pressed. */
    val pendingLanguage: StateFlow<AppLanguage> =
        savedStateHandle.getStateFlow(KEY_PENDING_TAG, appliedLanguage.tag)
            .map { AppLanguage.fromTag(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, appliedLanguage)

    fun selectPendingLanguage(language: AppLanguage) {
        savedStateHandle[KEY_PENDING_TAG] = language.tag
    }

    suspend fun commitPendingLanguage(): AppLanguage {
        val chosen = pendingLanguage.value
        settingsRepository.setLanguage(chosen.tag, markSelected = true)
        return chosen
    }

    companion object {
        private const val KEY_PENDING_TAG = "pending_language_tag"
    }
}
