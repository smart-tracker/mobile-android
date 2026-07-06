package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.RegisterResult
import com.example.smarttracker.domain.repository.AllowedEmailDomainsRepository
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.validation.EmailValidator
import javax.inject.Inject

/**
 * МОБ-1.4 — UseCase регистрации нового пользователя.
 *
 * Ответственность:
 *   1. Валидация полей формы на клиенте
 *   2. Проверка домена почты по списку разрешённых (149-ФЗ)
 *   3. Вызов репозитория если валидация прошла
 *
 * Не знает ничего про UI, Android, Retrofit или БД.
 */
class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val allowedEmailDomains: AllowedEmailDomainsRepository,
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

        // — Проверка домена почты (149-ФЗ) ————————————————————————
        // Обязательная точка контроля: через invoke проходит любая
        // регистрация, независимо от того, проверил ли домен ViewModel.
        // Вход и восстановление пароля НЕ ограничиваются — закон
        // касается только новой регистрации.

        val domains = allowedEmailDomains.getAllowedDomains()
        if (!EmailValidator.isAllowedDomain(request.email, domains)) {
            return Result.failure(
                IllegalArgumentException(EmailValidator.RUSSIAN_EMAIL_REQUIRED_MESSAGE)
            )
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
        if (!EmailValidator.isValid(request.email)) {
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