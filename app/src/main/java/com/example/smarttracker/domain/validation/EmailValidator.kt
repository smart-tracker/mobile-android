package com.example.smarttracker.domain.validation

/**
 * Централизованная валидация email.
 *
 * Используется в LoginUseCase и RegisterUseCase.
 * Вынесено из use case'ов для соблюдения DRY — regex был продублирован.
 */
object EmailValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValid(email: String): Boolean = EMAIL_REGEX.matches(email)
}
