package com.example.smarttracker.presentation.auth.register

import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.domain.model.AuthResult
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.GoalResponse
import com.example.smarttracker.domain.model.NicknameCheckResponse
import com.example.smarttracker.domain.model.RegisterResult
import com.example.smarttracker.domain.model.ResendResult
import com.example.smarttracker.domain.repository.AllowedEmailDomainsRepository
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.usecase.RegisterUseCase
import com.example.smarttracker.domain.validation.EmailValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

/**
 * Unit-тесты RegisterViewModel — многошаговая регистрация.
 *
 * Покрывает:
 *  - Валидацию шага 1 (имя, username, дата рождения, пол) и переход на шаг 2
 *  - Валидацию даты рождения (будущая дата, возраст 6–120 лет)
 *  - Валидацию шага 2 (выбор цели) и переход на шаг 3
 *  - Валидацию шага 3 (формат email, домен 149-ФЗ, пароль, условия)
 *  - Успешную регистрацию: переход на шаг 4 + кулдаун 120 сек (нюанс 9:
 *    НЕ result.expiresIn=600) + roleId из выбранной цели
 *  - Верификацию email: ошибки кода, событие NavigateToHome
 *  - Повторную отправку кода: кулдаун и тик таймера
 *  - Debounce-проверку уникальности nickname (700 мс)
 *
 * Дата рождения в тестах — строка из 8 цифр ддммгггг (нюанс 2:
 * DateVisualTransformation хранит только цифры).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var registerUseCase: RegisterUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var roleConfigStorage: RoleConfigStorage
    private lateinit var allowedEmailDomainsRepository: AllowedEmailDomainsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Создаёт ViewModel со стабами по умолчанию. Стабы задаются ДО конструктора:
     * init ViewModel сразу запускает загрузку доменов и debounce-коллектор.
     */
    private fun createViewModel(
        domains: Set<String> = setOf("yandex.ru", "mail.ru"),
        goals: List<GoalResponse> = emptyList(),
    ): RegisterViewModel {
        registerUseCase = mock()
        authRepository = mock {
            onBlocking { getGoalsByRole(anyOrNull()) } doReturn Result.success(goals)
            onBlocking { checkNickname(any()) } doReturn
                Result.success(NicknameCheckResponse("ivan123", isAvailable = true, message = "ok"))
        }
        roleConfigStorage = mock()
        allowedEmailDomainsRepository = mock {
            onBlocking { getAllowedDomains() } doReturn domains
        }
        return RegisterViewModel(
            registerUseCase,
            authRepository,
            roleConfigStorage,
            allowedEmailDomainsRepository,
        )
    }

    /** Заполняет валидный шаг 1. */
    private fun RegisterViewModel.fillStep1() {
        onFirstNameChange("Иван")
        onUsernameChange("ivan123")
        onBirthDateChange("01011990")
        onGenderChange(Gender.MALE)
    }

    /** Проходит шаги 1–2, оставляет ViewModel на шаге 3. */
    private fun RegisterViewModel.goToStep3() {
        fillStep1()
        onNext()            // шаг 1 → 2
        onGoalSelected(1)
        onNext()            // шаг 2 → 3
    }

    /** Заполняет валидный шаг 3. */
    private fun RegisterViewModel.fillStep3Valid() {
        onEmailChange("user@yandex.ru")
        onPasswordChange("password1")
        onConfirmPasswordChange("password1")
        onTermsAcceptedChange(true)
    }

    // ── Шаг 1: личные данные ──────────────────────────────────────────────────

    @Test
    fun `шаг 1 - пустое имя блокирует переход`() = runTest {
        val vm = createViewModel()
        vm.onUsernameChange("ivan123")
        vm.onBirthDateChange("01011990")
        vm.onGenderChange(Gender.MALE)

        vm.onNext()

        assertEquals(1, vm.state.value.step)
        assertEquals("Введите имя", vm.state.value.fieldError)
    }

    @Test
    fun `шаг 1 - username короче 3 символов блокирует переход`() = runTest {
        val vm = createViewModel()
        vm.fillStep1()
        vm.onUsernameChange("iv")

        vm.onNext()

        assertEquals(1, vm.state.value.step)
        assertEquals("Имя пользователя: минимум 3 символа", vm.state.value.fieldError)
    }

    @Test
    fun `шаг 1 - валидные данные переводят на шаг 2`() = runTest {
        val vm = createViewModel()
        vm.fillStep1()

        vm.onNext()

        assertEquals(2, vm.state.value.step)
        assertNull(vm.state.value.fieldError)
    }

    // ── Валидация даты рождения ───────────────────────────────────────────────

    @Test
    fun `дата рождения в будущем - статус ERROR`() = runTest {
        val vm = createViewModel()

        vm.onBirthDateChange("01012099")

        assertTrue(vm.state.value.birthDateCheckStatus is BirthDateCheckStatus.ERROR)
    }

    @Test
    fun `возраст меньше 6 лет - статус ERROR`() = runTest {
        val vm = createViewModel()

        // Родился ~3 года назад относительно текущей даты
        val year = java.time.LocalDate.now().year - 3
        vm.onBirthDateChange("0101$year")

        assertTrue(vm.state.value.birthDateCheckStatus is BirthDateCheckStatus.ERROR)
    }

    @Test
    fun `корректная дата рождения - статус SUCCESS`() = runTest {
        val vm = createViewModel()

        vm.onBirthDateChange("01011990")

        assertTrue(vm.state.value.birthDateCheckStatus is BirthDateCheckStatus.SUCCESS)
    }

    // ── Шаг 2: цель ───────────────────────────────────────────────────────────

    @Test
    fun `шаг 2 - без выбранной цели блокирует переход`() = runTest {
        val vm = createViewModel()
        vm.fillStep1()
        vm.onNext()

        vm.onNext()

        assertEquals(2, vm.state.value.step)
        assertNotNull(vm.state.value.fieldError)
    }

    @Test
    fun `шаг 2 - выбранная цель переводит на шаг 3`() = runTest {
        val vm = createViewModel()
        vm.fillStep1()
        vm.onNext()
        vm.onGoalSelected(1)

        vm.onNext()

        assertEquals(3, vm.state.value.step)
    }

    // ── Шаг 3: email, пароль, условия ─────────────────────────────────────────

    @Test
    fun `шаг 3 - некорректный формат email`() = runTest {
        val vm = createViewModel()
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onEmailChange("не-email")

        vm.onNext()

        assertEquals(3, vm.state.value.step)
        assertEquals("Некорректный формат email", vm.state.value.fieldError)
    }

    @Test
    fun `шаг 3 - домен не из списка разрешённых (149-ФЗ)`() = runTest {
        val vm = createViewModel(domains = setOf("yandex.ru"))
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onEmailChange("user@gmail.com")

        vm.onNext()

        assertEquals(3, vm.state.value.step)
        assertEquals(EmailValidator.RUSSIAN_EMAIL_REQUIRED_MESSAGE, vm.state.value.fieldError)
    }

    @Test
    fun `шаг 3 - пароль короче 8 символов`() = runTest {
        val vm = createViewModel()
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onPasswordChange("pass1")
        vm.onConfirmPasswordChange("pass1")

        vm.onNext()

        assertEquals("Пароль: минимум 8 символов", vm.state.value.fieldError)
    }

    @Test
    fun `шаг 3 - пароли не совпадают`() = runTest {
        val vm = createViewModel()
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onConfirmPasswordChange("password2")

        vm.onNext()

        assertEquals("Пароли не совпадают", vm.state.value.fieldError)
    }

    @Test
    fun `шаг 3 - без принятия условий`() = runTest {
        val vm = createViewModel()
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onTermsAcceptedChange(false)

        vm.onNext()

        assertEquals("Необходимо принять условия использования", vm.state.value.fieldError)
    }

    // ── Регистрация (submit шага 3) ───────────────────────────────────────────

    @Test
    fun `успешная регистрация - шаг 4 и кулдаун 120 сек, не expiresIn`() = runTest {
        val vm = createViewModel()
        registerUseCase.stub {
            // expiresIn=600 — жизнь кода; кулдаун кнопки должен остаться 120 (нюанс 9)
            onBlocking { invoke(any()) } doReturn
                Result.success(RegisterResult(email = "user@yandex.ru", expiresIn = 600))
        }
        vm.goToStep3()
        vm.fillStep3Valid()

        vm.onNext()

        assertEquals(4, vm.state.value.step)
        assertEquals(120, vm.state.value.resendCooldownSeconds)
    }

    @Test
    fun `регистрация - roleId берётся из выбранной цели`() = runTest {
        val vm = createViewModel(goals = listOf(GoalResponse(id = 7, description = "Похудеть", roleId = 2)))
        registerUseCase.stub {
            onBlocking { invoke(any()) } doReturn
                Result.success(RegisterResult(email = "user@yandex.ru", expiresIn = 600))
        }
        vm.loadAvailableGoals()   // загрузить стабовые цели без ожидания init-задержки
        vm.fillStep1()
        vm.onNext()
        vm.onGoalSelected(7)
        vm.onNext()
        vm.fillStep3Valid()

        vm.onNext()

        verify(roleConfigStorage).saveSelectedRoles(listOf(2))
        val captor = argumentCaptor<com.example.smarttracker.domain.model.RegisterRequest>()
        verifyBlocking(registerUseCase) { invoke(captor.capture()) }
        assertEquals(listOf(2), captor.firstValue.roleIds)
        assertEquals("ivan123", captor.firstValue.username)
    }

    @Test
    fun `ошибка регистрации - остаёмся на шаге 3 с ошибкой`() = runTest {
        val vm = createViewModel()
        registerUseCase.stub {
            onBlocking { invoke(any()) } doReturn Result.failure(Exception("server error"))
        }
        vm.goToStep3()
        vm.fillStep3Valid()

        vm.onNext()

        assertEquals(3, vm.state.value.step)
        assertNotNull(vm.state.value.error)
    }

    // ── Шаг 4: верификация email ──────────────────────────────────────────────

    @Test
    fun `верификация - код короче 6 символов даёт fieldError`() = runTest {
        val vm = createViewModel()
        registerUseCase.stub {
            onBlocking { invoke(any()) } doReturn
                Result.success(RegisterResult(email = "user@yandex.ru", expiresIn = 600))
        }
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onNext()
        vm.onVerificationCodeChange("123")

        vm.onNext()

        assertEquals("Код должен содержать 6 символов", vm.state.value.fieldError)
        verifyBlocking(authRepository, never()) { verifyEmail(any(), any()) }
    }

    @Test
    fun `успешная верификация - событие NavigateToHome`() = runTest {
        val vm = createViewModel()
        registerUseCase.stub {
            onBlocking { invoke(any()) } doReturn
                Result.success(RegisterResult(email = "user@yandex.ru", expiresIn = 600))
        }
        authRepository.stub {
            onBlocking { verifyEmail(any(), any()) } doReturn
                Result.success(AuthResult(accessToken = "a", refreshToken = "r"))
        }
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onNext()
        vm.onVerificationCodeChange("123456")

        // Коллектор должен подписаться ДО emit: SharedFlow без буфера
        val events = mutableListOf<RegisterEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.onNext()

        assertTrue(events.contains(RegisterEvent.NavigateToHome))
    }

    @Test
    fun `ошибка верификации - error, остаёмся на шаге 4`() = runTest {
        val vm = createViewModel()
        registerUseCase.stub {
            onBlocking { invoke(any()) } doReturn
                Result.success(RegisterResult(email = "user@yandex.ru", expiresIn = 600))
        }
        authRepository.stub {
            onBlocking { verifyEmail(any(), any()) } doReturn
                Result.failure(Exception("Invalid verification code"))
        }
        vm.goToStep3()
        vm.fillStep3Valid()
        vm.onNext()
        vm.onVerificationCodeChange("999999")

        vm.onNext()

        assertEquals(4, vm.state.value.step)
        assertNotNull(vm.state.value.error)
    }

    // ── Повторная отправка кода ───────────────────────────────────────────────

    @Test
    fun `resend - кулдаун 120 сек и тик таймера`() = runTest {
        val vm = createViewModel()
        authRepository.stub {
            onBlocking { resendCode(any()) } doReturn Result.success(ResendResult(expiresIn = 600))
        }
        vm.onEmailChange("user@yandex.ru")

        vm.onResendCode()
        assertEquals(120, vm.state.value.resendCooldownSeconds)

        advanceTimeBy(1001)
        assertEquals(119, vm.state.value.resendCooldownSeconds)
    }

    @Test
    fun `resend - ошибка не запускает кулдаун`() = runTest {
        val vm = createViewModel()
        authRepository.stub {
            onBlocking { resendCode(any()) } doReturn
                Result.failure(Exception("Please wait 90 seconds"))
        }
        vm.onEmailChange("user@yandex.ru")

        vm.onResendCode()

        assertEquals(0, vm.state.value.resendCooldownSeconds)
        assertNotNull(vm.state.value.error)
    }

    // ── Debounce-проверка nickname ────────────────────────────────────────────

    @Test
    fun `nickname - проверка уходит после debounce 700 мс`() = runTest {
        val vm = createViewModel()

        vm.onUsernameChange("ivan123")
        advanceTimeBy(701)

        assertTrue(vm.state.value.nicknameCheckStatus is NicknameCheckStatus.SUCCESS)
        verifyBlocking(authRepository) { checkNickname("ivan123") }
    }

    @Test
    fun `nickname короче 3 символов - проверка не вызывается`() = runTest {
        val vm = createViewModel()

        vm.onUsernameChange("iv")
        advanceTimeBy(701)

        assertEquals(NicknameCheckStatus.IDLE, vm.state.value.nicknameCheckStatus)
        verifyBlocking(authRepository, never()) { checkNickname(any()) }
    }
}
