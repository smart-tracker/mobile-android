package com.example.smarttracker.utils

import org.junit.Test
import org.junit.Assert.assertEquals
import retrofit2.HttpException
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.Request

/**
 * Юнит-тесты для ApiErrorHandler.
 * Проверяют корректность парсинга ошибок и категоризации.
 */
class ApiErrorHandlerTest {

    /**
     * Тест 1: HttpException с JSON ошибкой "username already exists"
     */
    @Test
    fun testCategorizeUsername_Taken() {
        val message = "Username 'john_doe' already exists"
        val category = ApiErrorHandler.categorizeError(message)
        assertEquals(ErrorCategory.USERNAME_TAKEN, category)
    }

    /**
     * Тест 2: HttpException с JSON ошибкой "email already registered"
     */
    @Test
    fun testCategorizeEmail_Taken() {
        val message = "Email 'user@example.com' is already registered"
        val category = ApiErrorHandler.categorizeError(message)
        assertEquals(ErrorCategory.EMAIL_TAKEN, category)
    }

    /**
     * Тест 3: HttpException с JSON ошибкой "too many failed attempts"
     */
    @Test
    fun testCategorizeTooManyAttempts() {
        val message = "Too many failed attempts. Account temporarily locked"
        val category = ApiErrorHandler.categorizeError(message)
        assertEquals(ErrorCategory.TOO_MANY_ATTEMPTS, category)
    }

    /**
     * Тест 4: HttpException с JSON ошибкой "please wait before resending"
     */
    @Test
    fun testCategorizeResendCooldown() {
        val message = "Please wait 87 seconds before resending verification code"
        val category = ApiErrorHandler.categorizeError(message)
        assertEquals(ErrorCategory.RESEND_COOLDOWN, category)
    }

    /**
     * Тест 5: Неизвестная ошибка → GENERIC
     */
    @Test
    fun testCategorizeGeneric() {
        val message = "Some other error from server"
        val category = ApiErrorHandler.categorizeError(message)
        assertEquals(ErrorCategory.GENERIC, category)
    }

    /**
     * Тест 6: Сообщение об ошибке на русском
     */
    @Test
    fun testCategorizeRussian_EmailTaken() {
        val message = "Email занята"
        val category = ApiErrorHandler.categorizeError(message)
        assertEquals(ErrorCategory.EMAIL_TAKEN, category)
    }

    /**
     * Тест 7: Network error → сообщение об интернете
     */
    @Test
    fun testGetErrorMessage_IOException() {
        val exception = java.io.IOException("Connection refused")
        val message = ApiErrorHandler.getErrorMessage(exception)
        assertTrue(message.contains("подключен", ignoreCase = true))
    }

    /**
     * Тест 8: Неизвестное исключение → fallback сообщение
     */
    @Test
    fun testGetErrorMessage_UnknownException() {
        val exception = RuntimeException("Random error")
        val message = ApiErrorHandler.getErrorMessage(exception)
        assertEquals("Random error", message)
    }

    private fun assertTrue(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Assertion failed")
        }
    }
}
