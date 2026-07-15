package com.example.smarttracker.presentation.menu.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.hrm.HrmManager
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.data.local.SavedHrmDevice
import com.example.smarttracker.data.local.SettingsStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние экрана/диалога «Датчики».
 *
 * @param permissionsGranted Bluetooth-разрешения выданы (гейт для скана)
 * @param savedDevices       сохранённые пульсометры (список из настроек)
 * @param activeAddress      адрес активного датчика (к нему автоконнект)
 * @param connectionState    текущее состояние соединения (общее с сервисом —
 *   HrmManager singleton)
 * @param currentBpm         живой пульс; null если не подключён/нет данных
 * @param isScanning         идёт сканирование
 * @param hasScanned         пользователь запускал скан — гейт секции
 *   «найденные» и подсказки «не найдено»
 * @param promptEnableBluetooth показать окно «включите Bluetooth» —
 *   выставляется при попытке скана / входе с выключенным адаптером
 * @param scanResults        сырые результаты скана: дедуп по адресу,
 *   сортировка по rssi (для UI фильтруются через [foundDevices])
 */
data class SensorsUiState(
    val permissionsGranted: Boolean = false,
    val savedDevices: List<SavedHrmDevice> = emptyList(),
    val activeAddress: String? = null,
    val connectionState: HrmConnectionState = HrmConnectionState.DISCONNECTED,
    val currentBpm: Int? = null,
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val promptEnableBluetooth: Boolean = false,
    val scanResults: List<HrmScanResult> = emptyList(),
) {
    /**
     * Найденные НОВЫЕ датчики: результаты скана без уже сохранённых.
     * Добавленный через «+» датчик реактивно исчезает из «найденных»
     * и появляется в списке сохранённых.
     */
    val foundDevices: List<HrmScanResult>
        get() = scanResults.filterNot { result ->
            savedDevices.any { it.address == result.address }
        }
}

/**
 * ViewModel экрана/диалога «Датчики»: список сохранённых пульсометров,
 * скан и добавление новых.
 *
 * Соединением владеет [HrmManager] (singleton) — при уходе с экрана оно
 * НЕ рвётся намеренно: пользователь подключил датчик → пошёл на экран
 * тренировки → сервис пишет пульс с уже живого соединения. Разрыв — только
 * при удалении активного датчика или в onDestroy сервиса. Скан при уходе
 * останавливается ([onCleared] / [onScanStopRequested]).
 *
 * Семантика списка: активный датчик = последний выбранный пользователем
 * (тап по строке или добавление через «+»); автоконнект идёт к нему.
 */
