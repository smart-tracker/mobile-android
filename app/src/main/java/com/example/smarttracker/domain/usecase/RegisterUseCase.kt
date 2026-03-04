package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.RegisterResult
import com.example.smarttracker.domain.repository.AuthRepository

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/**
 * МОБ-1.4 — UseCase регистрации нового пользователя.
 *
 * Ответственность:
 *   1. Валидация полей формы на клиенте
 *   2. Вызов репозитория если валидация прошла
 *
 * Не знает ничего про UI, Android, Retrofit или БД.
 */
class RegisterUseCase(
    private val repository: AuthRepository
) {

    /**
     * operator fun invoke позволяет вызывать UseCase как функцию:
     *   registerUseCase(request)
     * вместо:
     *   registerUseCase.execute(request)
     */
    suspend operator fun invoke(request: RegisterRequest): Result<RegisterResult> {

        // — Валидация —————————————————————————————————————————————

        val validationError = validate(request)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }

        // — Запрос к серверу ——————————————————————————————————————

        return repository.register(request)
    }

    // ————————————————————————————————————————————————————————————
    // Приватная функция валидации.
    // Возвращает текст ошибки или null если всё корректно.
    // ————————————————————————————————————————————————————————————

    private fun validate(request: RegisterRequest): String? {

        // firstName — не пустой
        if (request.firstName.isBlank()) {
            return "Введите имя"
        }

        // username — не пустой и минимум 3 символа
        if (request.username.isBlank()) {
            return "Введите имя пользователя"
        }
        if (request.username.length < 3) {
            return "Имя пользователя должно содержать минимум 3 символа"
        }

        // email — корректный формат
        if (request.email.isBlank()) {
            return "Введите email"
        }
        if (!EMAIL_REGEX.matches(request.email)) {
            return "Введите корректный email"
        }

        // password — минимум 8 символов
        if (request.password.isBlank()) {
            return "Введите пароль"
        }
        if (request.password.length < 8) {
            return "Пароль должен содержать минимум 8 символов"
        }
        if (!request.password.any { it.isDigit() }) {
            return "Пароль должен содержать хотя бы одну цифру"
        }

        // confirmPassword — совпадает с password
        if (request.confirmPassword != request.password) {
            return "Пароли не совпадают"
        }

        // birthDate — выбрана (не дефолтная)
        // Проверяем что пользователь действительно выбрал дату,
        // а не оставил поле пустым
        if (request.birthDate.year < 1900) {
            return "Введите дату рождения"
        }

        // purpose — выбран пользователем
        // Enum всегда имеет значение, но поле должно быть
        // явно выбрано на экране, а не просто дефолтным
        // TODO: добавить флаг isPurposeSelected в RegisterRequest
        //       когда договоримся с Артёмом о UX экрана выбора цели

        // Все проверки пройдены
        return null
    }
}