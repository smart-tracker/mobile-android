# SmartTracker Android — CLAUDE.md

## Проект
Android-приложение для трекинга тренировок.
GitHub: `smart-tracker/mobile-android`, ветка `main`
Backend API: `https://runtastic.gottland.ru/` (FastAPI + PostgreSQL)
API Docs: https://runtastic.gottland.ru/docs

---

## Команды
- Сборка: `./gradlew assembleDebug`
- Тесты: `./gradlew test`
- Запуск на устройстве: `./gradlew installDebug`

---

## Архитектура
**Clean Architecture:** `domain` → `data` → `presentation`
- `domain` — только Kotlin, **никаких** `android.*`, `retrofit2.*`, `gson.*`
- `data` — DTO в `data/remote/dto/`, репозитории в `data/repository/`
- `presentation` — Jetpack Compose, ViewModels, UiState/Event

**DI:** Hilt через `kapt` (не KSP — несовместимость с Kotlin 1.9.x)
**UI:** Jetpack Compose
**Сеть:** Retrofit
**Токены:** EncryptedSharedPreferences

---

## Конфигурация
- minSdk=26, compileSdk=35, targetSdk=35, jvmTarget="17"
- Gradle: 8.6, AGP: 8.13.2, Kotlin: 1.9.24
- Все версии библиотек — только через `gradle/libs.versions.toml`
- `minSdk=26` → `java.time` (LocalDate) доступен нативно, desugaring не нужен

---

## Структура пакетов
```
com.example.smarttracker/
├── data/
│   ├── local/          (TokenStorage + TokenStorageImpl)
│   ├── remote/
│   │   ├── dto/        (DTO + mappers)
│   │   └── AuthApiService.kt
│   └── repository/     (AuthRepositoryImpl, MockWorkoutRepository)
├── di/                 (AuthModule.kt)
├── domain/
│   ├── model/          (WorkoutType и др.)
│   ├── repository/     (интерфейсы: AuthRepository, WorkoutRepository)
│   └── usecase/
├── presentation/
│   ├── auth/
│   │   ├── login/
│   │   ├── register/
│   │   └── forgot/
│   ├── navigation/     (Screen.kt + AppNavGraph.kt)
│   ├── theme/
│   └── workout/
│       ├── WorkoutHomeScreen.kt   (Scaffold + нижний бар: Старт/Тренировки/Меню)
│       └── start/
│           ├── WorkoutStartScreen.kt
│           └── WorkoutStartViewModel.kt
└── utils/
```

---

## Стиль разработчика
- Объяснения подробные: почему так, какие альтернативы, какие подводные камни
- Язык объяснений и комментариев в коде: **русский**
- Уровень: начинающий в Android/mobile, есть база CS

---

## Обязательный чеклист перед каждым файлом

**Перед кодом** — сверить схемы с backend API (типы, nullable, обязательность полей).

**После кода** пройти по каждому пункту:

**Корректность**
- [ ] Все типы соответствуют API (nullable там, где `Optional` на бэкенде)
- [ ] Направление зависимостей не нарушено: `data` → `domain`, никогда наоборот
- [ ] Нет неявных предположений о данных
- [ ] `suspend`-функции вызываются только из корутин

**Безопасность**
- [ ] Токены/пароли не в обычных SharedPreferences и не в логах
- [ ] Нет `Log.d(...)` с чувствительными данными
- [ ] Исключения не проглатываются молча

**Архитектура**
- [ ] `domain` не содержит `android.*`, `retrofit2.*`, `gson.*`
- [ ] DTO — только в `data/remote/dto/`, domain-модели — только в `domain/model/`
- [ ] Mapper-функции в правильном направлении

**Читаемость**
- [ ] KDoc-комментарий на каждом файле
- [ ] Нетривиальные решения прокомментированы с "почему так"
- [ ] Имена переменных однозначны

**После написания явно указать:**
- Что может сломаться при изменении API
- Какие допущения сделаны

---

## Критические нюансы (не забыть!)

1. **`username` vs `nickname`** — в domain это `username`, в API это `nickname`.
   В `RegisterRequestDto` → `@SerializedName("nickname")`. ✅

