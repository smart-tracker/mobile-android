package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.UserPurpose
import com.example.smarttracker.domain.repository.AllowedEmailDomainsRepository
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.validation.EmailValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDate

/**
 * Unit-тесты RegisterUseCase.
 *
 * Покрывает:
 * - Пароль без цифры → failure без API-вызова
 *   (требование есть в validate(), отсутствует в UI-подсказке → легко незаметно убрать)
 * - Несовпадение confirmPassword → failure без API-вызова
 * - Иностранный почтовый домен → failure без API-вызова (149-ФЗ)
 * - Российский домен проходит доменную проверку и доходит до репозитория
 */
class RegisterUseCaseTest {

    private lateinit var repository: AuthRepository
    private lateinit var useCase: RegisterUseCase

    /** Фейк вместо мока: интерфейс из одного метода, поведение фиксировано. */
    private val allowedDomains = object : AllowedEmailDomainsRepository {
        override suspend fun getAllowedDomains(): Set<String> =
            setOf("yandex.ru", "mail.ru", "rambler.ru")
    }

    @Before
    fun setUp() {
        repository = mock()
        useCase    = RegisterUseCase(repository, allowedDomains)
    }

    // ── Тест 4: пароль без цифры → failure ────────────────────────────────────

    @Test
    fun `пароль без цифры возвращает failure без вызова репозитория`() = runTest {
        val request = makeRequest(password = "PasswordOnly", confirmPassword = "PasswordOnly")

        val result = useCase(request)

        assertTrue("Ожидался Result.failure для пароля без цифры", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verify(repository, never()).register(any())
    }

    // ── Тест 7: пароли не совпадают → failure ────────────────────────────────

    @Test
    fun `несовпадение confirmPassword возвращает failure без вызова репозитория`() = runTest {
        val request = makeRequest(password = "Secret1234", confirmPassword = "Different9")

        val result = useCase(request)

        assertTrue("Ожидался Result.failure при несовпадении паролей", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verify(repository, never()).register(any())
    }

    // ── 149-ФЗ: иностранный домен → failure ──────────────────────────────────

    @Test
    fun `иностранный почтовый домен возвращает failure без вызова репозитория`() = runTest {
        val request = makeRequest(email = "ivan@gmail.com")

        val result = useCase(request)

        assertTrue("Ожидался Result.failure для иностранного домена", result.isFailure)
        assertEquals(
            EmailValidator.RUSSIAN_EMAIL_REQUIRED_MESSAGE,
            result.exceptionOrNull()?.message,
        )
        verify(repository, never()).register(any())
    }

    // ── 149-ФЗ: российский домен проходит до репозитория ─────────────────────

    @Test
    fun `российский почтовый домен доходит до вызова репозитория`() = runTest {
        val request = makeRequest(email = "ivan@yandex.ru")

        useCase(request)

        verify(repository).register(request)
    }

    // ── Хелпер ───────────────────────────────────────────────────────────────

    private fun makeRequest(
        password:        String = "Secret1234",
        confirmPassword: String = "Secret1234",
        // Российский домен по умолчанию: тесты других полей не должны
        // спотыкаться о доменную проверку 149-ФЗ
        email:           String = "ivan@yandex.ru",
    ) = RegisterRequest(
        firstName       = "Иван",
        username        = "ivan_user",
        birthDate       = LocalDate.of(2000, 1, 1),
        gender          = Gender.MALE,
        purpose         = UserPurpose.ATHLETE,
        email           = email,
        password        = password,
        confirmPassword = confirmPassword,
    )
}
