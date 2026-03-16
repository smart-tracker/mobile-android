package com.example.smarttracker.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.HttpException

/**
 * Утилита для непарсенпия и преобразования ошибок API в понятные пользователю сообщения.
 *
 * Сервер возвращает ошибки в формате:
 * - 400/409/422: {"detail": "Описание проблемы"}
 * - 500+: {"detail": "Internal server error"}
 *
 * Функция распарсивает JSON-ошибку и сопоставляет с конкретными сценариями.
 */
object ApiErrorHandler {

    private val gson = Gson()

    /**
     * Преобразует исключение в понятное пользователю сообщение об ошибке.
     *
     * @param throwable — исключение из Retrofit/OkHttp
     * @return человеческое описание ошибки на русском
     */
    fun getErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> handleHttpException(throwable)
            is java.io.IOException -> "Ошибка подключения. Проверьте интернет"
            else -> throwable.message ?: "Неизвестная ошибка"
        }
    }

    private fun handleHttpException(exception: HttpException): String {
        val responseBody = exception.response()?.errorBody()?.string() ?: run {
            return when (exception.code()) {
                400 -> "Неверные данные"
                401 -> "Неверные учётные данные"
                403 -> "Доступ запрещён"
                404 -> "Ресурс не найден"
                409 -> "Конфликт данных (возможно, пользователь уже существует)"
                422 -> "Ошибка валидации"
                429 -> "Слишком много запросов. Попробуйте позже"
                500 -> "Ошибка сервера. Попробуйте позже"
                503 -> "Сервис недоступен"
                else -> "Ошибка ${exception.code()}"
            }
        }

        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            
            // Сервер может возвращать "detail" или "message"
            json.get("detail")?.asString ?: 
            json.get("message")?.asString ?:
            "Ошибка сервера"
        } catch (e: Exception) {
            // Если JSON не парсится, возвращаем generic сообщение по статус-коду
            when (exception.code()) {
                400 -> "Неверные данные"
                409 -> "Конфликт данных"
                422 -> "Ошибка валидации"
                else -> "Ошибка ${exception.code()}"
            }
        }
    }

    /**
     * Парсит детали ошибки для конкретных полей.
     * Бэкенд может возвращать {detail: {field: "message"}} или плоский JSONоб-ект.
     *
     * @param throwable — исключение
     * @return map вида {fieldName -> errorMessage}, или пусто если не распарсилось
     */
    fun getFieldErrors(throwable: Throwable): Map<String, String> {
        if (throwable !is HttpException) return emptyMap()
        
        val responseBody = throwable.response()?.errorBody()?.string() ?: return emptyMap()
        
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val result = mutableMapOf<String, String>()
            
            // Если detail — это объект (ошибки по полям)
            if (json.has("detail") && json.get("detail").isJsonObject) {
                val detail = json.getAsJsonObject("detail")
                detail.entrySet().forEach { (key, value) ->
                    result[key] = value.asString
                }
            }
            
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Сопоставляет API-ошибку с конкретным сценарием (username taken, email taken и т.д.)
     * для более точных сообщений на UI.
     *
     * Примеры логики:
     * - Если в ошибке упоминается "username", показать ошибку под полем username
     * - Если "email", показать под email
     * - Если "password", показать под password
     */
    fun categorizeError(errorMessage: String): ErrorCategory {
        return when {
            errorMessage.contains("username", ignoreCase = true) &&
            (errorMessage.contains("exist", ignoreCase = true) || 
             errorMessage.contains("already", ignoreCase = true) ||
             errorMessage.contains("занят", ignoreCase = true)) -> 
                ErrorCategory.USERNAME_TAKEN

            errorMessage.contains("email", ignoreCase = true) &&
            (errorMessage.contains("exist", ignoreCase = true) || 
             errorMessage.contains("already", ignoreCase = true) ||
             errorMessage.contains("занят", ignoreCase = true)) -> 
                ErrorCategory.EMAIL_TAKEN

            errorMessage.contains("password", ignoreCase = true) -> 
                ErrorCategory.PASSWORD_ERROR

            errorMessage.contains("Too many failed attempts", ignoreCase = true) ||
            errorMessage.contains("Слишком много", ignoreCase = true) ->
                ErrorCategory.TOO_MANY_ATTEMPTS

            errorMessage.contains("Please wait", ignoreCase = true) ||
            errorMessage.contains("подождите", ignoreCase = true) ->
                ErrorCategory.RESEND_COOLDOWN

            else -> ErrorCategory.GENERIC
        }
    }
}

/**
 * Категория ошибки для корректного отображения на UI.
 */
enum class ErrorCategory {
    USERNAME_TAKEN,           // Имя пользователя уже используется
    EMAIL_TAKEN,              // Email уже используется
    PASSWORD_ERROR,           // Проблема с паролем
    TOO_MANY_ATTEMPTS,        // Слишком много попыток верификации
    RESEND_COOLDOWN,          // Кулдаун на повторную отправку кода
    GENERIC                   // Прочие ошибки
}
