package com.example.smarttracker.data.repository

import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.dto.EmailVerificationDto
import com.example.smarttracker.data.remote.dto.ForgotPasswordRequestDto
import com.example.smarttracker.data.remote.dto.ResendResetCodeRequestDto
import com.example.smarttracker.data.remote.dto.ResetPasswordRequestDto
import com.example.smarttracker.data.remote.dto.toDomain
import com.example.smarttracker.domain.model.ForgotPasswordRequest
import com.example.smarttracker.domain.model.ForgotPasswordResult
import com.example.smarttracker.domain.model.ResendResetCodeResult
import com.example.smarttracker.domain.model.ResetPasswordRequest
import com.example.smarttracker.domain.model.ResetPasswordResult
import com.example.smarttracker.domain.repository.PasswordRecoveryRepository
import javax.inject.Inject

/**
 * Реальная реализация PasswordRecoveryRepository через production API.
 * Активирована в AuthModule через @Binds.
 */
class PasswordRecoveryRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
) : PasswordRecoveryRepository {

    override suspend fun initiateForgotPassword(request: ForgotPasswordRequest): Result<ForgotPasswordResult> =
        runCatching {
            api.forgotPassword(
                ForgotPasswordRequestDto(email = request.email)
            ).toDomain()
        }

    override suspend fun verifyResetCode(email: String, code: String): Result<Unit> =
        runCatching {
            api.verifyResetCode(
                EmailVerificationDto(
                    email = email,
                    code = code,
                )
            )
            Unit
        }

    override suspend fun resendResetCode(email: String): Result<ResendResetCodeResult> =
        runCatching {
            api.resendResetCode(
                ResendResetCodeRequestDto(email = email)
            ).toDomain()
        }

    override suspend fun resetPassword(request: ResetPasswordRequest): Result<ResetPasswordResult> =
        runCatching {
            // Токены из ответа не сохраняем — ViewModel перенаправляет на логин через clearAll().
            api.resetPassword(
                ResetPasswordRequestDto(
                    email = request.email,
                    code = request.code,
                    newPassword = request.newPassword,
                    confirmPassword = request.confirmPassword,
                )
            ).toDomain()
        }
}
