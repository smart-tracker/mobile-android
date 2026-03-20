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

### ✅ Регистрационный поток (E2E тестирование)

- RegisterScreen Step 1-4 (регистрация + верификация email) → HomeScreen ✅ 
  - Step 1: Имя, фамилия, дата рождения
  - Step 2: Пол, цель использования
  - Step 3: Email, пароль, подтверждение пароля + live nickname validation
  - Step 4: Ввод 6-значного кода верификации, таймер 10 мин, кнопка "Отправить повторно" (cooldown 2 мин)
- ✅ LoginScreen — полная реализация с UI, ViewModel, навигацией
- ✅ ForgotPasswordScreen / PasswordRecovery — трёхшаговый процесс восстановления пароля
- ✅ Все API endpoints работают корректно
- ✅ Навигация RegisterScreen → VerifyEmailScreen → HomeScreen функционирует

## Статус бэкенда

### 🔧 Production API

**Статус:** ✅ Разработка ведётся с полноценным production сервером.

**Конфигурация:**
- **Сервер:** FastAPI + PostgreSQL на `https://runtastic.gottland.ru/`
- **API Docs:** https://runtastic.gottland.ru/docs
- **BASE_URL в приложении:** `https://runtastic.gottland.ru/` (через `app/build.gradle.kts`)
- **Android:** Используется production API для всех requests

**Тестирование:**
1. `/auth/register` → ✅ 200 OK (Email отправляется на реальный адрес)
2. `/auth/resend-code` → ✅ 200 OK (Код переотправляется)
3. `/auth/verify-email` → ✅ 200 OK (JWT токены, пользователь авторизован)
4. **Навигация:** RegisterScreen → VerifyEmailScreen → HomeScreen ✅ успешна

---

### � Статус production: Сервер runtastic.gottland.ru

> Сервер: FastAPI + PostgreSQL, uvicorn слушает `0.0.0.0:8000`, SSL терминируется внешним балансировщиком хостинга.
> Uvicorn **не управляется systemd** — перезапуск только вручную от пользователя `mihail`.
> `.env` находится в `/home/mihail/api/.env`.
> API документация доступна по адресу https://runtastic.gottland.ru/docs

**✅ Статус:**
- Регистрация пользователей работает корректно
- Email верификация работает (отправка кодов на реальные адреса)
- JWT авторизация функционирует
- API эндпоинты стабильны

### ✅ Исправлено на сервере (сессия)

| Ошибка | Причина | Решение |
|---|---|---|
| `Chain validation failed` (TLS) | Сервер отдавал только leaf-cert | Само исправилось (certbot обновил конфиг) |
| HTTP 500 — `ConnectionRefusedError: ('127.0.0.1', 5434)` | `.env` имел `POSTGRES_PORT=5434`, PostgreSQL слушает `5432` | `sed -i` в `.env`, заменено на 5432 |
| HTTP 500 — `NotNullViolationError: null value in column "last_name"` | Схема БД имела NOT NULL на необязательных полях | `ALTER TABLE users ALTER COLUMN last_name/middle_name/weight/height DROP NOT NULL` |

> ⚠️ **Важно:** `ALTER TABLE` сделан напрямую в БД — SQLAlchemy-модели на бэке **не синхронизированы**. Бэкенд-команде нужно обновить модели.



## Важные нюансы (не забыть)

8. **`MAX_VERIFICATION_ATTEMPTS = 5`** — бэкенд блокирует верификацию после 5 неверных попыток. Android должен обрабатывать 400 `"Too many failed attempts"` — показывать сообщение и скрывать поле ввода кода. Учесть в VerifyEmailScreen (будущая задача).

9. **`RESEND_COOLDOWN_SECONDS = 120`** (2 минуты) — кулдаун повторной отправки кода. При 400-ошибке от `/auth/resend-code` тело содержит `"Please wait N seconds before resending"`. На UI — таймер обратного отсчёта 120 секунд.

10. **`VERIFICATION_CODE_EXPIRE_MINUTES = 10`** → `expires_in = 600` секунд в ответе `/auth/register`. Используется для таймера на экране верификации.

1. **`username` vs `nickname`** — в Android domain это `username`, в API БД поле `nickname`. В `RegisterRequestDto` используется `@SerializedName("nickname")`. ✅ Учтено.

2. **`UserPurpose` / Цели использования** — ✅ **В API ЕСТЬ полная структура:**
   - Таблицы БД: `roles` (роли), `goal_register` (цели с описанием), `user_and_goal` (связь User↔Goal)
   - Pydantic-схемы: `RoleResponse`, `GoalRegisterResponse`
   - Логика в `app/services/auth.py`: `goal_ids` преобразуются в `role_ids` при регистрации
   - **⚠️ Но endpoints НЕТ:** нет `GET /roles` и `GET /goals` для загрузки списка с клиента
   - **Решение:** Использовать hardcoded список на клиенте (как сейчас) ИЛИ создать endpoints на API

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
- Gradle: 8.6, AGP: **8.13.2** (обновлено 20.03.2026), Kotlin: 1.9.24
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

