package com.example.smarttracker.data.repository

import com.example.smarttracker.domain.model.ForgotPasswordRequest
import com.example.smarttracker.domain.model.ForgotPasswordResult
import com.example.smarttracker.domain.model.ResetPasswordRequest
import com.example.smarttracker.domain.model.ResetPasswordResult
import com.example.smarttracker.domain.model.ResendResetCodeResult
import com.example.smarttracker.domain.repository.PasswordRecoveryRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Mock-реализация PasswordRecoveryRepository для тестирования без реального API.
 * Имитирует поведение сервера с задержками и логикой cooldown-таймера.
 *
 * Для включения mockdata в production нужно переключить binding в Hilt:
 * @Binds
 * abstract fun bindPasswordRecoveryRepository(impl: MockPasswordRecoveryRepository): PasswordRecoveryRepository
 */
class MockPasswordRecoveryRepository @Inject constructor() : PasswordRecoveryRepository {
    
    // Simulated state for testing
    private val lastResendTime = mutableMapOf<String, Long>()
    private val mockValidCodes = mapOf(
        "test@example.com" to "123456"
    )
    
    override suspend fun initiateForgotPassword(request: ForgotPasswordRequest): Result<ForgotPasswordResult> {
        delay(500) // Simulate network latency
        
        // Validate email format
        if (!request.email.contains("@")) {
            return Result.failure(
                Exception("Invalid email format")
            )
        }
        
        // Simulate email not found error for specific pattern
        if (request.email == "notfound@example.com") {
            return Result.failure(
                Exception("EMAIL_NOT_FOUND")
            )
        }
        
        return Result.success(
            ForgotPasswordResult(
                message = "Код восстановления отправлен на вашу почту",
                email = request.email,
                expiresIn = 600, // 10 minutes
                emailSent = request.email
            )
        )
    }

    override suspend fun verifyResetCode(email: String, code: String): Result<Unit> {
        delay(300)

        if (code.length != 6 || !code.all { it.isDigit() }) {
            return Result.failure(Exception("INVALID_CODE"))
        }

        val validCode = mockValidCodes[email]
        if (code != validCode && code != "123456") {
            return Result.failure(Exception("INVALID_CODE"))
        }

        return Result.success(Unit)
    }
    
    override suspend fun resendResetCode(email: String): Result<ResendResetCodeResult> {
        delay(300)
        
        val now = System.currentTimeMillis()
        val lastTime = lastResendTime[email] ?: 0L
        val timeSinceLastResend = now - lastTime
        val cooldownMs = 120_000L // 2 minutes
        
        if (timeSinceLastResend < cooldownMs) {
            val remainingSeconds = ((cooldownMs - timeSinceLastResend) / 1000).toInt()
            return Result.failure(
                Exception("RESEND_COOLDOWN: Please wait $remainingSeconds seconds")
            )
        }
        
        lastResendTime[email] = now
        
        return Result.success(
            ResendResetCodeResult(
                message = "Код повторно отправлен на вашу почту",
                expiresAt = "2025-06-10T10:15:30Z",
                remainingSeconds = 600
            )
        )
    }
    
    override suspend fun resetPassword(request: ResetPasswordRequest): Result<ResetPasswordResult> {
        delay(800)
        
        // Validate inputs
        if (request.newPassword.length < 8) {
            return Result.failure(
                Exception("PASSWORD_TOO_SHORT")
            )
        }
        
        if (request.newPassword != request.confirmPassword) {
            return Result.failure(
                Exception("PASSWORD_MISMATCH")
            )
        }
        
        // Validate code (for testing)
        val validCode = mockValidCodes[request.email]
        if (request.code != validCode && request.code != "123456") {
            return Result.failure(
                Exception("INVALID_CODE")
            )
        }
        
        return Result.success(
            ResetPasswordResult(
                message = "Пароль успешно изменён",
                success = true,
                redirectToLogin = true
            )
        )
    }
}
