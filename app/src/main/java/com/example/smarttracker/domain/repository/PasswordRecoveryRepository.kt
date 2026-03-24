package com.example.smarttracker.domain.repository

import com.example.smarttracker.domain.model.ForgotPasswordRequest
import com.example.smarttracker.domain.model.ForgotPasswordResult
import com.example.smarttracker.domain.model.ResetPasswordRequest
import com.example.smarttracker.domain.model.ResetPasswordResult
import com.example.smarttracker.domain.model.ResendResetCodeResult

/**
 * Repository interface для операций восстановления пароля.
 * Определяет контракт между domain и data слоями для password recovery flow.
 *
 * Используемые типы ошибок при Result<T>.isFailure:
 * - "EMAIL_NOT_FOUND" — email не зарегистрирован в системе
 * - "TOO_MANY_ATTEMPTS" — слишком много попыток (достигнут лимит 5 неверных кодов)
 * - "RESEND_COOLDOWN" — попытка отправить код слишком рано (должна ждать N секунд)
 * - "GENERIC" — неизвестная ошибка
 */
interface PasswordRecoveryRepository {
    /**
     * Инициирует процесс восстановления пароля.
     * Отправляет код верификации на указанный email.
     *
     * @param request Содержит email пользователя
     * @return Result<ForgotPasswordResult> с информацией о сроке действия кода
     */
    suspend fun initiateForgotPassword(request: ForgotPasswordRequest): Result<ForgotPasswordResult>

    /**
     * Проверяет код верификации перед вводом нового пароля.
     *
     * @param email Email пользователя
     * @param code 6-значный код из письма
     */
    suspend fun verifyResetCode(email: String, code: String): Result<Unit>

    /**
     * Повторно отправляет код верификации для восстановления пароля.
     * Подчиняется cooldown-таймеру (обычно 120 секунд между отправками).
     *
     * @param email Email пользователя
     * @return Result<ResendResetCodeResult> с информацией о сроке действия и cooldown
     */
    suspend fun resendResetCode(email: String): Result<ResendResetCodeResult>

    /**
     * Завершает процесс восстановления пароля.
     * Проверяет код верификации и устанавливает новый пароль.
     *
     * @param request Содержит email, code, newPassword, confirmPassword
     * @return Result<ResetPasswordResult> с результатом операции
     */
    suspend fun resetPassword(request: ResetPasswordRequest): Result<ResetPasswordResult>
}
