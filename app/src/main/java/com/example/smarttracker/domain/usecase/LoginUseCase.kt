package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.AuthResult
import com.example.smarttracker.domain.repository.AuthRepository
import javax.inject.Inject

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/**
 * МОБ-3.2 — UseCase входа в приложение.
 *
 * Ответственность:
 *   1. Валидация email и пароля на клиенте
 *   2. Вызов репозитория если валидация прошла
 *
 * Не знает ничего про UI, Android, Retrofit или БД.
 */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {

    /**
     * Вызывается как функция:
     *   loginUseCase(email, password)
     */
    suspend operator fun invoke(email: String, password: String): Result<AuthResult> {

        // — Валидация —————————————————————————————————————————————

        val validationError = validate(email, password)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }

        // — Запрос к серверу ——————————————————————————————————————

        return repository.login(email, password)
    }

    // ————————————————————————————————————————————————————————————
    // Приватная функция валидации.
    // ————————————————————————————————————————————————————————————

    private fun validate(email: String, password: String): String? {

        // email — не пустой и валидный формат
        if (email.isBlank()) {
            return "Введите email"
        }
        if (!EMAIL_REGEX.matches(email)) {
            return "Введите корректный email"
        }

        // password — минимум 8 символов
        if (password.isBlank()) {
            return "Введите пароль"
        }
        if (password.length < 8) {
            return "Пароль должен содержать минимум 8 символов"
        }

        return null
    }
}
