package com.example.smarttracker.utils

import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Protocol
import okhttp3.Request
import com.example.smarttracker.utils.ErrorCategory

/**
 * Юнит-тесты для ApiErrorHandler.
 * Проверяют корректность парсинга, перевода ошибок и категоризации.
 * 
 * Примечание: для запуска требуется JUnit4 в testImplementation (build.gradle.kts)
 */
class ApiErrorHandlerTest {

    /**
     * Тест 1: Перевод ошибки email на русский
     */
    fun testTranslation_EmailAlreadyExists() {
        val englishError = "User with this email already exists"
        val russian = ApiErrorHandler.getErrorMessage(
            createHttpException(409, """{"detail": "$englishError"}""")
        )
        if (russian != "Пользователь с такой почтой уже существует") {
            throw AssertionError("Expected Russian translation, got: $russian")
        }
    }

    /**
     * Тест 2: Перевод ошибки username на русский
     */
    fun testTranslation_UsernameAlreadyExists() {
        val englishError = "User with this username already exists"
        val russian = ApiErrorHandler.getErrorMessage(
            createHttpException(409, """{"detail": "$englishError"}""")
        )
        if (russian != "Это имя пользователя уже используется") {
            throw AssertionError("Expected Russian translation, got: $russian")
        }
    }

    /**
     * Тест 3: Категоризация переведённой ошибки email
     */
    fun testCategorizeEmail_Translated() {
        val message = "Пользователь с такой почтой уже существует"
        val category = ApiErrorHandler.categorizeError(message)
        if (category != ErrorCategory.EMAIL_TAKEN) {
            throw AssertionError("Expected EMAIL_TAKEN, got: $category")
        }
    }

    /**
     * Тест 4: Категоризация переведённой ошибки username
     */
    fun testCategorizeUsername_Translated() {
        val message = "Это имя пользователя уже используется"
        val category = ApiErrorHandler.categorizeError(message)
        if (category != ErrorCategory.USERNAME_TAKEN) {
            throw AssertionError("Expected USERNAME_TAKEN, got: $category")
        }
    }

    /**
     * Тест 5: Перевод ошибки "Too many attempts"
     */
    fun testTranslation_TooManyAttempts() {
        val englishError = "Too many failed attempts. Account temporarily locked"
        val russian = ApiErrorHandler.getErrorMessage(
            createHttpException(400, """{"detail": "$englishError"}""")
        )
        if (!russian.contains("Слишком много")) {
            throw AssertionError("Expected Russian 'много', got: $russian")
        }
    }

    /**
     * Тест 6: Перевод ошибки с cooldown (извлечение числа)
     */
    fun testTranslation_CooldownWithSeconds() {
        val englishError = "Please wait 87 seconds before resending"
        val russian = ApiErrorHandler.getErrorMessage(
            createHttpException(400, """{"detail": "$englishError"}""")
        )
        if (!russian.contains("87") || !russian.contains("секунд")) {
            throw AssertionError("Expected Russian with '87' and 'секунд', got: $russian")
        }
    }

    /**
     * Тест 7: Network error на русском
     */
    fun testGetErrorMessage_IOException() {
        val exception = java.io.IOException("Connection refused")
        val message = ApiErrorHandler.getErrorMessage(exception)
        if (!message.contains("Ошибка подключения")) {
            throw AssertionError("Expected Russian 'подключение', got: $message")
        }
    }

    /**
     * Тест 8: Категоризация русской ошибки из любого источника
     */
    fun testCategorize_RussianEmail() {
        val message = "Пользователь с такой почтой уже существует"
        val category = ApiErrorHandler.categorizeError(message)
        if (category != ErrorCategory.EMAIL_TAKEN) {
            throw AssertionError("Expected EMAIL_TAKEN, got: $category")
        }
    }

    // ── Хелперы ──────────────────────────────────────────────────────────────

    private fun createHttpException(code: Int, responseBody: String): HttpException {
        val body = responseBody.toResponseBody()
        @Suppress("UNCHECKED_CAST")
        val response = Response.error<Any>(code, body) as Response<Any>
        return HttpException(response)
    }
}
