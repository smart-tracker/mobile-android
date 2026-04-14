package com.example.smarttracker.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-тесты маппера ResendCodeResponseDto → ResendResult.
 *
 * Покрывает:
 * - remainingSeconds=null → expiresIn=0 (не NPE, не краш)
 * - remainingSeconds=120 → expiresIn=120
 * - expiresAt=null — не вызывает исключений
 */
class ResendCodeResponseDtoTest {

    @Test
    fun `remainingSeconds null даёт expiresIn = 0`() {
        val dto = ResendCodeResponseDto(
            message          = "Код отправлен",
            expiresAt        = null,
            remainingSeconds = null
        )
        val result = dto.toDomain()
        assertEquals(0, result.expiresIn)
    }

    @Test
    fun `remainingSeconds 120 маппится в expiresIn = 120`() {
        val dto = ResendCodeResponseDto(
            message          = "Код отправлен",
            expiresAt        = "2026-04-13T12:00:00Z",
            remainingSeconds = 120
        )
        val result = dto.toDomain()
        assertEquals(120, result.expiresIn)
    }

    @Test
    fun `remainingSeconds 0 маппится в expiresIn = 0`() {
        val dto = ResendCodeResponseDto(
            message          = "Код отправлен",
            expiresAt        = null,
            remainingSeconds = 0
        )
        val result = dto.toDomain()
        assertEquals(0, result.expiresIn)
    }

    @Test
    fun `expiresAt null при существующем remainingSeconds не вызывает исключения`() {
        val dto = ResendCodeResponseDto(
            message          = "OK",
            expiresAt        = null,
            remainingSeconds = 600
        )
        // Не должно бросать NullPointerException
        val result = dto.toDomain()
        assertEquals(600, result.expiresIn)
    }
}
