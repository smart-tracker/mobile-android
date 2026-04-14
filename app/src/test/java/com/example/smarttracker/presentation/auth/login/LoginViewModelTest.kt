package com.example.smarttracker.presentation.auth.login

import com.example.smarttracker.domain.usecase.LoginUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit-тесты LoginViewModel.
 *
 * Покрывает:
 * - Консистентность isFormValid() и validateForm() при password.length=7:
 *   обе функции проверяют минимум 8 символов независимо.
 *   Рассинхронизация → кнопка активна, submit выдаёт ошибку.
 */
class LoginViewModelTest {

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LoginViewModel(mock<LoginUseCase>())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Тест 13: консистентность isFormValid и validateForm ──────────────────

    @Test
    fun `isFormValid false при password длиной 7 символов`() = runTest {
        viewModel.onEmailChange("user@test.com")
        viewModel.onPasswordChange("Short1!")  // 7 символов

        assertFalse(
            "isFormValid() должен вернуть false для пароля < 8 символов",
            viewModel.isFormValid()
        )
    }

    @Test
    fun `onSubmitLogin выставляет errorMessage при password длиной 7 символов`() = runTest {
        viewModel.onEmailChange("user@test.com")
        viewModel.onPasswordChange("Short1!")  // 7 символов

        viewModel.onSubmitLogin()

        assertTrue(
            "errorMessage должен быть непустым при пароле < 8 символов",
            viewModel.state.value.errorMessage != null
        )
    }
}
