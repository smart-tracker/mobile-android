package com.example.smarttracker.presentation.menu.sensors

import com.example.smarttracker.data.hrm.HrmManager
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmSample
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.data.local.AppSettings
import com.example.smarttracker.data.local.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

/**
 * Тесты [SensorsViewModel]: накопление результатов скана (дедуп/сортировка),
 * сохранение датчика только после успешного подключения, «Забыть датчик»,
 * автостоп скана по таймауту.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensorsViewModelTest {

    private val settingsFlow = MutableStateFlow(AppSettings())
    private val connectionState = MutableStateFlow(HrmConnectionState.DISCONNECTED)
    private val currentSample = MutableStateFlow<HrmSample?>(null)
    private val isScanning = MutableStateFlow(false)
    private val scanResults = MutableSharedFlow<HrmScanResult>(extraBufferCapacity = 16)

    private lateinit var hrmManager: HrmManager
    private lateinit var settingsStorage: SettingsStorage
    private lateinit var viewModel: SensorsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        connectionState.value = HrmConnectionState.DISCONNECTED
        currentSample.value = null
        isScanning.value = false
        settingsFlow.value = AppSettings()
        hrmManager = mock {
            on { this.connectionState } doReturn connectionState
            on { this.currentSample } doReturn currentSample
            on { this.isScanning } doReturn isScanning
            on { this.scanResults } doReturn scanResults
        }
        settingsStorage = mock {
            on { settings } doReturn settingsFlow
        }
        viewModel = SensorsViewModel(hrmManager, settingsStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `результаты скана - дедуп по адресу и сортировка по rssi`() = runTest {
        scanResults.tryEmit(HrmScanResult("AA", "Polar", -80))
        scanResults.tryEmit(HrmScanResult("BB", "Garmin", -50))
        // Повтор AA с обновлённым rssi — должен заменить старую запись
        scanResults.tryEmit(HrmScanResult("AA", "Polar", -40))

        val list = viewModel.state.value.scanResults
        assertEquals(2, list.size)
        assertEquals("Сильный сигнал сверху", "AA", list[0].address)
        assertEquals(-40, list[0].rssi)
        assertEquals("BB", list[1].address)
    }

    @Test
    fun `клик по устройству - connect, сохранение только после CONNECTED`() = runTest {
        val device = HrmScanResult("AA:BB", "Polar H10", -50)
        viewModel.onDeviceClick(device)

        verify(hrmManager).connect("AA:BB")
        // До подключения ничего не сохраняем — клик по чужому датчику
        // не должен затирать рабочий сохранённый
        verifyBlocking(settingsStorage, never()) { setHrmDevice("AA:BB", "Polar H10") }

        connectionState.value = HrmConnectionState.CONNECTED

        verifyBlocking(settingsStorage) { setHrmDevice("AA:BB", "Polar H10") }
    }

    @Test
    fun `забыть датчик - disconnect и очистка настроек`() = runTest {
        viewModel.onForgetClick()

        verify(hrmManager).disconnect()
        verifyBlocking(settingsStorage) { setHrmDevice(null, null) }
    }

    @Test
    fun `скан автоматически останавливается по таймауту`() = runTest {
        viewModel.onScanClick()
        verify(hrmManager).startScan()

        advanceTimeBy(SensorsViewModel.SCAN_TIMEOUT_MS + 1)

        verify(hrmManager).stopScan()
    }

    @Test
    fun `повторный тап по скану во время скана игнорируется`() = runTest {
        isScanning.value = true
        viewModel.onScanClick()

        verify(hrmManager, never()).startScan()
    }

    @Test
    fun `bpm показывается только при CONNECTED`() = runTest {
        currentSample.value = HrmSample(bpm = 150, timestampMs = 0L)
        assertNull("Без соединения пульс не показывается", viewModel.state.value.currentBpm)

        connectionState.value = HrmConnectionState.CONNECTED
        assertEquals(150, viewModel.state.value.currentBpm)
    }

    @Test
    fun `state отражает сохранённый датчик из настроек`() = runTest {
        settingsFlow.value = AppSettings(
            hrmDeviceAddress = "AA:BB:CC",
            hrmDeviceName = "Magene H64",
        )

        assertEquals("AA:BB:CC", viewModel.state.value.savedDeviceAddress)
        assertEquals("Magene H64", viewModel.state.value.savedDeviceName)
        assertTrue(viewModel.state.value.scanResults.isEmpty())
    }
}
