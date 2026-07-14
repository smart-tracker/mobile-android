package com.example.smarttracker.presentation.menu.sensors

import com.example.smarttracker.data.hrm.HrmManager
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmSample
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.data.local.AppSettings
import com.example.smarttracker.data.local.SavedHrmDevice
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

/**
 * Тесты [SensorsViewModel]: список сохранённых датчиков, накопление и
 * фильтрация результатов скана, добавление через «+», переключение
 * активного, удаление корзиной, автостоп скана.
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

    private val polar = SavedHrmDevice("AA:BB:CC:DD:EE:FF", "Polar H10")
    private val magene = SavedHrmDevice("11:22:33:44:55:66", "Magene H64")

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
    fun `найденные фильтруются от уже сохранённых`() = runTest {
        settingsFlow.value = AppSettings(
            hrmDevices = listOf(polar),
            hrmActiveAddress = polar.address,
        )
        scanResults.tryEmit(HrmScanResult(polar.address, "Polar H10", -50))
        scanResults.tryEmit(HrmScanResult("CC:DD", "Coospo", -60))

        val found = viewModel.state.value.foundDevices
        assertEquals("Сохранённый Polar не должен попадать в найденные", 1, found.size)
        assertEquals("CC:DD", found.first().address)
    }

    @Test
    fun `плюс - добавляет в настройки и подключается`() = runTest {
        val device = HrmScanResult("CC:DD", "Coospo", -60)

        viewModel.onAddDeviceClick(device)

        verifyBlocking(settingsStorage) { addHrmDevice("CC:DD", "Coospo") }
        verify(hrmManager).connect("CC:DD")
    }

    @Test
    fun `тап по сохранённому - переключение активного и подключение`() = runTest {
        viewModel.onSavedDeviceClick(magene)

        verifyBlocking(settingsStorage) { setActiveHrmDevice(magene.address) }
        verify(hrmManager).connect(magene.address)
    }

    @Test
    fun `корзина на активном - удаление с разрывом соединения`() = runTest {
        settingsFlow.value = AppSettings(
            hrmDevices = listOf(polar, magene),
            hrmActiveAddress = polar.address,
        )

        viewModel.onRemoveDeviceClick(polar)

        verify(hrmManager).disconnect()
        verifyBlocking(settingsStorage) { removeHrmDevice(polar.address) }
    }

    @Test
    fun `корзина на неактивном - удаление без разрыва`() = runTest {
        settingsFlow.value = AppSettings(
            hrmDevices = listOf(polar, magene),
            hrmActiveAddress = polar.address,
        )

        viewModel.onRemoveDeviceClick(magene)

        verify(hrmManager, never()).disconnect()
        verifyBlocking(settingsStorage) { removeHrmDevice(magene.address) }
    }

    @Test
    fun `скан автоматически останавливается по таймауту`() = runTest {
        viewModel.onScanClick()
        verify(hrmManager).startScan()

        advanceTimeBy(SensorsViewModel.SCAN_TIMEOUT_MS + 1)

        verify(hrmManager).stopScan()
    }

    @Test
    fun `автопоиск при входе без сохранённых датчиков`() = runTest {
        // Пустой список (дефолт) + выданы разрешения → скан стартует сам
        viewModel.onPermissionsGranted()

        verify(hrmManager).startScan()

        // И останавливается по короткому авто-таймауту
        advanceTimeBy(SensorsViewModel.AUTO_SCAN_TIMEOUT_MS + 1)
        verify(hrmManager).stopScan()
    }

    @Test
    fun `автопоиск НЕ стартует при наличии сохранённых датчиков`() = runTest {
        settingsFlow.value = AppSettings(
            hrmDevices = listOf(polar),
            hrmActiveAddress = polar.address,
        )

        viewModel.onPermissionsGranted()

        verify(hrmManager, never()).startScan()
    }

    @Test
    fun `повторный тап по скану во время скана игнорируется`() = runTest {
        isScanning.value = true
        viewModel.onScanClick()

        verify(hrmManager, never()).startScan()
    }

    @Test
    fun `onScanStopRequested останавливает скан и отменяет таймаут`() = runTest {
        viewModel.onScanClick()

        viewModel.onScanStopRequested()
        verify(hrmManager).stopScan()

        // Таймаут-джоба отменена — второго stopScan по истечении таймаута нет
        advanceTimeBy(SensorsViewModel.SCAN_TIMEOUT_MS + 1)
        verify(hrmManager, times(1)).stopScan()
    }

    @Test
    fun `bpm показывается только при CONNECTED`() = runTest {
        currentSample.value = HrmSample(bpm = 150, timestampMs = 0L)
        assertNull("Без соединения пульс не показывается", viewModel.state.value.currentBpm)

        connectionState.value = HrmConnectionState.CONNECTED
        assertEquals(150, viewModel.state.value.currentBpm)
    }

    @Test
    fun `state отражает список и активный адрес из настроек`() = runTest {
        settingsFlow.value = AppSettings(
            hrmDevices = listOf(polar, magene),
            hrmActiveAddress = magene.address,
        )

        assertEquals(listOf(polar, magene), viewModel.state.value.savedDevices)
        assertEquals(magene.address, viewModel.state.value.activeAddress)
        assertTrue(viewModel.state.value.scanResults.isEmpty())
    }
}
