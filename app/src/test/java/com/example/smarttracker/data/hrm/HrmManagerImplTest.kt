package com.example.smarttracker.data.hrm

import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.data.hrm.sensor.HeartRateSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Тесты state-machine [HrmManagerImpl] на фейковом [HeartRateSensor].
 *
 * Ключевой контракт — агрессивный реконнект без лимита попыток:
 * тренировка должна быть максимально покрыта пульсом, менеджер не сдаётся
 * после N неудач. Плюс: свежесть сэмпла (freshBpm), реакция на выключение
 * Bluetooth, ручной disconnect.
 *
 * ВАЖНО про тайминг: цикл подключения бесконечен ПО КОНТРАКТУ — тест,
 * завершившийся с живым циклом (pending delay на общем scheduler'е),
 * заставляет cleanup runTest крутить виртуальное время вечно (та же грабля,
 * что self-rescheduling таймер в WorkoutStartViewModelTest). Поэтому:
 * (1) все тесты идут через [runManagerTest], который отменяет scope менеджера
 * в конце ТЕЛА теста; (2) внутри тестов — только runCurrent/advanceTimeBy,
 * никакого advanceUntilIdle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HrmManagerImplTest {

    /** Фейковый сенсор: считает вызовы, отдаёт listener наружу для симуляции событий. */
    private class FakeSensor : HeartRateSensor {
        var connectCount = 0
        var disconnectCount = 0
        var stopScanCount = 0
        var lastAddress: String? = null
        var lastListener: HeartRateSensor.Listener? = null
        var scanOnResult: ((HrmScanResult) -> Unit)? = null
        var scanOnError: ((Int) -> Unit)? = null

        override fun startScan(onResult: (HrmScanResult) -> Unit, onError: (Int) -> Unit) {
            scanOnResult = onResult
            scanOnError = onError
        }

        override fun stopScan() {
            stopScanCount++
        }

        override fun connect(address: String, listener: HeartRateSensor.Listener) {
            connectCount++
            lastAddress = address
            lastListener = listener
        }

        override fun disconnect() {
            disconnectCount++
        }
    }

    private lateinit var fake: FakeSensor
    private var btEnabled = true
    private var nowMs = 100_000L

    private fun TestScope.createManager(): HrmManagerImpl {
        fake = FakeSensor()
        btEnabled = true
        nowMs = 100_000L
        return HrmManagerImpl(mock()).apply {
            sensorProvider = { fake }
            timeSource = { nowMs }
            bluetoothEnabledProvider = { btEnabled }
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        }
    }

    /**
     * runTest с гарантированной отменой scope менеджера в конце тела теста —
     * до фазы очистки runTest (см. KDoc класса).
     */
    private fun runManagerTest(
        block: suspend TestScope.(HrmManagerImpl) -> Unit,
    ) = runTest {
        val manager = createManager()
        try {
            block(manager)
        } finally {
            manager.scope.cancel()
        }
    }

    // ── Подключение ───────────────────────────────────────────────────────────

    @Test
    fun `connect - CONNECTING затем CONNECTED после подписки`() = runManagerTest { manager ->
        manager.connect("AA:BB")
        runCurrent()

        assertEquals(HrmConnectionState.CONNECTING, manager.connectionState.value)
        assertEquals(1, fake.connectCount)
        assertEquals("AA:BB", fake.lastAddress)

        fake.lastListener!!.onConnected()
        runCurrent()

        assertEquals(HrmConnectionState.CONNECTED, manager.connectionState.value)
    }

    @Test
    fun `обрыв соединения - немедленный реконнект и сброс сэмпла`() = runManagerTest { manager ->
        manager.connect("AA:BB")
        runCurrent()
        fake.lastListener!!.onConnected()
        runCurrent()
        fake.lastListener!!.onHeartRate(150)
        assertEquals(150, manager.currentSample.value?.bpm)

        // Датчик отвалился (вне зоны / снят)
        fake.lastListener!!.onDisconnected(unexpected = true)
        runCurrent()

        assertEquals(HrmConnectionState.RECONNECTING, manager.connectionState.value)
        assertEquals("Реконнект должен начаться немедленно", 2, fake.connectCount)
        assertNull("Сэмпл сброшен при обрыве", manager.currentSample.value)

        fake.lastListener!!.onConnected()
        runCurrent()
        assertEquals(HrmConnectionState.CONNECTED, manager.connectionState.value)
    }

    @Test
    fun `серия неудачных попыток НЕ останавливает цикл - лимита нет`() = runManagerTest { manager ->
        manager.connect("AA:BB")
        runCurrent()
        assertEquals(1, fake.connectCount)

        // 6 неудач подряд — больше любого «разумного» лимита ретраев
        repeat(6) {
            fake.lastListener!!.onDisconnected(unexpected = true)
            runCurrent()
            advanceTimeBy(HrmManagerImpl.RETRY_DELAY_MS + 1)
            runCurrent()
        }

        assertEquals("Цикл продолжает ретраить", 7, fake.connectCount)
        assertEquals(HrmConnectionState.RECONNECTING, manager.connectionState.value)

        // И в итоге всё же подключается
        fake.lastListener!!.onConnected()
        runCurrent()
        assertEquals(HrmConnectionState.CONNECTED, manager.connectionState.value)
    }

    @Test
    fun `таймаут попытки - закрытие gatt и следующий ретрай`() = runManagerTest { manager ->
        manager.connect("AA:BB")
        runCurrent()
        assertEquals(1, fake.connectCount)

        // Датчик молчит: ни onConnected, ни onDisconnected — ждём таймаут
        advanceTimeBy(HrmManagerImpl.CONNECT_TIMEOUT_MS + HrmManagerImpl.RETRY_DELAY_MS + 1)
        runCurrent()

        assertTrue("gatt закрыт перед ретраем", fake.disconnectCount >= 1)
        assertEquals("Начата следующая попытка", 2, fake.connectCount)
    }

    @Test
    fun `ручной disconnect - ретраи остановлены, состояние DISCONNECTED`() = runManagerTest { manager ->
        manager.connect("AA:BB")
        runCurrent()
        fake.lastListener!!.onConnected()
        runCurrent()
        fake.lastListener!!.onHeartRate(140)

        manager.disconnect()
        runCurrent()

        assertEquals(HrmConnectionState.DISCONNECTED, manager.connectionState.value)
        assertNull(manager.currentSample.value)

        // Никаких новых попыток после ручного разрыва
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, fake.connectCount)
    }

    // ── freshBpm ──────────────────────────────────────────────────────────────

    @Test
    fun `freshBpm - свежий сэмпл возвращается, протухший нет`() = runManagerTest { manager ->
        manager.connect("AA:BB")
        runCurrent()
        fake.lastListener!!.onConnected()
        runCurrent()

        fake.lastListener!!.onHeartRate(148)
        assertEquals(148, manager.freshBpm())

        // Датчик замолчал (но соединение формально живо) — сэмпл протухает
        nowMs += HrmManager.DEFAULT_FRESH_BPM_MAX_AGE_MS + 1
        assertNull("Протухший сэмпл не отдаётся", manager.freshBpm())
    }

    @Test
    fun `freshBpm - null без активного соединения`() = runManagerTest { manager ->
        assertNull(manager.freshBpm())
    }

    // ── Bluetooth-адаптер ─────────────────────────────────────────────────────

    @Test
    fun `connect при выключенном Bluetooth - BLUETOOTH_OFF без попыток`() = runManagerTest { manager ->
        btEnabled = false

        manager.connect("AA:BB")
        runCurrent()

        assertEquals(HrmConnectionState.BLUETOOTH_OFF, manager.connectionState.value)
        assertEquals(0, fake.connectCount)
    }

    @Test
    fun `выключение BT рвёт соединение, включение - возобновляет`() = runManagerTest { manager ->
        manager.connect("AA:BB")
        runCurrent()
        fake.lastListener!!.onConnected()
        runCurrent()

        btEnabled = false
        manager.onBluetoothOff()

        assertEquals(HrmConnectionState.BLUETOOTH_OFF, manager.connectionState.value)
        assertNull(manager.currentSample.value)
        assertTrue(fake.disconnectCount >= 1)

        btEnabled = true
        manager.onBluetoothOn()
        runCurrent()

        assertEquals(HrmConnectionState.RECONNECTING, manager.connectionState.value)
        assertEquals("Подключение возобновлено", 2, fake.connectCount)
    }

    // ── Сканирование ──────────────────────────────────────────────────────────

    @Test
    fun `startScan - isScanning true, результаты доходят подписчику, stopScan сбрасывает`() = runManagerTest { manager ->
        val results = mutableListOf<HrmScanResult>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            manager.scanResults.collect { results.add(it) }
        }

        manager.startScan()
        runCurrent()
        assertTrue(manager.isScanning.value)

        fake.scanOnResult!!(HrmScanResult("AA:BB", "Polar H10", -60))
        runCurrent()
        assertEquals(1, results.size)
        assertEquals("Polar H10", results.first().name)

        manager.stopScan()
        runCurrent()
        assertFalse(manager.isScanning.value)
        assertEquals(1, fake.stopScanCount)

        job.cancel()
    }

    @Test
    fun `ошибка скана сбрасывает isScanning`() = runManagerTest { manager ->
        manager.startScan()
        runCurrent()
        assertTrue(manager.isScanning.value)

        fake.scanOnError!!(2) // SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
        runCurrent()

        assertFalse(manager.isScanning.value)
    }
}