Формат: `тип(название-фичи): описание на русском языке`

> **Формат scope:** используйте kebab-case для названия фичи (например: `auth-validation`, `date-picker`, `error-handler`).
> Правильно: `feat(nickname-validation)` — Неправильно: `feat(МОБ-2.3)` или `feat(NicknameValidation)`

Примеры:
```
feat(auth-dto): добавлены DTO модели и mappers для auth-слоя
feat(auth-api): добавлен Retrofit интерфейс AuthApiService
feat(token-storage): добавлено хранилище токенов TokenStorage
feat(auth-repo): реализован AuthRepositoryImpl
feat(hilt-di): добавлен Hilt AuthModule
feat(register-ui): stateless Compose UI для RegisterScreen
feat(birth-date-validation): добавлена валидация даты рождения (возраст 6-120 лет)
fix(terms-screen): исправлена ссылка "Условия использования" в AppNavGraph
chore: обновлён CONTEXT.md — новый формат коммитов
```

Типы: `feat` — новая функция, `fix` — исправление, `refactor` — рефакторинг, `chore` — служебное, `docs` — документация.

> **PowerShell + кириллица в коммитах:** `-m "..."` и `[System.IO.File]::WriteAllText` через терминал VS Code — оба обрезают кириллицу внутри скобок (инструмент упрощает команду).
> `Out-File -Encoding utf8` в PS 5.1 добавляет BOM. `create_file` тоже добавляет BOM — не использовать для COMMIT_MSG.
>
> ✅ **Единственный надёжный способ** — Python-скрипт с Unicode-эскейпами (только ASCII в исходнике):
> ```python
> # fix_commit.py
> msg = u'feat(auth-validation): \u043e\u043f\u0438\u0441\u0430\u043d\u0438\u0435'
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

### Готовый функционал

#### Android Application (✅ BUILD SUCCESSFUL)

**Архитектура и DI:**
- ✅ Domain-слой (Clean Architecture)
  - `User.kt`, `RegisterRequest.kt`, `AuthResult.kt`, `RegisterResult.kt`, `ResendResult.kt`, `NicknameCheckResponse.kt` — domain модели
  - `AuthRepository` — интерфейс с методами: `register()`, `verifyEmail()`, `resendCode()`, `login()`, `refreshToken()`, `checkNickname()`
  - `RegisterUseCase.kt` — валидация (regex, без android.util)

- ✅ Data-слой
  - `data/remote/dto/` — 5 DTO файлов с mappers: `RegisterRequestDto`, `AuthResponseDto`, `ResendCodeResponseDto`, `ErrorResponseDto`, `NicknameCheckResponseDto`
  - `AuthApiService.kt` — Retrofit интерфейс с 6 методами (register, verify-email, resend-code, login, refresh, check-nickname)
  - `TokenStorage.kt` + `TokenStorageImpl.kt` — EncryptedSharedPreferences для хранения JWT токенов
  - `AuthRepositoryImpl.kt` — реализация бизнес-логики

- ✅ Dependency Injection
  - `AuthModule.kt` — Hilt конфигурация: OkHttpClient, Retrofit instance, AuthApiService binding, TokenStorage binding

**Presentation-слой (Jetpack Compose):**
- ✅ RegisterScreen (4-шаговая регистрация)
  - Step 1: Имя, фамилия, дата рождения (DatePickerDialog с форматированием DD.MM.YYYY)
    - **DateVisualTransformation** — состояние хранит 8 цифр (`04052004`), визуально отображает `04.05.2004`
    - Валидация: возраст 6-120 лет, дата не в будущем, правильный формат
    - Визуальный feedback: зелёная/красная рамка поля + иконки ✓/✗
  - Step 2: Пол (RadioButtons), цель использования (Checkboxes)
    - Чёрная граница вокруг чекбокса при выборе, бирюзово-зелёный фон (#4DACA7)
  - Step 3: Email, пароль, подтверждение, никнейм + Legal links
    - **Live nickname validation** с debounce 700ms, иконки ✓/✗, кеширование результатов (min 3 символа)
    - ClickableText с ссылками на "Условия использования" и "Политика конфиденциальности"
  - Step 4: Ввод 6-значного кода верификации, таймер 10 мин, кнопка "Отправить повторно" (cooldown 120с)
    - Таймер обратного отсчёта отображается в формате `MM:SS`
    - Кнопка "Отправить повторно" отключена во время cooldown'а
  - `RegisterViewModel.kt` + `RegisterUiState.kt` + `RegisterEvent.kt` — state management
  - **role_ids**: отправляются в виде массива `[1]` или пусты для EXPLORING/OTHER целей

- ✅ LoginScreen (сессия 17.03.2026)
  - Email и пароль с валидацией
  - Кнопка "Войти" и ссылка "Забыли пароль?"
  - Социальные кнопки (Yandex, VK, Max) с иконками
  - Полный рефакторинг UI: граница 2dp, placeholder вместо label

- ✅ PasswordRecoveryScreen (ForgotPasswordScreen) — полностью реализован и интегрирован
  - Step 1: Ввод email для восстановления
  - Step 2: Ввод нового пароля + подтверждение
  - Step 3: Ввод 6-значного кода верификации, таймер, resend cooldown
  - `ForgotPasswordViewModel.kt` + `ForgotPasswordUiState.kt` + `ForgotPasswordEvent.kt`
  - Навигация из LoginScreen через "Забыли пароль?" ссылку
  - PasswordRecoveryRepository интегрирован с API

- ✅ Navigation
  - `Screen.kt` — sealed class с маршрутами (registerScreen, loginScreen, homeScreen, termsOfService, privacyPolicy, passwordRecovery)
  - `AppNavGraph.kt` — NavHost с переходами, LaunchedEffect для обработки навигационных событий
  - `MainActivity.kt` — инициализация с Hilt DI

- ✅ Legal Screens
  - `TermsOfServiceScreen` — полный текст "Условия использования" с TopAppBar и прокруткой
  - `PrivacyPolicyScreen` — полный текст "Политика конфиденциальности" с TopAppBar и прокруткой
  - Доступны по ClickableText ссылкам на RegisterStep3

- ✅ HomeScreen (МОБ-6) — динамический BottomNav
  - Генерируется на основе ролей пользователя (ATHLETE, TRAINER, CLUB_OWNER)
  - Навигация: Home, MyWorkouts (атлеты), MyAthletes (тренеры), MyClub (владельцы), Profile (все)
  - `HomeViewModel.kt` загружает NavigationConfig из RoleConfigStorage
  - `NavigationConfig.kt` + `BottomNavItem` + `RoleConfig.kt` — модели конфигурации

**UI & Styling:**
- ✅ SmartTrackerTheme
  - `FontFamily` для Geologica (Light, Regular, Italic) — встроены в `app/src/main/res/font/`
  - `SmartTrackerTypography` с 5 TextStyle: titleLarge (32px italic), headlineSmall (20px light), labelLarge (20px light), bodyMedium (16px), bodySmall (14px)
  - Цветовая схема: Primary=#0A1928, Background=white, Placeholder=#525760
  - 2dp border radius для текстовых полей, 10dp для кнопок

- ✅ Ресурсы
  - Шрифты встроены: `geologica_light.ttf`, `geologica_regular.ttf`, `geologica_italic.ttf`
  - Иконки социальных сетей: `ic_yandex.png`, `ic_vk.png`, `ic_max.png`

**Error Handling & Validation:**
- ✅ ApiErrorHandler
  - Парсинг HTTP-ошибок (JSON responses)
  - Автоперевод с английского на русский (карта errorTranslations)
  - Категоризация: USERNAME_TAKEN, EMAIL_TAKEN, PASSWORD_ERROR, TOO_MANY_ATTEMPTS, RESEND_COOLDOWN, GENERIC
  - User-friendly сообщения вместо raw HTTP errors

- ✅ Клиентская валидация
  - Email: `android.util.Patterns.EMAIL_ADDRESS`
  - Password: минимум 8 символов
  - Confirm password: совпадение с password
  - Verification code: ровно 6 символов
  - Nickname: минимум 3 символа (перед API-вызовом)
  - **Birth date**: возраст 6-120 лет, дата не в будущем, формат DD.MM.YYYY (ровно 8 цифр)

**API Integration:**
- ✅ Production API: `https://runtastic.gottland.ru/` (через BuildConfig.BASE_URL)
- ✅ 6 endpoints:
  - `POST /auth/register` → returns access_token, refresh_token, expires_in
  - `POST /auth/verify-email` → returns access_token, refresh_token
  - `POST /auth/resend-code` → returns message, expires_at, remaining_seconds
  - `POST /auth/login` → returns access_token, refresh_token
  - `POST /auth/refresh` → returns access_token, refresh_token
  - `POST /auth/check-nickname` → returns is_available

