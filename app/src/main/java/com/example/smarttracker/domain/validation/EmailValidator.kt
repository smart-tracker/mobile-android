package com.example.smarttracker.domain.validation

/**
 * Централизованная валидация email.
 *
 * Используется в LoginUseCase, RegisterUseCase и RegisterViewModel.
 * Вынесено из use case'ов для соблюдения DRY — regex был продублирован.
 */
object EmailValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /**
     * Текст ошибки для неразрешённого домена (149-ФЗ).
     * Единая константа — используется и в RegisterUseCase, и в RegisterViewModel,
     * чтобы пользователь видел одно и то же сообщение независимо от того,
     * какой слой отклонил ввод.
     */
    const val RUSSIAN_EMAIL_REQUIRED_MESSAGE =
        "Регистрация доступна только с российской почтой (Яндекс, Mail.ru, Rambler и др.)"

    fun isValid(email: String): Boolean = EMAIL_REGEX.matches(email)

    /**
     * Проверка, что домен email входит в список разрешённых (149-ФЗ:
     * регистрация только через российские почтовые сервисы).
     *
     * Список передаётся параметром, а не хранится здесь: источник —
     * AllowedEmailDomainsRepository (сейчас захардкожен в data-слое,
     * позже будет приходить с бэкенда). Сравнение — точное совпадение
     * домена в нижнем регистре; поддомены ("user@mail.yandex.ru")
     * намеренно НЕ проходят — иначе "attacker-yandex.ru" пришлось бы
     * отсекать отдельной логикой.
     *
     * Применять только к регистрации: вход и восстановление пароля
     * существующих аккаунтов с иностранной почтой закон не ограничивает.
     */
    fun isAllowedDomain(email: String, allowedDomains: Set<String>): Boolean {
        val domain = email.substringAfterLast('@', missingDelimiterValue = "").lowercase()
        return domain in allowedDomains
    }
}
