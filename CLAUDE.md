# SmartTracker Android — CLAUDE.md

## Проект
Android-приложение для трекинга тренировок.
GitHub: `smart-tracker/mobile-android`, ветка `main`
Backend API: `https://runtastic.gottland.ru/` (FastAPI + PostgreSQL)
API Docs: https://runtastic.gottland.ru/docs
Версия: `0.2` (versionCode 1)

## Статус проекта: PRODUCTION (не дипломный прототип)
Проект перешёл из статуса дипломной работы в **полноценную разработку
с целью публичного выката**. Следствия — обязательны, без исключений:

- **Никаких послаблений «для диплома сойдёт»** — каждое решение оценивать
  как для боевого продукта с реальными пользователями и их данными.
- **Безопасность — приоритет №1:** утечки токенов/PII, логирование
  чувствительных данных, небезопасные дефолты (backup, exported,
  cleartext) — блокеры, а не «потом починим».
- **Соответствие законодательству РФ обязательно** (149-ФЗ: регистрация
  только через российские почты/ID — реализовано; далее проверять новые
  фичи на 152-ФЗ о персональных данных, особенно GPS-треки = геоданные).
- **Временные решения** (хардкоды, моки, обходы) допустимы только с
  TODO-планом замены и записью в тех-долг (CLAUDE.md TODO).
- **Задачи для бэкенда — только в `BACK_REQ.md`** (канонический список,
  формат BR-N: что/зачем/статус Android/приёмка). В CLAUDE.md и CONTEXT.md —
  лишь ссылки на BR-номера, без дублирования содержания.
- **Тесты обязательны** для новой бизнес-логики; ✅ дыры покрытия закрыты:
  RegisterViewModelTest, ProfileViewModelTest, WorkoutStartViewModelTest
  (recovery-контракт, нюанс 28).
- **Перед релизом:** ✅ минификация + proguard-правила, ✅ подписание (wiring;
  keystore генерирует владелец по `keystore.properties.example`),
  ✅ 152-ФЗ-пакет на клиенте (политика по структуре 152-ФЗ в LegalScreens,
  гео-согласие перед GPS-разрешением, удаление аккаунта проверено),
  ✅ AppMetrica-крашрепортинг (wiring; ключ задаёт владелец).
  Осталось: реквизиты оператора ПДн + юр-проверка текстов (плейсхолдеры
  в LegalScreens.kt), хостинг политики (BR-15), регистрация приложения
  в кабинете AppMetrica + ключ, убрать debug_code на бэке (BR-1),
  ревизия публичности CONTEXT.md, smoke-test release-APK на устройстве
  (логин → тренировка → финиш → история).

---

## Документация — обязательный workflow каждой сессии

Источники истины (в порядке обращения):

| Файл | Что это | Правило |
|---|---|---|
| **PROJECT.md** | Справочник по коду: каждый файл/класс/функция + нюансы | **Искать здесь ПЕРЕД грепом кода** — секции содержат сигнатуры и подводные камни. Проверить хеш в шапке: отстаёт от HEAD → секции изменённых файлов перепроверить по коду |
| **CLAUDE.md** | Правила, конфигурация, критические нюансы 1–29 | Загружается автоматически; новые грабли → новый нюанс N+1 |
| **BACK_REQ.md** | Задачи бэкенда BR-1…BR-14 | Новые требования к бэку — ТОЛЬКО сюда (формат: что/зачем/статус Android/приёмка) |
| **CONTEXT.md** | ⚠️ Архив (март 2026) | Коду НЕ доверять, ничего не записывать |

**После работы (перед коммитом) — чек:**
1. Менялись сигнатуры/файлы/эндпоинты/миграции → обновить секции PROJECT.md,
   хеш в шапке — последним docs-коммитом.
2. Найден неочевидный нюанс → CLAUDE.md «Критические нюансы».
3. Всплыла задача для бэка → BACK_REQ.md новым BR-N.
4. Kotlin-файл добавлен/удалён → структура пакетов в CLAUDE.md.

### Стартовый промпт новой сессии (копировать)

