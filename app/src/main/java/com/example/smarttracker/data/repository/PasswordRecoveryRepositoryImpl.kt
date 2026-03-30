package com.example.smarttracker.data.repository

import android.util.Log
import com.example.smarttracker.data.local.TokenStorage
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
 *
 * ВНИМАНИЕ:
 * В текущий момент может быть не активирована через DI, если backend ещё не
 * реализовал recovery endpoints. Переключение производится в AuthModule.
 */
class PasswordRecoveryRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val tokenStorage: TokenStorage,
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
            val response = api.resetPassword(
                ResetPasswordRequestDto(
                    email = request.email,
                    code = request.code,
                    newPassword = request.newPassword,
                    confirmPassword = request.confirmPassword,
                )
            )

            // Backend возвращает токены после успешного сброса пароля —
            // сохраняем их, чтобы пользователь был автоматически авторизован.
            tokenStorage.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                roleIds = tokenStorage.getUserRoles(), // Роли не меняются при сбросе пароля
            )
            Log.d("PasswordRecovery", "Токены сохранены после сброса пароля")

            response.toDomain()
        }
}
