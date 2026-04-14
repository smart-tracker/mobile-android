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
}