```
Продолжаем SmartTracker Android (PRODUCTION, не диплом — см. CLAUDE.md).
Перед задачей: PROJECT.md — справочник по коду (ищи там до грепа, сверь хеш
в шапке с HEAD), BACK_REQ.md — задачи бэка, CONTEXT.md — архив, не читать
как актуальный. После изменений обнови PROJECT.md/CLAUDE.md/BACK_REQ.md
по правилам секции «Документация» в CLAUDE.md.

Задача: <описание>
```

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
- `presentation` — Jetpack Compose, ViewModels, UiState/Event (MVI-стиль)

**DI:** Hilt через `kapt` (не KSP — несовместимость с Kotlin 1.9.x)
**UI:** Jetpack Compose
**Сеть:** Retrofit + OkHttp с `TokenRefreshAuthenticator` (автообновление токенов на 401)
**Токены:** EncryptedSharedPreferences
**БД:** Room (5 entity, 4 DAO, v8)
**Фоновая работа:** WorkManager (offline-first sync)
**Карты:** MapLibre 11.8.2

---

## Конфигурация
- `applicationId = com.smarttracker.app` (сторы не принимают `com.example.*`;
  после публикации менять НЕЛЬЗЯ). `namespace` остался `com.example.smarttracker` —
  Kotlin-пакеты не переименовывались, в сторы namespace не уходит.
- Release: R8 включён (`isMinifyEnabled + isShrinkResources`), правила — `app/proguard-rules.pro`
  (`Log.v/d/i` вырезаются из байткода). Подписание — из `keystore.properties`
  (вне git, шаблон `keystore.properties.example`); файла нет → неподписанный APK.
- ⚠️ После изменения proguard-правил — smoke-test release-сборки на устройстве:
  логин → тренировка → финиш → история (R8 ломает Gson/Retrofit-рефлексию молча).
- AppMetrica (крашрепортинг): ключ — gradle-property `APPMETRICA_API_KEY`
  (`%USERPROFILE%\.gradle\gradle.properties`, вне репо); пустой ключ → no-op.
  `withLocationTracking(false)` обязателен — геоданные в аналитику не уходят
  (обещание политики конфиденциальности).
- minSdk=26, compileSdk=35, targetSdk=35, jvmTarget="17"
- Gradle: 8.13 (wrapper), AGP: 8.13.2, Kotlin: 1.9.24, Compose Compiler Extension: 1.5.14
- Все версии библиотек — только через `gradle/libs.versions.toml`
- `minSdk=26` → `java.time` (LocalDate) доступен нативно, desugaring не нужен

### Версии ключевых библиотек
| Библиотека | Версия |
|---|---|
| Compose BOM | 2024.10.01 |
| Hilt | 2.51.1 |
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| Room | 2.6.1 |
| MapLibre | 11.8.2 |
| Coil | 2.7.0 |
| WorkManager | 2.9.1 |
| GMS Location | 21.3.0 |
| HMS Location | 6.12.0.300 |
| AppMetrica | 8.3.0 |
| Coroutines | 1.8.1 |
| Security Crypto | 1.1.0-alpha06 |
| DataStore Prefs | 1.1.1 |

---

