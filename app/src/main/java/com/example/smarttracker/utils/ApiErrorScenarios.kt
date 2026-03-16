package com.example.smarttracker.utils

/**
 * Примеры типичных ошибок API (для тестирования ApiErrorHandler).
 *
 * Сервер FastAPI обычно возвращает:
 * - 400 Bad Request: {"detail": "..."}
 * - 409 Conflict: {"detail": "Username already exists"} или {"detail": "Email already registered"}
 * - 422 Unprocessable Entity: {"detail": "Invalid email format"} или ошибки валидации
 * - 500 Internal Server Error: {"detail": "Internal server error"}
 *
 * Сценарии для обработки:
 */

/*
СЦЕНАРИЙ 1: Имя пользователя уже используется
─────────────────────────────────────────────────
Request: POST /auth/register
{
  "firstName": "John",
  "username": "existing_user",  ← такой ник уже есть в БД
  "email": "john@example.com",
  ...
}

Response: HTTP 409 Conflict
{
  "detail": "Username 'existing_user' already exists"
}

ApiErrorHandler:
├─ getErrorMessage()
│  └─ returns: "Username 'existing_user' already exists"
└─ categorizeError()
   └─ matches "username" + "exist" + "already"
      → ErrorCategory.USERNAME_TAKEN


СЦЕНАРИЙ 2: Email уже зарегистрирован
──────────────────────────────────────
Request: POST /auth/register
{
  "email": "used@example.com",  ← этот email уже в таблице users
  ...
}

Response: HTTP 409 Conflict
{
  "detail": "Email 'used@example.com' is already registered"
}

ApiErrorHandler:
├─ getErrorMessage()
│  └─ returns: "Email 'used@example.com' is already registered"
└─ categorizeError()
   └─ matches "email" + "exist"/"already"/"registered"
      → ErrorCategory.EMAIL_TAKEN


СЦЕНАРИЙ 3: Неверный формат email
────────────────────────────────────
Request: POST /auth/register
{
  "email": "invalid_email",  ← не соответствует EMAIL_ADDRESS pattern
  ...
}

Response: HTTP 422 Unprocessable Entity
{
  "detail": "Invalid email format"
}

ApiErrorHandler:
├─ getErrorMessage()
│  └─ returns: "Invalid email format"
└─ categorizeError()
   └─ No specific match
      → ErrorCategory.GENERIC


СЦЕНАРИЙ 4: Слишком много попыток верификации кода
────────────────────────────────────────────────────
Request: POST /auth/verify-email (6-й раз подряд с неверным кодом)
{
  "email": "user@example.com",
  "code": "000000"  ← неверный код
}

Response: HTTP 400 Bad Request
{
  "detail": "Too many failed attempts. Account temporarily locked. Try again in 15 minutes"
}

ApiErrorHandler:
├─ getErrorMessage()
│  └─ returns: "Too many failed attempts..."
└─ categorizeError()
   └─ matches "Too many"
      → ErrorCategory.TOO_MANY_ATTEMPTS


СЦЕНАРИЙ 5: Кулдаун на повторную отправку
─────────────────────────────────────────────
Request: POST /auth/resend-code (второй раз за 2 минуты)
{
  "email": "user@example.com"
}

Response: HTTP 400 Bad Request
{
  "detail": "Please wait 87 seconds before resending verification code"
}

ApiErrorHandler:
├─ getErrorMessage()
│  └─ returns: "Please wait 87 seconds before resending verification code"
└─ categorizeError()
   └─ matches "Please wait"
      → ErrorCategory.RESEND_COOLDOWN


СЦЕНАРИЙ 6: Ошибка сервера (SMTP недоступен)
──────────────────────────────────────────────
Request: POST /auth/register
{
  ...
}

Response: HTTP 500 Internal Server Error
{
  "detail": "SMTP server connection failed"
}

ApiErrorHandler:
├─ getErrorMessage()
│  └─ returns: "SMTP server connection failed"
└─ categorizeError()
   └─ No specific match
      → ErrorCategory.GENERIC


СЦЕНАРИЙ 7: Network error (нет интернета)
──────────────────────────────────────────
Exception: java.io.IOException (ConnectException, SocketTimeoutException и т.д.)

ApiErrorHandler:
└─ getErrorMessage()
   └─ returns: "Ошибка подключения. Проверьте интернет"


────────────────────────────────────────────────────────────────────────────────

ИНТЕГРАЦИЯ В UI
───────────────

RegisterViewModel при вызове registerUseCase:
  .onFailure { error ->
    val errorMessage = ApiErrorHandler.getErrorMessage(error)
    val category = ApiErrorHandler.categorizeError(errorMessage)
    
    when (category) {
      USERNAME_TAKEN -> показать ошибку под полем username
      EMAIL_TAKEN    → показать ошибку под полем email
      TOO_MANY_ATTEMPTS → заблокировать поле ввода кода
      RESEND_COOLDOWN → показать таймер на кнопке
      GENERIC        → показать ошибку внизу формы
    }
  }

RegisterScreen отображает:
  - state.fieldError (под конкретным полем)
  - state.error (общая ошибка внизу)
  - state.resendCooldownSeconds (таймер на кнопке "Отправить повторно")

*/
