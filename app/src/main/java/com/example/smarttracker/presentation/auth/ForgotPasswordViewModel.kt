package com.example.smarttracker.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.domain.model.ForgotPasswordRequest
import com.example.smarttracker.domain.model.ResetPasswordRequest
import com.example.smarttracker.domain.repository.PasswordRecoveryRepository
import com.example.smarttracker.utils.ApiErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для трёхшагового password recovery flow.
 * Обрабатывает события пользователя и управляет состоянием UI.
 *
 * Валидация:
 * - Email: формат через android.util.Patterns.EMAIL_ADDRESS (откладывается до Submit)
 * - Password: минимум 8 символов
 * - Verification code: ровно 6 символов
 * - Matching: newPassword == confirmPassword
 *
 * Таймеры:
 * - VERIFICATION_CODE_EXPIRE_MINUTES: 10 минут (жизнь кода)
 * - RESEND_COOLDOWN_SECONDS: 120 секунд (минимум между отправками)
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val passwordRecoveryRepository: PasswordRecoveryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState
    
    private val _events = MutableSharedFlow<ForgotPasswordEvent>()
    val events = _events.asSharedFlow()
    
    fun onEvent(event: ForgotPasswordEvent) {
        when (event) {
            is ForgotPasswordEvent.OnEmailChanged -> {
                _uiState.value = _uiState.value.copy(
                    email = event.email,
                    emailError = null
                )
            }
            
            ForgotPasswordEvent.OnContinueFromStep1 -> submitEmail()
            
            is ForgotPasswordEvent.OnNewPasswordChanged -> {
                _uiState.value = _uiState.value.copy(
                    newPassword = event.password,
                    newPasswordError = null
                )
            }
            
            is ForgotPasswordEvent.OnConfirmPasswordChanged -> {
                _uiState.value = _uiState.value.copy(
                    confirmPassword = event.password,
                    confirmPasswordError = null
                )
            }
            
            ForgotPasswordEvent.OnToggleNewPasswordVisibility -> {
                _uiState.value = _uiState.value.copy(
                    newPasswordVisibility = !_uiState.value.newPasswordVisibility
                )
            }
            
            ForgotPasswordEvent.OnToggleConfirmPasswordVisibility -> {
                _uiState.value = _uiState.value.copy(
                    confirmPasswordVisibility = !_uiState.value.confirmPasswordVisibility
                )
            }
            
            ForgotPasswordEvent.OnContinueFromStep2 -> submitNewPassword()
            
            is ForgotPasswordEvent.OnVerificationCodeChanged -> {
                // Ограничиваем ввод 6 символами
                val code = event.code.take(6).filter { it.isDigit() }
                _uiState.value = _uiState.value.copy(
                    verificationCode = code,
                    verificationCodeError = null
                )
            }
            
            ForgotPasswordEvent.OnResendCode -> resendCode()
            ForgotPasswordEvent.OnResetPassword -> submitResetPassword()
            ForgotPasswordEvent.OnBackPressed -> navigateBack()
        }
    }
    
    private fun submitEmail() {
        val email = _uiState.value.email.trim()
        
        // Client-side validation
        if (email.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                emailError = "Email не может быть пустым"
            )
            return
        }
        
        // Simple email format check (proper validation happens on API)
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(
                emailError = "Некорректный формат email"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val result = passwordRecoveryRepository.initiateForgotPassword(
                ForgotPasswordRequest(email)
            )
            
            if (result.isSuccess) {
                val forgotResult = result.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    currentStep = 2,
                    verificationCodeExpiresIn = forgotResult.expiresIn,
                    isLoading = false,
                    generalError = null
                )
                startCooldown()
            } else {
                val errorMessage = ApiErrorHandler.translateError(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
                _uiState.value = _uiState.value.copy(
                    generalError = errorMessage,
                    isLoading = false
                )
            }
        }
    }
    
    private fun submitNewPassword() {
        val newPassword = _uiState.value.newPassword
        val confirmPassword = _uiState.value.confirmPassword
        
        val newPasswordError = validatePassword(newPassword)
        if (newPasswordError != null) {
            _uiState.value = _uiState.value.copy(newPasswordError = newPasswordError)
            return
        }
        
        if (confirmPassword.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                confirmPasswordError = "Подтвердите пароль"
            )
            return
        }
        
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(
                confirmPasswordError = "Пароли не совпадают"
            )
            return
        }
        
        // Move to step 3
        _uiState.value = _uiState.value.copy(
            currentStep = 3,
            generalError = null
        )
    }
    
    private fun resendCode() {
        if (_uiState.value.resendCodeCooldown > 0) {
            return // Button should be disabled
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val result = passwordRecoveryRepository.resendResetCode(_uiState.value.email)
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                startCooldown()
            } else {
                val errorMessage = ApiErrorHandler.translateError(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
                _uiState.value = _uiState.value.copy(
                    generalError = errorMessage,
                    isLoading = false
                )
            }
        }
    }
    
    private fun submitResetPassword() {
        val verificationCode = _uiState.value.verificationCode
        
        if (verificationCode.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                verificationCodeError = "Введите код подтверждения"
            )
            return
        }
        
        if (verificationCode.length != 6) {
            _uiState.value = _uiState.value.copy(
                verificationCodeError = "Код должен состоять из 6 цифр"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val result = passwordRecoveryRepository.resetPassword(
                ResetPasswordRequest(
                    email = _uiState.value.email,
                    code = verificationCode,
                    newPassword = _uiState.value.newPassword,
                    confirmPassword = _uiState.value.confirmPassword
                )
            )
            
            if (result.isSuccess) {
                // Успешный сброс - переход на экран логина
                _uiState.value = _uiState.value.copy(isLoading = false)
                viewModelScope.launch {
                    _events.emit(ForgotPasswordEvent.OnContinueFromStep1) // Navigate to login
                }
            } else {
                val errorMessage = ApiErrorHandler.translateError(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
                _uiState.value = _uiState.value.copy(
                    verificationCodeError = errorMessage,
                    isLoading = false
                )
            }
        }
    }
    
    private fun startCooldown() {
        if (_uiState.value.resendCodeCooldown > 0) return
        
        viewModelScope.launch {
            for (seconds in 120 downTo 0) {
                _uiState.value = _uiState.value.copy(
                    resendCodeCooldown = seconds
                )
                if (seconds > 0) {
                    delay(1000)
                }
            }
        }
    }
    
    private fun validatePassword(password: String): String? {
        return when {
            password.isEmpty() -> "Пароль не может быть пустым"
            password.length < 8 -> "Пароль должен содержать минимум 8 символов"
            else -> null
        }
    }
    
    private fun navigateBack() {
        val currentStep = _uiState.value.currentStep
        if (currentStep > 1) {
            _uiState.value = _uiState.value.copy(
                currentStep = currentStep - 1,
                generalError = null
            )
        }
    }
}