## Структура пакетов
```
com.example.smarttracker/
├── SmartTrackerApp.kt          (Hilt @HiltAndroidApp)
├── data/
│   ├── cache/
│   │   └── RoleGoalCache.kt   (in-memory, stale-while-revalidate, TTL 1ч)
│   ├── local/
│   │   ├── db/
│   │   │   ├── SmartTrackerDatabase.kt  (Room v8, seeding callback)
│   │   │   ├── entity/  GpsPointEntity, ActivityTypeEntity, PendingFinishEntity,
│   │   │   │            METActivityEntity, MetZoneEntity
│   │   │   ├── dao/     GpsPointDao, ActivityTypeDao, PendingFinishDao, METActivityDao
│   │   │   └── mapper/  ActivityTypeMapper, METMapper
│   │   ├── TokenStorage + TokenStorageImpl     (EncryptedSharedPreferences)
│   │   ├── RoleConfigStorage + RoleConfigStorageImpl
│   │   ├── UserProfileCache + UserProfileCacheImpl  (in-memory)
│   │   └── IconCacheManager                    (filesDir/activity_icons/{id}.png)
│   ├── location/
│   │   ├── LocationTrackingService.kt          (foreground service)
│   │   ├── LocationTrackerFactory.kt           (runtime: GMS/HMS/AOSP)
│   │   ├── RuntimeDetector.kt
│   │   ├── LocationConfig.kt
│   │   ├── OfflineMapManager.kt                (no-op стаб после перехода на raster-тайлы)
│   │   ├── tracker/   LocationTracker (интерфейс), GmsLocationTracker,
│   │   │              HmsLocationTracker, AospLocationTracker
│   │   └── model/     LocationRuntime (enum), TrackLocation, TrackingConfig
│   ├── remote/
│   │   ├── AuthApiService.kt       (auth, password-reset, роли/цели, профиль;
│   │   │                            актуальный список методов — PROJECT.md)
│   │   ├── TrainingApiService.kt   (тренировки; актуальный список — PROJECT.md)
│   │   ├── AuthInterceptor.kt      (buildAuthInterceptor: Bearer только на хост API)
│   │   ├── TokenRefreshAuthenticator.kt
│   │   └── dto/        (DTO + mappers; актуальный состав — PROJECT.md)
│   ├── repository/
│   │   ├── AuthRepositoryImpl.kt
│   │   ├── WorkoutRepositoryImpl.kt
│   │   ├── PasswordRecoveryRepositoryImpl.kt
│   │   ├── AllowedEmailDomainsRepositoryImpl.kt  (149-ФЗ, хардкод до BR-4)
│   │   ├── location/LocationRepositoryImpl.kt    (Room persistence)
│   │   ├── MockPasswordRecoveryRepository.kt     (в DI не используется)
│   │   └── MockWorkoutRepository.kt              (в DI не используется)
│   ├── system/
│   │   └── BatteryOptimizationHelper.kt    (Doze whitelist)
│   └── work/
│       ├── SyncGpsPointsWorker.kt
│       ├── SaveTrainingWorker.kt
│       └── OfflineFinishScheduler.kt       (постановка цепочки, reconcilePending)
├── di/
│   └── AuthModule.kt   (Hilt, единственный модуль; @Provides + @Binds,
│                        актуальный состав — PROJECT.md)
├── domain/
│   ├── model/
│   │   ├── ActiveTrainingResult, ActiveTrainingConflictException
│   │   ├── AuthResult, RegisterRequest, RegisterResult
│   │   ├── ForgotPasswordRequest, ForgotPasswordResult
│   │   ├── ResendResult, ResendResetCodeResult, ResetPasswordRequest, ResetPasswordResult
│   │   ├── NicknameCheckResponse
│   │   ├── User  (id, firstName, lastName, middleName, username, email,
│   │   │         birthDate, gender, weight: Float?, height: Float?)
│   │   ├── Gender (enum), UserPurpose (enum), UserRole (enum)
│   │   ├── Role, RoleResponse, RoleConfig, GoalResponse
│   │   ├── LocationPoint
│   │   ├── METActivity, MetZone
│   │   ├── TrainingHistoryItem
│   │   ├── SaveTrainingResult
│   │   ├── WorkoutType
│   │   ├── NavigationConfig
│   │   ├── NetworkUnavailableException
│   │   └── TrainingAlreadyClosedException
│   ├── repository/
│   │   ├── AuthRepository
│   │   ├── WorkoutRepository
│   │   ├── PasswordRecoveryRepository
│   │   ├── LocationRepository
│   │   └── AllowedEmailDomainsRepository   (149-ФЗ, домены почты для регистрации)
│   ├── usecase/
│   │   ├── CalculateTrainingStatsUseCase   (haversine, инкрементальный расчёт)
│   │   ├── CalorieCalculator               (MET + Харрис-Бенедикт, object)
│   │   ├── LoginUseCase
│   │   └── RegisterUseCase
│   └── validation/
│       └── EmailValidator                  (формат + isAllowedDomain, 149-ФЗ)
├── presentation/
│   ├── MainActivity.kt
│   ├── AppViewModel.kt             (startRoute, logout, reconcilePending)
│   ├── navigation/  AppNavGraph.kt, Screen.kt
│   ├── theme/       SmartTrackerTheme.kt, WorkoutTextStyles.kt, ProfileTextStyles.kt
│   ├── common/      StyledTextField, StepScaffold, SmartTrackerBottomBar,
│   │                UiTokens, DateVisualTransformation, PrimaryButton,
│   │                ProfileAvatarImage, ProfileFieldBox
│   ├── auth/
│   │   ├── login/    LoginScreen, LoginViewModel, LoginUiState, LoginEvent
│   │   ├── register/ RegisterScreen, RegisterViewModel, RegisterUiState,
│   │   │             RegisterEvent, RegisterComponents, LegalScreens
│   │   └── forgot/   ForgotPasswordScreen, ForgotPasswordViewModel,
│   │                 ForgotPasswordUiState, ForgotPasswordEvent
│   ├── calendar/    TrainingHistoryScreen, TrainingHistoryViewModel,
│   │                TrainingHistoryUiState, DayTimelineView, WeekTimelineView,
│   │                MonthTimelineView, CalendarComponents, CalendarConstants,
│   │                CalendarFormatters
│   ├── menu/
│   │   ├── MenuScreen.kt
│   │   └── profile/  ProfileScreen, ProfileViewModel, ProfileUiState,
│   │                 ProfileEditScreen, ProfileEditViewModel, ProfileEditUiState
│   └── workout/
│       ├── WorkoutHomeScreen.kt        (Scaffold + нижний бар)
│       ├── ActivityIcons.kt            (iconKey → drawable res)
│       ├── start/      WorkoutStartScreen, WorkoutStartViewModel
│       ├── summary/    SummaryOverlay, WorkoutSummaryUiState,
│       │               WorkoutSummaryFormatters
│       ├── map/        MapViewComposable, OfflineMapFallback
│       └── permission/ LocationPermissionHandler
└── utils/       ApiErrorHandler (перевод ошибок API на русский),
                 ApiErrorScenarios (справочник форматов ошибок), DurationFormatter
```

