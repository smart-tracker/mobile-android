package com.example.smarttracker.presentation

import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.presentation.navigation.Screen
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
 * - logout() вызывает tokenStorage.clearAll()
 *
 * Поломка startRoute ломает авто-логин молча: пользователь каждый раз
 * попадает на экран входа даже после успешного логина.
 */
class AppViewModelTest {

    private lateinit var tokenStorage: TokenStorage

    @Before
    fun setUp() {
        tokenStorage = mock()
    }

    // ── Тест 10: startRoute определяется по наличию токенов ──────────────────

    @Test
    fun `startRoute равен Home если токены есть`() {
        whenever(tokenStorage.hasTokens()).thenReturn(true)
        val viewModel = AppViewModel(tokenStorage)
        assertEquals(Screen.Home.route, viewModel.startRoute)
    }

    @Test
    fun `startRoute равен Login если токенов нет`() {
        whenever(tokenStorage.hasTokens()).thenReturn(false)
        val viewModel = AppViewModel(tokenStorage)
        assertEquals(Screen.Login.route, viewModel.startRoute)
    }

    @Test
    fun `logout вызывает clearAll в TokenStorage`() {
        whenever(tokenStorage.hasTokens()).thenReturn(true)
        val viewModel = AppViewModel(tokenStorage)

        viewModel.logout()

        verify(tokenStorage).clearAll()
    }
}
