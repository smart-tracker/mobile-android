package com.example.smarttracker.presentation.menu.settings

import com.example.smarttracker.data.local.AppSettings
import com.example.smarttracker.data.local.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Тесты [SettingsViewModel]: каждый обработчик экрана транслируется в
 * соответствующий вызов [SettingsStorage], состояние отражает поток хранилища.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val settingsFlow = MutableStateFlow(AppSettings())
    private lateinit var storage: SettingsStorage
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        storage = mock {
            on { settings } doReturn settingsFlow
        }
        viewModel = SettingsViewModel(storage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state отражает значения из хранилища`() = runTest {
        // stateIn(WhileSubscribed) начинает собирать только при подписчике
        val job = launch { viewModel.state.collect {} }
        settingsFlow.value = AppSettings(
            autopauseEnabled = true,
            voiceCuesEnabled = false,
            voiceCueIntervalKm = 5,
            keepScreenOn = true,
        )
        testScheduler.advanceUntilIdle()

        assertEquals(settingsFlow.value, viewModel.state.value)
        job.cancel()
    }

    @Test
    fun `переключение автопаузы пишет в хранилище`() = runTest {
        viewModel.onAutopauseChanged(true)
        testScheduler.advanceUntilIdle()
        verify(storage).setAutopauseEnabled(true)
    }

    @Test
    fun `переключение голосовых подсказок пишет в хранилище`() = runTest {
        viewModel.onVoiceCuesChanged(false)
        testScheduler.advanceUntilIdle()
        verify(storage).setVoiceCuesEnabled(false)
    }

    @Test
    fun `смена частоты подсказок пишет в хранилище`() = runTest {
        viewModel.onVoiceCueIntervalChanged(2)
        testScheduler.advanceUntilIdle()
        verify(storage).setVoiceCueIntervalKm(2)
    }

    @Test
    fun `переключение keepScreenOn пишет в хранилище`() = runTest {
        viewModel.onKeepScreenOnChanged(true)
        testScheduler.advanceUntilIdle()
        verify(storage).setKeepScreenOn(true)
    }

    @Test
    fun `переключение удержания завершения пишет в хранилище`() = runTest {
        viewModel.onFinishConfirmationHoldChanged(false)
        testScheduler.advanceUntilIdle()
        verify(storage).setFinishConfirmationHold(false)
    }
}