---

## Стиль разработчика
- Объяснения подробные: почему так, какие альтернативы, какие подводные камни
- Язык объяснений и комментариев в коде: **русский**
- Уровень: начинающий в Android/mobile, есть база CS

## Стиль ответов (обязательно, каждый ответ)
- Язык: **русский**
- Режим: **caveman full** — без артиклей, фрагменты ОК, короткие синонимы, без воды/флаффа
- Правило: убрать «просто», «на самом деле», «конечно», «отлично», «рад помочь» и любой флафф
- Код и коммиты — без изменений (нормальный стиль)
- Отключение: «stop caveman» или «normal mode»

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

## PROJECT.md — поддержание актуальности

`PROJECT.md` — полный справочник по кодовой базе (каждый файл, класс, функция).
Привязан к коммиту (хеш в шапке). Правила:

- В PR, который **добавляет/удаляет файл** или **меняет публичные сигнатуры**
  (классы, функции, поля моделей/DTO, эндпоинты, миграции Room) — обновить
  соответствующую секцию PROJECT.md и хеш коммита в шапке.
- Правки только тела функции без смены сигнатуры/поведения — PROJECT.md не трогать.
- Формат секции файла: назначение → классы/функции с сигнатурами → особенности.
  Иерархия: `## 2.x` часть → `### пакет` → `#### файл.kt`. Не добавлять H1.
- При крупном рефакторинге (переименование пакетов, >10 файлов) — дешевле
  перегенерировать секцию целиком, чем править точечно.

---

## Критические нюансы (не забыть!)

1. **`username` vs `nickname`** — в domain это `username`, в API это `nickname`.
   В `RegisterRequestDto` → `@SerializedName("nickname")`. ✅

2. **`DateVisualTransformation`** — state хранит только цифры `"04052004"` (max 8),
   трансформация добавляет точки визуально. `parseBirthDate()` парсит из 8-цифрной строки.
   ⚠️ Альтернативный подход (вставлять точки в строку программно) содержит баг курсора — не использовать.

