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
     * Маппинг типичных ошибок бэкенда с английского на русский.
     * Используется для переведения сообщений об ошибках API.
     */
    private val errorTranslations = mapOf(
        // Email errors
        "User with this email already exists" to "Пользователь с такой почтой уже существует",
        "user with this email already exists" to "Пользователь с такой почтой уже существует",
        "Email already registered" to "Эта почта уже зарегистрирована",
        "email already registered" to "Эта почта уже зарегистрирована",
        
        // Username errors
        "User with this username already exists" to "Это имя пользователя уже используется",
        "user with this username already exists" to "Это имя пользователя уже используется",
        "Username already exists" to "Это имя пользователя уже используется",
        "username already exists" to "Это имя пользователя уже используется",
        
        // Nickname errors (API использует nickname вместо username)
        "User with this nickname already exists" to "Это имя пользователя уже используется",
        "user with this nickname already exists" to "Это имя пользователя уже используется",
        "Nickname already exists" to "Это имя пользователя уже используется",
        "nickname already exists" to "Это имя пользователя уже используется",
        
        // Email format errors
        "Invalid email format" to "Неверный формат почты",
        "invalid email format" to "Неверный формат почты",
        
        // Verification errors
        "Verification code is invalid" to "Неверный код подтверждения",
        "verification code is invalid" to "Неверный код подтверждения",
        "Invalid verification code" to "Неверный код подтверждения",
        "invalid verification code" to "Неверный код подтверждения",
        
        // Too many attempts
        "Too many failed attempts" to "Слишком много неверных попыток. Попробуйте позже",
        "too many failed attempts" to "Слишком много неверных попыток. Попробуйте позже",
        "Account temporarily locked" to "Аккаунт временно заблокирован",
        
        // Cooldown errors
        "Please wait" to "Пожалуйста, подождите перед повторной отправкой",
        "please wait" to "Пожалуйста, подождите перед повторной отправкой",
        
        // Password errors
        "Password too short" to "Пароль слишком короткий (минимум 8 символов)",
        "password too short" to "Пароль слишком короткий (минимум 8 символов)",
        
        // Server errors
        "Internal server error" to "Ошибка сервера. Попробуйте позже",
        "internal server error" to "Ошибка сервера. Попробуйте позже",
    )

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
                409 -> "Конфликт данных"
                422 -> "Ошибка валидации"
                429 -> "Слишком много запросов. Попробуйте позже"
                500 -> "Ошибка сервера. Попробуйте позже"
                503 -> "Сервис недоступен"
                else -> "Ошибка ${exception.code()}"
            }
        }

        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            
            // Извлечь сообщение об ошибке из JSON
            val rawError = json.get("detail")?.asString ?: 
                          json.get("message")?.asString ?:
                          "Ошибка сервера"
            
            // Перевести ошибку на русский, если есть перевод
            translateError(rawError)
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
     * Переводит сообщение об ошибке с английского на русский.
     * Проверяет точное совпадение, а также подстроки.
     */
    private fun translateError(error: String): String {
        // Точное совпадение
        errorTranslations[error]?.let { return it }
        
        // Проверка по подстроке (для сообщений вроде "Please wait 87 seconds...")
        for ((english, russian) in errorTranslations) {
            if (error.contains(english, ignoreCase = true)) {
                return if (english.lowercase().contains("please wait")) {
                    // Для "Please wait N seconds" — оставить число
                    val regex = Regex("\\d+")
                    val seconds = regex.find(error)?.value
                    if (seconds != null) {
                        "Пожалуйста, подождите $seconds секунд перед повторной попыткой"
                    } else {
                        russian
                    }
                } else {
                    russian
                }
            }
        }
        
        // Если перевода нет, вернуть оригинальное сообщение (может быть уже на русском)
        return error
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
     * Сопоставляет API-ошибку с конкретным сценарием для более точных сообщений на UI.
     * Работает как с английскими, так и с русскими сообщениями об ошибках.
     */
    fun categorizeError(errorMessage: String): ErrorCategory {
        val lowerError = errorMessage.lowercase()
        
        return when {
            // Email errors
            lowerError.contains("email") && 
            (lowerError.contains("exist") || 
             lowerError.contains("already") ||
             lowerError.contains("занят") ||
             lowerError.contains("зарегистрирована")) -> 
                ErrorCategory.EMAIL_TAKEN

            // Username errors
            (lowerError.contains("username") || lowerError.contains("nickname")) &&
            (lowerError.contains("exist") || 
             lowerError.contains("already") ||
             lowerError.contains("занят") ||
             lowerError.contains("используется")) -> 
                ErrorCategory.USERNAME_TAKEN

            // Verification attempts
            (lowerError.contains("too many") || 
             lowerError.contains("слишком много")) &&
            (lowerError.contains("attempt") || 
             lowerError.contains("попыт")) ->
                ErrorCategory.TOO_MANY_ATTEMPTS

            // Resend cooldown
            (lowerError.contains("wait") || 
             lowerError.contains("подождите") ||
             lowerError.contains("seconds") ||
             lowerError.contains("секунд")) ->
                ErrorCategory.RESEND_COOLDOWN

            // Password errors
            (lowerError.contains("password") || 
             lowerError.contains("пароль")) -> 
                ErrorCategory.PASSWORD_ERROR

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
