package com.example.bluetoothtracker.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.bluetoothtracker.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val pageIndex: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_PAGE, 0)

    fun setPage(index: Int) {
        val clamped = index.coerceIn(0, LAST_PAGE)
        if (pageIndex.value != clamped) {
            savedStateHandle[KEY_PAGE] = clamped
        }
    }

    fun nextPage(): Boolean {
        val current = pageIndex.value
        return if (current < LAST_PAGE) {
            savedStateHandle[KEY_PAGE] = current + 1
            true
        } else {
            false
        }
    }

    fun previousPage(): Boolean {
        val current = pageIndex.value
        return if (current > 0) {
            savedStateHandle[KEY_PAGE] = current - 1
            true
        } else {
            false
        }
    }

    suspend fun completeOnboarding() {
        settingsRepository.setOnboardingCompleted(true)
    }

    companion object {
        private const val KEY_PAGE = "onboarding_page"
        const val LAST_PAGE = 2
        const val PAGE_COUNT = 3
    }
}