3. **`@Composable` import** — при правках `RegisterScreen.kt` инструмент иногда теряет
   `import androidx.compose.runtime.Composable`. Всегда проверять после правок.

4. **`/auth/refresh`** — `refresh_token` передаётся как JSON-тело (`@Body RefreshTokenRequestDto`).
   FastAPI-роут использует `Body(...)` явно. Подтверждено OpenAPI-схемой (`requestBody: required`).
   `@Query` — было ошибочное предположение, вызывало 422 → 4xx → logout.

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

11. **Auth-интерцептор в OkHttpClient** — `buildAuthInterceptor` (data/remote/AuthInterceptor.kt)
    добавляет `Authorization: Bearer <token>` ТОЛЬКО к запросам на хост API
    (host-check против утечки JWT: клиент общий с Coil/IconCacheManager,
    URL картинок приходят с сервера). Токен читается из `TokenStorage` в момент запроса.
    Публичные эндпоинты своего хоста тоже получают заголовок (сервер игнорирует).

12. **Тайминг `getUserRoles` в `login()` и `verifyEmail()`** — токены СНАЧАЛА сохраняются
    в `TokenStorage`, и только ПОТОМ вызывается `getUserRoles()`. Если сохранить после —
    интерцептор прочитает пустое хранилище → 401. Паттерн: `saveTokens(emptyList)` →
    `getUserRoles()` → `saveTokens(roleIds)`.

13. **`GET /role/user_roles`** — не принимает `@Query("email")`. Использует Bearer-токен.
    В `AuthApiService`: `suspend fun getUserRoles(): List<RoleDto>` (без параметров).

14. **`iconKey` в `WorkoutType`** — это `type_activ_id.toString()` (не название!).
    Маппинг в `activityIconRes()` в `ActivityIcons.kt`: `"1"→бег`, `"2"→сев.ходьба`,
    `"3"→вело`, `"5"→ходьба`. Не использовать название для маппинга — зависит от языка API.

15. **`WorkoutType.imageUrl`** — URL иконки с сервера. Coil использует его напрямую если
    `iconFile == null` (файл ещё не скачан `IconCacheManager`). Цепочка: `iconFile ?: imageUrl ?: activityIconRes(iconKey)`.

16. **Тема приложения** — `Theme.Material3.Light.NoActionBar` (из `com.google.android.material`).
    Старая `android:Theme.Material.Light.NoActionBar` не определяет `R.attr.isLightTheme`,
    что вызывает `Invalid resource ID 0x00000000` при вызове `enableEdgeToEdge()`.

17. **Worktree + коммиты** — в worktree `.git` — это файл, а не директория.
    Python-путь для `COMMIT_MSG`: читать реальный git-dir через `git rev-parse --git-dir`,
    затем писать в `{git-dir}/COMMIT_MSG`.

18. **Расчёт калорий (MET-метод)** — CF = 3.5 / VO2rest; VO2rest зависит от RMR (формула Харриса-Бенедикта).
    Нужны: W (кг), H (см), A (лет), пол. CF не меняется в ходе тренировки.
    Для видов с переменной скоростью: считать E на каждой GPS-точке, суммировать.
    Интерполяция MET между зонами: MET(v) = MET₁ + (v−v₁)×(MET₂−MET₁)/(v₂−v₁)
    `calories` в `GpsPointDto` = ккал за данный интервал (отправлять на сервер в реальном времени).
    `User.weight`/`height` — `Float?`; `CalorieCalculator` принимает `Float`, возвращает `Double`.

19. **MapLibre — удаление логотипа** — `uiSettings.isLogoEnabled = false`. BSD лицензия
    разрешает убирать логотип. OSM-атрибуцию (`attributionEnabled`) убирать нельзя — лицензия ODbL.
    Позиция атрибуции: `attributionGravity = Gravity.TOP or Gravity.END` для fullscreen-режима.

