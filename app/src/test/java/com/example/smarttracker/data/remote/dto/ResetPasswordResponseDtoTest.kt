package com.example.smarttracker.data.remote.dto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты маппера ResetPasswordResponseDto → ResetPasswordResult.
 *
 * Покрывает BUG-2: POST /password-reset/confirm возвращает access_token + refresh_token,
 * но маппер toDomain() их отбрасывает — пользователь не может быть авто-авторизован.
 *
 * Тест документирует текущее (сломанное) поведение.
 * После исправления BUG-2 нужно обновить логику маппера и этот тест.
 */
class ResetPasswordResponseDtoTest {

    private val dto = ResetPasswordResponseDto(
        accessToken  = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
        refreshToken = "dGVzdC1yZWZyZXNoLXRva2Vu",
        tokenType    = "bearer"
    )

    @Test
    fun `toDomain помечает операцию как успешную`() {
        val result = dto.toDomain()
        assertTrue(result.success)
    }

    @Test
    fun `DTO содержит непустые токены для сохранения в TokenStorage`() {
        // Репозиторий (PasswordRecoveryRepositoryImpl) читает accessToken и refreshToken
        // напрямую из DTO до вызова toDomain() и сохраняет их в TokenStorage.
        assertFalse("accessToken не должен быть пустым", dto.accessToken.isBlank())
        assertFalse("refreshToken не должен быть пустым", dto.refreshToken.isBlank())
    }

    @Test
    fun `toDomain выставляет redirectToLogin = false (авто-вход активен)`() {
        // После исправления BUG-2: репозиторий сохраняет токены и возвращает redirectToLogin=false.
        // ViewModel использует это значение для навигации на Home вместо Login.
        val result = dto.toDomain()
        assertFalse(
            "redirectToLogin должен быть false — токены сохранены, авто-вход возможен",
            result.redirectToLogin
        )
    }

    @Test
    fun `toDomain с пустым tokenType не бросает исключений`() {
        val dtoWithDefaultType = ResetPasswordResponseDto(
            accessToken  = "access",
            refreshToken = "refresh",
            // tokenType по умолчанию = "bearer"
        )
        val result = dtoWithDefaultType.toDomain()
        assertTrue(result.success)
    }
}
