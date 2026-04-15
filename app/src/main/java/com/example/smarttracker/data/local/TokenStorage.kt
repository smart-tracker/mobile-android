package com.example.smarttracker.data.local

import kotlinx.coroutines.flow.StateFlow

/**
 * Контракт хранилища JWT-токенов и ролей пользователя.
 *
 * Находится в слое data, а не domain — потому что это деталь реализации
 * (способ хранения токенов), а не бизнес-правило. Domain знает лишь о
 * том, что токены существуют (AuthResult), но не о том, где они хранятся.
 *
 * Реализация — TokenStorageImpl через EncryptedSharedPreferences.
 * Привязка интерфейса к реализации — в AuthModule (МОБ-5.1).
 *
 * Ролевая модель (МОБ-6):
 * - После верификации email пользователь имеет список ролей (role_ids)
 * - Роли определяют доступные экраны в BottomNavigation
 * - Роли сохраняются здесь же для быстрого доступа при запуске приложения
 */
interface TokenStorage {

    /**
     * Сохраняет оба токена и список ролей атомарно в одной транзакции.
     * Вызывается после успешной верификации email (AuthRepositoryImpl.verifyEmail).
     *
     * @param accessToken JWT access token
     * @param refreshToken JWT refresh token (для обновления access token)
     * @param roleIds Список ID ролей пользователя (например [1, 2] для Athlete+Trainer)
     */
    fun saveTokens(accessToken: String, refreshToken: String, roleIds: List<Int>)

    /**
     * Возвращает access token или null, если токен ещё не сохранён.
     */
    fun getAccessToken(): String?

    /**
     * Возвращает refresh token или null, если токен ещё не сохранён.
     */
    fun getRefreshToken(): String?

    /**
     * Возвращает список ролей пользователя или пустой список, если ролей нет.
     * Используется при инициализации приложения для построения BottomNavigation.
     */
    fun getUserRoles(): List<Int>

    /**
     * Удаляет оба токена и все роли (выход из аккаунта / истечение сессии).
     */
    fun clearAll()

    /**
     * Возвращает true, если оба токена присутствуют (пользователь авторизован).
     * Роли не влияют на эту проверку (могут быть пусты).
     */
    fun hasTokens(): Boolean

    /**
     * Flow-флаг принудительного выхода из сессии.
     *
     * Эмитирует `true` когда оба токена истекли и refresh тоже вернул 401.
     * UI должен наблюдать этот flow и при `true` переходить на экран Login.
     *
     * Используется [TokenRefreshAuthenticator] вместо прямого [clearAll],
     * чтобы сигнализировать об истечении сессии без прямой зависимости на ViewModel.
     */
    val sessionExpiredFlow: StateFlow<Boolean>

    /**
     * Очищает токены и поднимает [sessionExpiredFlow] в `true`.
     *
     * Вызывается из [TokenRefreshAuthenticator] когда refresh-запрос вернул 401.
     * В отличие от [clearAll], уведомляет UI о необходимости перейти на Login.
     */
    fun signalSessionExpired()
}