@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val hrmManager: HrmManager,
    private val settingsStorage: SettingsStorage,
) : ViewModel() {

    companion object {
        /**
         * Автостоп ручного скана. Один скан на тап + таймаут — защита от
         * троттлинга Android (>4 сканов за 30 с молча возвращают пустоту)
         * и от вечного жора батареи забытым сканом.
         */
        const val SCAN_TIMEOUT_MS = 30_000L

        /**
         * Таймаут автопоиска при входе без сохранённых датчиков — короче
         * ручного: это фоновое удобство, а не явное действие пользователя.
         * Не успел за 10 с → стоп, дальше пользователь ищет кнопкой.
         */
        const val AUTO_SCAN_TIMEOUT_MS = 10_000L
    }

    private val _state = MutableStateFlow(SensorsUiState())
    val state: StateFlow<SensorsUiState> = _state.asStateFlow()

    private var scanTimeoutJob: Job? = null

    /** Снимок внешних источников (настройки + менеджер) для зеркалирования в UiState. */
    private data class ExternalState(
        val devices: List<SavedHrmDevice>,
        val activeAddress: String?,
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
                    devices = settings.hrmDevices,
                    activeAddress = settings.hrmActiveAddress,
                    connection = connection,
                    bpm = if (connection == HrmConnectionState.CONNECTED) sample?.bpm else null,
                    scanning = scanning,
                )
            }.collect { external ->
                _state.update {
                    it.copy(
                        savedDevices = external.devices,
                        activeAddress = external.activeAddress,
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
    }

    fun onPermissionsGranted() {
        _state.update { it.copy(permissionsGranted = true) }
        // При входе с выключенным адаптером — сразу предложить включить,
        // не пытаясь сканировать (скан вернул бы пустоту).
        if (!hrmManager.isBluetoothEnabled()) {
            _state.update { it.copy(promptEnableBluetooth = true) }
            return
        }
        maybeAutoScan()
    }

    fun onPermissionsDenied() {
        _state.update { it.copy(permissionsGranted = false) }
    }

    /** Один скан на тап; повторный тап во время скана игнорируется. */
    fun onScanClick() {
        if (_state.value.isScanning) return
        if (!hrmManager.isBluetoothEnabled()) {
            _state.update { it.copy(promptEnableBluetooth = true) }
            return
        }
        startScan(SCAN_TIMEOUT_MS)
    }

    /** Пользователь закрыл окно «включите Bluetooth» без включения. */
    fun onDismissBluetoothPrompt() {
        _state.update { it.copy(promptEnableBluetooth = false) }
    }

    /**
     * Bluetooth включён через системный диалог — убрать окно и продолжить
     * то, ради чего оно показывалось (поиск датчиков).
     */
    fun onBluetoothEnabled() {
        _state.update { it.copy(promptEnableBluetooth = false) }
        onScanClick()
    }

    /**
     * Автопоиск при входе: сохранённых датчиков нет — сразу сканируем,
     * не заставляя пользователя жать кнопку. Вызывается из
     * [onPermissionsGranted] (скан без разрешений/адаптера невозможен —
     * оба гейта уже пройдены к этому месту).
     *
     * Список читается напрямую из хранилища (settings.first()), а не из
     * UiState: зеркалирующий collector мог ещё не отработать — по стейту
     * автопоиск стартовал бы и при наличии сохранённых датчиков.
     */
    private fun maybeAutoScan() {
        viewModelScope.launch {
            if (settingsStorage.settings.first().hrmDevices.isNotEmpty()) return@launch
            if (_state.value.isScanning) return@launch
            startScan(AUTO_SCAN_TIMEOUT_MS)
        }
    }

    private fun startScan(timeoutMs: Long) {
        _state.update { it.copy(scanResults = emptyList(), hasScanned = true) }
        hrmManager.startScan()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(timeoutMs)
            hrmManager.stopScan()
        }
    }

    /**
     * «+» на найденном датчике: сохранить в список (сразу активным —
     * пользователь явно его выбрал) и подключиться. Persist немедленный,
     * не после CONNECTED: датчик только что найден сканом, он включён.
     */
    fun onAddDeviceClick(device: HrmScanResult) {
        viewModelScope.launch {
            settingsStorage.addHrmDevice(device.address, device.name)
        }
        hrmManager.connect(device.address)
    }

    /** Тап по строке сохранённого датчика: сделать активным и подключиться. */
    fun onSavedDeviceClick(device: SavedHrmDevice) {
        viewModelScope.launch {
            settingsStorage.setActiveHrmDevice(device.address)
        }
        hrmManager.connect(device.address)
    }

    /**
     * Корзина на строке датчика: удалить из списка. Если удаляется
     * активный (к нему идёт/держится соединение) — разорвать.
     */
    fun onRemoveDeviceClick(device: SavedHrmDevice) {
        if (device.address == _state.value.activeAddress) {
            hrmManager.disconnect()
        }
        viewModelScope.launch {
            settingsStorage.removeHrmDevice(device.address)
        }
    }

    /**
     * Явная остановка скана — для закрытия диалога «Датчики».
     * VM скоупится на backstack-entry Home и переживает закрытие диалога,
     * onCleared не срабатывает — без явного вызова скан продолжал бы жечь
     * батарею под экраном тренировки.
     */
    fun onScanStopRequested() {
        scanTimeoutJob?.cancel()
        hrmManager.stopScan()
    }

    override fun onCleared() {
        // Скан бессмысленен без экрана; соединение НЕ рвём (см. KDoc класса)
        scanTimeoutJob?.cancel()
        hrmManager.stopScan()
        super.onCleared()
    }
}
