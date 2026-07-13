package com.example.smarttracker.data.hrm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmSample
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.data.hrm.sensor.HeartRateSensor
import com.example.smarttracker.data.hrm.sensor.HeartRateSensorFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [HrmManager]: state-machine соединения поверх «тонкого»
 * [HeartRateSensor].
 *
 * Потокобезопасность: колбэки сенсора приходят на binder-потоках
 * Bluetooth-стека, поэтому ВСЕ переходы состояния сериализуются через
 * [scope] с параллелизмом 1 — гонки между обрывом, ретраем и командами
 * пользователя исключены по построению. Исключение — `_currentSample.value`
 * в onHeartRate: запись в StateFlow атомарна, порядок с переходами
 * состояния не важен.
 *
 * Цикл переподключения (см. контракт [HrmManager]): одна попытка =
 * `sensor.connect` + ожидание исхода с таймаутом [CONNECT_TIMEOUT_MS];
 * неудача → `sensor.disconnect()` (закрыть gatt — иначе гарантированный
 * GATT 133 на следующей попытке) → пауза [RETRY_DELAY_MS] → снова.
 * Реконнект идёт по MAC-адресу без сканирования — троттлинг сканов
 * Android (5 сканов / 30 с) его не задевает.
 *
 * Тестовые швы ([sensorProvider], [timeSource], [scope],
 * [bluetoothEnabledProvider]) — internal var с боевыми дефолтами:
 * unit-тесты подменяют их до первого использования, Hilt-граф не усложняется.
 */
@Singleton
class HrmManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : HrmManager {

    companion object {
        private const val TAG = "HrmManager"

        /** Таймаут одной попытки подключения (connectGatt + discover + подписка). */
        const val CONNECT_TIMEOUT_MS = 15_000L

        /** Пауза между попытками — минимальная, только чтобы стек закрыл gatt. */
        const val RETRY_DELAY_MS = 1_000L
    }

    // --- тестовые швы -----------------------------------------------------

    internal var sensorProvider: () -> HeartRateSensor = { HeartRateSensorFactory.create(context) }

    internal var timeSource: () -> Long = System::currentTimeMillis

