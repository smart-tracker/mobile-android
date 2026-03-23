package com.example.smarttracker.presentation.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.UserPurpose
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.usecase.RegisterUseCase
import com.example.smarttracker.utils.ApiErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview

@OptIn(FlowPreview::class)
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val authRepository: AuthRepository,
    private val roleConfigStorage: RoleConfigStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()

    private var cooldownJob: Job? = null
    
    // ── Проверка уникальности nickname ──────────────────────────────────────
    private val nicknameCheckDebounceFlow = MutableStateFlow<String>("")
    private val nicknameCheckCache = mutableMapOf<String, Boolean>()
    private var nicknameCheckJob: Job? = null

    // Кулдаун для повторной отправки кода (120 сек = 2 минуты)
    // Не путать с VERIFICATION_CODE_EXPIRE_MINUTES (10 минут) — жизнь самого кода верификации!
    companion object {
        private const val RESEND_COOLDOWN_SECONDS = 120
        private const val NICKNAME_CHECK_DEBOUNCE_MILLIS = 700L
    }
    
    init {
        // Инициализировать debounce для проверки nickname
        nicknameCheckJob = viewModelScope.launch {
            nicknameCheckDebounceFlow
                .debounce(NICKNAME_CHECK_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect { nickname ->
                    if (nickname.length >= 3) {
                        checkNicknameUniqueAsync(nickname)
                    } else {
                        // Сбрасываем статус при пустом или коротком вводе
                        _state.update { it.copy(nicknameCheckStatus = NicknameCheckStatus.IDLE) }
                    }
                }
        }

        // МОБ-6 — Предзагружаем цели на Step 1, чтобы они были готовы к Step 2
        // Это избегает мерцания сообщения об ошибке при переходе на Step 2
        // Задержка в 500ms даёт время UI отрендериться перед начало загрузки
        viewModelScope.launch {
            delay(500)
            loadAvailableGoals()
        }
    }

    // ── Шаг 1: Личные данные ─────────────────────────────────────────────────

    fun onFirstNameChange(value: String) =
        _state.update { it.copy(firstName = value, fieldError = null) }

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value, fieldError = null) }
        // Запустить debounce для проверки уникальности nickname
        nicknameCheckDebounceFlow.value = value
    }

    fun onBirthDateChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(8)
        _state.update { it.copy(birthDate = digits, fieldError = null) }
        // Запустить валидацию даты
        if (digits.length == 8) {
            validateBirthDate(digits)
        } else {
            _state.update { it.copy(birthDateCheckStatus = BirthDateCheckStatus.IDLE) }
        }
    }

    fun onGenderChange(gender: Gender) =
        _state.update { it.copy(gender = gender, fieldError = null) }

    // ── Шаг 2: Цель использования ────────────────────────────────────────────

    fun onPurposeChange(purpose: UserPurpose) =
        _state.update { it.copy(purpose = purpose, fieldError = null) }

    /**
     * МОБ-6 — Загрузить доступные цели для Step 2.
     * Вызывается при первом отображении Step 2.
     * Результат кешируется на 1 час в RoleGoalCache.
     */
    fun loadAvailableGoals() {
        val currentGoals = _state.value.availableGoals
        // Если уже загружены — не загружаем повторно
        if (currentGoals.isNotEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingGoals = true, error = null) }
            authRepository.getGoalsByRole(null)  // Загружаем ВСЕ цели
                .onSuccess { goals ->
                    _state.update {
                        it.copy(
                            availableGoals = goals,
                            isLoadingGoals = false,
                        )
                    }
                }
                .onFailure { error ->
                    val errorMessage = ApiErrorHandler.getErrorMessage(error)
                    _state.update { it.copy(isLoadingGoals = false, error = errorMessage) }
                }
        }
    }

    /**
     * МОБ-6 — Выбрать цель использования.
     * На основе выбранной цели автоматически определяется роль пользователя.
     */
    fun onGoalSelected(goalId: Int) {
        _state.update { it.copy(selectedGoalId = goalId, fieldError = null) }
    }

    // ── Шаг 3: Безопасность и доступ ─────────────────────────────────────────

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, fieldError = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, fieldError = null) }

    fun onConfirmPasswordChange(value: String) =
        _state.update { it.copy(confirmPassword = value, fieldError = null) }

    fun onTogglePasswordVisibility() =
        _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun onToggleConfirmPasswordVisibility() =
        _state.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }

    fun onTermsAcceptedChange(accepted: Boolean) =
        _state.update { it.copy(termsAccepted = accepted, fieldError = null) }

    // ── Шаг 4: Подтверждение почты ───────────────────────────────────────────

    fun onVerificationCodeChange(value: String) =
        _state.update { it.copy(verificationCode = value, fieldError = null) }

    fun onResendCode() {
        val email = _state.value.email
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fieldError = null) }
            authRepository.resendCode(email)
                .onSuccess { _ ->
                    // Кулдаун RESEND_COOLDOWN_SECONDS (120 сек = 2 мин), НЕ result.expiresIn (600 сек = 10 мин)
                    // result.expiresIn используется для таймера жизни самого кода верификации
                    startCooldown(RESEND_COOLDOWN_SECONDS)
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    val errorMessage = ApiErrorHandler.getErrorMessage(error)
                    _state.update {
                        it.copy(isLoading = false, error = errorMessage)
                    }
                }
        }
    }

    // ── Навигация между шагами ────────────────────────────────────────────────

    fun onNext() {
        when (_state.value.step) {
            1 -> validateStep1()
            2 -> validateStep2()
            3 -> submitRegistration()
            4 -> verifyEmail()
        }
    }

    fun onBack() {
        val currentStep = _state.value.step
        if (currentStep > 1) {
            _state.update { it.copy(step = currentStep - 1, fieldError = null, error = null) }
        } else {
            viewModelScope.launch { _events.emit(RegisterEvent.NavigateBack) }
        }
    }

    // ── Проверка заполненности полей (без валидации - для disabling кнопки) ──

    fun isStep1Complete(): Boolean {
        val s = _state.value
        return s.firstName.isNotBlank() &&
               s.username.isNotBlank() &&
               s.birthDate.length == 8 &&
               s.gender != null
    }

    fun isStep2Complete(): Boolean {
        // МОБ-6 — Проверяем что пользователь выбрал цель
        val s = _state.value
        return s.selectedGoalId != null || s.purpose != null
    }

    fun isStep3Complete(): Boolean {
        val s = _state.value
        return s.email.isNotBlank() &&
               s.password.isNotBlank() &&
               s.confirmPassword.isNotBlank() &&
               s.termsAccepted
    }

    fun isStep4Complete(): Boolean {
        return _state.value.verificationCode.length == 6
    }

    // ── Приватная логика ──────────────────────────────────────────────────────

    private fun validateStep1() {
        val s = _state.value
        
        // Проверить статус даты рождения — если есть ошибка, не переходить дальше
        val dateError = when (s.birthDateCheckStatus) {
            is BirthDateCheckStatus.ERROR -> s.birthDateCheckStatus.message
            else -> null
        }
        
        val error = when {
            s.firstName.isBlank()  -> "Введите имя"
            s.username.isBlank()   -> "Введите имя пользователя"
            s.username.length < 3  -> "Имя пользователя: минимум 3 символа"
            s.birthDate.isEmpty()  -> "Введите дату рождения"
            dateError != null      -> dateError  // Использовать сообщение об ошибке валидации
            s.gender == null       -> "Выберите пол"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(fieldError = error) }
        } else {
            _state.update { it.copy(step = 2, fieldError = null) }
        }
    }

    private fun validateStep2() {
        val s = _state.value
        // МОБ-6 — Проверяем что выбрана цель (или старый механизм purpose)
        if (s.selectedGoalId == null && s.purpose == null) {
            _state.update { it.copy(fieldError = "Выберите цель использования приложения") }
        } else {
            _state.update { it.copy(step = 3, fieldError = null) }
        }
    }

    private fun submitRegistration() {
        val s = _state.value
        val birthDate = parseBirthDate(s.birthDate) ?: run {
            _state.update { it.copy(fieldError = "Некорректная дата рождения") }
            return
        }
        val emailError = when {
            s.email.isBlank() -> "Введите email"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email).matches() -> "Некорректный формат email"
            else -> null
        }
        if (emailError != null) {
            _state.update { it.copy(fieldError = emailError) }
            return
        }
        val passwordError = when {
            s.password.length < 8 -> "Пароль: минимум 8 символов"
            s.password != s.confirmPassword -> "Пароли не совпадают"
            else -> null
        }
        if (passwordError != null) {
            _state.update { it.copy(fieldError = passwordError) }
            return
        }
        if (!s.termsAccepted) {
            _state.update { it.copy(fieldError = "Необходимо принять условия использования") }
            return
        }

        // Если выбрана цель — получаем roleId из цели, иначе используем purpose (может быть null)
        val selectedGoal = s.availableGoals.find { it.id == s.selectedGoalId }
        val roleId = selectedGoal?.roleId  // Получаем роль из выбранной цели
        val purpose = s.purpose ?: UserPurpose.EXPLORING  // Дефолт для старого механизма

        val request = RegisterRequest(
            firstName = s.firstName,
            username = s.username,
            birthDate = birthDate,
            gender = s.gender!!,
            purpose = purpose,
            roleIds = if (roleId != null) listOf(roleId) else emptyList(),  // Используем roleId из цели
            email = s.email,
            password = s.password,
            confirmPassword = s.confirmPassword,
        )

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fieldError = null) }
            
            // МОБ-6 — Сохранить выбранную цель/роль перед регистрацией
            if (roleId != null) {
                roleConfigStorage.saveSelectedRoles(listOf(roleId))
            }
            
            registerUseCase(request)
                .onSuccess { _ ->
                    // Кулдаун RESEND_COOLDOWN_SECONDS (120 сек = 2 мин), НЕ result.expiresIn (600 сек = 10 мин)
                    // result.expiresIn используется для таймера жизни самого кода верификации
                    startCooldown(RESEND_COOLDOWN_SECONDS)
                    _state.update { it.copy(isLoading = false, step = 4) }
                }
                .onFailure { error ->
                    val errorMessage = ApiErrorHandler.getErrorMessage(error)
                    _state.update {
                        it.copy(isLoading = false, error = errorMessage)
                    }
                }
        }
    }

    private fun verifyEmail() {
        val s = _state.value
        if (s.verificationCode.isBlank()) {
            _state.update { it.copy(fieldError = "Введите код подтверждения") }
            return
        }
        if (s.verificationCode.length != 6) {
            _state.update { it.copy(fieldError = "Код должен содержать 6 символов") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fieldError = null) }
            authRepository.verifyEmail(s.email, s.verificationCode)
                .onSuccess {
                    cooldownJob?.cancel()
                    _state.update { it.copy(isLoading = false) }
                    _events.emit(RegisterEvent.NavigateToHome)
                }
                .onFailure { error ->
                    val errorMessage = ApiErrorHandler.getErrorMessage(error)
                    _state.update {
                        it.copy(isLoading = false, error = errorMessage)
                    }
                }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        _state.update { it.copy(resendCooldownSeconds = seconds) }
        cooldownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                delay(1000L)
                _state.update { it.copy(resendCooldownSeconds = remaining - 1) }
            }
        }
    }

    /** Парсинг даты из цифр в0405200 → LocalDate */
    private fun parseBirthDate(dateStr: String): LocalDate? {
        val digits = dateStr.filter { it.isDigit() }
        if (digits.length != 8) return null
        return try {
            val day   = digits.substring(0, 2).toInt()
            val month = digits.substring(2, 4).toInt()
            val year  = digits.substring(4, 8).toInt()
            LocalDate.of(year, month, day)
        } catch (e: Exception) {
            null
        }
    }

    /** Валидация даты рождения: возраст 6-120 лет, не будущая дата */
    private fun validateBirthDate(dateStr: String) {
        val birthDate = parseBirthDate(dateStr) ?: run {
            _state.update { state ->
                state.copy(
                    birthDateCheckStatus = BirthDateCheckStatus.ERROR("✗ Некорректная дата")
                )
            }
            return
        }

        val today = LocalDate.now()
        
        // Проверка: дата не может быть в будущем
        if (birthDate.isAfter(today)) {
            _state.update { state ->
                state.copy(
                    birthDateCheckStatus = BirthDateCheckStatus.ERROR("✗ Дата не может быть в будущем")
                )
            }
            return
        }

        // Вычислить возраст
        val age = today.year - birthDate.year - (
            if (today.monthValue < birthDate.monthValue || 
                (today.monthValue == birthDate.monthValue && today.dayOfMonth < birthDate.dayOfMonth)) 1 else 0
        )

        // Проверка: возраст >= 6 лет
        if (age < 6) {
            _state.update { state ->
                state.copy(
                    birthDateCheckStatus = BirthDateCheckStatus.ERROR("✗ Минимальный возраст: 6 лет")
                )
            }
            return
        }

        // Проверка: возраст <= 120 лет
        if (age > 120) {
            _state.update { state ->
                state.copy(
                    birthDateCheckStatus = BirthDateCheckStatus.ERROR("✗ Максимальный возраст: 120 лет")
                )
            }
            return
        }

        // Дата валидна
        _state.update { state ->
            state.copy(
                birthDateCheckStatus = BirthDateCheckStatus.SUCCESS("✓ Дата корректна")
            )
        }
    }

    /** Проверка уникальности nickname с debounce и кэшем */
    private fun checkNicknameUniqueAsync(nickname: String) {
        viewModelScope.launch {
            // Если в кэше — моментально обновить UI без API запроса
            if (nicknameCheckCache.containsKey(nickname)) {
                val isAvailable = nicknameCheckCache[nickname]!!
                _state.update { state ->
                    state.copy(
                        nicknameCheckStatus = if (isAvailable) {
                            NicknameCheckStatus.SUCCESS("✓ Никнейм доступен")
                        } else {
                            NicknameCheckStatus.ERROR("✗ Никнейм занят")
                        }
                    )
                }
                return@launch
            }

            // Показать состояние загрузки
            _state.update { it.copy(nicknameCheckStatus = NicknameCheckStatus.CHECKING) }

            authRepository.checkNickname(nickname)
                .onSuccess { response ->
                    nicknameCheckCache[nickname] = response.is_available
                    
                    _state.update { state ->
                        state.copy(
                            nicknameCheckStatus = if (response.is_available) {
                                NicknameCheckStatus.SUCCESS("✓ Никнейм доступен")
                            } else {
                                NicknameCheckStatus.ERROR("✗ Никнейм занят")
                            }
                        )
                    }
                }
                .onFailure { error ->
                    val errorMessage = ApiErrorHandler.getErrorMessage(error)
                    _state.update { state ->
                        state.copy(
                            nicknameCheckStatus = NicknameCheckStatus.ERROR(errorMessage)
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cooldownJob?.cancel()
        nicknameCheckJob?.cancel()
    }
}