20. **`calculateDeltaDistance` — семантика** — функция в `CalculateTrainingStatsUseCase`
    считает сумму расстояний от `max(0, fromIndex-1)` до **конца** списка, не одну пару.
    В `buildCumulativeData` для одного шага i→i+1 передавать:
    ```kotlin
    calculateDeltaDistance(listOf(points[i - 1], points[i]), 0)
    ```
    Вызов `calculateDeltaDistance(points, i - 1)` вернёт расстояние от i−2 до конца — баг.

21. **`trackPoints` дублирование памяти** — в `onFinishClick` после создания snapshot-а
    `_state.update { it.copy(..., trackPoints = emptyList()) }`. В `WorkoutStartScreen`:
    `trackPoints = summary?.trackPoints ?: state.trackPoints`.
    Это освобождает ~180 КБ на час тренировки (~1800 точек) пока открыт оверлей итогов.

22. **`TokenRefreshAuthenticator`** — OkHttp `Authenticator` (отличается от `Interceptor`!).
    Вызывается автоматически при 401. Запрашивает `/auth/refresh`, сохраняет новые токены,
    повторяет оригинальный запрос. Если refresh тоже 401 — разлогинивает пользователя.

23. **Room DB — миграции** — текущая версия 8. Миграции: v5→v6, v6→v7, v7→v8.
    `fallbackToDestructiveMigration()` включён. При добавлении новой entity обязательно
    писать `Migration(N, N+1)` с ALTER TABLE, иначе при обновлении — деструктивная пересборка.

24. **WorkManager offline sync** — цепочка `SyncGpsPointsWorker` → `SaveTrainingWorker`.
    При отсутствии сети GPS-точки пишутся в `GpsPointEntity`, финиш — в `PendingFinishEntity`.
    Worker запускается при восстановлении сети (`NetworkType.CONNECTED`).
    `LocationRepository` — интерфейс в domain, `LocationRepositoryImpl` — в data (Room).

25. **Маркеры старта/финиша на карте** — `makeMarkerBitmap()` в `MapViewComposable.kt`.
    Composite bitmap: белый круг 32dp + тонкая рамка `ColorPrimary` + иконка 20dp по центру.
    Activity-иконка тинтируется `PorterDuffColorFilter(ColorPrimary, SRC_IN)`.
    Финишный флаг — без тинта (цветная иконка). Добавляются через `SymbolLayer` в MapLibre.

26. **Scrub-маркер только в fullscreen** — `MapViewComposable` не знает о режиме экрана.
    В `WorkoutStartScreen`: `scrubPoint = if (isFullscreen) scrubPoint else null`.
    Передача `null` убирает маркер без изменений в composable.

27. **Multi-provider GPS** — `LocationTrackerFactory` определяет рантайм через `RuntimeDetector`.
    Порядок приоритета: GMS → HMS → AOSP. `LocationTrackingService` — foreground service,
    показывает постоянное уведомление. `LocationRepository` сохраняет точки в Room.

28. **Crash-recovery тренировки — контракт сервис↔ViewModel** — recovery-префы
    (`PREFS_RECOVERY`) пишет ТОЛЬКО сервис (полный контекст: id, chronometer,
    `isRecording`, профиль калорий, gap-индексы, `KEY_IS_REGISTERED`, heartbeat
    каждые ~5 сек). `WorkoutStartViewModel.init` обязан вызывать
    `readRecoverableSession()` ДО `startDiscoveryGps()`: discovery-интент на живой
    восстановленный сервис перезапишет его `trainingId` и молча убьёт запись.
    Протухание — по heartbeat (`RECOVERY_STALE_MS`, 2 мин). Основная ветка
    `onStartCommand` останавливает старый `activeTracker` перед новым — иначе
    двойные GPS-колбэки. `pauseGapIndices` класть в state ДО `observeTrackingData`.

