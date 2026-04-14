package com.example.smarttracker.data.repository

import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.dto.ForgotPasswordResponseDto
import com.example.smarttracker.data.remote.dto.ResetPasswordResponseDto
import com.example.smarttracker.data.remote.dto.RoleDto
import com.example.smarttracker.domain.model.ResetPasswordRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.InOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit-тесты PasswordRecoveryRepositoryImpl.
 *
 * Критический инвариант (CLAUDE.md #12):
 * в resetPassword() токены СНАЧАЛА сохраняются в TokenStorage,
 * и только ПОТОМ вызывается getUserRoles(). Нарушение → 401.
 */
class PasswordRecoveryRepositoryImplTest {

    private lateinit var api: AuthApiService
    private lateinit var tokenStorage: TokenStorage
    private lateinit var repository: PasswordRecoveryRepositoryImpl

    @Before
    fun setUp() {
        api          = mock()
        tokenStorage = mock()
        repository   = PasswordRecoveryRepositoryImpl(api, tokenStorage)
    }

    // ── resetPassword — порядок сохранения токенов (критический инвариант) ────

    @Test
    fun `resetPassword - saveTokens вызывается ДО getUserRoles`() = runTest {
        stubSuccessfulReset()

        repository.resetPassword(makeResetRequest())

        val order: InOrder = inOrder(tokenStorage, api)
        order.verify(tokenStorage).saveTokens(any(), any(), any())
        order.verify(api).getUserRoles()
    }

    // ── resetPassword — graceful degradation при падении getUserRoles ─────────

    @Test
    fun `resetPassword - getUserRoles падает, Result_success всё равно возвращается`() = runTest {
        whenever(api.resetPassword(any())).thenReturn(
            ResetPasswordResponseDto("access-token", "refresh-token", "bearer")
        )
        whenever(api.getUserRoles()).thenThrow(RuntimeException("503 Service Unavailable"))

        val result = repository.resetPassword(makeResetRequest())

        assertTrue("Ожидался Result.success, получено: ${result.exceptionOrNull()}", result.isSuccess)
        assertFalse(
            "redirectToLogin должен быть false — токены уже сохранены",
            result.getOrNull()?.redirectToLogin ?: true
        )
    }

    // ── Хелперы ───────────────────────────────────────────────────────────────

    private suspend fun stubSuccessfulReset() {
        whenever(api.resetPassword(any())).thenReturn(
            ResetPasswordResponseDto("access-token", "refresh-token", "bearer")
        )
        whenever(api.getUserRoles()).thenReturn(listOf(RoleDto(1, "sportsman")))
    }

    private fun makeResetRequest() = ResetPasswordRequest(
        email           = "user@test.com",
        code            = "123456",
        newPassword     = "NewPass1",
        confirmPassword = "NewPass1"
    )
}
