package com.example.smarttracker.data.local

/**
 * Контракт хранилища JWT-токенов.
 *
 * Находится в слое data, а не domain — потому что это деталь реализации
 * (способ хранения токенов), а не бизнес-правило. Domain знает лишь о
 * том, что токены существуют (AuthResult), но не о том, где они хранятся.
 *
 * Реализация — TokenStorageImpl через EncryptedSharedPreferences.
 * Привязка интерфейса к реализации — в AuthModule (МОБ-5.1).
 */
interface TokenStorage {

    /** Сохраняет оба токена атомарно в одной транзакции SharedPreferences. */
    fun saveTokens(accessToken: String, refreshToken: String)

    /** Возвращает access token или null, если токен ещё не сохранён. */
    fun getAccessToken(): String?

    /** Возвращает refresh token или null, если токен ещё не сохранён. */
    fun getRefreshToken(): String?

    /** Удаляет оба токена (выход из аккаунта / истечение сессии). */
    fun clearTokens()

    /** Возвращает true, если оба токена присутствуют (пользователь авторизован). */
    fun hasTokens(): Boolean
}