**Project Configuration:**
- ✅ Gradle: 8.6, AGP: **8.13.2**, Kotlin: 1.9.24
- ✅ SDK: minSdk=26, compileSdk=35, targetSdk=35, jvmTarget="17"
- ✅ Все версии библиотек в `gradle/libs.versions.toml`
- ✅ Hilt через `kapt` (не KSP)
- ✅ SSL certificate chain — обойдено через `network_security_config.xml`

**Сборка:** ✅ BUILD SUCCESSFUL

---

### Детали реализации HomeScreen (Фаза 0 и 1 — МОБ-6)

**Фаза 0 — Domain models для динамического BottomNav (МОБ-6.1)**
- ✅ `domain/model/NavigationConfig.kt` (BottomNavItem, NavigationConfig, DrawerItem)
- ✅ `domain/model/RoleConfig.kt` (getNavigationConfig, константы ROLE_ATHLETE/TRAINER/CLUB_OWNER)
- ✅ `presentation/navigation/Screen.kt` (новые экраны: MyWorkouts, MyAthletes, MyClub, Profile, TermsOfService, PrivacyPolicy)

**Фаза 1 — TokenStorage + Роли (МОБ-6.1)**
- ✅ `data/local/TokenStorage.kt` (интерфейс: saveTokens(roleIds), getUserRoles(), clearAll())
- ✅ `data/local/TokenStorageImpl.kt` (реализация: сохранение roleIds как "1,2,3" в EncryptedSharedPreferences)
- ✅ `data/remote/AuthApiService.kt` (endpoints: check-nickname, role/user_roles)
- ✅ `data/remote/dto/RoleDto.kt` (новый DTO с маппером toDomain())
- ✅ `domain/model/Role.kt` (новая domain модель для ролей)
- ✅ `data/repository/AuthRepositoryImpl.kt`:
  - `verifyEmail()` — загружает роли после верификации email
  - `login()` — загружает роли при входе
  - `refreshToken()` — сохраняет текущие роли при обновлении токенов

