package com.example.smarttracker.presentation

import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.local.UserProfileCache
import com.example.smarttracker.data.work.OfflineFinishScheduler
import com.example.smarttracker.presentation.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit-тесты AppViewModel.
 *
 * Покрывает:
 * - startRoute = Home если токены есть (авто-логин)
 * - startRoute = Login если токенов нет
 * - logout() вызывает tokenStorage.clearAll() и userProfileCache.clear()
 *
 * Поломка startRoute ломает авто-логин молча: пользователь каждый раз
 * попадает на экран входа даже после успешного логина.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var userProfileCache: UserProfileCache
    private lateinit var offlineFinishScheduler: OfflineFinishScheduler

    @Before
    fun setUp() {
        // viewModelScope в init AppViewModel требует Main-диспетчер.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tokenStorage = mock()
        userProfileCache = mock()
        offlineFinishScheduler = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Тест 10: startRoute определяется по наличию токенов ──────────────────

    @Test
    fun `startRoute равен Home если токены есть`() {
        whenever(tokenStorage.hasTokens()).thenReturn(true)
        val viewModel = AppViewModel(tokenStorage, userProfileCache, offlineFinishScheduler)
        assertEquals(Screen.Home.route, viewModel.startRoute)
    }

    @Test
    fun `startRoute равен Login если токенов нет`() {
        whenever(tokenStorage.hasTokens()).thenReturn(false)
        val viewModel = AppViewModel(tokenStorage, userProfileCache, offlineFinishScheduler)
        assertEquals(Screen.Login.route, viewModel.startRoute)
    }

    @Test
    fun `logout вызывает clearAll в TokenStorage`() {
        whenever(tokenStorage.hasTokens()).thenReturn(true)
        val viewModel = AppViewModel(tokenStorage, userProfileCache, offlineFinishScheduler)

        viewModel.logout()

        verify(tokenStorage).clearAll()
    }

    @Test
    fun `logout вызывает clear в UserProfileCache`() {
        whenever(tokenStorage.hasTokens()).thenReturn(true)
        val viewModel = AppViewModel(tokenStorage, userProfileCache, offlineFinishScheduler)

        viewModel.logout()

        verify(userProfileCache).clear()
    }
}
