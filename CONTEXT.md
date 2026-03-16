# Контекст проекта SmartTracker — Android

## Что это за проект
Android-приложение для создания, трекинга и анализа тренировок.
Организация на GitHub: `smart-tracker`
Репозиторий: `smart-tracker/mobile-android`, ветка `main`

---

Базовый промт

Продолжаем проект SmartTracker Android.
Прочитай CONTEXT.md и /memories/repo/ — там всё актуальное состояние.

---

## Архитектура
Clean Architecture: `domain` → `data` → `presentation`
DI: Hilt | UI: Jetpack Compose | Сеть: Retrofit | Токены: EncryptedSharedPreferences

---

## Статус задач

### ✅ Выполнено
- МОБ-1.1 — `domain/model/User.kt`
- МОБ-1.2 — `domain/model/RegisterRequest.kt`, `AuthResult.kt`, `RegisterResult.kt`
- МОБ-1.3 — `domain/repository/AuthRepository.kt`
- МОБ-1.4 — `domain/usecase/RegisterUseCase.kt` (валидация через Regex, без android.util)
- `domain/model/ResendResult.kt` — добавлен, `resendCode` в AuthRepository изменён с `Result<Int>` на `Result<ResendResult>`
- `chore` — создана полная конфигурация Gradle-проекта (Gradle 8.6, AGP 8.3.2, Kotlin 1.9.24)
- МОБ-2.1 — `data/remote/dto/` — 5 DTO файлов + mappers (с учётом расхождений с API)
- МОБ-2.2 — `data/remote/AuthApiService.kt` + `dto/RequestDtos.kt` — Retrofit интерфейс, 5 методов
- МОБ-2.4 — `data/local/TokenStorage.kt` + `TokenStorageImpl.kt` — EncryptedSharedPreferences
- МОБ-2.3 — `data/repository/AuthRepositoryImpl.kt` — реализация AuthRepository
- МОБ-5.1 — `di/AuthModule.kt` — Hilt-модуль: OkHttpClient, Retrofit, AuthApiService, Binds
- МОБ-4.1 — `presentation/auth/RegisterUiState.kt` — состояние 4-шагового экрана
- МОБ-3.1 — `presentation/auth/RegisterScreen.kt` — stateless Compose UI по Figma-макету
  - UI-улучшения (сессия 11.03.2026): убрана кнопка назад из TopAppBar, добавлен BackHandler, KeyboardCapitalization.Words для поля имени, DatePickerField с иконкой 📅 + DatePickerDialog, DateVisualTransformation (state хранит цифры «04052004», отображает «04.05.2004»)
  - UI-улучшения (сессия 16.03.2026): смещение заголовков на 15% вниз (Spacer 80.dp), шрифт заголовков SemiBold (менее жирный), RadioButton → Checkbox для "Цель использования", чёрная граница и галочка для чекбоксов, бирюзово-зелёный фон при выборе (#4DACA7)
- МОБ-4.2/4.3 — `presentation/auth/RegisterViewModel.kt` + `RegisterEvent.kt`
- МОБ-5.2 — `presentation/navigation/Screen.kt` + `AppNavGraph.kt` + MainActivity обновлена

- BASE_URL переключён на `https://runtastic.gottland.ru/` через `BuildConfig.BASE_URL` в `app/build.gradle.kts`
- Клиентская валидация в `RegisterViewModel.submitRegistration()` и `verifyEmail()`:
  - email — формат через `android.util.Patterns.EMAIL_ADDRESS`
  - password — минимум 8 символов
  - confirmPassword — совпадение с password
  - verificationCode — ровно 6 символов
- Commit `380588d` запушен: `fix: switch BASE_URL to prod, add client-side email/password/code validation`
- Commit `4d36a58` запушен: `feat(МОБ-3.1): убрать кнопку назад, автоформат даты через VisualTransformation, DatePickerDialog`
- **Обработка API-ошибок** (сессия 16.03.2026):
  - Создана утилита `utils/ApiErrorHandler.kt` для парсинга ошибок HTTP-ответов
  - Вместо сырых `error.message` пользователю выводятся понятные сообщения на русском
  - `RegisterViewModel` обновлена для использования `ApiErrorHandler` в методах: `submitRegistration()`, `verifyEmail()`, `onResendCode()`
  - Поддержка категоризации ошибок: USERNAME_TAKEN, EMAIL_TAKEN, PASSWORD_ERROR, TOO_MANY_ATTEMPTS, RESEND_COOLDOWN, GENERIC
  - Примеры типичных ошибок и их обработки в `utils/ApiErrorScenarios.kt` (документация для разработчиков)
  - **Перевод ошибок на русский:** добавлена карта `errorTranslations` с автоматическим переводом типичных ошибок с английского (от бэкенда) на русский
  - Примеры переводов: "User with this email already exists" → "Пользователь с такой почтой уже существует"
  - Коммит `114f617`: feat: add API error handling with user-friendly messages
  - Коммит `7420009`: feat: translate all API error messages to Russian (only Russian UI errors)

### ✅ Compile: BUILD SUCCESSFUL

- Commit `380588d`: `fix: switch BASE_URL to prod, add client-side email/password/code validation` ✅ Полный Gradle-билд
- Commit `4d36a58`: `feat(МОБ-3.1): убрать кнопку назад, автоформат даты через VisualTransformation, DatePickerDialog` ✅ Компилируется без ошибок

### ✅ Регистрационный поток (E2E тестирование)

- RegisterScreen (Step 1-3) → VerifyEmailScreen (Step 4) → HomeScreen ✅ 
- Все API endpoints работают корректно после исправления багов (см. выше)
- Возможна быстрая разработка и тестирование других экранов

### 🔜 Следующие задачи
- **LoginScreen** (Figma node: 172:640)
- **PasswordRecovery** screens (nodes: 227:186, 227:288, 227:339)

## Статус бэкенда

### 🔧 Локальная разработка (Local API setup)

**Статус:** ✅ Готов к тестированию. Android приложение успешно регистрируется и верифицирует email через локальный API.

**Конфигурация:**
- **Сервер:** FastAPI + SQLite (временно вместо PostgreSQL) на `http://localhost:8000`
- **Маршрутизация:** Debug build использует `http://10.0.2.2:8000/` (эмулятор → локальный Я хост)
- **Окружение:** Python venv в `C:\Users\novsm\Documents\GitHub\api`, uvicorn с флагом `--reload`
- **Конфиг:** `.env` с `DEBUG=true`, `DATABASE_URL=sqlite+aiosqlite:///./smarttracker.db`

**Тестирование:**
1. `/auth/register` → ✅ 200 OK (с `debug_code` в DEBUG-режиме)
2. `/auth/resend-code` → ✅ 200 OK (код переотправлен)
3. `/auth/verify-email` → ✅ 200 OK (JWT токены, пользователь авторизован)
4. **Навигация:** RegisterScreen → VerifyEmailScreen → HomeScreen ✅ успешна

---

### 🐛 Найденные баги API (нужны в production)

**Баг 1:** `app/services/auth.py` — метод `can_resend_code()` (строка ~143)
- **Проблема:** Использовано `.scalar_one_or_none()` после `.order_by()` → выбрасывает `MultipleResultsFound`
- **Когда возникает:** При повторной отправке кода (когда в таблице уже несколько кодов для пользователя)
- **Исправление:** Заменить на `.first()` и распаковать результат
- **Статус:** ✅ Исправлено локально

**Баг 2:** `app/services/auth.py` — метод `verify_email()` (строка ~97)
- **Проблема:** Использовано `.scalar_one_or_none()` после `.order_by()` → выбрасывает `MultipleResultsFound`
- **Когда возникает:** При подтверждении email кодом (при наличии нескольких кодов в таблице)
- **Исправление:** Заменить на `.first()` и распаковать результат
- **Статус:** ✅ Исправлено локально

**Баг 3:** SMTP ошибки в `/auth/register` и `/auth/resend-code`
- **Проблема:** При недоступности SMTP сервера endpoint возвращает 500 (необработанное исключение)
- **Когда возникает:** При попытке отправить email с кодом верификации
- **Исправление:** Обернуть `email_service.send_verification_code()` в try-except, логировать и продолжать работу (особенно в DEBUG режиме)
- **Статус:** ✅ Исправлено локально

**Улучшения:**
- Добавлено поле `debug_code` в `EmailVerificationResponse` schema (возвращается при `DEBUG=true`)
- BASE_URL в `app/build.gradle.kts` переключен обратно на `http://10.0.2.2:8000/` для локального тестирования

### 📌 Статус production: Сервер runtastic.gottland.ru

> Сервер: FastAPI + PostgreSQL, uvicorn слушает `0.0.0.0:8000`, SSL терминируется внешним балансировщиком хостинга.
> Uvicorn **не управляется systemd** — перезапуск только вручную от пользователя `mihail`.
> `.env` находится в `/home/mihail/api/.env`.

**⚠️ Важно:** Баги 1 и 2 выше нужно **обязательно исправить** на production API перед запуском. Без исправлений пользователи не смогут верифицировать email при повторной отправке кода.

### ✅ Исправлено на сервере (сессия)

| Ошибка | Причина | Решение |
|---|---|---|
| `Chain validation failed` (TLS) | Сервер отдавал только leaf-cert | Само исправилось (certbot обновил конфиг) |
| HTTP 500 — `ConnectionRefusedError: ('127.0.0.1', 5434)` | `.env` имел `POSTGRES_PORT=5434`, PostgreSQL слушает `5432` | `sed -i` в `.env`, заменено на 5432 |
| HTTP 500 — `NotNullViolationError: null value in column "last_name"` | Схема БД имела NOT NULL на необязательных полях | `ALTER TABLE users ALTER COLUMN last_name/middle_name/weight/height DROP NOT NULL` |

> ⚠️ **Важно:** `ALTER TABLE` сделан напрямую в БД — SQLAlchemy-модели на бэке **не синхронизированы**. Бэкенд-команде нужно обновить модели.

### 🔴 SMTP: Статус решается локально

```
Production статус: ⏳ Ждём нового SMTP-сервиса от команды бэкенда
Local разработка: ✅ Решено через DEBUG-режим (ошибки логируются, не крашят endpoint)
```

**Решение для локальной разработки:**
- Добавлена конфигурация `DEBUG=true` в `.env`
- При DEBUG=true SMTP ошибки логируются, но не выбрасываются → endpoint возвращает 200 OK
- `debug_code` возвращается в ответе `/auth/register` и `/auth/resend-code` при DEBUG=true для быстрого тестирования

**Ожидаемое решение на production:**
1. Новый сервер/хостинг с настроенным SMTP-сервисом
2. Замена Gmail на профессиональный сервис (Resend, SendGrid, Brevo и т.д.)
3. Обновление `.env` на production

Пользователь **уже записывается в БД** (INSERT + COMMIT в логах) — проблема только в отправке email с кодом.

---

## Важные нюансы (не забыть)

8. **`MAX_VERIFICATION_ATTEMPTS = 5`** — бэкенд блокирует верификацию после 5 неверных попыток. Android должен обрабатывать 400 `"Too many failed attempts"` — показывать сообщение и скрывать поле ввода кода. Учесть в VerifyEmailScreen (будущая задача).

9. **`RESEND_COOLDOWN_SECONDS = 120`** (2 минуты) — кулдаун повторной отправки кода. При 400-ошибке от `/auth/resend-code` тело содержит `"Please wait N seconds before resending"`. На UI — таймер обратного отсчёта 120 секунд.

10. **`VERIFICATION_CODE_EXPIRE_MINUTES = 10`** → `expires_in = 600` секунд в ответе `/auth/register`. Используется для таймера на экране верификации.

1. **`username` vs `nickname`** — в Android domain это `username`, в API БД поле `nickname`. В `RegisterRequestDto` используется `@SerializedName("nickname")`. ✅ Учтено.

2. **`UserPurpose`** — в Android есть, в API нет. Ждём решения от Артёма: сохранять в БД или только клиентское. До ответа не трогать. В `RegisterRequestDto` поле НЕ включено.

3. **`debug_code`** — в ответе `POST /auth/register` сервер возвращает код верификации открытым текстом. Временное поле, убрать до прода. В `RegisterResultDto` не включено. ✅ Учтено.

4. **`resendCode` ответ** — API возвращает объект `{message, expires_at, remaining_seconds}`, не просто число. Уже исправлено: `Result<ResendResult>`. ✅ Учтено.

5. **`confirm_password` обязателен в API** — FastAPI-схема `UserCreate` содержит `confirm_password` как обязательное поле с валидатором. В `RegisterRequestDto` включён. ✅ Расхождение с CONTEXT.md исправлено.

6. **`remaining_seconds` — nullable** — `EmailVerificationResponse.remaining_seconds: Optional[int]` на бэкенде. В `ResendCodeResponseDto` тип `Int?`. ✅ Расхождение исправлено.

7. **`/auth/refresh` — query param, не body** — FastAPI без явного `Body(...)` трактует `refresh_token: str` как query parameter. В `AuthApiService` (МОБ-2.2) использовать `@POST("auth/refresh") suspend fun refreshToken(@Query("refresh_token") token: String): AuthResponseDto`.

11. **`DateVisualTransformation` — подход к форматированию даты** — state хранит только цифры `"04052004"` (max 8), `DateVisualTransformation` добавляет точки только визуально через `OffsetMapping`. `parseBirthDate()` парсит из 8-цифрной строки: `digits[0..1]` — день, `[2..3]` — месяц, `[4..7]` — год. Альтернативный подход (вставлять точки в строку программно) — **содержит баг курсора**, не использовать.

12. **`@Composable` import — не забыть** — При добавлении новых импортов в `RegisterScreen.kt` инструмент не всегда сохраняет `import androidx.compose.runtime.Composable`. Без него компилятор падает с `BackendException: Unresolved annotation type for @Composable` на этапе `kaptGenerateStubsDebugKotlin`. Всегда проверять этот импорт после правок.

13. **`startCooldown` в RegisterViewModel** — при получении ответа от `/auth/register` прередаётся `result.expiresIn` (600 сек), а кулдаун кнопки "Отправить повторно" должен быть `RESEND_COOLDOWN_SECONDS = 120` сек. Требует отдельной проверки/исправления при работе с VerifyEmailScreen.

---

## API (бэкенд, Python/FastAPI)
Репозиторий: `smart-tracker/api`
Эндпоинты авторизации: `POST /auth/register`, `/auth/verify-email`, `/auth/resend-code`, `/auth/login`, `/auth/refresh`
Последний коммит бэка: `make last_name and middle_name optional` (3 марта, автор qerrij)

---

## Конфигурация проекта
- `minSdk = 26` — `java.time` (LocalDate) доступен нативно, desugaring не нужен
- `compileSdk = 35`, `targetSdk = 35`
- `jvmTarget = "17"`
- Все версии библиотек в `gradle/libs.versions.toml`
- Hilt через `kapt` (не KSP — совместимость с Kotlin 1.9.x)

---

## Стиль работы с AI-ассистентом

### Уровень разработчика
- Начинающий в Android/mobile, есть база по Computer Science
- Объяснения — подробные: почему именно так, какие альтернативы существуют, какие подводные камни
- Язык объяснений: русский

### Стандарт качества кода — обязательный чеклист перед каждым файлом

Перед написанием кода — **сверить с бэкендом** (схемы, типы, обязательность полей).

После написания кода — **пройти по каждому пункту**:

**Корректность**
- [ ] Все типы соответствуют реальному API (nullable там, где `Optional` на бэкенде)
- [ ] Направление зависимостей не нарушено: `data` → `domain`, никогда наоборот
- [ ] Нет неявных предположений о данных (числа не кастуются без проверки, строки не обрезаются без явного намерения)
- [ ] `suspend`-функции вызываются только из корутин или других `suspend`-функций

**Безопасность**
- [ ] Нет хранения токенов/паролей в обычных `SharedPreferences` или логах
- [ ] Нет `Log.d(...)` с чувствительными данными (токены, пароли, email)
- [ ] Исключения не проглатываются молча (`catch(e) {}` без обработки)

**Архитектура**
- [ ] Слой `domain` не содержит импортов из `android.*`, `retrofit2.*`, `gson.*`
- [ ] DTO живут только в `data/remote/dto/`, domain-модели — только в `domain/model/`
- [ ] Mapper-функции написаны в правильном направлении (см. нюанс про зависимости)

**Читаемость**
- [ ] Каждый файл содержит KDoc-комментарий с описанием назначения
- [ ] Нетривиальные решения прокомментированы с объяснением "почему так, а не иначе"
- [ ] Имена переменных и функций однозначны и не требуют контекста для понимания

**После написания — явно указать:**
- Что может сломаться при изменении API
- Какие допущения сделаны (например, "предполагаем, что сервер всегда возвращает UTF-8")

---

## Соглашения по коммитам

Формат: `тип(МОБ-X.X): описание на русском языке`

> **Обязательно:** в скобках всегда писать `МОБ-` перед номером задачи.
> Правильно: `feat(МОБ-2.3)` — Неправильно: `feat(-2.3)` или `feat(2.3)`

Примеры:
```
feat(МОБ-2.1): добавлены DTO модели и mappers для auth-слоя
feat(МОБ-2.2): добавлен AuthApiService — Retrofit интерфейс
feat(МОБ-2.4): добавлено хранилище токенов TokenStorage
feat(МОБ-2.3): реализован AuthRepositoryImpl
feat(МОБ-5.1): добавлен Hilt AuthModule
chore: обновлён CONTEXT.md — зафиксированы расхождения с API
fix(МОБ-2.1): исправлена nullable-типизация в ResendCodeResponseDto
```

Типы: `feat` — новая функция, `fix` — исправление, `refactor` — рефакторинг, `chore` — служебное, `docs` — документация.

> **PowerShell + кириллица в коммитах:** `-m "..."` и `[System.IO.File]::WriteAllText` через терминал VS Code — оба обрезают кириллицу внутри скобок (инструмент упрощает команду).
> `Out-File -Encoding utf8` в PS 5.1 добавляет BOM. `create_file` тоже добавляет BOM — не использовать для COMMIT_MSG.
>
> ✅ **Единственный надёжный способ** — Python-скрипт с Unicode-эскейпами (только ASCII в исходнике):
> ```python
> # fix_commit.py
> msg = u'feat(\u041c\u041e\u0411-X.X): \u043e\u043f\u0438\u0441\u0430\u043d\u0438\u0435'
> with open(r'C:\...\mobile\.git\COMMIT_MSG', 'wb') as f:
>     f.write(msg.encode('utf-8'))
> ```
> Затем: `git commit -F .git/COMMIT_MSG` (или `--amend`), удалить файл и скрипт.
> Проверить результат: `python -c "import subprocess; r=subprocess.run(['git','log','--oneline','-1'],capture_output=True); print(r.stdout.decode('utf-8'))"`

---

## Инструменты (MCP серверы подключены)
- **GitHub MCP** — работает, через npx, токен в `mcp.json`
- **Figma MCP** — работает, авторизован как novsmax@gmail.com (план Starter, 6 вызовов/месяц — экономить!)

---

## Структура пакетов
```
com.example.smarttracker/
├── SmartTrackerApp.kt          (@HiltAndroidApp)
├── data/
│   ├── local/                  (✅ TokenStorage.kt + TokenStorageImpl.kt)
│   ├── remote/
│   │   ├── dto/                (✅ 5 DTO + RequestDtos.kt + mappers)
│   │   └── AuthApiService.kt   (✅ Retrofit интерфейс, 5 методов)
│   └── repository/             (✅ AuthRepositoryImpl.kt)
├── di/                         (✅ AuthModule.kt)
├── domain/
│   ├── model/                  (✅ все модели созданы)
│   ├── repository/             (✅ AuthRepository)
│   └── usecase/                (✅ RegisterUseCase)
├── presentation/
│   ├── MainActivity.kt
│   ├── auth/                   (✅ RegisterScreen, RegisterViewModel, RegisterUiState, RegisterEvent)
│   ├── common/
│   ├── navigation/             (✅ Screen.kt + AppNavGraph.kt)
│   └── theme/
│       └── SmartTrackerTheme.kt
└── utils/
```

---

## Отчёт о проделанной работе

### Выполненные задачи

#### Android (реализовано полностью)

| Задача | Файл(ы) | Статус |
|---|---|---|
| МОБ-1.1–1.4 | Domain-слой: модели, репозиторий, UseCase | ✅ |
| МОБ-2.1 | 5 DTO + mappers в `data/remote/dto/` | ✅ |
| МОБ-2.2 | `AuthApiService.kt` — Retrofit, 5 методов | ✅ |
| МОБ-2.3 | `AuthRepositoryImpl.kt` | ✅ |
| МОБ-2.4 | `TokenStorage` + `TokenStorageImpl` (EncryptedSharedPreferences) | ✅ |
| МОБ-5.1 | `AuthModule.kt` — Hilt DI | ✅ |
| МОБ-3.1 | `RegisterScreen.kt` — Compose UI по Figma | ✅ |
| МОБ-3.1 🆕 | UI-улучшения: BackHandler, DatePickerField, DateVisualTransformation, KeyboardCapitalization | ✅ |
| МОБ-4.1–4.3 | `RegisterUiState`, `RegisterViewModel`, `RegisterEvent` | ✅ |
| МОБ-5.2 | `Screen.kt` + `AppNavGraph.kt` + `MainActivity` | ✅ |
| fix | `BASE_URL` → `https://runtastic.gottland.ru/` | ✅ |
| fix | Клиентская валидация: email, пароль, совпадение, код 6 цифр | ✅ |

**Сборка:** BUILD SUCCESSFUL (43 задачи). Commit `380588d` в ветке `main`.
**Последний коммит:** `4d36a58` — `feat(МОБ-3.1): убрать кнопку назад, автоформат даты, DatePickerDialog` (get_errors чист, полный Gradle-билд не запускался)

#### Сервер — устранённые баги (в процессе тестирования)

| Ошибка | Причина | Решение |
|---|---|---|
| TLS Chain validation failed | Неполная цепочка сертификатов | Само исправилось (certbot) |
| HTTP 500 — порт БД | `.env` содержал `POSTGRES_PORT=5434` вместо `5432` | Исправлено в `.env` |
| HTTP 500 — NOT NULL | Схема БД имела NOT NULL на необязательных полях | `ALTER TABLE` для last_name, middle_name, weight, height |

---

### Что мешает дальнейшей работе

#### 🔴 Блокер 1 — SMTP (регистрация не завершается)

Сервер не может отправить email с кодом подтверждения. Причина: Gmail-аккаунт `mgromihala@gmail.com` требует **App Password** вместо обычного пароля (включена 2FA).

**Что нужно сделать участнику команды:**
1. Открыть `myaccount.google.com/apppasswords`
2. Создать App Password → получить 16-символьный код
3. На сервере: `nano /home/mihail/api/.env` → заменить `SMTP_PASSWORD=alfavit13`
4. Перезапустить uvicorn: `kill <pid>` → `nohup uvicorn app.main:app --host 0.0.0.0 --port 8000 &`

> ⚠️ **Долгосрочная рекомендация:** заменить Gmail на профессиональный транзакционный сервис (Resend, SendGrid, Brevo). Gmail ненадёжен для продакшна: суточные лимиты, зависимость от настроек личного аккаунта, риск блокировки.

#### 🟡 Блокер 2 — SQLAlchemy-модели не синхронизированы с БД

`ALTER TABLE` были применены напрямую в PostgreSQL — схема в коде (SQLAlchemy-модели) не обновлена. При следующих миграциях (`alembic`) возможен откат изменений. **Бэкенд-команде нужно обновить модели** (`last_name`, `middle_name`, `weight`, `height` — сделать Optional).

#### 🟡 Блокер 3 — UserPurpose не реализован на бэкенде

В Android-модели есть `UserPurpose` (цель использования), но в API этого поля нет. Поле не включено в `RegisterRequestDto`. Нужно решение от команды: сохранять в БД или оставить только на клиенте.

#### 🟡 Блокер 4 — Экран верификации не реализован

После успешной регистрации navGraph переходит на `verify_email`, но сам экран ввода кода ещё не создан. Без него пользователь зайдёт в тупик.

---

### Нюансы технического характера

- **Uvicorn не управляется systemd** — после перезагрузки сервера процесс не поднимется автоматически. Нужно настроить systemd-сервис.
- **`/auth/refresh` — query param, не body** — FastAPI обрабатывает `refresh_token` как query-параметр, в `AuthApiService.kt` используется `@Query`, не `@Body`.
- **`debug_code` в ответе `/auth/register`** — сервер временно возвращает код верификации открытым текстом. Убрать до выхода в прод.
- **MAX_VERIFICATION_ATTEMPTS = 5** — после 5 неверных кодов аккаунт блокируется. Нужна обработка на UI.
- **RESEND_COOLDOWN = 120 сек** — таймер обратного отсчёта на экране верификации.

---

### Предложение следующих задач

#### Приоритет 1 — разблокировать тестирование
- [ ] **Исправить SMTP** (App Password или замена на Resend) — без этого нельзя завершить регистрацию

#### Приоритет 2 — завершить auth-флоу
- [ ] **VerifyEmailScreen** — экран ввода 6-значного кода с таймером 10 мин, кнопкой "Отправить повторно" (cooldown 2 мин), обработка блокировки после 5 попыток
- [ ] **LoginScreen** (Figma node: `172:640`) — email, пароль, кнопка входа, ссылка на регистрацию
- [ ] **PasswordRecovery** — 3 экрана (Figma nodes: `227:186`, `227:288`, `227:339`): ввод email, ввод кода, новый пароль

#### Приоритет 3 — инфраструктура
- [ ] **Настроить systemd-сервис** для uvicorn — автозапуск после перезагрузки сервера
- [ ] **Синхронизировать SQLAlchemy-модели** с изменениями в БД (nullable-поля)
- [ ] **Alembic-миграция** для фиксации схемы

---

## Результаты дебаггинга (11.03.2026)

Проведён полный аудит кода обоих репозиториев: `smart-tracker/mobile` (Android) и `smart-tracker/api` (FastAPI).

### 🔴 Критические баги

#### API-1: Timezone-naive vs timezone-aware — `TypeError` при сравнении дат
**Файлы:** `app/services/auth.py`, `app/models/email_verification.py`
Модель `EmailVerification` хранит `expires_at` и `created_at` как `DateTime(timezone=True)` — PostgreSQL возвращает timezone-aware объекты. Но сервис везде использует `datetime.now()` (без таймзоны) → `TypeError: can't compare offset-naive and offset-aware datetimes`.
Затронуты: `verify_email()`, `can_resend_code()`, `is_expired()`.
**Исправление:** заменить `datetime.now()` на `datetime.now(timezone.utc)` во всём `app/services/auth.py` и `app/models/email_verification.py`.

#### API-2: Старые коды верификации не инвалидируются
**Файл:** `app/services/auth.py`, методы `register_user()` и `resend_verification_code()`
В обоих методах выполняется `SELECT` по старым неподтверждённым записям `EmailVerification`, но результат нигде не сохраняется и не используется — это мёртвый код. Результат: одновременно существует несколько валидных кодов, все работают до истечения, база засоряется.
**Исправление:** заменить `SELECT` на `DELETE`, либо выставлять `verified_at = now()` для старых записей перед созданием новой.

#### API-3: Blocking SMTP в async event loop
**Файл:** `app/services/email.py`
Метод `_send_email` помечен как `async`, но внутри использует синхронный `smtplib.SMTP`. Блокируется event loop FastAPI на время отправки письма — все остальные запросы подвисают. Параметр `background_tasks: BackgroundTasks` принят в endpoint `/register`, но не используется.
**Исправление:** использовать `aiosmtplib`, обернуть в `asyncio.to_thread()`, или задействовать `background_tasks.add_task()`.

#### ✅ МОБ-1: Cooldown таймер показывает 10 минут вместо 2 — ИСПРАВЛЕНО
**Файл:** `RegisterViewModel.kt`, методы `submitRegistration()` и `onResendCode()`
**Проблема:** вызывалось `startCooldown(result.expiresIn)` — 600с (жизнь кода) вместо 120с (кулдаун повторной отправки)
**Решение:** ✅ добавлена константа `RESEND_COOLDOWN_SECONDS = 120`, обе функции переделаны
**Коммит:** `fix(МОБ-1): cooldown таймер 120с (2 мин) вместо 600с (10 мин)`

### 🟠 Высокие баги

#### API-4: `debug_code` в production-ответе
**Файл:** `app/api/auth.py`, endpoint `/register`
Код верификации возвращается клиенту в теле ответа (`"debug_code": code`). На проде это полностью обходит email-верификацию.
**Исправление:** убрать `"debug_code"` из ответа или возвращать только при `DEBUG=True`.

#### API-5: `sha256_crypt` вместо `bcrypt` для хеширования паролей
**Файл:** `app/core/security.py`
`pwd_context = CryptContext(schemes=["sha256_crypt"], ...)` — `requirements.txt` устанавливает `passlib[bcrypt]`, но код использует значительно более слабый `sha256_crypt`.
**Исправление:** заменить на `schemes=["bcrypt"]`.

### 🟡 Средние баги

#### API-6: Refresh token передаётся как query-параметр
**Файл:** `app/api/auth.py`, endpoint `/refresh`
`refresh_token: str` — FastAPI трактует как query-параметр → токен попадает в URL, логируется в access logs, прокси, кешируется.
**Исправление:** передавать через Pydantic-схему в теле запроса. **Примечание:** Android-клиент (`AuthApiService.kt`) также использует `@Query` — при исправлении API нужно обновить и мобильный клиент на `@Body`.

#### API-7: CORS — `allow_origins=["*"]` с `allow_credentials=True`
**Файл:** `app/main.py`
По спецификации CORS wildcard `*` несовместим с `allow_credentials=True`. Браузеры отклоняют такую конфигурацию.
**Исправление:** указать конкретные origins или убрать `allow_credentials`.

#### API-8: `random.choices` для кода верификации — не криптографически безопасен
**Файл:** `app/core/security.py`
`random` предсказуем. Для security-sensitive кодов нужен `secrets.choice`.

#### МОБ-2: SSL cert chain не настроен для prod
Из tech-debt: сервер `runtastic.gottland.ru` может отдавать неполную TLS-цепочку. В `network_security_config.xml` нет trust-anchor для Let's Encrypt E7. Если проблема на сервере не исправлена — HTTPS-запросы падают с `Chain validation failed`.

### 🔵 Низкие баги

#### API-9: `datetime.utcnow()` deprecated с Python 3.12
**Файл:** `app/core/security.py`
Использовать `datetime.now(timezone.utc)`.

#### API-10: `DATABASE_URL` может быть `None`
**Файл:** `app/database.py`
Если `.env` отсутствует, `DATABASE_URL = None` → крэш при создании engine.

#### API-11: Тест `test_expired_verification_code` не работает
**Файл:** `tests/test_auth.py`
Monkeypatch подменяет `app.services.auth.datetime`, но в модуле используется прямой импорт `from datetime import datetime` — патч не действует.

#### МОБ-3: Мёртвый `catch (DateTimeParseException)` в `parseBirthDate`
**Файл:** `RegisterViewModel.kt`
`LocalDate.of()` бросает `DateTimeException`, а не `DateTimeParseException`. Работает благодаря `catch (e: Exception)` ниже.

### Сводная таблица

| # | Серьёзность | Репо | Описание |
|---|---|---|---|
| API-1 | 🔴 Критический | API | Timezone-naive vs aware → `TypeError` |
| API-2 | 🔴 Критический | API | Старые verification codes не инвалидируются |
| API-3 | 🔴 Критический | API | Blocking SMTP в async event loop |
| МОБ-1 | 🔴 Критический | Mobile | Cooldown 10 мин вместо 2 мин |
| API-4 | 🟠 Высокий | API | `debug_code` в production-ответе |
| API-5 | 🟠 Высокий | API | `sha256_crypt` вместо `bcrypt` |
| API-6 | 🟡 Средний | API | Refresh token в URL query |
| API-7 | 🟡 Средний | API | CORS wildcard + credentials |
| API-8 | 🟡 Средний | API | `random` вместо `secrets` для кодов |
| МОБ-2 | 🟡 Средний | Mobile | SSL cert chain не настроен |
| API-9 | 🔵 Низкий | API | `datetime.utcnow()` deprecated |
| API-10 | 🔵 Низкий | API | `DATABASE_URL = None` → крэш |
| API-11 | 🔵 Низкий | API | Тест expired code патчит не то |
| МОБ-3 | 🔵 Низкий | Mobile | Мёртвый catch `DateTimeParseException` |
