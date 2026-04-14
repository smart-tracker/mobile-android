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
 * Активирована в AuthModule через @Binds.
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
                    email           = request.email,
                    code            = request.code,
                    newPassword     = request.newPassword,
                    confirmPassword = request.confirmPassword,
                )
            )

            // Сохраняем токены сразу (без ролей) — чтобы интерцептор добавил Bearer
            // в следующий запрос getUserRoles(). Тот же паттерн, что в login().
            tokenStorage.saveTokens(response.accessToken, response.refreshToken, emptyList())

            // Загружаем роли с API (токен уже в хранилище → Bearer добавится автоматически)
            val roleIds = runCatching { api.getUserRoles().map { it.roleId } }
                .onFailure { Log.w("PasswordRecovery", "Не удалось загрузить роли: ${it.message}") }
                .getOrElse { emptyList() }

            // Обновляем токены с ролями
            tokenStorage.saveTokens(response.accessToken, response.refreshToken, roleIds)

            ResetPasswordResult(
                message         = "Пароль успешно изменён",
                success         = true,
                redirectToLogin = false, // токены сохранены — авто-вход, логин не нужен
            )
        }
}