2. **`DateVisualTransformation`** — state хранит только цифры `"04052004"` (max 8),
   трансформация добавляет точки визуально. `parseBirthDate()` парсит из 8-цифрной строки.
   ⚠️ Альтернативный подход (вставлять точки в строку программно) содержит баг курсора — не использовать.

3. **`@Composable` import** — при правках `RegisterScreen.kt` инструмент иногда теряет
   `import androidx.compose.runtime.Composable`. Всегда проверять после правок.

4. **`/auth/refresh`** — `refresh_token` передаётся как `@Query`, не `@Body`.
   FastAPI трактует его как query param без явного `Body(...)`.

5. **`MAX_VERIFICATION_ATTEMPTS = 5`** — бэкенд блокирует после 5 неверных попыток.
   Android должен обрабатывать 400 `"Too many failed attempts"` и скрывать поле ввода.

6. **`RESEND_COOLDOWN_SECONDS = 120`** — при 400 от `/auth/resend-code` тело содержит
   `"Please wait N seconds"`. UI — таймер 120 секунд.

7. **`debug_code`** — `/auth/register` возвращает код верификации открытым текстом.
   В `RegisterResultDto` не включать. Убрать до прода.

8. **`remaining_seconds` — nullable** — `Optional[int]` на бэкенде → `Int?` в DTO.

9. **`startCooldown` в RegisterViewModel** — `result.expiresIn` = 600 сек (таймер верификации),
   кулдаун кнопки "Отправить повторно" = 120 сек (отдельная константа).

10. **Legal-блок регистрации** — использовать `LinkAnnotation`, не `ClickableText` (deprecated).

---

## Текущие ограничения и временные решения

**`WorkoutHomeScreen` активен** — маршрут `Screen.Home` ведёт на `WorkoutHomeScreen`.
Вкладки «Тренировки» и «Меню» — заглушки (`PlaceholderScreen`), реализация pending.

**`MockWorkoutRepository`** — временно используется вместо реальной реализации.
`WorkoutRepositoryImpl` нужно реализовать после появления backend-эндпоинтов для типов тренировок.
Активировать через DI в `AuthModule` (заменить `bindWorkoutRepository`).

**`PasswordRecoveryRepositoryImpl` активирован** — backend эндпоинты готовы:
`POST /password-reset/request`, `/verify-code`, `/resend-verify-code`, `/confirm`.
`MockPasswordRecoveryRepository` остался в коде, но в DI не используется.

---

## API эндпоинты (авторизация)
- `POST /auth/register` → access_token, refresh_token, expires_in
- `POST /auth/verify-email` → access_token, refresh_token
- `POST /auth/resend-code` → message, expires_at, remaining_seconds
- `POST /auth/login` → access_token, refresh_token
- `POST /auth/refresh` → access_token, refresh_token (**query param!**)
- `POST /auth/check-nickname` → is_available

## API эндпоинты (восстановление пароля)
- `POST /password-reset/request` → `{}` (тело пустое)
- `POST /password-reset/verify-code` → `{}` (тело пустое)
- `POST /password-reset/resend-verify-code` → `{}` (тело пустое)
- `POST /password-reset/confirm` → access_token, refresh_token, token_type

---

## Коммиты
Формат: `тип(название-фичи): описание на русском`
Scope — kebab-case: `feat(nickname-validation)`, не `feat(МОБ-2.3)`
Типы: `feat`, `fix`, `refactor`, `chore`, `docs`

**⚠️ PowerShell + кириллица** — `-m "..."` обрезает кириллицу в скобках.
Надёжный способ — Python-скрипт с Unicode-эскейпами:
```python
msg = u'feat(auth-validation): описание'
with open(r'.git\COMMIT_MSG', 'wb') as f:
    f.write(msg.encode('utf-8'))
```
Затем: `git commit -F .git/COMMIT_MSG`

---

## Инструменты
- **GitHub MCP** — подключён
- **Figma MCP** — подключён, план Starter (6 вызовов/месяц, экономить!)