    @OptIn(ExperimentalCoroutinesApi::class)
    internal var scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    internal var bluetoothEnabledProvider: () -> Boolean = {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        try {
            adapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    // --- публичное состояние ----------------------------------------------

    private val _connectionState = MutableStateFlow(HrmConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<HrmConnectionState> = _connectionState.asStateFlow()

    private val _currentSample = MutableStateFlow<HrmSample?>(null)
    override val currentSample: StateFlow<HrmSample?> = _currentSample.asStateFlow()

    private val _scanResults = MutableSharedFlow<HrmScanResult>(extraBufferCapacity = 64)
    override val scanResults: SharedFlow<HrmScanResult> = _scanResults.asSharedFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // --- внутреннее состояние (мутируется только внутри scope) -------------

    private var sensor: HeartRateSensor? = null

    /** Адрес, соединение с которым нужно удерживать; null = соединение не нужно. */
    @Volatile
    private var desiredAddress: String? = null

    private var connectJob: Job? = null

    /** Исход текущей попытки подключения; null вне попытки. */
    private var pendingAttempt: CompletableDeferred<Boolean>? = null

    private var btReceiverRegistered = false

    // --- команды ------------------------------------------------------------

    override fun startScan() {
        scope.launch {
            ensureBtReceiver()
            _isScanning.value = true
            getSensor().startScan(
                onResult = { result -> _scanResults.tryEmit(result) },
                onError = { code ->
                    Log.w(TAG, "Ошибка сканирования: $code")
                    scope.launch { _isScanning.value = false }
                },
            )
        }
    }

    override fun stopScan() {
        scope.launch { stopScanInternal() }
    }

    override fun connect(address: String) {
        scope.launch {
            ensureBtReceiver()
            stopScanInternal()
            // Идемпотентность: живое соединение с тем же датчиком не трогаем
            if (desiredAddress == address && _connectionState.value == HrmConnectionState.CONNECTED) {
                return@launch
            }
            desiredAddress = address
            launchConnectLoop(firstAttempt = true)
        }
    }

    override fun disconnect() {
        scope.launch {
            desiredAddress = null
            connectJob?.cancel()
            connectJob = null
            pendingAttempt = null
            getSensor().disconnect()
            _currentSample.value = null
            _connectionState.value = HrmConnectionState.DISCONNECTED
        }
    }

    override fun freshBpm(maxAgeMs: Long): Int? {
        if (_connectionState.value != HrmConnectionState.CONNECTED) return null
        val sample = _currentSample.value ?: return null
        return sample.bpm.takeIf { timeSource() - sample.timestampMs <= maxAgeMs }
    }

    // --- цикл подключения ----------------------------------------------------

    /**
     * (Пере)запустить цикл подключения к [desiredAddress].
     * Крутится до успеха, отмены или выключения Bluetooth — лимита попыток НЕТ.
     */
    private fun launchConnectLoop(firstAttempt: Boolean) {
        connectJob?.cancel()
        connectJob = scope.launch {
            var first = firstAttempt
            while (isActive) {
                val address = desiredAddress ?: return@launch
                if (!bluetoothEnabledProvider()) {
                    _connectionState.value = HrmConnectionState.BLUETOOTH_OFF
                    return@launch // возобновится по ACTION_STATE_CHANGED → STATE_ON
                }
                _connectionState.value =
                    if (first) HrmConnectionState.CONNECTING else HrmConnectionState.RECONNECTING
                first = false

                val attempt = CompletableDeferred<Boolean>()
                pendingAttempt = attempt
                getSensor().connect(address, sensorListener)
                val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { attempt.await() } ?: false
                pendingAttempt = null

                if (connected) return@launch // обрыв перезапустит цикл через listener

                getSensor().disconnect()
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private val sensorListener = object : HeartRateSensor.Listener {

        override fun onConnected() {
            scope.launch {
                _connectionState.value = HrmConnectionState.CONNECTED
                pendingAttempt?.complete(true)
            }
        }

        override fun onDisconnected(unexpected: Boolean) {
            scope.launch {
                _currentSample.value = null
                val attempt = pendingAttempt
                when {
                    // Неудача текущей попытки — цикл сам уйдёт на следующий ретрай
                    attempt != null && !attempt.isCompleted -> attempt.complete(false)

                    // Обрыв установленного соединения — немедленный реконнект
                    unexpected && desiredAddress != null -> launchConnectLoop(firstAttempt = false)

                    desiredAddress == null ->
                        _connectionState.value = HrmConnectionState.DISCONNECTED
                }
            }
        }

        override fun onHeartRate(bpm: Int) {
            // Атомарная запись StateFlow — сериализация через scope не нужна,
            // а лишний hop добавил бы задержку самому частому событию (~1/с)
            _currentSample.value = HrmSample(bpm, timeSource())
        }
    }

    // --- Bluetooth-адаптер ----------------------------------------------------

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> scope.launch { onBluetoothOff() }
                BluetoothAdapter.STATE_ON -> scope.launch { onBluetoothOn() }
            }
        }
    }

    /** Адаптер выключен: всё закрыть, ретраи приостановить. Internal — для тестов. */
    internal fun onBluetoothOff() {
        connectJob?.cancel()
        connectJob = null
        pendingAttempt = null
        getSensor().disconnect()
        stopScanInternal()
        _currentSample.value = null
        _connectionState.value =
            if (desiredAddress != null) HrmConnectionState.BLUETOOTH_OFF
            else HrmConnectionState.DISCONNECTED
    }

    /** Адаптер включён: немедленно возобновить удержание соединения. Internal — для тестов. */
    internal fun onBluetoothOn() {
        if (desiredAddress != null) {
            launchConnectLoop(firstAttempt = false)
        }
    }

    // --- служебное -------------------------------------------------------------

    private fun getSensor(): HeartRateSensor =
        sensor ?: sensorProvider().also { sensor = it }

    private fun stopScanInternal() {
        sensor?.stopScan()
        _isScanning.value = false
    }

    /**
     * Ленивая регистрация ресивера состояния адаптера. Singleton живёт
     * до смерти процесса — unregister не нужен.
     * ACTION_STATE_CHANGED — protected system broadcast, NOT_EXPORTED достаточно.
     */
    private fun ensureBtReceiver() {
        if (btReceiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                context,
                btStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            btReceiverRegistered = true
        } catch (e: Exception) {
            // Без ресивера деградация мягкая: не заметим off/on адаптера,
            // но цикл подключения сам упрётся в bluetoothEnabledProvider
            Log.w(TAG, "Не удалось зарегистрировать BT-ресивер", e)
        }
    }
}