---

### Валидация и проверки (Фаза 20.03.2026)
- ✅ `domain/model/BirthDateCheckStatus.kt` (IDLE, SUCCESS, ERROR состояния)
- ✅ `domain/model/UserPurpose.kt` + `toRoleId()` маппер (ATHLETE→1, TRAINER→2, CLUB_OWNER→3)
- ✅ `RegisterViewModel.validateBirthDate()`:
  - Формат проверки (ровно 8 цифр DD.MM.YYYY)
  - Дата не в будущем
  - Возраст ≥ 6 лет, ≤ 120 лет
  - Визуальный feedback: цветная рамка + иконки ✓/✗
- ✅ `RegisterRequestDto` отправляет `role_ids: List<Int>` вместо role_id
- ✅ `TermsOfServiceScreen` и `PrivacyPolicyScreen` — полноценные экраны с документами

---

**Сборка:** ✅ BUILD SUCCESSFUL

---

#### 🟠 Нюансы PasswordRecovery (уже реализованы)

PasswordRecovery полностью интегрирован с API:
- **MAX_VERIFICATION_ATTEMPTS = 5** — обработка блокировки после 5 ошибок (ошибка `"Too many failed attempts"`)
- **RESEND_COOLDOWN_SECONDS = 120** — таймер обратного отсчёта между отправками кодов
- **VERIFICATION_CODE_EXPIRE_MINUTES = 10** → `expires_in = 600` секунд (таймер на UI)
- Валидация: email (формат), пароль (8+ символов), код (ровно 6 цифр)
- Обработка ошибок через `ApiErrorHandler` (перевод на русский)

---

### Следующие задачи разработки

#### Приоритет 1 — Основной функционал
- [ ] **HomeScreen** / Dashboard — начальный экран после входа
- [ ] **ProfileScreen** — редактирование профиля, выход
- [ ] **SettingsScreen** — настройки приложения

---

## Известные баги и нюансы Backend API

**Статус:** Разработка ведётся с production API на `https://runtastic.gottland.ru/`. Некоторые баги из первоначального аудита могут быть актуальны для API.

### Критические для мониторинга

1. **Timezone handling** — API работает с UTC, убедиться, что `expires_at` и `created_at` корректно сравниваются на backend
2. **Старые verification codes** — при повторной отправке кода старые должны инвалидироваться (DELETE или SET verified_at)
3. **Blocking SMTP** — если отправка email занимает 3-10 сек, может блокировать event loop

### Высокие баги для исправления (если на API)

- **API-4:** `debug_code` в response `/register` должен отсутствовать в production (только при DEBUG=true)
- **API-5:** Проверить, что хеширование паролей использует `bcrypt`, а не `sha256_crypt`
- **API-6:** `/auth/refresh` передаёт `refresh_token` как query param (текущая реализация Android-клиента соответствует)
- **API-7:** CORS конфигурация — проверить, что не используется `allow_origins=["*"]` с `allow_credentials=True`
- **API-8:** Коды верификации должны генерироваться через `secrets`, а не `random`
