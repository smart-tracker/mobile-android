package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.UserPurpose
import com.example.smarttracker.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
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
 */
class RegisterUseCaseTest {

    private lateinit var repository: AuthRepository
    private lateinit var useCase: RegisterUseCase

    @Before
    fun setUp() {
        repository = mock()
        useCase    = RegisterUseCase(repository)
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

    // ── Хелпер ───────────────────────────────────────────────────────────────

    private fun makeRequest(
        password:        String = "Secret1234",
        confirmPassword: String = "Secret1234",
    ) = RegisterRequest(
        firstName       = "Иван",
        username        = "ivan_user",
        birthDate       = LocalDate.of(2000, 1, 1),
        gender          = Gender.MALE,
        purpose         = UserPurpose.ATHLETE,
        email           = "ivan@test.com",
        password        = password,
        confirmPassword = confirmPassword,
    )
}
