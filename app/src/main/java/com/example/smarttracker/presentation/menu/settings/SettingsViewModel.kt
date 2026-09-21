package com.example.smarttracker.presentation.menu.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.local.AppSettings
import com.example.smarttracker.data.local.SettingsStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана «Настройки».
 *
 * UiState — сам [AppSettings]: экран отображает ровно то, что лежит в
 * хранилище, отдельная UiState-обёртка не даёт ничего (загрузка DataStore
 * мгновенная, ошибок чтения нет — storage деградирует к дефолтам сам).
 *
 * Записи fire-and-forget: DataStore сериализует edit-транзакции сам,
 * состояние обновится через подписку на [SettingsStorage.settings].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStorage: SettingsStorage,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settingsStorage.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    fun onAutopauseChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStorage.setAutopauseEnabled(enabled) }
    }

    fun onVoiceCuesChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStorage.setVoiceCuesEnabled(enabled) }
    }

    fun onVoiceCueIntervalChanged(intervalKm: Int) {
        viewModelScope.launch { settingsStorage.setVoiceCueIntervalKm(intervalKm) }
    }

    fun onKeepScreenOnChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStorage.setKeepScreenOn(enabled) }
    }

    fun onFinishConfirmationHoldChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStorage.setFinishConfirmationHold(enabled) }
    }

    fun onShowHeartRateBadgeChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStorage.setShowHeartRateBadge(enabled) }
    }
}
