package com.example.smarttracker.data.repository

import com.example.smarttracker.data.cache.RoleGoalCache
import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.dto.AuthResponseDto
import com.example.smarttracker.data.remote.dto.LoginRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckResponseDto
import com.example.smarttracker.data.remote.dto.RoleDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.InOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.argumentCaptor

/**
 * Unit-тесты AuthRepositoryImpl.
 *
 * Все зависимости замоканы через mockito-kotlin.
 * Критический инвариант (CLAUDE.md #12):
 * токены СНАЧАЛА сохраняются в TokenStorage, и только ПОТОМ вызывается getUserRoles().
 * Нарушение порядка → интерцептор прочитает пустое хранилище → 401.
 */
class AuthRepositoryImplTest {

    private lateinit var api: AuthApiService
    private lateinit var tokenStorage: TokenStorage
    private lateinit var roleGoalCache: RoleGoalCache
    private lateinit var roleConfigStorage: RoleConfigStorage
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        api               = mock()
        tokenStorage      = mock()
        roleGoalCache     = mock()
        roleConfigStorage = mock()

        repository = AuthRepositoryImpl(api, tokenStorage, roleGoalCache, roleConfigStorage)
    }

    // ── login() — порядок токенов и ролей (критический инвариант) ────────────

    @Test
    fun `login - saveTokens вызывается ДО getUserRoles`() = runTest {
        stubSuccessfulLogin()

        repository.login("user@test.com", "Secret1234")

        // Проверить порядок: сначала saveTokens, затем getUserRoles
        val order: InOrder = inOrder(tokenStorage, api)
        order.verify(tokenStorage).saveTokens(any(), any(), any())
        order.verify(api).getUserRoles()
    }

    @Test
    fun `login - access token сохраняется в TokenStorage`() = runTest {
        stubSuccessfulLogin()

        repository.login("user@test.com", "Secret1234")

        // Первый вызов saveTokens (до ролей) должен содержать токен
        val captor = argumentCaptor<String>()
        verify(tokenStorage, org.mockito.kotlin.atLeastOnce()).saveTokens(
            captor.capture(), any(), any()
        )
        assertEquals("test-access-token", captor.firstValue)
    }

    @Test
    fun `login - roleIds из getUserRoles сохраняются в TokenStorage`() = runTest {
        stubSuccessfulLogin()

        repository.login("user@test.com", "Secret1234")

        // Второй вызов saveTokens (после ролей) должен содержать roleIds=[1]
        val rolesCaptor = argumentCaptor<List<Int>>()
        verify(tokenStorage, org.mockito.kotlin.atLeastOnce()).saveTokens(
            any(), any(), rolesCaptor.capture()
        )
        // Последний вызов содержит актуальные роли
        assertTrue(rolesCaptor.lastValue.contains(1))
    }

    @Test
    fun `login - возвращает Result_success при успешном ответе API`() = runTest {
        stubSuccessfulLogin()
        val result = repository.login("user@test.com", "Secret1234")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `login - возвращает Result_failure при HttpException от api_login`() = runTest {
        whenever(api.login(any())).thenThrow(RuntimeException("401 Unauthorized"))
        val result = repository.login("user@test.com", "Secret1234")
        assertTrue(result.isFailure)
    }

    @Test
    fun `login - Result_success даже если getUserRoles бросает исключение`() = runTest {
        // Если getUserRoles падает → логируем ошибку, но login считается успешным.
        // Роли остаются пустыми.
        whenever(api.login(any())).thenReturn(
            AuthResponseDto("access", "refresh", "bearer")
        )
        whenever(api.getUserRoles()).thenThrow(RuntimeException("503 Service Unavailable"))

        val result = repository.login("user@test.com", "Secret1234")
        assertTrue(result.isSuccess)
    }

    // ── verifyEmail() — тот же инвариант ─────────────────────────────────────

    @Test
    fun `verifyEmail - saveTokens вызывается ДО getUserRoles`() = runTest {
        stubSuccessfulVerifyEmail()
        whenever(roleConfigStorage.getSelectedRoles()).thenReturn(emptyList())

        repository.verifyEmail("user@test.com", "123456")

        val order: InOrder = inOrder(tokenStorage, api)
        order.verify(tokenStorage).saveTokens(any(), any(), any())
        order.verify(api).getUserRoles()
    }

    @Test
    fun `verifyEmail - если RoleConfigStorage не пуст getUserRoles не вызывается`() = runTest {
        stubSuccessfulVerifyEmail()
        // При регистрации пользователь выбрал роль → роли уже сохранены
        whenever(roleConfigStorage.getSelectedRoles()).thenReturn(listOf(1))

        repository.verifyEmail("user@test.com", "123456")

        // getUserRoles не должен вызываться (роли уже есть)
        verify(api, org.mockito.kotlin.never()).getUserRoles()
    }

    // ── refreshToken() — сохранение ролей ────────────────────────────────────

    @Test
    fun `refreshToken - roleIds сохраняются из TokenStorage, не сбрасываются`() = runTest {
        // Пользователь уже имеет роль [2] (тренер)
        whenever(tokenStorage.getUserRoles()).thenReturn(listOf(2))
        whenever(api.refreshToken(any())).thenReturn(
            AuthResponseDto("new-access", "new-refresh", "bearer")
        )

        repository.refreshToken("old-refresh-token")

        // saveTokens должен вызываться с roleIds=[2] (из TokenStorage)
        val rolesCaptor = argumentCaptor<List<Int>>()
        verify(tokenStorage).saveTokens(any(), any(), rolesCaptor.capture())
        assertEquals(listOf(2), rolesCaptor.firstValue)
    }

    @Test
    fun `refreshToken - новый accessToken сохраняется`() = runTest {
        whenever(tokenStorage.getUserRoles()).thenReturn(listOf(1))
        whenever(api.refreshToken(any())).thenReturn(
            AuthResponseDto("brand-new-access", "brand-new-refresh", "bearer")
        )

        repository.refreshToken("old-refresh")

        val accessCaptor = argumentCaptor<String>()
        verify(tokenStorage).saveTokens(accessCaptor.capture(), any(), any())
        assertEquals("brand-new-access", accessCaptor.firstValue)
    }

    // ── checkNickname() ───────────────────────────────────────────────────────

    @Test
    fun `checkNickname - возвращает isAvailable=false для занятого nickname`() = runTest {
        whenever(api.checkNickname(NicknameCheckRequestDto("busy_nick"))).thenReturn(
            NicknameCheckResponseDto(
                nickname    = "busy_nick",
                isAvailable = false,
                message     = "Nickname already taken"
            )
        )

        val result = repository.checkNickname("busy_nick")

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull()?.isAvailable)
    }

    @Test
    fun `checkNickname - возвращает isAvailable=true для свободного nickname`() = runTest {
        whenever(api.checkNickname(NicknameCheckRequestDto("free_nick"))).thenReturn(
            NicknameCheckResponseDto(
                nickname    = "free_nick",
                isAvailable = true,
                message     = "Nickname available"
            )
        )

        val result = repository.checkNickname("free_nick")

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull()?.isAvailable)
    }

    // ── Хелперы ───────────────────────────────────────────────────────────────

    private suspend fun stubSuccessfulLogin() {
        whenever(api.login(LoginRequestDto("user@test.com", "Secret1234"))).thenReturn(
            AuthResponseDto("test-access-token", "test-refresh-token", "bearer")
        )
        whenever(api.getUserRoles()).thenReturn(listOf(RoleDto(1, "sportsman")))
    }

    private suspend fun stubSuccessfulVerifyEmail() {
        whenever(api.verifyEmail(any())).thenReturn(
            AuthResponseDto("test-access-token", "test-refresh-token", "bearer")
        )
        whenever(api.getUserRoles()).thenReturn(listOf(RoleDto(1, "sportsman")))
    }
}
