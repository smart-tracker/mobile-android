package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * Unit-тесты LoginUseCase.
 *
 * Покрывает:
 * - Невалидный email → IllegalArgumentException, репозиторий НЕ вызывается.
 *   Это важно: поломка короткого замыкания ведёт к лишнему API-запросу
 *   или неверному типу ошибки в UI (ApiError вместо ValidationError).
 */
class LoginUseCaseTest {

    private lateinit var repository: AuthRepository
    private lateinit var useCase: LoginUseCase

    @Before
    fun setUp() {
        repository = mock()
        useCase    = LoginUseCase(repository)
    }

    // ── Тест 6: невалидный email → нет API-запроса ────────────────────────────

    @Test
    fun `невалидный email возвращает failure без вызова репозитория`() = runTest {
        val result = useCase("not-an-email", "Password1")

        assertTrue("Ожидался Result.failure для невалидного email", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        // Короткое замыкание: репозиторий (= API) не должен вызываться
        verify(repository, never()).login(any(), any())
    }
}
