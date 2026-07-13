package com.example.smarttracker.presentation.menu.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.hrm.HrmManager
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.data.local.SettingsStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние экрана «Датчики».
 *
 * @param permissionsGranted Bluetooth-разрешения выданы (гейт для скана)
 * @param savedDeviceAddress адрес сохранённого пульсометра (null = не настроен)
 * @param savedDeviceName    имя сохранённого пульсометра для отображения
 * @param connectionState    текущее состояние соединения (общее с сервисом —
 *   HrmManager singleton)
 * @param currentBpm         живой пульс; null если не подключён/нет данных
 * @param isScanning         идёт сканирование
 * @param hasScanned         пользователь запускал скан на этом экране —
 *   гейт подсказки «датчики не найдены» (до первого скана она не к месту)
 * @param scanResults        найденные устройства: дедупликация по адресу,
 *   сортировка по силе сигнала (ближайший датчик — сверху)
 */
data class SensorsUiState(
    val permissionsGranted: Boolean = false,
    val savedDeviceAddress: String? = null,
    val savedDeviceName: String? = null,
    val connectionState: HrmConnectionState = HrmConnectionState.DISCONNECTED,
    val currentBpm: Int? = null,
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val scanResults: List<HrmScanResult> = emptyList(),
)

/**
 * ViewModel экрана «Датчики»: скан, подключение и сохранение BLE-пульсометра.
 *
 * Соединением владеет [HrmManager] (singleton) — при уходе с экрана оно
 * НЕ рвётся намеренно: пользователь подключил датчик → пошёл на экран
 * тренировки → сервис пишет пульс с уже живого соединения. Разрыв — только
 * явный («Забыть датчик») или в onDestroy сервиса. Скан при уходе с экрана
 * останавливается ([onCleared]) — он нужен только для списка.
 *
 * Выбранное устройство сохраняется в DataStore ТОЛЬКО после первого
 * успешного CONNECTED: клик по чужому/недоступному устройству не должен
 * затирать рабочий сохранённый датчик.
 */
@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val hrmManager: HrmManager,
    private val settingsStorage: SettingsStorage,
) : ViewModel() {

    companion object {
        /**
         * Автостоп скана. Один скан на тап + таймаут — защита от троттлинга
         * Android (>4 сканов за 30 с молча возвращают пустоту) и от вечного
         * жора батареи забытым сканом.
         */
        const val SCAN_TIMEOUT_MS = 30_000L
    }

    private val _state = MutableStateFlow(SensorsUiState())
    val state: StateFlow<SensorsUiState> = _state.asStateFlow()

    private var scanTimeoutJob: Job? = null

    /** Устройство, ожидающее сохранения после первого CONNECTED. */
    private var pendingSave: HrmScanResult? = null

    /** Снимок внешних источников (настройки + менеджер) для зеркалирования в UiState. */
    private data class ExternalState(
        val address: String?,
        val name: String?,
        val connection: HrmConnectionState,
        val bpm: Int?,
        val scanning: Boolean,
    )

    init {
        // Зеркалим настройки и состояние менеджера в UiState
        viewModelScope.launch {
            combine(
                settingsStorage.settings,
                hrmManager.connectionState,
                hrmManager.currentSample,
                hrmManager.isScanning,
            ) { settings, connection, sample, scanning ->
                ExternalState(
                    address = settings.hrmDeviceAddress,
                    name = settings.hrmDeviceName,
                    connection = connection,
                    bpm = if (connection == HrmConnectionState.CONNECTED) sample?.bpm else null,
                    scanning = scanning,
                )
            }.collect { external ->
                _state.update {
                    it.copy(
                        savedDeviceAddress = external.address,
                        savedDeviceName = external.name,
                        connectionState = external.connection,
                        currentBpm = external.bpm,
                        isScanning = external.scanning,
                    )
                }
            }
        }

        // Накопление результатов скана: дедуп по адресу (обновляем rssi/имя),
        // сортировка по rssi — сильный сигнал сверху.
        viewModelScope.launch {
            hrmManager.scanResults.collect { result ->
                _state.update { current ->
                    val merged = current.scanResults
                        .filterNot { it.address == result.address } + result
                    current.copy(scanResults = merged.sortedByDescending { it.rssi })
                }
            }
        }

        // Персист выбранного устройства после первого успешного подключения
        viewModelScope.launch {
            hrmManager.connectionState.collect { connection ->
                if (connection == HrmConnectionState.CONNECTED) {
                    pendingSave?.let { device ->
                        pendingSave = null
                        settingsStorage.setHrmDevice(device.address, device.name)
                    }
                }
            }
        }
    }

    fun onPermissionsGranted() {
        _state.update { it.copy(permissionsGranted = true) }
    }

    fun onPermissionsDenied() {
        _state.update { it.copy(permissionsGranted = false) }
    }

    /** Один скан на тап; повторный тап во время скана игнорируется. */
    fun onScanClick() {
        if (_state.value.isScanning) return
        _state.update { it.copy(scanResults = emptyList(), hasScanned = true) }
        hrmManager.startScan()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            hrmManager.stopScan()
        }
    }

    /** Клик по найденному устройству: подключить, при успехе — сохранить. */
    fun onDeviceClick(device: HrmScanResult) {
        scanTimeoutJob?.cancel()
        pendingSave = device
        hrmManager.connect(device.address)
    }

    /** «Подключить» на карточке сохранённого датчика (проверка связи вне тренировки). */
    fun onConnectSavedClick() {
        val address = _state.value.savedDeviceAddress ?: return
        hrmManager.connect(address)
    }

    /** «Забыть датчик»: разорвать соединение и стереть из настроек. */
    fun onForgetClick() {
        pendingSave = null
        hrmManager.disconnect()
        viewModelScope.launch { settingsStorage.setHrmDevice(null, null) }
    }

    override fun onCleared() {
        // Скан бессмысленен без экрана; соединение НЕ рвём (см. KDoc класса)
        scanTimeoutJob?.cancel()
        hrmManager.stopScan()
        super.onCleared()
    }
}