29. **`TokenStorage` — контракт «не бросает» + самовосстановление** —
    EncryptedSharedPreferences кидает RuntimeException (AEADBadTagException при
    повреждённых префах/восстановлении из бэкапа; KeyStoreException после OTA).
    Хранилище вызывается из OkHttp-интерцептора и Authenticator'а: не-IOException
    там OkHttp 4 перебрасывает на dispatcher-потоке → краш процесса на любом
    запросе. Поэтому: (1) все методы реализаций TokenStorage обязаны не бросать —
    сбой = null/emptyList + лог; (2) `TokenStorageImpl` пересоздаёт повреждённое
    хранилище (удаляет файл + master-key, цена — перелогин); (3) интерцептор и
    authenticator дополнительно страхуются try/catch. Тесты:
    AuthTokenFlowTest + TokenRefreshAuthenticatorTest (кейсы «storage бросает»).

30. **Gradle-сборка в worktree** — в свежем worktree сборка падает: `.gitignore`
    исключает `*.jar` и `local.properties`, поэтому нет `gradle/wrapper/gradle-wrapper.jar`
    (`ClassNotFoundException: GradleWrapperMain`) и SDK-пути. Скопировать оба из основного
    чекаута `C:\Users\novsm\Documents\GitHub\mobile-android\`. `JAVA_HOME` в шелле не задан —
    ставить `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` перед gradlew.

---

## Текущие ограничения и временные решения

**Профиль активен** — `ProfileScreen` и `ProfileEditScreen` реализованы.
`PATCH /user/edit` и `GET /user/` работают через `AuthRepositoryImpl`.

**`WorkoutHomeScreen` активен** — маршрут `Screen.Home` ведёт на `WorkoutHomeScreen`.
Вкладка «Тренировки» — история тренировок, pending.

**`WorkoutRepositoryImpl` активирован** — загружает типы активностей из `GET /training/types_activity`.
Иконки кэшируются в `filesDir/activity_icons/{id}.png` через `IconCacheManager`.
`MockWorkoutRepository` остался в коде, но в DI не используется.

**Иконки активностей** — `image_path` возвращается бэкендом. Для некоторых типов
(например, Ходьба id=5) URL может быть недоступен — показывается `placeholder.png`.
На следующий запуск после успешного скачивания иконка отобразится из `filesDir`.

**`PasswordRecoveryRepositoryImpl` активирован** — backend эндпоинты готовы:
`POST /password-reset/request`, `/verify-code`, `/resend-verify-code`, `/confirm`.
`MockPasswordRecoveryRepository` остался в коде, но в DI не используется.

**`AppViewModel`** — определяет `startRoute` при старте приложения (есть токены → Home, нет → Login).
Обрабатывает глобальный logout через SharedFlow.

---

## API эндпоинты (авторизация) — AuthApiService
- `POST /auth/register` → access_token, refresh_token, expires_in
- `POST /auth/verify-email` → access_token, refresh_token
- `POST /auth/resend-code` → message, expires_at, remaining_seconds
- `POST /auth/login` → access_token, refresh_token
- `POST /auth/refresh` → access_token, refresh_token (**refresh_token — JSON-тело, `@Body RefreshTokenRequestDto`; см. нюанс 4. Query-param — старое ошибочное предположение, давало 422**)
- `POST /auth/check-nickname` → is_available
- `GET /role/` → `[{role_id, name, ...}]`
- `GET /role/user_roles` → `[{role_id, name}]` (**Bearer-токен обязателен**)
- `GET /goal/` → `[{goal_id, description, id_role}]`
- `GET /user/` → user info (id, firstName, lastName, email, username, birthDate, gender, weight, height)
- `PATCH /user/edit` → обновлённый user info
- `POST /user/photo` → пусто (multipart, поле "file", jpg/png до 5 МБ; новый image_path — повторным `GET /user/`)
- `DELETE /user/photo` → пусто (бэкенд подставляет плейсхолдер)
- `DELETE /user/delete` → пусто
- `GET /training/types_activity` → живёт в **AuthApiService** (исторически), см. список тренировок ниже

## API эндпоинты (восстановление пароля) — AuthApiService
- `POST /password-reset/request` → `{}` (тело пустое)
- `POST /password-reset/verify-code` → `{}` (тело пустое)
- `POST /password-reset/resend-verify-code` → `{}` (тело пустое)
- `POST /password-reset/confirm` → access_token, refresh_token, token_type

## API эндпоинты (тренировки) — TrainingApiService
- `GET /training/types_activity` → `[{type_activ_id, name, image_path}]`
  - `image_path` — URL иконки (может быть `placeholder.png` для типов без иконки)
  - Публичный эндпоинт, Bearer-токен не нужен (но интерцептор добавит его автоматически)
- `GET /training/met/{type_activ_id}` → `{base_met, uses_speed_zones, zones[]{speed_min, speed_max, met_value}}`
  - `uses_speed_zones=true` → MET зависит от скорости, интерполяция на каждой GPS-точке
  - `uses_speed_zones=false` → использовать `base_met` для всей тренировки
- `POST /training/start` → `{training_id, ...}` — начать тренировку на сервере
- `GET /training/active` → `{training_id, type_activ_id, time_start, ...}` — активная тренировка
- `POST /training/{training_id}/gps_points` → подтверждение сохранения — загрузить пачку GPS-точек
- `POST /training/{training_id}/save_training` → `{training_id, ...}` — завершить тренировку
- `GET /training/history` → `[{training_id, type_activ_id, time_start, time_end, distance, ...}]`
- `GET /training/{training_id}/get_training` → полные данные тренировки + gps_track
  (сейчас GeoJSON без временных меток — формат меняется по BR-5)
- `DELETE /training/{training_id}/delete` → пусто (тестовый, в production-потоке не используется)
- `DELETE /training/{training_id}/delete_completed` → пусто (удаление из истории, вызывается из SummaryOverlay)

---

## Pull Requests
Название — по самому значимому изменению в ветке (не перечислять всё).
Остальные изменения — в описании PR (маркированный список).
Формат названия: `тип(scope): описание` — те же правила что у коммитов.

Пример: ветка содержит fix бага scrubbing + удаление debug-лога + очистку памяти →
- Название: `fix(workout-map): исправить накопление дистанции при scrubbing`
- Описание: удалён ElevationDebug-лог, освобождена память trackPoints в summary-оверлее

---

## Коммиты
Формат: `тип(название-фичи): описание на русском`
Scope — kebab-case: `feat(nickname-validation)`, не `feat(МОБ-2.3)`
Типы: `feat`, `fix`, `refactor`, `chore`, `docs`

**⚠️ PowerShell + кириллица** — `-m "..."` обрезает кириллицу в скобках.
Надёжный способ — Python-скрипт с Unicode-эскейпами:
```python
msg = u'feat(auth-validation): описание'
# В worktree .git — файл, а не директория. Путь к git-dir:
# git rev-parse --git-dir  →  C:/.../.git/worktrees/quirky-satoshi-2d98ed
git_dir = 'C:/Users/novsm/Documents/GitHub/mobile-android/.git/worktrees/quirky-satoshi-2d98ed'
with open(f'{git_dir}/COMMIT_MSG', 'wb') as f:
    f.write(msg.encode('utf-8'))
```
Затем: `git commit -F "<git_dir>/COMMIT_MSG"`

---

## TODO

> Задачи для бэкенда — в **BACK_REQ.md** (BR-1…BR-13). Здесь — только
> Android-часть, которая разблокируется после выполнения BR-задачи.

- **После BR-5 (gps_track с `recorded_at`)** — обновить
  `GetTrainingDetailResponseDto`: убрать `JsonElement?`, вернуть
  `List<GpsTrackPointDto>?`, добавить `recorded_at` в `GpsTrackPointDto`,
  обновить маппер. Разблокирует elapsed/скорость в scrub-оверлее истории
  и экспорт GPX.

- **После BR-4 (`GET /auth/allowed-email-domains`)** — заменить
  `AllowedEmailDomainsRepositoryImpl` на сетевую реализацию с кэшем
  (план в TODO-комментарии внутри impl), хардкод оставить fallback'ом.
  Клиентская часть 149-ФЗ уже реализована: `EmailValidator.isAllowedDomain`,
  проверка только на регистрации (login/recovery не ограничиваются).

---

## Инструменты
- **GitHub MCP** — подключён
- **Figma MCP** — подключён, план Starter (6 вызовов/месяц, экономить!)
