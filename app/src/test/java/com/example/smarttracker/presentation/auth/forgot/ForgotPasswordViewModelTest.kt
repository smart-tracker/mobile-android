package com.example.smarttracker.presentation.auth.forgot

import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.domain.model.ResetPasswordResult
import com.example.smarttracker.domain.repository.PasswordRecoveryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit-тесты ForgotPasswordViewModel.
 *
 * Покрывает:
 * - Навигацию после сброса пароля (redirectToLogin → Home vs Login)
 * - Машину состояний шагов 1-3 и кнопку Back
 * - Фильтрацию нецифровых символов в verificationCode
 * - Защиту от повторной отправки кода при cooldown > 0
 *
 * Примечание по `submitEmail()`: использует android.util.Patterns.EMAIL_ADDRESS,
 * который в JVM-тестах возвращает null → NPE. Тесты, требующие шага 2 или 3,
 * устанавливают состояние напрямую через reflection, минуя submitEmail().
 */
class ForgotPasswordViewModelTest {

    private lateinit var repository: PasswordRecoveryRepository
    private lateinit var tokenStorage: TokenStorage
    private lateinit var viewModel: ForgotPasswordViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository   = mock()
        tokenStorage = mock()
        viewModel    = ForgotPasswordViewModel(repository, tokenStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Тест 2: навигация после сброса пароля ────────────────────────────────

    @Test
    fun `submitResetPassword - redirectToLogin=false приводит к NavigateToHomeAfterReset`() = runTest {
        // Переводим ViewModel в шаг 3 напрямую (минуя Patterns-зависимый submitEmail)
        setState(
            ForgotPasswordUiState(
                currentStep      = 3,
                email            = "user@test.com",
                verificationCode = "123456",
            )
        )
        viewModel.onEvent(ForgotPasswordEvent.OnNewPasswordChanged("NewPass1"))
        viewModel.onEvent(ForgotPasswordEvent.OnConfirmPasswordChanged("NewPass1"))

        whenever(repository.resetPassword(any())).thenReturn(
            Result.success(
                ResetPasswordResult(
                    message         = "ok",
                    success         = true,
                    redirectToLogin = false, // авто-вход удался
                )
            )
        )

        val emittedEvents = mutableListOf<ForgotPasswordEvent>()
        val collectJob = launch(testDispatcher) {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.onEvent(ForgotPasswordEvent.OnResetPassword)
        collectJob.cancel()

        assertTrue(
            "Ожидался NavigateToHomeAfterReset, получено: $emittedEvents",
            emittedEvents.contains(ForgotPasswordEvent.NavigateToHomeAfterReset)
        )
        // tokenStorage.clearAll() НЕ должен вызываться — токены уже сохранены репозиторием
        verify(tokenStorage, never()).clearAll()
    }

    // ── Тест 3: машина состояний шагов ──────────────────────────────────────

    @Test
    fun `back на шаге 1 эмитит NavigateToLoginFromBack`() = runTest {
        // Начальное состояние — шаг 1 (дефолт)
        val emittedEvents = mutableListOf<ForgotPasswordEvent>()
        val collectJob = launch(testDispatcher) {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.onEvent(ForgotPasswordEvent.OnBackPressed)
        collectJob.cancel()

        assertTrue(
            "Ожидался NavigateToLoginFromBack, получено: $emittedEvents",
            emittedEvents.contains(ForgotPasswordEvent.NavigateToLoginFromBack)
        )
    }

    @Test
    fun `back на шаге 2 уменьшает currentStep до 1`() = runTest {
        setState(viewModel.uiState.value.copy(currentStep = 2))

        viewModel.onEvent(ForgotPasswordEvent.OnBackPressed)

        assertEquals(1, viewModel.uiState.value.currentStep)
    }

    // ── Тест 8: фильтрация нецифровых символов в коде ────────────────────────

    @Test
    fun `OnVerificationCodeChanged - нецифровые символы отфильтровываются, max 6`() = runTest {
        // take(6) применяется ДО filter → "abc123456".take(6) = "abc123", filter → "123"
        viewModel.onEvent(ForgotPasswordEvent.OnVerificationCodeChanged("abc123456"))
        assertEquals("123", viewModel.uiState.value.verificationCode)

        // 7 цифр → take(6) → 6 цифр
        viewModel.onEvent(ForgotPasswordEvent.OnVerificationCodeChanged("1234567"))
        assertEquals("123456", viewModel.uiState.value.verificationCode)
    }

    // ── Тест 9: защита от спама при cooldown > 0 ────────────────────────────

    @Test
    fun `resendCode при cooldown больше нуля не вызывает API`() = runTest {
        setState(viewModel.uiState.value.copy(resendCodeCooldown = 60))

        viewModel.onEvent(ForgotPasswordEvent.OnResendCode)

        verify(repository, never()).resendResetCode(any())
    }

    // ── Хелпер: установить внутреннее состояние через reflection ─────────────

    private fun setState(state: ForgotPasswordUiState) {
        val field = ForgotPasswordViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableStateFlow<ForgotPasswordUiState>).value = state
    }
}
