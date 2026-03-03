package com.example.smarttracker.domain.repository

import com.example.smarttracker.domain.model.AuthResult
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.RegisterResult

/**
 * МОБ-1.3 — Контракт репозитория авторизации.
 *
 * Поток регистрации состоит из двух шагов:
 *   1. register()     → сервер создаёт пользователя и отправляет код на email
 *   2. verifyEmail()  → сервер подтверждает email и возвращает токены
 *
 * Реализация — AuthRepositoryImpl в слое data (МОБ-2.3).
 * Все методы suspend, возвращают Result<T> для безопасной обработки
 * ошибок во ViewModel без try/catch.
 */
interface AuthRepository {

    /**
     * POST /auth/register
     * Шаг 1 потока регистрации.
     * Возвращает email и время жизни кода — для отображения таймера
     * на экране верификации.
     */
    suspend fun register(request: RegisterRequest): Result<RegisterResult>

    /**
     * POST /auth/verify-email
     * Шаг 2 потока регистрации.
     * Возвращает токены — пользователь считается авторизованным.
     */
    suspend fun verifyEmail(email: String, code: String): Result<AuthResult>

    /**
     * POST /auth/resend-code
     * Повторный запрос кода. Доступен раз в 2 минуты (RESEND_COOLDOWN на сервере).
     * Возвращает новое время жизни кода в секундах.
     */
    suspend fun resendCode(email: String): Result<Int>

    /**
     * POST /auth/login
     * Вход для уже подтверждённых пользователей.
     */
    suspend fun login(email: String, password: String): Result<AuthResult>

    /**
     * POST /auth/refresh
     * Обновление access token. Вызывается автоматически из AuthInterceptor (МОБ-2.2).
     */
    suspend fun refreshToken(refreshToken: String): Result<AuthResult>
}