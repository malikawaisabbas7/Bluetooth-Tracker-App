package com.example.bluetoothtracker.ui.home

data class HomeUiState(
    val isScanning: Boolean = false,
    val airTagCount: Int = 0,
    val otherTagCount: Int = 0,
    val bluetoothCount: Int = 0
) {
    val total: Int get() = airTagCount + bluetoothCount
}

sealed interface HomeEvent {
    data class ScanFailed(val reason: String) : HomeEvent
}
