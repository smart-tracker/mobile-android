package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.dto.EmailVerificationDto
import com.example.smarttracker.data.remote.dto.LoginRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckRequestDto
import com.example.smarttracker.data.remote.dto.ResendEmailDto
import com.example.smarttracker.data.remote.dto.toDomain
import com.example.smarttracker.data.remote.dto.toDto
import com.example.smarttracker.domain.model.AuthResult
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.RegisterResult
import com.example.smarttracker.domain.model.ResendResult
import com.example.smarttracker.domain.model.NicknameCheckResponse
import com.example.smarttracker.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * МОБ-2.3 — Реализация контракта AuthRepository.
 *
 * Задача этого класса — перевести вызовы use-case'ов в HTTP-запросы через
 * AuthApiService и при успехе сохранить/обновить токены в TokenStorage.
 *
 * Принцип обработки ошибок:
 * Каждый метод оборачивается в runCatching { }. Это даёт Result<T>:
 * - Result.success(value) — запрос прошёл, value — domain-объект
 * - Result.failure(throwable) — любое исключение (IOException, HttpException и т.д.)
 * ViewModel вызывает result.fold(...) и не пишет try/catch.
 *
 * Почему runCatching работает с suspend-вызовами:
 * runCatching — inline-функция, поэтому блок вставляется прямо в тело
 * suspend-метода. Компилятор видит suspend-контекст и позволяет вызывать
 * другие suspend-функции внутри блока.
 */
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    /**
     * Шаг 1 регистрации. Токены НЕ сохраняются — пользователь ещё не верифицирован,
     * is_active=false на бэкенде.
     */
    override suspend fun register(request: RegisterRequest): Result<RegisterResult> =
        runCatching { api.register(request.toDto()).toDomain() }

    /**
     * Шаг 2 регистрации. Бэкенд устанавливает is_active=true и возвращает токены.
     * Сохраняем токены сразу — пользователь считается авторизованным.
     */
    override suspend fun verifyEmail(email: String, code: String): Result<AuthResult> =
        runCatching {
            val result = api.verifyEmail(EmailVerificationDto(email, code)).toDomain()
            tokenStorage.saveTokens(result.accessToken, result.refreshToken)
            result
        }

    /**
     * Повторный запрос кода подтверждения. Токены не затрагиваются.
     */
    override suspend fun resendCode(email: String): Result<ResendResult> =
        runCatching { api.resendCode(ResendEmailDto(email)).toDomain() }

    /**
     * Вход для верифицированных пользователей. Перезаписывает токены
     * (старая сессия заменяется новой).
     */
    override suspend fun login(email: String, password: String): Result<AuthResult> =
        runCatching {
            val result = api.login(LoginRequestDto(email, password)).toDomain()
            tokenStorage.saveTokens(result.accessToken, result.refreshToken)
            result
        }

    /**
     * Обновление access token по refresh token.
     * Новая пара токенов заменяет старую в хранилище.
     *
     * Refresh token передаётся как @Query (не @Body) — подтверждено
     * сигнатурой FastAPI-роута (нюанс #7 в CONTEXT.md).
     */
    override suspend fun refreshToken(refreshToken: String): Result<AuthResult> =
        runCatching {
            val result = api.refreshToken(refreshToken).toDomain()
            tokenStorage.saveTokens(result.accessToken, result.refreshToken)
            result
        }

    /**
     * Проверка доступности nickname.
     * Не требует авторизации — вызывается на этапе регистрации.
     * Результат не сохраняется в TokenStorage.
     */
    override suspend fun checkNickname(nickname: String): Result<NicknameCheckResponse> =
        runCatching {
            api.checkNickname(NicknameCheckRequestDto(nickname)).toDomain()
        }
}
