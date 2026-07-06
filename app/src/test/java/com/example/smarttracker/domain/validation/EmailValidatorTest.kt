package com.example.smarttracker.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты EmailValidator.
 *
 * Покрывает edge-cases regex `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`:
 * - user+tag@example.com — должен быть валиден (символ '+' разрешён)
 * - user@sub.domain.com — должен быть валиден (поддомены разрешены)
 * - @domain.com — должен быть невалиден (отсутствует локальная часть)
 *
 * Ошибки здесь блокируют легитимных пользователей при регистрации/входе.
 */
class EmailValidatorTest {

    // ── Тест 11: edge cases ──────────────────────────────────────────────────

    @Test
    fun `email с плюсом валиден`() {
        assertTrue(EmailValidator.isValid("user+tag@example.com"))
    }

    @Test
    fun `email с поддоменом валиден`() {
        assertTrue(EmailValidator.isValid("user@sub.domain.com"))
    }

    @Test
    fun `email без локальной части невалиден`() {
        assertFalse(EmailValidator.isValid("@domain.com"))
    }

    // ── isAllowedDomain: проверка домена по списку (149-ФЗ) ──────────────────

    private val russianDomains = setOf("yandex.ru", "ya.ru", "mail.ru", "rambler.ru")

    @Test
    fun `российский домен из списка проходит`() {
        assertTrue(EmailValidator.isAllowedDomain("user@yandex.ru", russianDomains))
        assertTrue(EmailValidator.isAllowedDomain("user@mail.ru", russianDomains))
    }

    @Test
    fun `иностранный домен отклоняется`() {
        assertFalse(EmailValidator.isAllowedDomain("user@gmail.com", russianDomains))
        assertFalse(EmailValidator.isAllowedDomain("user@outlook.com", russianDomains))
    }

    @Test
    fun `домен сравнивается без учёта регистра`() {
        assertTrue(EmailValidator.isAllowedDomain("User@YANDEX.RU", russianDomains))
    }

    @Test
    fun `поддомен разрешённого домена отклоняется`() {
        // Точное совпадение: "mail.yandex.ru" не равен "yandex.ru".
        // Иначе пришлось бы отдельно отсекать "attacker-yandex.ru".
        assertFalse(EmailValidator.isAllowedDomain("user@mail.yandex.ru", russianDomains))
    }

    @Test
    fun `домен-суффикс не проходит как подстрока`() {
        assertFalse(EmailValidator.isAllowedDomain("user@notyandex.ru", russianDomains))
    }

    @Test
    fun `строка без @ отклоняется`() {
        assertFalse(EmailValidator.isAllowedDomain("yandex.ru", russianDomains))
    }

    @Test
    fun `пустой список доменов отклоняет любой email`() {
        assertFalse(EmailValidator.isAllowedDomain("user@yandex.ru", emptySet()))
    }
}
