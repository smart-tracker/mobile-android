# SmartTracker Android — PROJECT.md

Полное техническое описание кодовой базы: структура проекта, конфигурация сборки и
подробная документация по каждому файлу, каждому классу/интерфейсу и каждой функции.

Документ сгенерирован автоматическим обходом всех Kotlin-файлов проекта (main, test, androidTest)
по состоянию на 2026-07-09, коммит `99081eb`, ветка `claude/smarttracker-local-server-86c555`.

---

## Оглавление

- **1. Обзор проекта**
  - Возможности
  - Архитектура
  - Конфигурация сборки
  - AndroidManifest.xml — ключевые моменты
  - Структура пакетов
  - Прочие файлы репозитория
- **2. Документация по файлам (по пакетам)**
- **2.1 domain/ — доменный слой**
  - domain/model
  - domain/repository
  - domain/usecase
  - domain/validation
  - Тесты domain-слоя
  - Итоги по слою domain
- **2.2 data/local/ и data/cache/ — локальное хранилище, Room, кэши**
  - data/local
  - data/local/db
  - data/cache
  - Тесты data/local (androidTest)
  - Итоги по data/local и data/cache
- **2.3 data/remote/ — Retrofit API и DTO**
  - Retrofit-сервисы
  - DTO (data/remote/dto/)
  - Тесты data/remote
  - Итоги по data/remote
- **2.4 data/repository/, data/work/, data/location/, data/system/, di/**
  - data/repository
  - data/work
  - data/location
  - data/system
  - di
  - Тесты data/repository и data/work
  - Итоги по data/repository, data/work, data/location, di
- **2.5 presentation/ — корень, auth/, common/, theme/, navigation/, menu/**
  - Корень presentation
  - presentation/auth/forgot
  - presentation/auth/login
  - presentation/auth/register
  - presentation/common
  - presentation/theme
  - presentation/navigation
  - presentation/menu
  - Тесты presentation
  - Итоги по presentation (auth/common/theme/navigation/menu)
- **2.6 presentation/workout/, presentation/calendar/, utils/**
  - presentation/workout
  - presentation/calendar
  - utils
  - Тесты utils
  - Итоги по presentation/workout, presentation/calendar, utils

---

## 1. Обзор проекта

Android-клиент для трекинга спортивных тренировок (дипломный проект, ПетрГУ, командная разработка).
Организация на GitHub: `smart-tracker`, репозиторий `smart-tracker/mobile-android`, ветка `main`.

**Backend:** FastAPI + PostgreSQL, `https://runtastic.gottland.ru/` (docs: `/docs`).
**Версия приложения:** `0.2` (versionCode 1), `applicationId = com.smarttracker.app`
(сменён с `com.example.smarttracker` — сторы не принимают `com.example.*`;
`namespace` остался `com.example.smarttracker`, Kotlin-пакеты не переименовывались).

### Возможности
- Запись тренировки с GPS-треком в реальном времени, отображение маршрута на карте (MapLibre).
- Фоновая запись при свёрнутом приложении / заблокированном экране (Foreground Service).
- Offline-first: GPS-точки и завершённые тренировки копятся локально (Room) и синхронизируются
  с бэкендом через WorkManager при восстановлении сети.
- История тренировок с разбивкой по дням/неделям/месяцам, сводка (дистанция, время, калории по MET).
- Авторизация, регистрация (4 шага + email-верификация), восстановление пароля, профиль пользователя.
- Мультиплатформенный GPS: единый интерфейс `LocationTracker` с реализациями под Google (GMS),
  Huawei (HMS) и чистый AOSP — выбор в рантайме через `RuntimeDetector`.
- Итоги тренировки: километровые сплиты и график скорости/высоты (панель «Детали»),
  шаринг картинкой (снимок карты + статистика / карточка с силуэтом маршрута без геопривязки).
- Автопауза по скорости GPS (гистерезис, опционально) и голосовые подсказки TTS
  на километровых рубежах — обе фичи живут в foreground-сервисе, работают с погашенным экраном.
- Экран настроек (DataStore Preferences): автопауза, голосовые подсказки + частота,
  «не гасить экран во время тренировки».

### Архитектура

Clean Architecture в три слоя, зависимости направлены только внутрь:

```
presentation → data → domain
```

- **`domain`** — чистый Kotlin, без `android.*`, `retrofit2.*`, `gson.*`. Модели, интерфейсы
  репозиториев, use-case'ы, валидаторы.
- **`data`** — реализации репозиториев, Retrofit-сервисы + DTO (`data/remote/dto/`), Room
  (сущности/DAO/мапперы), локальное хранилище (EncryptedSharedPreferences), WorkManager-воркеры,
  GPS-трекеры.
- **`presentation`** — Jetpack Compose UI, ViewModel'и в MVI-стиле (UiState + Event/SharedFlow).

**DI:** Hilt через `kapt` (не KSP — несовместимость с используемой версией Kotlin 1.9.x).
**UI:** Jetpack Compose + Material 3, Navigation Compose.
**Сеть:** Retrofit + OkHttp, `TokenRefreshAuthenticator` (автообновление JWT на 401).
**Токены:** EncryptedSharedPreferences (`androidx.security.crypto`).
**БД:** Room, версия схемы 8 (5 entity, 4 DAO).
**Фоновая работа:** WorkManager, offline-first синхронизация (`SyncGpsPointsWorker` → `SaveTrainingWorker`).
**Карты:** MapLibre 11.8.2, тайлы OpenFreeMap (без API-ключа).
**Тесты:** JUnit, Mockito-Kotlin, MockWebServer, Robolectric, Room-testing, WorkManager-testing.

### Конфигурация сборки

| Параметр | Значение |
|---|---|
| `namespace` | `com.example.smarttracker` (внутренний package для R/BuildConfig, в сторы не уходит) |
| `applicationId` | `com.smarttracker.app` (идентификатор в сторах; после публикации менять нельзя) |
| Минификация release | `isMinifyEnabled = true` + `isShrinkResources = true` (R8, правила — `app/proguard-rules.pro`) |
| Подписание release | `signingConfigs.release` из `keystore.properties` (вне git); файла нет → неподписанный APK |
| `minSdk` | 26 (⇒ `java.time`/`LocalDate` нативно, desugaring не нужен) |
| `compileSdk` / `targetSdk` | 35 |
| `versionCode` / `versionName` | 1 / `0.2` |
| `jvmTarget` | 17 |
| Gradle | 8.13 (wrapper; в CLAUDE.md ранее ошибочно значился 8.6) |
| AGP | 8.13.2 |
| Kotlin | 1.9.24 |
| Compose Compiler Extension | 1.5.14 |
| Compose BOM | 2024.10.01 |

Ключевые зависимости (полный список версий — `gradle/libs.versions.toml`): Hilt 2.51.1,
Retrofit 2.11.0, OkHttp 4.12.0 (BOM), Coroutines 1.8.1, Room 2.6.1, MapLibre 11.8.2,
Coil 2.7.0, WorkManager 2.9.1, GMS Location 21.3.0, HMS Location 6.12.0.300,
AppMetrica 8.3.0, Security Crypto 1.1.0-alpha06, DataStore Preferences 1.1.1,
Robolectric 4.13.

`APPMETRICA_API_KEY` — gradle-property (вне репозитория:
`%USERPROFILE%\.gradle\gradle.properties` или `-P` в CI) → `BuildConfig.APPMETRICA_API_KEY`;
пустое значение → AppMetrica не инициализируется.

`BASE_URL` задаётся через `buildConfigField` в `app/build.gradle.kts`, debug и release
различаются. Release — prod `https://runtastic.gottland.ru/`. Debug — **ВРЕМЕННО**
локальный API-сервер: по умолчанию `http://10.0.2.2:8000/` (эмулятор), для физического
устройства — override gradle-property `-PLOCAL_API_URL=http://<LAN-IP>:8000/`.
Cleartext для локальных хостов разрешён только в debug —
`app/src/debug/res/xml/network_security_config.xml` (полностью перекрывает main-версию
ресурса). TODO(local-api): вернуть prod-URL в debug и удалить debug-оверлей конфига.

`settings.gradle.kts` подключает дополнительный Maven-репозиторий Huawei
(`https://developer.huawei.com/repo/`) — обязателен для HMS Location SDK.

### AndroidManifest.xml — ключевые моменты
- Разрешения: `INTERNET`, `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`,
  `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`,
  `POST_NOTIFICATIONS`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `CAMERA`.
- `SmartTrackerApp` — Application-класс (`@HiltAndroidApp`), также реализует
  `Configuration.Provider` для ленивой инициализации WorkManager со своей `HiltWorkerFactory`
  (автозапуск через `androidx.startup` отключён — иначе `@HiltWorker` падает с
  `NoSuchMethodException`, т.к. дефолтная `WorkerFactory` инициализируется раньше `Application.onCreate()`).
- `MainActivity` — единственная Activity, `launchMode="singleTop"`, портретная ориентация.
- `LocationTrackingService` — `foregroundServiceType="location"`, `stopWithTask="false"`
  (сервис не убивается при смахивании приложения из Recents на Samsung/Xiaomi и т.п.).
- `FileProvider` — для передачи URI камеры при съёмке фото профиля (`file_paths.xml`).
- `android:allowBackup="false"` — облачный/ADB-бэкап отключён: иначе в бэкап уезжает
  Room-база с GPS-треками (история перемещений открытым текстом) и
  EncryptedSharedPreferences (нечитаемы без Keystore-ключа, но крешат восстановление).
  Все значимые данные синхронизируются с сервером.
- `network_security_config.xml` — разрешает cleartext ТОЛЬКО для `10.0.2.2`
  (localhost эмулятора); все остальные соединения — строго HTTPS.

### Структура пакетов

```
com.example.smarttracker/
├── SmartTrackerApp.kt
├── data/
│   ├── cache/            RoleGoalCache (in-memory, stale-while-revalidate, TTL 1ч)
│   ├── local/            TokenStorage, RoleConfigStorage, UserProfileCache, IconCacheManager
│   │   └── db/           Room v8: entities, DAO, мапперы
│   ├── location/         GPS-трекинг: LocationTrackingService, RuntimeDetector, LocationConfig,
│   │   ├── model/        OfflineMapManager
│   │   └── tracker/      GmsLocationTracker, HmsLocationTracker, AospLocationTracker
│   ├── remote/           AuthApiService, TrainingApiService, TokenRefreshAuthenticator
│   │   └── dto/          26 DTO-классов + mappers
│   ├── repository/       *RepositoryImpl + Mock-репозитории
│   │   └── location/      LocationRepositoryImpl (Room persistence)
│   ├── system/           BatteryOptimizationHelper
│   └── work/             SyncGpsPointsWorker, SaveTrainingWorker, OfflineFinishScheduler
├── di/                   AuthModule (Hilt: provides + bindings)
├── domain/
│   ├── model/            Доменные модели, sealed-результаты, доменные исключения
│   ├── repository/       Интерфейсы репозиториев
│   ├── usecase/          Бизнес-логика (статистика, калории, авторизация)
│   └── validation/       EmailValidator
├── presentation/
│   ├── MainActivity.kt, AppViewModel.kt
│   ├── auth/             login/, register/, forgot/
│   ├── calendar/         История тренировок (день/неделя/месяц)
│   ├── common/           Переиспользуемые Compose-компоненты
│   ├── menu/             MenuScreen + profile/
│   ├── navigation/       AppNavGraph, Screen
│   ├── theme/            SmartTrackerTheme, WorkoutTextStyles, ProfileTextStyles
│   └── workout/          start/, map/, summary/, permission/
└── utils/                ApiErrorHandler, ApiErrorScenarios, DurationFormatter
```

### Прочие файлы репозитория
- [`CLAUDE.md`](CLAUDE.md) — правила работы AI-ассистента с проектом: чеклист качества кода,
  критические нюансы API, соглашения по коммитам/PR, статус PRODUCTION.
- [`BACK_REQ.md`](BACK_REQ.md) — канонический список задач для бэкенда (BR-1…BR-13:
  что/зачем/статус Android/критерий приёмки, с приоритетами). Новые требования
  к бэку записываются только сюда.
- [`CONTEXT.md`](CONTEXT.md) — журнал состояния проекта, статус бэкенда; список багов API
  перенесён в BACK_REQ.md, здесь сохранён как исторический.
- [`README.md`](README.md) — публичное описание проекта со скриншотами.
- [`FONTS_SETUP.md`](FONTS_SETUP.md) — инструкция по установке шрифта Geologica (используется
  в `SmartTrackerTheme`).
- [`docs/gps-tracking-implementation-plan.md`](docs/gps-tracking-implementation-plan.md) — исходный
  поэтапный план реализации GPS-трекинга (Room → GPS → таймер → MapLibre → sync); по нему в
  основном построена текущая реализация `data/location/`, `data/work/`, `presentation/workout/`.
- `app/proguard-rules.pro` — правила R8 для release (`isMinifyEnabled = true`): keep для
  Retrofit/DTO (Gson-рефлексия), Gson `TypeToken`, GMS/HMS Location, MapLibre (JNI-мост);
  `-dontwarn com.huawei.*` для транзитивных HMS-классов, отсутствующих в зависимостях;
  `-assumenosideeffects` вырезает `Log.v/d/i` из release-байткода (`Log.w/e` оставлены).
- `keystore.properties.example` — шаблон конфигурации подписания release: команда генерации
  keystore + поля storeFile/storePassword/keyAlias/keyPassword. Рабочая копия
  `keystore.properties` и `*.jks` — в `.gitignore`, в репозиторий не попадают.
- `screenshots/` — скриншоты экранов для README.

---

## 2. Документация по файлам (по пакетам)

Ниже — построчный обход каждого пакета: назначение файла, все классы/интерфейсы/объекты,
все функции с сигнатурами, и нюансы реализации там, где они есть.


## 2.1 domain/ — доменный слой


### domain/model

#### `domain/model/ActiveTrainingConflictException.kt`
Исключение сигнализирующее о конфликте активной тренировки на сервере.
- `ActiveTrainingConflictException` (class, наследует `Exception`) — бросается когда сервер отклонил старт тренировки с HTTP 400, так как у пользователя уже есть незавершённая активная тренировка. Сообщение по умолчанию: "На сервере есть незавершённая тренировка".

Особенности: бросается в `WorkoutRepositoryImpl` при HTTP 400 от `POST /training/start`. Обрабатывается в `WorkoutStartViewModel`: вызывается `GET /training/active`, orphaned-тренировка завершается через `POST /training/{id}/save_training`, затем старт повторяется автоматически без участия пользователя.

#### `domain/model/ActiveTrainingResult.kt`
Результат успешного запуска тренировки на сервере.
- `ActiveTrainingResult` (data class) — поля: `activeTrainingId: String` (серверный UUID тренировки), `typeActivId: Int` (идентификатор типа активности), `timeStart: String` (время начала, ISO 8601), `message: String` (сообщение от сервера).

Особенности: `activeTrainingId` используется во всех последующих операциях (загрузка GPS-точек, завершение тренировки) и сохраняется в Room как `trainingId` для GPS-точек.

#### `domain/model/AuthResult.kt`
Результат успешной аутентификации/верификации email.
- `AuthResult` (data class) — поля: `accessToken: String`, `refreshToken: String`, `tokenType: String = "bearer"`.

Особенности: соответствует ответу `POST /auth/verify-email` (`access_token`, `refresh_token`, `token_type`). Токены сохраняются в EncryptedSharedPreferences.

#### `domain/model/ForgotPasswordRequest.kt`
Запрос на инициирование восстановления пароля.
- `ForgotPasswordRequest` (data class) — поле: `email: String`.

Особенности: отправляется на первом шаге flow восстановления пароля, когда пользователь вводит email.

#### `domain/model/ForgotPasswordResult.kt`
Результат успешной инициализации восстановления пароля.
- `ForgotPasswordResult` (data class) — поле: `message: String`.

Особенности: backend `POST /password-reset/request` возвращает пустой объект `{}`; поле `message` заполняется дефолтным значением на стороне маппера в data-слое.

#### `domain/model/Gender.kt`
Перечисление биологического пола пользователя.
- `Gender` (enum class) — значения: `MALE`, `FEMALE`.

#### `domain/model/GoalResponse.kt`
Domain-модель цели регистрации (Step 2 регистрации, МОБ-6).
- `GoalResponse` (data class) — поля: `id: Int` (goal_id из API), `description: String` (описание цели), `roleId: Int` (идентификатор связанной роли, определяется из id_role).

Особенности: каждая цель автоматически привязана к роли (например Goal(id=1, roleId=1) → SPORTSMAN).

#### `domain/model/LocationPoint.kt`
Domain-модель одной GPS-точки, записанной во время тренировки.
- `LocationPoint` (data class) — поля: `id: Long = 0`, `trainingId: String` (UUID тренировки), `timestampUtc: Long` (epoch millis из Location.time), `elapsedNanos: Long` (монотонные часы из Location.elapsedRealtimeNanos), `latitude: Double`, `longitude: Double`, `altitude: Double?`, `speed: Float?` (м/с), `accuracy: Float?` (метры), `bearing: Float? = null` (курс движения в градусах [0,360)), `externalId: String? = null` (UUID для идемпотентной синхронизации), `batchId: String? = null` (UUID блока для idempotency), `isSent: Boolean = false`, `calories: Double? = null` (расход ккал за интервал от предыдущей точки).

Особенности: намеренно не содержит импортов `android.*` — domain-слой остаётся чистым Kotlin. Поля `altitude`, `speed`, `accuracy` nullable, так как Android Location API не гарантирует их наличие (`hasAltitude()`, `hasSpeed()`, `hasAccuracy()` могут вернуть false). `bearing` равен null если скорость ниже `MIN_SPEED_FOR_BEARING_MPS` или `hasBearing() == false` — при медленном движении компас шумит. `calories` равен null если профиль пользователя (weight/height) не заполнен.

#### `domain/model/METActivity.kt`
Domain-модель MET-конфигурации вида активности для расчёта расхода калорий.
- `METActivity` (data class) — поля: `baseMet: Double` (базовый MET без учёта скорости), `usesSpeedZones: Boolean`, `zones: List<MetZone>`.
- `MetZone` (data class) — поля: `speedMin: Double` (км/ч), `speedMax: Double` (км/ч, `Double.POSITIVE_INFINITY` для открытой последней зоны), `metValue: Double`.

Особенности: если `usesSpeedZones == true`, MET берётся из `zones` с линейной интерполяцией по текущей скорости точки: `MET(v) = MET₁ + (v−v₁)×(MET₂−MET₁)/(v₂−v₁)`; если false — используется `baseMet` для всей тренировки. Бэкенд передаёт `null` в `speed_max` для последней зоны (нет верхнего предела), маппинг null → `POSITIVE_INFINITY` происходит в `MetZoneDto.toDomain` (data-слой).

#### `domain/model/NavigationConfig.kt`
Конфигурация динамической навигации приложения в зависимости от ролей пользователя.
- `BottomNavItem` (data class) — поля: `id: String`, `label: String`, `icon: String` (Material Icon ID), `route: String`, `requiredRoles: List<Int>`.
- `NavigationConfig` (data class) — поля: `roleIds: List<Int>`, `bottomNavItems: List<BottomNavItem>`, `drawerItems: List<DrawerItem>? = null`.
- `DrawerItem` (data class) — поля: `id: String`, `label: String`, `icon: String`, `route: String`, `requiredRoles: List<Int>` (для будущего использования, drawer-меню пока не реализован).

Особенности: генерируется на основе набора ролей после регистрации/авторизации (МОБ-6.1). `DrawerItem` зарезервирован на будущее.

#### `domain/model/NetworkUnavailableException.kt`
Исключение недоступности сети.
- `NetworkUnavailableException` (class, наследует `Exception`) — конструктор принимает `cause: Throwable? = null`; сообщение — "Сеть недоступна".

Особенности: оборачивает `java.io.IOException` из Retrofit/OkHttp, изолируя presentation-слой от прямой зависимости на Retrofit. Используется в presentation-слое для решения о постановке операции в офлайн-очередь (ViewModel и Worker не импортируют `retrofit2.*`).

#### `domain/model/NicknameCheckResponse.kt`
Ответ на проверку уникальности nickname.
- `NicknameCheckResponse` (data class) — поля: `nickname: String`, `isAvailable: Boolean`, `message: String`.

Особенности: возвращается от `POST /auth/check-nickname`.

#### `domain/model/RegisterRequest.kt`
Domain-модель запроса регистрации нового пользователя, охватывающая все шаги формы регистрации.
- `RegisterRequest` (data class) — поля: `firstName: String`, `username: String`, `birthDate: LocalDate`, `gender: Gender` (шаг 1/4), `purpose: UserPurpose` (шаг 2/4), `roleIds: List<Int> = emptyList()` (динамические роли из API, МОБ-6 — если не пусто, отправляются вместо purpose), `email: String`, `password: String` (шаг 3/4), `confirmPassword: String` (только для клиентской валидации, на сервер не отправляется).

Особенности: TODO в коде — уточнить, сохранять ли `purpose` в БД или использовать только для определения роли; закомментированное поле `role: UserRole` ожидает добавления колонки `role` в таблицу users на бэкенде. Использует `java.time.LocalDate` (доступен нативно благодаря minSdk=26, desugaring не требуется).

#### `domain/model/RegisterResult.kt`
Результат успешной отправки формы регистрации.
- `RegisterResult` (data class) — поля: `email: String`, `expiresIn: Int` (секунды до истечения кода верификации — для таймера UI).

Особенности: соответствует ответу `POST /auth/register` (`message`, `email`, `expires_in`). После получения пользователь переходит на экран ввода кода подтверждения.

#### `domain/model/ResendResetCodeResult.kt`
Результат повторной отправки кода верификации при восстановлении пароля.
- `ResendResetCodeResult` (data class) — поле: `message: String`.

Особенности: backend `POST /password-reset/resend-verify-code` возвращает пустой объект `{}`; `message` заполняется дефолтным значением маппером.

#### `domain/model/ResendResult.kt`
Результат повторной отправки кода верификации при регистрации.
- `ResendResult` (data class) — поле: `expiresIn: Int` (соответствует `remaining_seconds` из ответа — секунды до истечения нового кода).

Особенности: соответствует ответу `POST /auth/resend-code`. Используется для обновления таймера на экране верификации.

#### `domain/model/ResetPasswordRequest.kt`
Запрос на завершение процесса восстановления пароля.
- `ResetPasswordRequest` (data class) — поля: `email: String`, `code: String`, `newPassword: String`, `confirmPassword: String`.

Особенности: отправляется на третьем шаге flow (пользователь вводит код и новый пароль).

#### `domain/model/ResetPasswordResult.kt`
Результат успешного сброса пароля.
- `ResetPasswordResult` (data class) — поля: `message: String`, `success: Boolean`, `redirectToLogin: Boolean = true` (может содержать предложение перейти на экран логина).

#### `domain/model/Role.kt`
Domain-модель роли пользователя в приложении (МОБ-6.2).
- `Role` (data class) — поля: `roleId: Int`, `name: String`.

Особенности: после регистрации пользователь может иметь несколько ролей. Соответствие таблице БД `roles`: roleId=1 → "sportsman" (ATHLETE), roleId=2 → "trainer" (TRAINER), roleId=3 → "club_organizer" (CLUB_OWNER). Отличается от `RoleResponse` — эта модель для ролей уже авторизованного пользователя, а `RoleResponse` — для списка доступных ролей на экране регистрации.

#### `domain/model/RoleConfig.kt`
Object конфигурации ролей и динамической навигации (МОБ-6.1).
- `RoleConfig` (object) — константы: `ROLE_ATHLETE = 1`, `ROLE_TRAINER = 2`, `ROLE_CLUB_OWNER = 3`.
  - `fun getNavigationConfig(roleIds: List<Int>): NavigationConfig` — генерирует конфигурацию BottomNavigation на основе ролей пользователя; порядок кнопок: Главная → специфичные для ролей (ATHLETE → TRAINER → CLUB_OWNER) → Профиль; при наличии нескольких ролей объединяет все доступные экраны.
  - `fun getRoleName(roleId: Int): String` — преобразует roleId в читаемое название роли (для отладки/логирования); возвращает "Неизвестная роль ($roleId)" для нераспознанного id.
  - `fun hasRole(roleIds: List<Int>, roleId: Int): Boolean` — проверяет наличие конкретной роли в списке.
  - `fun getAllRoles(): List<Pair<Int, String>>` — возвращает все доступные роли как пары (id, название) — для будущей админ-панели.

Особенности: маппинг ролей на доступные экраны в BottomNavigation жёстко закодирован (не конфигурируется с бэкенда).

#### `domain/model/RoleResponse.kt`
Domain-модель роли, доступной для выбора на экране регистрации (МОБ-6.3).
- `RoleResponse` (data class) — поля: `id: Int`, `name: String`, `description: String? = null`.

Особенности: отличается от `Role` (роли уже вошедшего пользователя) наличием опционального `description` для UI; обычно загружается один раз при старте приложения и кешируется.

#### `domain/model/SaveTrainingResult.kt`
Результат завершения тренировки на сервере.
- `SaveTrainingResult` (data class) — поля: `trainingId: String` (UUID завершённой тренировки), `message: String`.

Особенности: соответствует ответу `POST /training/{id}/save_training`.

#### `domain/model/TrainingAlreadyClosedException.kt`
Исключение — тренировка уже закрыта на сервере.
- `TrainingAlreadyClosedException` (class, наследует `Exception`) — конструктор принимает `httpCode: Int`; сообщение — "Тренировка уже закрыта (HTTP $httpCode)".

Особенности: бросается в `WorkoutRepositoryImpl` при получении HTTP 400–499 от `POST /training/{id}/save_training`. Означает, что тренировка уже закрыта (например, auto-recovery успел завершить её раньше) или не существует. Запись из `pending_finishes` можно безопасно удалять — повторная попытка бессмысленна.

#### `domain/model/TrainingHistoryItem.kt`
Элемент истории тренировки.
- `TrainingHistoryItem` (data class) — поля: `trainingId: String`, `typeActivId: Int`, `date: LocalDate`, `timeStart: String?`, `timeEnd: String?`, `kilocalories: Double?`, `distanceM: Double?`, `avgSpeed: Double?`, `elevationGain: Double?`.

Особенности: получается с эндпоинта `GET /training/history`. Для UI из всех временных полей нужна только `date` как `LocalDate`. Большинство полей nullable, отражая опциональность на бэкенде.

#### `domain/model/User.kt`
Domain-модель аутентифицированного пользователя.
- `User` (data class) — поля: `id: Int` (user_id в БД), `firstName: String`, `lastName: String? = null`, `middleName: String? = null`, `username: String` (nickname в БД), `email: String`, `birthDate: LocalDate`, `gender: Gender`, `weight: Float? = null`, `height: Float? = null`, `photoUrl: String? = null`.

Особенности: `lastName`, `middleName`, `weight`, `height` заполняются позже в профиле, а не при регистрации. `photoUrl` всегда не-null после первого логина — бэкенд подставляет плейсхолдер. Токены (jwt_session, jwt_reload) не включаются в domain-модель — идут отдельно в `AuthResult`. Закомментированное поле `role: UserRole` ожидает добавления колонки в таблицу users.

#### `domain/model/UserPurpose.kt`
Перечисление цели использования приложения при регистрации и функции маппинга в роль.
- `UserPurpose` (enum class) — значения: `ATHLETE`, `TRAINER`, `CLUB_OWNER`, `EXPLORING`, `OTHER`.
- Top-level функции:
  - `fun UserPurpose.toUserRole(): UserRole` — маппинг цели в роль пользователя (ATHLETE→ATHLETE, TRAINER→TRAINER, CLUB_OWNER→CLUB_OWNER, EXPLORING/OTHER→USER).
  - `fun UserPurpose.toRoleId(): Int?` — конвертирует цель в номер роли для API (1/2/3 для ATHLETE/TRAINER/CLUB_OWNER; null для EXPLORING и OTHER — роль не отправляется).

#### `domain/model/UserRole.kt`
Перечисление ролей пользователя, определяющих доступный функционал.
- `UserRole` (enum class) — значения: `ATHLETE` (цель ATHLETE), `TRAINER` (цель TRAINER), `CLUB_OWNER` (цель CLUB_OWNER), `USER` (цель EXPLORING или OTHER).

#### `domain/model/WorkoutType.kt`
Domain-модель типа тренировки.
- `WorkoutType` (data class) — поля: `id: Int`, `name: String` (отображаемое название), `iconKey: String` (строковое представление type_activ_id для fallback-иконки), `iconFile: File? = null` (скачанный файл иконки в filesDir), `imageUrl: String? = null` (URL иконки с сервера, резерв для Coil если iconFile ещё не скачан).

Особенности: загружается через `GET /training/types_activity`. Маппинг `iconKey → drawable` не в названии, а по числовому id (например "1"=Бег, "2"=Северная ходьба, "3"=Велосипед) — согласно CLAUDE.md, маппинг находится в `activityIconRes()` в `ActivityIcons.kt` (комментарий в файле ссылается на `iconResForKey()` в `WorkoutStartScreen`). Цепочка отображения иконки: `iconFile ?: imageUrl ?: activityIconRes(iconKey)`.

---

### domain/repository

#### `domain/repository/AuthRepository.kt`
Контракт репозитория авторизации и управления профилем пользователя (МОБ-1.3).
- `AuthRepository` (interface) — методы:
  - `suspend fun register(request: RegisterRequest): Result<RegisterResult>` — шаг 1 регистрации, `POST /auth/register`.
  - `suspend fun verifyEmail(email: String, code: String): Result<AuthResult>` — шаг 2 регистрации, `POST /auth/verify-email`, возвращает токены.
  - `suspend fun resendCode(email: String): Result<ResendResult>` — повторная отправка кода, `POST /auth/resend-code`, доступно раз в 2 минуты.
  - `suspend fun login(email: String, password: String): Result<AuthResult>` — вход, `POST /auth/login`.
  - `suspend fun refreshToken(refreshToken: String): Result<AuthResult>` — обновление access token, `POST /auth/refresh`, вызывается автоматически из Authenticator.
  - `suspend fun checkNickname(nickname: String): Result<NicknameCheckResponse>` — проверка доступности nickname, `POST /auth/check-nickname`.
  - `suspend fun getAvailableRoles(): Result<List<RoleResponse>>` — список доступных ролей для регистрации (МОБ-6.3), кешируется на 1 час.
  - `suspend fun getGoalsByRole(roleId: Int? = null): Result<List<GoalResponse>>` — список целей, опционально по role_id (МОБ-6.4), кешируется на 1 час.
  - `suspend fun getUserInfo(): Result<User>` — профиль текущего пользователя по Bearer-токену, `GET /user_info/user/`.
  - `suspend fun updateProfile(firstName: String?, lastName: String?, middleName: String?, birthDate: String?, weight: Float?, height: Float?, gender: String?, nickname: String?): Result<User>` — обновление профиля, `PATCH /user/edit`; все параметры nullable (null = не менять).
  - `suspend fun uploadPhoto(file: File): Result<Unit>` — загрузка фото профиля, `POST /user/photo` (multipart, поле "file", jpg/png до 5 МБ).
  - `suspend fun deletePhoto(): Result<Unit>` — удаление фото профиля, `DELETE /user/photo`.
  - `suspend fun deleteAccount(): Result<Unit>` — удаление аккаунта, `DELETE /user/delete`.
  - `val sessionExpiredFlow: StateFlow<Boolean>` — флаг принудительного выхода из сессии, эмитит `true` когда оба токена истекли; источник — `TokenStorage.sessionExpiredFlow`, вынесен в domain, чтобы ViewModel не зависел от TokenStorage напрямую.

#### `domain/repository/AllowedEmailDomainsRepository.kt`
Источник списка почтовых доменов, разрешённых для регистрации (требование 149-ФЗ: регистрация только через российские почтовые сервисы).
- `AllowedEmailDomainsRepository` (interface) — методы:
  - `suspend fun getAllowedDomains(): Set<String>` — множество разрешённых доменов в нижнем регистре; не бросает исключений, реализация обязана вернуть непустой список (при недоступности сети — локальный fallback).

Особенности: интерфейс в domain, чтобы источник списка менялся без правок бизнес-логики — сейчас data-реализация возвращает захардкоженный список, после появления эндпоинта `GET /auth/allowed-email-domains` на бэкенде станет сетевой с кэшем. `suspend` заложен заранее под сетевую реализацию.

Особенности: все suspend-функции возвращают `Result<T>` для обработки ошибок во ViewModel без try/catch. Поток регистрации состоит из двух шагов: `register()` → `verifyEmail()`.

#### `domain/repository/LocationRepository.kt`
Контракт репозитория GPS-точек тренировки — единственный источник правды для данных трекинга.
- `LocationRepository` (interface) — методы:
  - `suspend fun savePoint(point: LocationPoint)` — сохранить одну точку.
  - `suspend fun savePoints(points: List<LocationPoint>)` — batch-вставка нескольких точек за одну транзакцию.
  - `suspend fun getPointsForTraining(trainingId: String): List<LocationPoint>` — все точки тренировки единовременно.
  - `suspend fun getUnsentPoints(trainingId: String): List<LocationPoint>` — точки, ещё не отправленные на сервер.
  - `suspend fun assignBatchId(pointIds: List<Long>, batchId: String)` — назначить batchId группе точек перед отправкой (для атомарной пометки батча отправленным).
  - `suspend fun markBatchAsSent(batchId: String)` — пометить все точки батча как отправленные после успешной загрузки.
  - `fun observePointsForTraining(trainingId: String): Flow<List<LocationPoint>>` — наблюдение за точками в реальном времени для UI.
  - `suspend fun getLastKnownPoint(): LocationPoint?` — последняя сохранённая точка из любой тренировки, для начального центрирования карты до получения GPS-сигнала.
  - `suspend fun deletePointsForTraining(trainingId: String)` — удаляет все точки тренировки (используется для очистки discovery-точек).
  - `suspend fun rekeyTrainingId(oldId: String, newId: String)` — переназначает trainingId точек с локального UUID на серверный при офлайн-старте.

Особенности: не содержит импортов `android.*`. Реализация — `LocationRepositoryImpl` в data-слое через Room.

#### `domain/repository/PasswordRecoveryRepository.kt`
Контракт репозитория для операций восстановления пароля.
- `PasswordRecoveryRepository` (interface) — методы:
  - `suspend fun initiateForgotPassword(request: ForgotPasswordRequest): Result<ForgotPasswordResult>` — инициирует восстановление пароля, отправляет код на email.
  - `suspend fun verifyResetCode(email: String, code: String): Result<Unit>` — проверяет 6-значный код верификации перед вводом нового пароля.
  - `suspend fun resendResetCode(email: String): Result<ResendResetCodeResult>` — повторная отправка кода, с cooldown (обычно 120 секунд).
  - `suspend fun resetPassword(request: ResetPasswordRequest): Result<ResetPasswordResult>` — завершает восстановление пароля (проверка кода + установка нового пароля).

Особенности: документированы коды ошибок в `Result.isFailure`: `EMAIL_NOT_FOUND`, `TOO_MANY_ATTEMPTS` (лимит 5 неверных кодов), `RESEND_COOLDOWN`, `GENERIC`.

#### `domain/repository/WorkoutRepository.kt`
Контракт репозитория тренировок — справочные данные и жизненный цикл тренировки.
- `WorkoutRepository` (interface) — методы:
  - `fun workoutTypesFlow(): Flow<List<WorkoutType>>` — реактивный поток видов активности из Room; эмитит кэш немедленно, затем повторно после фонового обновления из сети (сеть не блокирует первый emit).
  - `suspend fun startTraining(typeActivId: Int, timeStart: String? = null): Result<ActiveTrainingResult>` — старт тренировки на сервере; `timeStart` для офлайн-тренировок (реальное время начала), null — бэкенд ставит now().
  - `suspend fun saveTraining(trainingId: String, timeEnd: String, totalDistanceMeters: Double?, totalKilocalories: Double?): Result<SaveTrainingResult>` — завершение тренировки на сервере.
  - `suspend fun uploadGpsPoints(trainingId: String, batchId: String, points: List<LocationPoint>): Result<Int>` — загрузка батча GPS-точек (максимум 100 за запрос), возвращает количество сохранённых точек.
  - `suspend fun getActiveTraining(): Result<String>` — получить текущую активную тренировку с сервера (используется при обработке `ActiveTrainingConflictException`).
  - `suspend fun getMETActivity(typeActivId: Int): Result<METActivity>` — получить MET-конфигурацию для расчёта калорий по виду активности.
  - `suspend fun getTrainingHistory(): Result<List<TrainingHistoryItem>>` — история тренировок пользователя.
  - `suspend fun getTrainingDetail(trainingId: String): Result<List<LocationPoint>>` — GPS-трек конкретной тренировки из истории (`GET /training/{id}/get_training`), пустой список при ошибке/недоступности.
  - `suspend fun deleteCompletedTraining(trainingId: String): Result<Unit>` — удаление завершённой тренировки из истории на сервере, эмитит `historyChangedFlow` после успеха.
  - `val historyChangedFlow: SharedFlow<Unit>` — эмитит `Unit` при изменении истории (сохранение или удаление тренировки); единый триггер перезагрузки для `TrainingHistoryViewModel`.
  - `suspend fun savePendingFinish(trainingId: String, timeEnd: String, totalDistanceMeters: Double?, totalKilocalories: Double?, typeActivId: Int? = null, timeStart: String? = null)` — сохраняет параметры завершения тренировки в локальную очередь при недоступности сети; идемпотентно (повторный вызов для того же trainingId игнорируется через IGNORE); `typeActivId`/`timeStart` заполняются только для офлайн-старта.

Особенности: объединяет справочные данные (типы активностей) и полный жизненный цикл тренировки: старт → GPS-синхронизация → завершение. Тесно связан с `SyncGpsPointsWorker` и `SaveTrainingWorker` в data-слое.

---

### domain/usecase

#### `domain/usecase/CalculateTrainingStatsUseCase.kt`
UseCase расчёта статистики тренировки (дистанция, скорость, калории) по списку GPS-точек методом Haversine.
- `TrainingStats` (data class) — поля: `distanceMeters: Double`, `avgSpeedMps: Double` (distanceMeters / durationSeconds), `kilocalories: Float`, `durationSeconds: Long` (по временным меткам первой и последней точки).
- `CalculateTrainingStatsUseCase` (class, `@Inject constructor()`) — методы:
  - `fun calculateDeltaDistance(points: List<LocationPoint>, fromIndex: Int): Double` — вычисляет дополнительную дистанцию только для новых точек; начинает с `max(0, fromIndex-1)`, чтобы не потерять отрезок между последней старой и первой новой точкой; возвращает сумму по всем парам от startIdx до конца списка. Используется для инкрементального расчёта O(n_new) вместо O(n_total).
  - `fun distanceBetween(from: LocationPoint, to: LocationPoint): Double` — расстояние между двумя соседними точками без аллокации промежуточного списка (для точечного инкрементального расчёта по одному шагу).
  - `fun execute(points: List<LocationPoint>): TrainingStats` — полный расчёт статистики: дистанция = сумма haversine между соседними точками; продолжительность = разница timestampUtc первой и последней точки; средняя скорость = дистанция/продолжительность; калории = (дистанция в км) × 70 (упрощённая формула для среднего веса 70 кг). Для списка < 2 точек возвращает нулевую статистику.
  - `private fun haversineMeters(p1: LocationPoint, p2: LocationPoint): Double` — расстояние по формуле гаверсинусов (радиус Земли 6 371 000 м, точность ~0.5%); фильтрует сегмент, если рассчитанное расстояние меньше максимальной accuracy из двух точек (защита от "прыгающих" координат при стоянии на месте).

Особенности/нюансы: **`calculateDeltaDistance` — семантика по CLAUDE.md**: функция считает сумму расстояний от `max(0, fromIndex-1)` до КОНЦА списка, а не только одну пару точек. Для расчёта одного шага i→i+1 нужно вызывать `calculateDeltaDistance(listOf(points[i-1], points[i]), 0)`, а не `calculateDeltaDistance(points, i-1)` (это дало бы расстояние от i−2 до конца — известный баг, задокументированный в CLAUDE.md). Не содержит импортов `android.*`.

#### `domain/usecase/CalorieCalculator.kt`
Object для расчёта расхода калорий методом MET (Compendium of Physical Activities 2024) на основе формулы Харриса-Бенедикта.
- `CalorieCalculator` (object) — методы:
  - `fun computeCF(weightKg: Float, heightCm: Float, ageYears: Int, gender: Gender): Double` — вычисляет персональный поправочный коэффициент CF = 3.5 / VO2rest, где RMR считается по формуле Харриса-Бенедикта (разные коэффициенты для MALE/FEMALE), а VO2rest = (RMR×1000)/(1440×5×W). CF зависит только от профиля пользователя — вычисляется один раз перед тренировкой (не меняется в ходе неё).
  - `fun energyForInterval(met: Double, cf: Double, weightKg: Float, durationMin: Double): Double` — расход ккал за интервал для возраста 18–59 лет: E = MET × CF × W × (t/60).
  - `fun energyOver60(met: Double, weightKg: Float, durationMin: Double): Double` — расход ккал за интервал для 60+ лет (любой пол), упрощённая формула без персонального BMR с фиксированным референсным VO2rest=2.7: E = (MET×(3.5/2.7))×2.7×W/200×t.
  - `fun interpolateMet(speedKmh: Double, zones: List<MetZone>): Double` — определяет MET для скорости по таблице зон с линейной интерполяцией; зоны сортируются по speedMin (защита от неотсортированного порядка от сервера); скорость ниже первой зоны → MET первой зоны, выше последней → MET последней зоны; на границе зон (`speedKmh == z2.speedMin`) — результат MET следующей зоны (правая граница exclusive, что корректно для плавного ускорения); при пустом списке зон возвращает 0.0.

Особенности/нюансы: не содержит импортов `android.*`, `retrofit2.*`, `gson.*`. Ссылка на источник — https://pacompendium.com/ (Compendium 2024). Комментарий в коде явно поясняет, что sortedBy создаёт новый список и не мутирует исходный `zones`.

#### `domain/usecase/LoginUseCase.kt`
UseCase входа в приложение с клиентской валидацией перед вызовом API (МОБ-3.2).
- `LoginUseCase` (class, `@Inject constructor(private val repository: AuthRepository)`) — методы:
  - `suspend operator fun invoke(email: String, password: String): Result<AuthResult>` — сначала валидирует email/пароль; при ошибке валидации возвращает `Result.failure(IllegalArgumentException(...))` БЕЗ вызова репозитория (короткое замыкание); иначе делегирует `repository.login(email, password)`.
  - `private fun validate(email: String, password: String): String?` — проверяет: email не пустой и соответствует `EmailValidator.isValid`; пароль не пустой и длиной минимум 8 символов. Возвращает текст ошибки на русском или null.

Особенности: не знает ничего про UI, Android, Retrofit или БД. `operator fun invoke` позволяет вызывать как функцию: `loginUseCase(email, password)`.

#### `domain/usecase/RegisterUseCase.kt`
UseCase регистрации нового пользователя с клиентской валидацией формы и проверкой почтового домена по 149-ФЗ (МОБ-1.4).
- `RegisterUseCase` (class, `@Inject constructor(private val repository: AuthRepository, private val allowedEmailDomains: AllowedEmailDomainsRepository)`) — методы:
  - `suspend operator fun invoke(request: RegisterRequest): Result<RegisterResult>` — валидирует запрос (`validate`); затем проверяет домен почты через `allowedEmailDomains.getAllowedDomains()` + `EmailValidator.isAllowedDomain` — при иностранном домене `Result.failure(IllegalArgumentException(RUSSIAN_EMAIL_REQUIRED_MESSAGE))` без вызова репозитория; иначе делегирует `repository.register(request)`.
  - `private fun validate(request: RegisterRequest): String?` — проверки по порядку: `firstName` не пустой; `username` не пустой и минимум 3 символа; `email` не пустой и валиден по `EmailValidator`; `password` не пустой, минимум 8 символов, содержит хотя бы одну цифру; `confirmPassword` совпадает с `password`; `birthDate.year >= 1900` (защита от дефолтной/невыбранной даты). Возвращает текст ошибки на русском или null.

Особенности/нюансы: доменная проверка почты — обязательная точка контроля 149-ФЗ (через `invoke` проходит любая регистрация независимо от того, проверил ли домен ViewModel); вход и восстановление пароля намеренно не ограничиваются. TODO в коде — добавить флаг `isPurposeSelected` в `RegisterRequest`, когда будет согласован UX экрана выбора цели (сейчас `purpose` не валидируется явно, так как enum всегда имеет значение). Требование "пароль содержит цифру" есть только в этой валидации, отсутствует в UI-подсказке — риск незаметной рассинхронизации.

---

### domain/validation

#### `domain/validation/EmailValidator.kt`
Централизованная валидация email: формат (regex) и принадлежность домена списку разрешённых (149-ФЗ).
- `EmailValidator` (object) — приватное поле `EMAIL_REGEX: Regex` = `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`; константа `RUSSIAN_EMAIL_REQUIRED_MESSAGE: String` — единый текст ошибки для неразрешённого домена (используется и в `RegisterUseCase`, и в `RegisterViewModel`).
  - `fun isValid(email: String): Boolean` — проверяет полное совпадение строки с regex.
  - `fun isAllowedDomain(email: String, allowedDomains: Set<String>): Boolean` — извлекает домен через `substringAfterLast('@')`, приводит к нижнему регистру, проверяет точное совпадение с множеством. Поддомены (`user@mail.yandex.ru`) намеренно не проходят — иначе пришлось бы отдельно отсекать `attacker-yandex.ru`.

Особенности: вынесен из `LoginUseCase` и `RegisterUseCase` для соблюдения DRY (regex ранее был продублирован в обоих). Используется также в `LoginViewModel`, `ForgotPasswordViewModel` и `RegisterViewModel` вместо `android.util.Patterns.EMAIL_ADDRESS` — Patterns в JVM unit-тестах является null-полем (`isReturnDefaultValues` отдаёт дефолты только для методов) и давал NPE. Список доменов передаётся параметром (источник — `AllowedEmailDomainsRepository`), сам валидатор остаётся чистой функцией. Доменную проверку применять только к регистрации: вход и восстановление пароля существующих аккаунтов закон не ограничивает.

---

### Тесты domain-слоя

#### `test/domain/usecase/CalculateTrainingStatsUseCaseTest.kt`
Тестирует `CalculateTrainingStatsUseCase` — расчёт дистанции, скорости, калорий и инкрементальный расчёт.

Покрытые сценарии (`@Test`):
- пустой список точек возвращает нулевую статистику
- одна точка возвращает нулевую статистику
- дистанция между двумя точками приблизительно верна (реальные координаты Москвы, допуск 240–350м)
- дистанция трёх точек равна сумме двух сегментов
- идентичные координаты дают нулевую дистанцию
- сегмент короче accuracy-пятна не учитывается в дистанции
- сегмент длиннее accuracy-пятна учитывается
- точки без accuracy не фильтруются
- durationSeconds рассчитывается по метке времени крайних точек
- avgSpeedMps = distanceMeters / durationSeconds
- kilocalories рассчитывается как distanceKm × 70
- calculateDeltaDistance с fromIndex=0 равен полной дистанции
- calculateDeltaDistance с fromIndex=points.size возвращает 0
- calculateDeltaDistance включает отрезок между последней старой и первой новой точкой
- calculateDeltaDistance с одной точкой возвращает 0

#### `test/domain/usecase/CalorieCalculatorTest.kt`
Тестирует `CalorieCalculator` — интерполяцию MET и формулу для возраста 60+.

Покрытые сценарии (`@Test`):
- interpolateMet с пустыми зонами возвращает 0
- interpolateMet корректно обрабатывает границы speedMin и speedMax (значение на нижней границе первой зоны, на верхней границе первой зоны, выше верхней границы последней зоны)
- interpolateMet на стыке зон возвращает MET следующей зоны (проверка exclusive-границы)
- energyOver60 рассчитывается по формуле 60+ (проверка алгебраической эквивалентности формулы в коде)

#### `test/domain/usecase/LoginUseCaseTest.kt`
Тестирует `LoginUseCase` с mock-репозиторием (Mockito-Kotlin).

Покрытые сценарии (`@Test`):
- невалидный email возвращает failure без вызова репозитория (короткое замыкание валидации; проверяется `verify(repository, never()).login(...)`)

#### `test/domain/usecase/RegisterUseCaseTest.kt`
Тестирует `RegisterUseCase` с mock-репозиторием (Mockito-Kotlin) и фейком `AllowedEmailDomainsRepository` (анонимный object с фиксированным списком доменов).

Покрытые сценарии (`@Test`):
- пароль без цифры возвращает failure без вызова репозитория
- несовпадение confirmPassword возвращает failure без вызова репозитория
- иностранный почтовый домен возвращает failure без вызова репозитория (149-ФЗ, проверка текста `RUSSIAN_EMAIL_REQUIRED_MESSAGE`)
- российский почтовый домен доходит до вызова репозитория

#### `test/domain/validation/EmailValidatorTest.kt`
Тестирует `EmailValidator`: граничные случаи regex формата и проверку домена по списку (149-ФЗ).

Покрытые сценарии (`@Test`):
- email с плюсом валиден (символ '+' разрешён)
- email с поддоменом валиден
- email без локальной части невалиден (`@domain.com`)
- российский домен из списка проходит
- иностранный домен отклоняется
- домен сравнивается без учёта регистра
- поддомен разрешённого домена отклоняется (точное совпадение, не suffix)
- домен-суффикс не проходит как подстрока (`notyandex.ru`)
- строка без `@` отклоняется
- пустой список доменов отклоняет любой email

---

### Итоги по слою domain

Слой domain в SmartTracker Android построен как чистый Kotlin без единой зависимости на `android.*`, `retrofit2.*` или `gson.*` — это последовательно подчёркнуто в KDoc-комментариях большинства файлов (`LocationPoint`, `NetworkUnavailableException`, `CalorieCalculator`, `LocationRepository` и др.), что позволяет полностью unit-тестировать бизнес-логику на JVM без Android-эмулятора. Основной паттерн работы с результатами операций — `Result<T>` из Kotlin stdlib: все suspend-методы репозиториев (`AuthRepository`, `WorkoutRepository`, `PasswordRecoveryRepository`) возвращают `Result<T>`, что даёт ViewModel единый способ обработки успеха/ошибки без try/catch на каждом вызове. Ошибки, специфичные для доменной логики, выделены в отдельные sealed-подобные исключения (`ActiveTrainingConflictException`, `TrainingAlreadyClosedException`, `NetworkUnavailableException`) — они бросаются из data-слоя и перехватываются в конкретных местах presentation/data для принятия решений (retry, офлайн-очередь, автоматическое завершение orphaned-тренировки). Модели данных — простые immutable `data class` с nullable-полями там, где на бэкенде объявлен `Optional`/`nullable` тип (`User.weight/height`, `LocationPoint.altitude/speed/accuracy`, `RoleResponse.description`); это правило зафиксировано в чеклисте CLAUDE.md и соблюдается во всех просмотренных файлах. UseCase-слой (`LoginUseCase`, `RegisterUseCase`) реализует паттерн "клиентская валидация + короткое замыкание перед сетевым вызовом" через `operator fun invoke`, что подтверждено тестами (`verify(repository, never())`). Расчётные UseCase (`CalculateTrainingStatsUseCase`, `CalorieCalculator`) инкапсулируют математику трекинга (Haversine-дистанция с фильтром по GPS-точности, MET-калории с формулой Харриса-Бенедикта и линейной интерполяцией по скоростным зонам) и покрыты наиболее плотным набором unit-тестов в проекте. Связи между файлами домена образуют граф: `model` описывает данные и контракты API, `repository`-интерфейсы объявляют операции над этими моделями (реализация — в data-слое), `usecase` комбинирует репозитории и `validation`-объекты (`EmailValidator`) для бизнес-правил, при этом domain никогда не зависит от data или presentation — зависимость всегда направлена внутрь, к domain.

---

## 2.2 data/local/ и data/cache/ — локальное хранилище, Room, кэши


### data/local

#### `app/src/main/java/com/example/smarttracker/data/local/IconCacheManager.kt`
Менеджер локального файлового кэша иконок видов активности: скачивает иконку по URL и хранит её на диске, отдаёт закэшированный файл без обращения к сети.
- `IconCacheManager` (class, `@Singleton`, DI через `@Inject constructor(@ApplicationContext Context, OkHttpClient)`) — использует директорию `filesDir/activity_icons` (создаётся в конструкторе через `mkdirs()`) и `SharedPreferences` с именем `"icon_url_cache"` (`urlPrefs`) для хранения URL последней успешной загрузки по каждому `typeId`.
  - `fun getCached(typeId: Int): File?` — возвращает `File(iconsDir, "$typeId.png")`, если файл существует, иначе `null`. Не suspend, не блокирует поток — только проверка существования.
  - `fun getDownloadedUrl(typeId: Int): String?` — читает `urlPrefs.getString("url_$typeId", null)`; используется, чтобы обнаружить смену `imagePath` на бэкенде и решить, нужно ли перекачивать иконку.
  - `suspend fun download(typeId: Int, url: String): File?` — выполняется в `Dispatchers.IO`; делает синхронный вызов `OkHttpClient` (`Request.Builder().url(url).build()` → `execute()`), читает тело ответа как `bytes()`, записывает в `File(iconsDir, "$typeId.png")`, затем сохраняет URL в `urlPrefs`. Обёрнуто в `runCatching { }.getOrNull()` — при любой ошибке сети или записи возвращает `null`, не бросает исключение, чтобы не ломать отображение остальных типов активностей.

Особенности: файлы хранятся в `filesDir` (не `cacheDir`), поэтому не удаляются системой при нехватке памяти — только вручную ("Очистить данные") или при деинсталляции. Имя файла — числовой `typeId`, а не название активности, чтобы не зависеть от локализации имени с бэкенда.

---

#### `app/src/main/java/com/example/smarttracker/data/local/RoleConfigStorage.kt`
Контракт хранилища ID ролей, выбранных пользователем на этапе регистрации (Step 2), — отдельно от ролей, приходящих из API `/role/user_roles`.
- `RoleConfigStorage` (interface) — описывает три операции над списком выбранных ролей.
  - `fun saveSelectedRoles(roleIds: List<Int>): Unit` — сохранить выбранные пользователем роли (например `[1, 2]` для Athlete+Trainer).
  - `fun getSelectedRoles(): List<Int>` — вернуть ранее сохранённые роли или пустой список.
  - `fun clearSelectedRoles(): Unit` — удалить сохранённые роли (при logout).

Особенности: отдельный источник данных от `TokenStorage.getUserRoles()` — первый хранит выбор пользователя на регистрации, второй — авторитетный список ролей с бэкенда. Оба нужны для полноты синхронизации.

---

#### `app/src/main/java/com/example/smarttracker/data/local/RoleConfigStorageImpl.kt`
Реализация `RoleConfigStorage` поверх обычного (не зашифрованного) `SharedPreferences`.
- `RoleConfigStorageImpl` (class, реализует `RoleConfigStorage`, `@Inject constructor(@ApplicationContext Context)`) — использует `SharedPreferences` с именем `role_config_prefs` (ленивая инициализация через `by lazy`).
  - `override fun saveSelectedRoles(roleIds: List<Int>)` — сохраняет как строку через `roleIds.joinToString(",")` под ключом `selected_role_ids`.
  - `override fun getSelectedRoles(): List<Int>` — читает строку, при `null` возвращает `emptyList()`, при пустой строке — `emptyList()`, иначе `split(",")` и `mapNotNull { it.trim().toIntOrNull() }`.
  - `override fun clearSelectedRoles()` — `prefs.edit().remove(KEY_SELECTED_ROLE_IDS).apply()`.

Особенности: намеренно не шифруется — ID ролей не считаются чувствительными данными (в отличие от токенов в `TokenStorage`).

---

#### `app/src/main/java/com/example/smarttracker/data/local/SettingsStorage.kt`
Контракт хранилища пользовательских настроек (экран «Меню → Настройки») + модель настроек.
- `AppSettings` (data class) — снимок настроек с осознанными дефолтами:
  - `autopauseEnabled: Boolean = false` — автопауза меняет поведение записи, включается осознанно;
  - `voiceCuesEnabled: Boolean = true` — подсказки не влияют на данные, легко выключить;
  - `voiceCueIntervalKm: Int = 1` — частота км-подсказок;
  - `keepScreenOn: Boolean = false` — экономия батареи по умолчанию.
  - `companion object { val ALLOWED_VOICE_INTERVALS = listOf(1, 2, 5) }` — допустимые интервалы.
- `SettingsStorage` (interface):
  - `val settings: Flow<AppSettings>` — текущее значение + все изменения (живая подписка: смена настройки во время тренировки подхватывается сервисом без перезапуска);
  - `suspend fun setAutopauseEnabled(enabled: Boolean)`;
  - `suspend fun setVoiceCuesEnabled(enabled: Boolean)`;
  - `suspend fun setVoiceCueIntervalKm(intervalKm: Int)` — значения вне `ALLOWED_VOICE_INTERVALS` приводятся к дефолту;
  - `suspend fun setKeepScreenOn(enabled: Boolean)`.

Потребители: `SettingsViewModel` (чтение+запись), `LocationTrackingService` (автопауза, TTS), `WorkoutStartViewModel` (keepScreenOn).

---

#### `app/src/main/java/com/example/smarttracker/data/local/SettingsStorageImpl.kt`
Реализация `SettingsStorage` на DataStore Preferences — **первое использование DataStore в проекте** (настройки не чувствительные, шифрование не нужно).
- Top-level: `private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")` — property-delegate гарантирует один экземпляр DataStore на процесс.
- `SettingsStorageImpl` (`@Singleton`, `@Inject constructor(@ApplicationContext Context)`):
  - `Keys` (private object) — `booleanPreferencesKey`/`intPreferencesKey` для четырёх настроек;
  - `override val settings: Flow<AppSettings>` — `data.catch { IOException → emit(emptyPreferences()) }.map { … }`: повреждённый файл деградирует к дефолтам, прочие исключения пробрасываются (ошибки программирования должны попадать в crash-репорты);
  - сеттеры — `context.settingsDataStore.edit { … }`; `setVoiceCueIntervalKm` валидирует значение.

---

#### `app/src/main/java/com/example/smarttracker/data/local/TokenStorage.kt`
Контракт хранилища JWT-токенов (access/refresh) и ролей пользователя; находится в `data`, а не `domain`, так как способ хранения — деталь реализации.
- `TokenStorage` (interface) — определяет операции с токенами, ролями и флагом истечения сессии.
  - `fun saveTokens(accessToken: String, refreshToken: String, roleIds: List<Int>): Unit` — атомарно сохраняет оба токена и роли; вызывается после успешной верификации email.
  - `fun getAccessToken(): String?` — возвращает access token или `null`.
  - `fun getRefreshToken(): String?` — возвращает refresh token или `null`.
  - `fun getUserRoles(): List<Int>` — возвращает список ID ролей или пустой список.
  - `fun clearAll(): Unit` — удаляет токены и роли (logout / истечение сессии).
  - `fun hasTokens(): Boolean` — `true`, если оба токена присутствуют (роли на проверку не влияют).
  - `val sessionExpiredFlow: StateFlow<Boolean>` — эмитит `true`, когда оба токена истекли и refresh-запрос тоже вернул 401; UI должен подписаться и при `true` переходить на Login.
  - `fun signalSessionExpired(): Unit` — очищает токены и поднимает `sessionExpiredFlow` в `true`; вызывается из `TokenRefreshAuthenticator` при 401 на refresh-запросе.

Особенности: реализация — `TokenStorageImpl` через `EncryptedSharedPreferences`; привязка интерфейса к реализации — в `AuthModule`. **Контракт отказоустойчивости:** ни один метод не бросает исключений — хранилище вызывается из OkHttp-интерцептора и Authenticator'а, где не-IOException роняет процесс (OkHttp 4 перебрасывает его на dispatcher-потоке). Сбой хранилища выражается `null`/`emptyList`/`false` + лог.

---

#### `app/src/main/java/com/example/smarttracker/data/local/TokenStorageImpl.kt`
Реализация `TokenStorage` на основе `EncryptedSharedPreferences` (AES256) с самовосстановлением при повреждении хранилища.
- `TokenStorageImpl` (class, реализует `TokenStorage`, `@Inject constructor(@ApplicationContext Context)`) — `prefs: SharedPreferences?` инициализируется лениво через `createPrefsWithRecovery()`; `null` = хранилище не удалось создать даже после пересоздания (Keystore полностью недоступен), все операции деградируют до no-op/null. Внутренний `_sessionExpiredFlow: MutableStateFlow(false)`.
  - `private fun createPrefs(): SharedPreferences` — `MasterKey` со схемой `AES256_GCM`, `EncryptedSharedPreferences.create(context, "secure_prefs", masterKey, AES256_SIV, AES256_GCM)`.
  - `private fun createPrefsWithRecovery(): SharedPreferences?` — try/catch вокруг `createPrefs()`: при исключении (AEADBadTagException — повреждённые префы/восстановление из бэкапа; KeyStoreException — глюк Keystore после OTA/смены блокировки) удаляет файл префов (`deleteSharedPreferences`) и master-key из AndroidKeyStore (`deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)`), затем пересоздаёт. Цена — один перелогин вместо вечного краша, лечащегося только очисткой данных приложения. Повторный сбой → `null` + лог.
  - `override val sessionExpiredFlow: StateFlow<Boolean>` — `_sessionExpiredFlow.asStateFlow()`.
  - `override fun saveTokens(accessToken, refreshToken, roleIds)` — под try/catch пишет `KEY_ACCESS_TOKEN`, `KEY_REFRESH_TOKEN`, `KEY_ROLE_IDS` (roleIds через `joinToString(",")`) одной транзакцией `.apply()`; затем сбрасывает `_sessionExpiredFlow.value = false`. Сбой записи логируется, но не бросается (цена — logout при следующем истечении access, сервер уже ротировал refresh).
  - `override fun getAccessToken(): String?` / `getRefreshToken(): String?` — чтение под try/catch, при сбое `null`.
  - `override fun getUserRoles(): List<Int>` — чтение под try/catch; парсинг split по `,`, `mapNotNull { toIntOrNull() }`; при сбое пустой список.
  - `override fun clearAll()` — под try/catch удаляет три ключа одной транзакцией.
  - `override fun signalSessionExpired()` — вызывает `clearAll()`, затем `_sessionExpiredFlow.value = true`.
  - `override fun hasTokens(): Boolean` — `getAccessToken() != null && getRefreshToken() != null`.

Особенности/нюансы: шифрование через `EncryptedSharedPreferences` (файл `"secure_prefs"`) — токены и пароли не должны попадать в обычный `SharedPreferences` или логи (см. чеклист безопасности CLAUDE.md). До введения recovery повреждённое хранилище роняло приложение на старте (`AppViewModel.startRoute` → `hasTokens()`) и на любом сетевом запросе (интерцептор → OkHttp-краш). `MutableStateFlow` не эмитит повторно то же значение (`false == false`), поэтому сброс флага в `saveTokens` в штатном сценарии (без предшествующего expiry) — no-op.

---

#### `app/src/main/java/com/example/smarttracker/data/local/UserProfileCache.kt`
Контракт локального кэша профиля пользователя (`domain.model.User`), чтобы не обращаться повторно к `GET /user/`.
- `UserProfileCache` (interface) — три метода.
  - `fun save(user: User): Unit` — сохраняет профиль; вызывается после успешного `GET /user/` при входе или обновлении.
  - `fun get(): User?` — возвращает сохранённый профиль или `null`, если кэш пуст/повреждён.
  - `fun clear(): Unit` — очищает кэш при выходе из аккаунта.

Особенности: находится в `data`, а не `domain` — деталь реализации хранения. Реализация — `UserProfileCacheImpl` через отдельный `EncryptedSharedPreferences`.

---

#### `app/src/main/java/com/example/smarttracker/data/local/UserProfileCacheImpl.kt`
Реализация `UserProfileCache` на `EncryptedSharedPreferences`, файл `"user_profile_prefs"` (изолирован от токенов в `"secure_prefs"`).
- `UserProfileCacheImpl` (class, реализует `UserProfileCache`, `@Inject constructor(@ApplicationContext Context)`) — `prefs` инициализируется лениво аналогично `TokenStorageImpl` (тот же `MasterKey`/схемы шифрования, но отдельный файл).
  - `override fun save(user: User)` — пишет `KEY_ID` (Int), `KEY_FIRST_NAME`, `KEY_USERNAME`, `KEY_EMAIL`, `KEY_BIRTH_DATE` (через `user.birthDate.toString()` — ISO-8601), `KEY_GENDER` (`user.gender.name`, т.е. `"MALE"`/`"FEMALE"`) в одной транзакции. Nullable-поля (`lastName`, `middleName`, `weight`, `height`, `photoUrl`) — если значение не `null`, пишутся строкой (`weight`/`height` через `.toString()`), иначе соответствующий ключ удаляется (`remove(...)`) — это позволяет отличить "не заполнено" от "не загружено".
  - `override fun get(): User?` — если в хранилище нет `KEY_FIRST_NAME`, кэш считается пустым → `null`. Иначе собирает `User` из сохранённых полей внутри `try/catch`; `LocalDate.parse(...)` для `birthDate`, `Gender.valueOf(...)` для `gender`; `weight`/`height` через `toFloatOrNull()`. При любом исключении парсинга (например, повреждённые данные) — логирует `Log.w(TAG, ...)` и возвращает `null` (кэш будет перезагружен из сети).
  - `override fun clear()` — `prefs.edit().clear().apply()`.

Особенности: `LocalDate` сериализуется как строка `"YYYY-MM-DD"`, парсится нативно (`minSdk=26` → `java.time` доступен без desugaring). Единственное логирование (`Log.w`) содержит только текст исключения, не персональные данные пользователя.

---

### data/local/db

#### `app/src/main/java/com/example/smarttracker/data/local/db/ActivityTypeDao.kt`
DAO для таблицы `activity_types` — кэш видов активности с реактивным чтением.
- `ActivityTypeDao` (`@Dao interface`) —
  - `fun observeAll(): Flow<List<ActivityTypeEntity>>` — `@Query("SELECT * FROM activity_types ORDER BY id ASC")`; Room автоматически переиздаёт список при каждом изменении таблицы (после `upsertAll`).
  - `suspend fun upsertAll(types: List<ActivityTypeEntity>): Unit` — `@Upsert`; вставка или замена строк при обновлении данных из сети (`GET /training/types_activity`).

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/ActivityTypeEntity.kt`
Room-сущность вида активности, кэширующая ответ бэкенда между запусками приложения.
- `ActivityTypeEntity` (`@Entity(tableName = "activity_types") data class`) — `@PrimaryKey val id: Int`, `val name: String`, `val imagePath: String?` (URL иконки от бэкенда; `null` для дефолтных типов и типов без иконки).

Особенности: таблица при первом запуске содержит три захардкоженных дефолта (Бег id=1, Велосипед id=3, Ходьба id=5), вставляемых через `RoomDatabase.Callback.onCreate` в `AuthModule.provideDatabase` (`INSERT OR IGNORE INTO activity_types(id, name, imagePath) VALUES(?,?,?)`).

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/ActivityTypeMapper.kt`
Файл с mapper-функциями между `ActivityTypeEntity` (Room), `ActivityTypeDto` (сеть) и `WorkoutType` (domain); намеренно живёт в data-слое, так как использует `IconCacheManager`.
- `fun ActivityTypeEntity.toDomain(iconCache: IconCacheManager): WorkoutType` — строит `WorkoutType(id, name, iconKey = id.toString(), iconFile = iconCache.getCached(id), imageUrl = imagePath)`; путь к иконке на диске разрешается в момент чтения.
- `fun ActivityTypeDto.toEntity(): ActivityTypeEntity` — прямое отображение `id`, `name`, `imagePath` для upsert после сетевого ответа.

Особенности: domain-слой (`WorkoutType`) остаётся без Room/сетевых импортов — вся связывающая логика (в т.ч. работа с файловым кэшем иконок) вынесена сюда, в data.

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/GpsPointDao.kt`
DAO для таблицы `gps_points` — CRUD и служебные операции над GPS-треком тренировки.
- `GpsPointDao` (`@Dao interface`) —
  - `suspend fun insert(point: GpsPointEntity)` — `@Insert(onConflict = OnConflictStrategy.IGNORE)`; вставка одной точки, `id` игнорируется (`autoGenerate`).
  - `suspend fun insertAll(points: List<GpsPointEntity>)` — `@Insert(onConflict = OnConflictStrategy.IGNORE)`; batch-вставка в одной транзакции Room, используется при сбросе in-memory буфера из `LocationTrackingService`; `IGNORE` предотвращает дубли при crash-recovery.
  - `suspend fun getPointsForTraining(trainingId: String): List<GpsPointEntity>` — `SELECT * FROM gps_points WHERE trainingId = :trainingId ORDER BY timestampUtc ASC`.
  - `suspend fun getUnsentPoints(trainingId: String): List<GpsPointEntity>` — `SELECT * FROM gps_points WHERE trainingId = :trainingId AND isSent = 0 ORDER BY timestampUtc ASC`.
  - `suspend fun assignBatchId(pointIds: List<Long>, batchId: String)` — `UPDATE gps_points SET batchId = :batchId WHERE id IN (:pointIds) AND batchId IS NULL`; условие `batchId IS NULL` гарантирует, что batchId выставляется только один раз (идемпотентность при ретраях синхронизации).
  - `suspend fun markBatchAsSent(batchId: String)` — `UPDATE gps_points SET isSent = 1 WHERE batchId = :batchId`; вызывается после успешной синхронизации батча.
  - `suspend fun deletePointsForTraining(trainingId: String)` — `DELETE FROM gps_points WHERE trainingId = :trainingId`; используется для очистки временных discovery-точек.
  - `fun observePointsForTraining(trainingId: String): Flow<List<GpsPointEntity>>` — `SELECT * FROM gps_points WHERE trainingId = :trainingId ORDER BY timestampUtc ASC`; реактивное наблюдение для UI/карты.
  - `suspend fun getLastPoint(excludedTrainingId: String?): GpsPointEntity?` — 
    ```sql
    SELECT * FROM gps_points
    WHERE (:excludedTrainingId IS NULL OR trainingId != :excludedTrainingId)
    ORDER BY timestampUtc DESC LIMIT 1
    ```
    возвращает последнюю сохранённую точку из прошлых тренировок (для центрирования карты до получения GPS-сигнала), с возможностью исключить активную/черновую тренировку.
  - `suspend fun rekeyTrainingId(oldId: String, newId: String)` — `UPDATE gps_points SET trainingId = :newId WHERE trainingId = :oldId`; используется при офлайн-старте для переключения localUUID → serverUUID.

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/GpsPointEntity.kt`
Room-сущность одной GPS-точки трека тренировки плюс mapper-функции к/от domain-модели `LocationPoint`.
- `GpsPointEntity` (`@Entity(tableName = "gps_points") data class`) — `@PrimaryKey(autoGenerate = true) val id: Long = 0`, `trainingId: String`, `timestampUtc: Long`, `elapsedNanos: Long`, `latitude: Double`, `longitude: Double`, `altitude: Double?`, `speed: Float?`, `accuracy: Float?`, `bearing: Float?` (курс движения [0,360), `null` при малой скорости), `@ColumnInfo(index = true) val externalId: String?` (UUID для идемпотентной синхронизации, индексирован), `batchId: String?`, `isSent: Boolean`, `calories: Double? = null` (расход ккал за интервал, MET-метод; появилось в schema v3).
  - `fun LocationPoint.toEntity(): GpsPointEntity` — маппинг domain → Room; если `externalId` не задан, генерируется `UUID.randomUUID().toString()` — гарантирует уникальность при batch-отправке.
  - `fun GpsPointEntity.toDomain(): LocationPoint` — обратный маппинг, поле в поле.

Особенности: комментарий в файле фиксирует историю схемы — schema v2 добавила `bearing` и `externalId`, schema v3 добавила `calories`.

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/METActivityDao.kt`
DAO для таблиц `met_activities` и `met_zones`, плюс POJO для их атомарного совместного чтения.
- `METActivityWithZones` (data class) — `@Embedded val activity: METActivityEntity`, `@Relation(parentColumn = "typeActivId", entityColumn = "typeActivId") val zones: List<MetZoneEntity>`.
- `METActivityDao` (`@Dao interface`) —
  - `suspend fun getWithZones(typeActivId: Int): METActivityWithZones?` — `@Transaction` + `@Query("SELECT * FROM met_activities WHERE typeActivId = :typeActivId")`; единственная точка чтения, гарантирует, что активность и её зоны читаются согласованно.
  - `suspend fun upsertActivity(activity: METActivityEntity)` — `@Upsert`.
  - `suspend fun deleteZones(typeActivId: Int)` — `@Query("DELETE FROM met_zones WHERE typeActivId = :typeActivId")`; удаляет зоны перед upsert, чтобы не оставались устаревшие записи при сокращении списка зон.
  - `suspend fun upsertZones(zones: List<MetZoneEntity>)` — `@Upsert`.

Особенности: `upsertActivity` + `deleteZones` + `upsertZones` предполагается вызывать вместе как атомарное обновление (в одной транзакции на уровне репозитория).

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/METActivityEntity.kt`
Room-сущность MET-конфигурации вида активности (кэш ответа `GET /training/met/{type_activ_id}`).
- `METActivityEntity` (`@Entity(tableName = "met_activities") data class`) — `@PrimaryKey val typeActivId: Int` (совпадает с `ActivityTypeEntity.id`), `val baseMet: Double` (используется когда `usesSpeedZones == false`), `val usesSpeedZones: Boolean`, `val cachedAt: Long` (`System.currentTimeMillis()`, используется для TTL).

Особенности: TTL кэша — 24 часа (сравнение с `cachedAt` происходит на уровне репозитория/use-case, не в самой entity). Предзагрузка запускается в фоне из `WorkoutRepositoryImpl.refreshFromNetwork`.

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/METMapper.kt`
Mapper-функции между Room-сущностями MET-данных, сетевыми DTO и domain-моделями.
- `fun METActivityWithZones.toDomain(): METActivity` — собирает `METActivity(baseMet, usesSpeedZones, zones = zones.sortedBy { speedMin }.map { it.toDomain() })`.
- `private fun MetZoneEntity.toDomain(): MetZone` — маппинг зоны; `speedMax` конвертируется: если `>= Double.MAX_VALUE`, в domain становится `Double.POSITIVE_INFINITY` (обратное преобразование сентинела).
- `fun METActivityResponseDto.toEntity(typeActivId: Int, cachedAt: Long): METActivityEntity` — прямое отображение полей DTO + переданные `typeActivId`/`cachedAt`.
- `fun MetZoneDto.toEntity(typeActivId: Int): MetZoneEntity` — `speedMax = speedMax ?: Double.MAX_VALUE` (null от бэкенда = "нет верхней границы", хранится как сентинел `MAX_VALUE`, а не `POSITIVE_INFINITY`, поскольку SQLite не гарантирует корректное хранение IEEE754 ±∞ через тип `REAL`).

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/MetZoneEntity.kt`
Room-сущность одной зоны скоростей MET-таблицы, связанная с `METActivityEntity` через внешний ключ с каскадным удалением.
- `MetZoneEntity` (`@Entity(tableName = "met_zones", foreignKeys = [ForeignKey(entity = METActivityEntity::class, parentColumns = ["typeActivId"], childColumns = ["typeActivId"], onDelete = ForeignKey.CASCADE)], indices = [Index("typeActivId")]) data class`) — `@PrimaryKey(autoGenerate = true) val id: Long = 0`, `val typeActivId: Int`, `val speedMin: Double`, `val speedMax: Double` (сентинел `Double.MAX_VALUE` вместо `POSITIVE_INFINITY`), `val metValue: Double`.

Особенности: при удалении родительской `METActivityEntity` связанные зоны удаляются автоматически (`CASCADE`). Обратное преобразование сентинела `MAX_VALUE → POSITIVE_INFINITY` выполняется в `METMapper.kt`.

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/PendingFinishDao.kt`
DAO для офлайн-очереди запросов завершения тренировки.
- `PendingFinishDao` (`@Dao interface`) —
  - `suspend fun insert(entity: PendingFinishEntity)` — `@Insert(onConflict = OnConflictStrategy.IGNORE)`; если запись с таким `trainingId` уже есть — не перезаписывается (защита от повторного нажатия «Завершить» без сети).
  - `suspend fun getAll(): List<PendingFinishEntity>` — `@Query("SELECT * FROM pending_finishes")`; используется воркером для обработки очереди.
  - `suspend fun getById(trainingId: String): PendingFinishEntity?` — `@Query("SELECT * FROM pending_finishes WHERE trainingId = :trainingId LIMIT 1")`.
  - `suspend fun delete(trainingId: String)` — `@Query("DELETE FROM pending_finishes WHERE trainingId = :trainingId")`; удаляет успешно отправленный запрос.

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/PendingFinishEntity.kt`
Room-сущность локальной очереди незавершённых запросов на завершение тренировки (для офлайн-first синхронизации).
- `PendingFinishEntity` (`@Entity(tableName = "pending_finishes") data class`) — `@PrimaryKey val trainingId: String` (один UUID тренировки не может быть завершён дважды; `OnConflictStrategy.IGNORE` в DAO обеспечивает идемпотентность), `val timeEnd: String` (ISO 8601 UTC, зафиксирован в момент нажатия «Завершить»), `val totalDistanceMeters: Double?`, `val totalKilocalories: Double?`, `val typeActivId: Int? = null` (non-null только для тренировок, начатых офлайн — нужен `SyncGpsPointsWorker`, чтобы сначала зарегистрировать тренировку на сервере, затем переключить точки на serverUUID), `val timeStart: String? = null` (реальное время старта офлайн-тренировки; без него time_start на сервере равен моменту синхронизации, что может дать `time_end < time_start`).

Особенности: запись создаётся, когда `WorkoutStartViewModel` не смог выполнить `POST /training/{id}/save_training` из-за отсутствия сети; `SaveTrainingWorker` при появлении сети читает таблицу, доставляет запросы и удаляет успешно отправленные строки.

---

#### `app/src/main/java/com/example/smarttracker/data/local/db/SmartTrackerDatabase.kt`
Единственный класс базы данных Room приложения — объявляет все entity, DAO-акцессоры и историю миграций схемы.
- `SmartTrackerDatabase` (`@Database(entities = [GpsPointEntity::class, ActivityTypeEntity::class, PendingFinishEntity::class, METActivityEntity::class, MetZoneEntity::class], version = 8, exportSchema = false) abstract class : RoomDatabase()`.
  - `abstract fun gpsPointDao(): GpsPointDao`
  - `abstract fun activityTypeDao(): ActivityTypeDao`
  - `abstract fun pendingFinishDao(): PendingFinishDao`
  - `abstract fun metActivityDao(): METActivityDao`
  - `companion object` содержит объекты миграций:
    - `MIGRATION_5_6` (`Migration(5, 6)`) — `ALTER TABLE pending_finishes ADD COLUMN typeActivId INTEGER` (для офлайн-старта тренировок).
    - `MIGRATION_6_7` (`Migration(6, 7)`) — `ALTER TABLE pending_finishes ADD COLUMN timeStart TEXT` (реальное время старта офлайн-тренировки для корректного `time_start` на бэкенде).
    - `MIGRATION_7_8` (`Migration(7, 8)`) — создаёт две новые таблицы одним SQL-блоком каждая:
      ```sql
      CREATE TABLE met_activities (
          typeActivId INTEGER PRIMARY KEY NOT NULL,
          baseMet REAL NOT NULL,
          usesSpeedZones INTEGER NOT NULL,
          cachedAt INTEGER NOT NULL
      )
      ```
      ```sql
      CREATE TABLE met_zones (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          typeActivId INTEGER NOT NULL,
          speedMin REAL NOT NULL,
          speedMax REAL NOT NULL,
          metValue REAL NOT NULL,
          FOREIGN KEY(typeActivId) REFERENCES met_activities(typeActivId) ON DELETE CASCADE
      )
      ```
      ```sql
      CREATE INDEX index_met_zones_typeActivId ON met_zones (typeActivId)
      ```

**Версии схемы (из KDoc и кода):**
- version 2 — добавлены `GpsPointEntity.bearing` (Float?) и `GpsPointEntity.externalId` (String?).
- version 3 — добавлено `GpsPointEntity.calories` (Double?).
- version 4 — добавлена таблица `ActivityTypeEntity` (кэш видов активности, stale-while-revalidate).
- version 5 — добавлена таблица `PendingFinishEntity` (очередь офлайн-завершений).
- version 6 (`MIGRATION_5_6`) — `pending_finishes.typeActivId`.
- version 7 (`MIGRATION_6_7`) — `pending_finishes.timeStart`.
- version 8 (`MIGRATION_7_8`) — таблицы `METActivityEntity` и `MetZoneEntity` (кэш MET-коэффициентов, TTL 24 часа).

**Seeding callback** (объявлен не в этом файле, а в месте создания `Room.databaseBuilder` — `AuthModule.provideDatabase`, `app/src/main/java/com/example/smarttracker/di/AuthModule.kt`): через `.addCallback(object : RoomDatabase.Callback() { override fun onCreate(db) {...} })` при первом создании БД вставляются три дефолтных вида активности (`INSERT OR IGNORE INTO activity_types(id, name, imagePath) VALUES(?,?,?)`): id=1 "Бег", id=3 "Велосипед", id=5 "Ходьба" (все с `imagePath = null`). `AuthModule` также подключает миграции через `.addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)` и `.fallbackToDestructiveMigration()` — если версия схемы скакнёт без соответствующей миграции (например, добавили entity, но забыли написать `Migration`), база будет пересоздана с потерей данных. Это осознанно допустимо, так как данные тренировок хранятся только на устройстве и не считаются критичными на текущем этапе разработки.

Особенности/нюансы: `exportSchema = false` отключает генерацию JSON-схемы Room. Экземпляр — синглтон, создаётся один раз через Hilt.

---

### data/cache

#### `app/src/main/java/com/example/smarttracker/data/cache/RoleGoalCache.kt`
In-memory кэш ответов `GET /role/` и `GET /goal/` с TTL и stale-разрешением, без персистентности между запусками процесса.
- `RoleGoalCache` (class, `@Singleton`, `@Inject constructor()`) — хранит `cachedRoles: List<RoleResponseDto>?` + `rolesTimestamp: Long`, а также `cachedGoals: MutableMap<Int?, List<GoalResponseDto>>` и `goalsTimestamp: MutableMap<Int?, Long>` (ключ — `roleId`, `null` означает «все цели без фильтра»). Константа `CACHE_TTL_MS = 3600 * 1000L` (1 час). Все операции чтения/записи обёрнуты в `synchronized(this)` для потокобезопасности.
  - `fun getCachedRoles(): List<RoleResponseDto>?` — возвращает `cachedRoles`, если `isRolesCacheValid()`, иначе `null`.
  - `fun setCachedRoles(roles: List<RoleResponseDto>): Unit` — сохраняет роли и обновляет `rolesTimestamp = System.currentTimeMillis()`.
  - `private fun isRolesCacheValid(): Boolean` — `cachedRoles != null && (now - rolesTimestamp) < CACHE_TTL_MS`.
  - `fun getCachedGoals(roleId: Int?): List<GoalResponseDto>?` — возвращает `cachedGoals[roleId]`, если `isGoalsCacheValid(roleId)`, иначе `null`.
  - `fun setCachedGoals(roleId: Int?, goals: List<GoalResponseDto>): Unit` — сохраняет цели для `roleId` и таймстамп.
  - `private fun isGoalsCacheValid(roleId: Int?): Boolean` — проверяет наличие записи в `goalsTimestamp` и что `(now - timestamp) < CACHE_TTL_MS`.
  - `fun clearCache(): Unit` — полностью очищает оба кэша (роли и цели); вызывается при logout или других критических событиях.
  - `fun invalidateGoalsCache(): Unit` — очищает только кэш целей (например, при изменении набора ролей).

Особенности: чисто in-memory — при killed-процессе кэш теряется полностью (персистентность между сеансами не реализована в этом классе). TTL одинаков (1 час) и для ролей, и для целей.

---

### Тесты data/local (androidTest)

#### `app/src/androidTest/java/com/example/smarttracker/data/local/db/GpsPointDaoTest.kt`
Инструментальные тесты (`@RunWith(AndroidJUnit4::class)`) для `GpsPointDao`, выполняются на устройстве/эмуляторе с Room in-memory базой (`Room.inMemoryDatabaseBuilder(..., SmartTrackerDatabase::class.java).allowMainThreadQueries().build()`), пересоздаваемой в `@Before setUp()` и закрываемой в `@After tearDown()`.

Покрытые сценарии (`@Test`):
- `assignBatchId_не_перезаписывает_уже_назначенный_batchId` — проверяет, что повторный вызов `assignBatchId` с новым `batchId` не меняет уже назначенный (`AND batchId IS NULL` в SQL делает операцию идемпотентной).
- `getLastPoint_с_null_возвращает_последнюю_точку_без_фильтра` — при `excludedTrainingId = null` возвращается самая свежая точка вне зависимости от `trainingId`.
- `getLastPoint_исключает_активную_тренировку` — при переданном `excludedTrainingId` метод исключает точки этой тренировки и возвращает последнюю из оставшихся.
- `getLastPoint_возвращает_null_если_все_тренировки_исключены` — если единственная тренировка в базе совпадает с `excludedTrainingId`, метод возвращает `null`.

Вспомогательные приватные функции: `makePoint(trainingId, timestampUtc)` — фабрика `GpsPointEntity` с дефолтными координатами (55.7558, 37.6173) и нулевыми optional-полями; `GpsPointDao.insertAndReturnId(point)` — вставляет точку и возвращает сгенерированный `id`, читая последнюю запись через `getPointsForTraining(...).last().id` (обходной путь, так как suspend `@Insert` в этом DAO не возвращает id напрямую).

---

### Итоги по data/local и data/cache

Хранилища проекта используют три разных паттерна в зависимости от чувствительности и природы данных. Для секретов (JWT access/refresh токены — `TokenStorageImpl`) и персональных данных профиля (`UserProfileCacheImpl`) применяется `EncryptedSharedPreferences` с `MasterKey` на схеме `AES256_GCM` и раздельным шифрованием ключей/значений (`AES256_SIV` / `AES256_GCM`); эти два кэша физически разделены на разные файлы (`secure_prefs` и `user_profile_prefs`), чтобы их можно было независимо очищать. Для несекретных небольших данных (выбранные роли на регистрации — `RoleConfigStorageImpl`) используется обычный незашифрованный `SharedPreferences`. Для файлового кэша (иконки активностей — `IconCacheManager`) — `filesDir`, переживающий системную очистку памяти, с отдельным `SharedPreferences` для отслеживания URL последней загрузки на файл. Для короткоживущих сетевых ответов (роли и цели — `RoleGoalCache`) — чистый in-memory кэш с TTL 1 час и потокобезопасностью через `synchronized`, полностью теряемый при убийстве процесса.

Room-база `SmartTrackerDatabase` находится на версии 8 и прошла историю из как минимум пяти изменений схемы: v2 (добавление `bearing`/`externalId` в GPS-точки), v3 (`calories`), v4 (таблица `ActivityTypeEntity`), v5 (таблица `PendingFinishEntity`), v6→v7→v8 — с явными объектами `Migration` (`MIGRATION_5_6`, `MIGRATION_6_7`, `MIGRATION_7_8`), выполняющими `ALTER TABLE` и `CREATE TABLE`/`CREATE INDEX` через `execSQL`. Более ранние версии (до 5) не имеют явных Migration-объектов в коде — для них (и как общий fallback при пропущенных версиях) используется `fallbackToDestructiveMigration()`, что означает потерю локальных данных тренировок при неожиданном скачке версии; это осознанное решение, так как данные тренировок хранятся только на устройстве и пока не считаются критичными (production-миграционная стратегия планируется позже).

DAO-слой в целом построен вокруг Kotlin Flow для реактивных экранов (`ActivityTypeDao.observeAll`, `GpsPointDao.observePointsForTraining`) и suspend-функций для разовых операций. Есть два кэш-стратегических паттерна поверх Room: stale-while-revalidate для видов активности (таблица заполняется дефолтными записями при первом создании БД через `RoomDatabase.Callback.onCreate`, далее обновляется через `upsertAll` при каждом сетевом ответе) и TTL-кэш для MET-коэффициентов (`cachedAt` в `METActivityEntity`, логика инвалидации на 24 часа реализуется в репозитории, а не в самой entity). Отдельного внимания заслуживает решение хранить "нет верхней границы скорости" как `Double.MAX_VALUE` вместо `Double.POSITIVE_INFINITY` в `MetZoneEntity` — обходит ограничение SQLite на надёжное хранение IEEE754 бесконечности через тип `REAL`; конвертация туда-обратно инкапсулирована в `METMapper.kt`. Внешний ключ `MetZoneEntity → METActivityEntity` с `ON DELETE CASCADE` обеспечивает согласованность: удаление активности автоматически удаляет её зоны скоростей. Инструментальный тест `GpsPointDaoTest` подтверждает два поведенческих контракта DAO: идемпотентность `assignBatchId` (защита от повторного назначения batchId при ретраях синхронизации) и корректную фильтрацию `getLastPoint` по `excludedTrainingId`, что критично для логики центрирования карты при старте новой тренировки.

---

## 2.3 data/remote/ — Retrofit API и DTO


### Retrofit-сервисы

#### `data/remote/AuthApiService.kt`
Retrofit-интерфейс со всеми эндпоинтами авторизации, регистрации, восстановления пароля, ролей/целей и профиля пользователя. Все методы — `suspend fun`, реализация генерируется Retrofit во время сборки; BASE_URL задаётся в `AuthModule` (Hilt).

Полный список методов:
- `@POST("auth/register") suspend fun register(@Body request: RegisterRequestDto): RegisterResultDto` — регистрация нового пользователя; бэкенд создаёт запись `is_active=false` и шлёт код на email.
- `@POST("auth/verify-email") suspend fun verifyEmail(@Body request: EmailVerificationDto): AuthResponseDto` — подтверждение email 6-значным кодом; при успехе `is_active=true` + пара токенов.
- `@POST("auth/resend-code") suspend fun resendCode(@Body request: ResendEmailDto): ResendCodeResponseDto` — повторная отправка кода подтверждения (не чаще раза в 2 минуты, логика на бэкенде).
- `@POST("auth/login") suspend fun login(@Body request: LoginRequestDto): AuthResponseDto` — вход для подтверждённых пользователей, возвращает новую пару access/refresh токенов.
- `@POST("auth/refresh") suspend fun refreshToken(@Body request: RefreshTokenRequestDto): AuthResponseDto` — обновление access token; refresh_token передаётся в JSON-теле (FastAPI `Body(...)`), НЕ query-параметром.
- `@POST("auth/check-nickname") suspend fun checkNickname(@Body request: NicknameCheckRequestDto): NicknameCheckResponseDto` — проверка доступности nickname.
- `@POST("password-reset/request") suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): ForgotPasswordResponseDto` — инициация восстановления пароля, код на email.
- `@POST("password-reset/verify-code") suspend fun verifyResetCode(@Body request: EmailVerificationDto): VerifyResetCodeResponseDto` — проверка кода сброса пароля.
- `@POST("password-reset/resend-verify-code") suspend fun resendResetCode(@Body request: ResendResetCodeRequestDto): ResendResetCodeResponseDto` — повторная отправка кода восстановления.
- `@POST("password-reset/confirm") suspend fun resetPassword(@Body request: ResetPasswordRequestDto): ResetPasswordResponseDto` — финализация восстановления пароля (проверка кода + установка нового пароля).
- `@GET("role/") suspend fun getRoles(): List<RoleResponseDto>` — получение всех доступных ролей (МОБ-6.3).
- `@GET("role/user_roles") suspend fun getUserRoles(): List<RoleDto>` — роли текущего пользователя; без параметров, авторизация только через Bearer-токен (email-параметр убран, МОБ-6.2).
- `@GET("goal/") suspend fun getGoals(): List<GoalResponseDto>` — цели для Step 2 регистрации, каждая привязана к роли (`id_role`); кешируется на 1 час.
- `@GET("training/types_activity") suspend fun getActivityTypes(): List<ActivityTypeDto>` — типы активности (id, name, опциональный image_url).
- `@GET("user/") suspend fun getUserInfo(): UserInfoResponseDto` — профиль текущего пользователя по Bearer-токену (weight/height для MET-калорий), путь подтверждён через openapi.json.
- `@PATCH("user/edit") suspend fun updateProfile(@Body request: UpdateProfileRequestDto): UserInfoResponseDto` — редактирование профиля; все поля nullable, бэкенд обновляет только непустые.
- `@Multipart @POST("user/photo") suspend fun uploadPhoto(@Part photo: MultipartBody.Part)` — загрузка фото профиля (jpg/png до 5 МБ), ответ пустой, обновление получают повторным `GET /user/`.
- `@DELETE("user/photo") suspend fun deletePhoto()` — удаление фото профиля, бэкенд подставляет плейсхолдер.
- `@DELETE("user/delete") suspend fun deleteAccount()` — удаление аккаунта, после успеха токены недействительны.

Особенности: комментарий явно фиксирует историческую ошибку — путь `/auth/refresh` раньше пробовали как `@Query`, что давало 422; правильно — JSON `@Body`. Аналогично для `getUserRoles()` — было ошибочное предположение о `@Query("email")`, убрано в пользу Bearer-токена.

#### `data/remote/TokenRefreshAuthenticator.kt`
`OkHttp Authenticator` (не `Interceptor`!) — автоматически вызывается при HTTP 401, обновляет access token и повторяет исходный запрос.

- `TokenRefreshAuthenticator` (класс, `@Singleton`, DI через `@Inject constructor(tokenStorage: TokenStorage, @Named("baseUrl") baseUrl: String)`) реализует интерфейс `Authenticator`.
  - Имеет собственный `refreshClient = OkHttpClient()` — простой клиент без interceptors/authenticator, чтобы разорвать циклическую зависимость Hilt (основной `OkHttpClient` использует этот `Authenticator`, который иначе снова обращался бы к `AuthApiService`, построенному на том же `OkHttpClient`).
  - `override fun authenticate(route: Route?, response: Response): Request?` — аннотирован `@Synchronized` (защита от гонки при двух параллельных 401). Логика:
    1. Если сам 401-ответ пришёл от `auth/refresh` (проверка по `encodedPath`) — сессия полностью истекла, вызывается `tokenStorage.signalSessionExpired()`, возврат `null`.
    2. Через `responseCount(response)` (приватная функция, считает цепочку `priorResponse`) проверяется число уже сделанных попыток; если `>= 2` — прекратить, вернуть `null` (защита от бесконечного цикла повторов).
    3. Читает `refreshToken` из `tokenStorage.getRefreshToken()`; если `null` — пользователь не авторизован, возврат `null`.
    4. Строит `POST {baseUrl}/auth/refresh` с JSON-телом `{"refresh_token": "..."}` через Gson, выполняет синхронно `refreshClient.newCall(...).execute()`.
    5. При сетевой ошибке (`catch Exception`) — `null`.
    6. Разбор ответа внутри `response.use { }`: если код не успешный — при 4xx (`resp.code in 400..499`) вызывается `signalSessionExpired()` (refresh token невалиден/истёк/422 — постоянная ошибка); при 5xx — просто `null` без логаута (временная ошибка сервера).
    7. Пустое тело ответа — `null` с логом ошибки.
    8. `gson.fromJson(body, AuthResponseDto::class.java)` — при ошибке парсинга `null`.
    9. Успех: читает текущие `currentRoles = tokenStorage.getUserRoles()`, сохраняет новую пару токенов через `tokenStorage.saveTokens(accessToken, refreshToken, currentRoles)` — роли НЕ меняются при refresh (обновляются только при login).
    10. Возвращает копию исходного запроса с новым заголовком `Authorization: Bearer <accessToken>`.
  - `responseCount(response: Response): Int` (приватная) — считает длину цепочки `priorResponse`, начиная с 1 за сам ответ.

Особенности/нюансы: комментарии в коде подробно объясняют, почему нужен именно `Authenticator`, а не `Interceptor` (встроенная защита от зацикливания + семантика 401), и почему нужен отдельный `refreshClient` (иначе циклическая DI-зависимость). `@Synchronized` предотвращает ситуацию, когда два потока читают один и тот же устаревший refresh-токен и второй запрос сбрасывает сессию. Все обращения к `TokenStorage` (`getRefreshToken`, `getUserRoles`/`saveTokens`) обёрнуты в try/catch — `authenticate()` выполняется на OkHttp-потоке, где необработанное исключение роняет процесс; при сбое чтения — `null` (штатный 401), при сбое записи новых токенов — повторный запрос всё равно строится с новым access-токеном (цена — logout при следующем истечении).

#### `data/remote/AuthInterceptor.kt`
Фабрика auth-интерцептора, вынесенная из `AuthModule` в отдельную top-level функцию, чтобы `AuthTokenFlowTest` тестировал реальную логику, а не инлайн-копию.
- `fun buildAuthInterceptor(tokenStorage: TokenStorage, apiHost: String): Interceptor` — возвращает интерцептор, добавляющий `Authorization: Bearer <token>` только когда токен непустой (`!isNullOrBlank()`) **и** хост запроса совпадает с `apiHost`. Токен читается из `TokenStorage` в момент каждого запроса.

Особенности/нюансы: host-check — защита от утечки JWT: тот же `OkHttpClient` используется Coil (фото профиля) и `IconCacheManager` (иконки активностей), а URL картинок приходят с сервера (`image_path`) — без проверки хоста токен ушёл бы на любой внешний CDN, который бэкенд однажды укажет в `image_path`. `getAccessToken()` обёрнут в try/catch (страховка поверх контракта `TokenStorage` «не бросает»): не-IOException из интерцептора OkHttp 4 перебрасывает на dispatcher-потоке → краш процесса на любом запросе; при сбое хранилища запрос уходит без `Authorization` → штатный 401.

#### `data/remote/TrainingApiService.kt`
Retrofit-интерфейс для эндпоинтов тренировок; отдельный bounded context от `AuthApiService`, использует тот же `Retrofit`/`OkHttpClient` (с auth-интерцептором). Все методы требуют Bearer-токен, добавляемый интерцептором автоматически.

Полный список методов:
- `@POST("training/start") suspend fun startTraining(@Body request: TrainingStartRequestDto): TrainingStartResponseDto` — начать тренировку, бэкенд создаёт активную запись и возвращает UUID.
- `@GET("training/active") suspend fun getActiveTraining(): ActiveTrainingResponseDto` — получить активную тренировку пользователя (восстановление после краша).
- `@POST("training/{training_id}/gps_points") suspend fun uploadGpsPoints(@Path("training_id") trainingId: String, @Body request: GpsPointsBatchRequestDto): GpsPointsSaveResponseDto` — загрузка батча GPS-точек (максимум 100 за запрос), `batch_id` обеспечивает идемпотентность.
- `@POST("training/{training_id}/save_training") suspend fun saveTraining(@Path("training_id") trainingId: String, @Body request: TrainingSaveRequestDto): TrainingSaveResponseDto` — завершение тренировки, фиксация времени окончания и итоговой статистики.
- `@GET("training/met/{type_activ_id}") suspend fun getMETActivity(@Path("type_activ_id") typeActivId: Int): METActivityResponseDto` — MET-конфигурация для расчёта калорий (Compendium of Physical Activities 2024); при `usesSpeedZones == true` нужна интерполяция по `zones`.
- `@GET("training/history") suspend fun getTrainingHistory(): List<TrainingHistoryResponseDto>` — история тренировок пользователя (может быть пустой).
- `@GET("training/{training_id}/get_training") suspend fun getTrainingDetail(@Path("training_id") trainingId: String): GetTrainingDetailResponseDto` — полные данные тренировки + GPS-трек для `SummaryOverlay` из истории.
- `@DELETE("training/{training_id}/delete") suspend fun deleteTraining(@Path("training_id") trainingId: String)` — удаление тренировки, тестовый эндпоинт, не используется в production-потоке.
- `@DELETE("training/{training_id}/delete_completed") suspend fun deleteCompletedTraining(@Path("training_id") trainingId: String)` — удаление завершённой тренировки из истории (вызывается из `SummaryOverlay`, origin = HISTORY); после 200/204 репозиторий эмитит `historyChangedFlow`, и `TrainingHistoryViewModel` перезагружает список.

---

### DTO (data/remote/dto/)

#### `data/remote/dto/ActiveTrainingDto.kt`
DTO ответа `GET /training/active` — используется для восстановления после краша, чтобы проверить наличие незавершённой тренировки на сервере.
- `ActiveTrainingResponseDto` (data class): `activeTrainingId: String` (`@SerializedName("active_training_id")`), `typeActivId: Int` (`@SerializedName("type_activ_id")`), `date: String`, `timeStart: String` (`@SerializedName("time_start")`), `trainingTime: Int` (`@SerializedName("training_time")`, секунды), `isPause: Boolean` (`@SerializedName("is_pause")`), `kilocalories: Double`. Mapper-функций нет в этом файле.

#### `data/remote/dto/ActivityTypeDto.kt`
DTO ответа `GET /training/types_activity` и вспомогательная функция маппинга в ключ иконки.
- `ActivityTypeDto` (data class): `id: Int` (`@SerializedName("type_activ_id")`), `name: String`, `imagePath: String?` (`@SerializedName("image_path")`, дефолт `null`).
- `fun ActivityTypeDto.toIconKey(): String` — возвращает `id.toString()`. Комментарий подчёркивает: используется ID, а НЕ имя, т.к. имя зависит от языка API и может измениться; маппинг ID→drawable — в `iconResForKey()` в `WorkoutStartScreen`, отсутствующий ID даёт `ic_activity_other` (placeholder).

#### `data/remote/dto/AuthRequestDtos.kt`
Вспомогательные DTO для тел запросов `POST /auth/resend-code` и `POST /auth/login`.
- `ResendEmailDto` (data class): `email: String`. Соответствует схеме `EmailVerificationRequest` на бэкенде.
- `LoginRequestDto` (data class): `email: String`, `password: String`. Соответствует схеме `UserLogin`.
Комментарий поясняет, что `POST /auth/refresh` не использует эти DTO — там отдельный `RefreshTokenRequestDto` с телом, а не query-параметр.

#### `data/remote/dto/AuthResponseDto.kt`
DTO для ответов эндпоинтов, возвращающих токены (`verify-email`, `login`, `refresh`), соответствует схеме `TokenResponse`.
- `AuthResponseDto` (data class): `accessToken: String` (`@SerializedName("access_token")`), `refreshToken: String` (`@SerializedName("refresh_token")`), `tokenType: String = "bearer"` (`@SerializedName("token_type")`).
- `fun AuthResponseDto.toDomain(): AuthResult` — прямой маппинг полей 1-в-1 в `AuthResult`.

#### `data/remote/dto/EmailVerificationDto.kt`
DTO для запроса `POST /auth/verify-email` (и переиспользуется для `password-reset/verify-code`).
- `EmailVerificationDto` (data class): `email: String`, `code: String` (6 символов, `min_length=6, max_length=6` на бэкенде). Mapper не нужен — объект создаётся напрямую в `AuthRepositoryImpl`.

#### `data/remote/dto/ForgotPasswordRequestDto.kt`
DTO запроса на первом шаге восстановления пароля.
- `ForgotPasswordRequestDto` (data class): `email: String`. Используется в `POST /password-reset/request`.

#### `data/remote/dto/ForgotPasswordResponseDto.kt`
DTO ответа `POST /password-reset/request`.
- `ForgotPasswordResponseDto` (data class): `message: String? = null` — бэкенд возвращает пустой объект `{}` при успехе, поэтому поле nullable с дефолтом.
- `fun ForgotPasswordResponseDto.toDomain(): ForgotPasswordResult` — `message ?: "Код отправлен на email"` (дефолтный текст при отсутствии сообщения от сервера).

#### `data/remote/dto/GetTrainingDetailResponseDto.kt`
DTO ответа `GET /training/{training_id}/get_training`, самый сложный DTO в проекте по логике маппинга GPS-трека.
- `GetTrainingDetailResponseDto` (data class): `trainingId: String` (`@SerializedName("training_id")`), `typeActivId: Int` (`@SerializedName("type_activ_id")`), `date: String`, `timeStart: String` (`@SerializedName("time_start")`), `timeEnd: String?` (`@SerializedName("time_end")`), `kilocalories: Double?`, `distanceM: Double?` (`@SerializedName("distance_m")`), `avgSpeed: Double?` (`@SerializedName("avg_speed")`), `elevationGain: Double?` (`@SerializedName("elevation_gain")`), `gpsTrack: JsonElement?` (`@SerializedName("gps_track")`, сырой GeoJSON LineString — используется `JsonElement?`, чтобы Gson не падал на любом формате), `gpsPointsTimestamps: List<String>?` (`@SerializedName("gps_points_timestamps")`, параллельный массив ISO-таймстемпов по индексу).
- `fun GetTrainingDetailResponseDto.gpsPointsToDomain(): List<LocationPoint>` — разбирает `gpsTrack` (`{"type":"LineString","coordinates":[[lon,lat,alt], ...]}`), для каждой точки:
  - координаты в порядке GeoJSON (longitude первым!): `arr[0]` → longitude, `arr[1]` → latitude, `arr[2]` (если есть) → altitude;
  - таймстемп берётся из `gpsPointsTimestamps[index]`, парсится сначала как `OffsetDateTime`, при ошибке — как `LocalDateTime` с явным `ZoneOffset.UTC` (бэк отдаёт два формата: со смещением `...613000Z` и без смещения `...705000`); при полном провале парсинга — fallback `index.toLong()`;
  - `speed`/`accuracy` не передаются сервером → всегда `null`.
  - Комментарий явно указывает: корректные `timestampUtc` нужны `WorkoutSummaryViewModel.buildCumulativeData` для расчёта `elapsedMs` — индикатора scrub-оверлея истории.

Особенности: это ровно тот DTO, что упомянут в TODO проекта — планируется замена `gps_track` (GeoJSON без таймстемпов) на массив объектов с `recorded_at`, после чего `JsonElement?` предполагается убрать в пользу типизированного `List<GpsTrackPointDto>?`.

#### `data/remote/dto/GoalResponseDto.kt`
DTO ответа `GET /goal/` (МОБ-6, цели для Step 2 регистрации).
- `GoalResponseDto` (data class): `id: Int` (`@SerializedName("goal_id")`), `description: String`, `roleId: Int` (`@SerializedName("id_role")`).
- `fun GoalResponseDto.toDomain(): GoalResponse` — прямой маппинг 1-в-1.

#### `data/remote/dto/GpsPointsSyncDto.kt`
Группа DTO для отправки батчей GPS-точек на сервер (`POST /training/{training_id}/gps_points`).
- `GpsPointDto` (data class): `recordedAt: String` (`@SerializedName("recorded_at")`, ISO 8601 UTC), `latitude: Double`, `longitude: Double`, `accuracy: Float? = null`, `altitude: Double? = null`, `speed: Float? = null`, `calories: Double? = null` (расход калорий за интервал до точки; `null`, если профиль пользователя — вес/рост/возраст — ещё не заполнен).
- `GpsPointsBatchRequestDto` (data class): `batchId: String` (`@SerializedName("batch_id")`, UUID для идемпотентности), `points: List<GpsPointDto>` (максимум 100 точек за запрос).
- `GpsPointsSaveResponseDto` (data class): `saved: Int`, `message: String`.
- `fun LocationPoint.toGpsPointDto(): GpsPointDto` — конвертирует `timestampUtc` (epoch millis) в ISO 8601 UTC строку через `Instant.ofEpochMilli(...).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)`; остальные поля копируются как есть. Комментарий: minSdk=26 → `java.time` доступен нативно, desugaring не требуется.

#### `data/remote/dto/METActivityResponseDto.kt`
DTO ответа `GET /training/met/{type_activ_id}` — MET-коэффициенты для расчёта калорий по методу Compendium of Physical Activities 2024.
- `METActivityResponseDto` (data class): `baseMet: Double` (`@SerializedName("base_met")`), `usesSpeedZones: Boolean` (`@SerializedName("uses_speed_zones")`), `zones: List<MetZoneDto>`.
- `MetZoneDto` (data class): `speedMin: Double` (`@SerializedName("speed_min")`), `speedMax: Double?` (`@SerializedName("speed_max")`, `null` для последней открытой зоны), `metValue: Double` (`@SerializedName("met_value")`).
- `fun METActivityResponseDto.toDomain(): METActivity` — маппинг `zones`, где `speedMax ?: Double.POSITIVE_INFINITY`. Комментарий объясняет, зачем именно `POSITIVE_INFINITY`, а не `0.0` или `MAX_VALUE`: guard-проверка в `CalorieCalculator.interpolateMet` вида `if (speedKmh >= zones.last().speedMax)` при `null → 0.0` всегда была бы истинной (баг), а `POSITIVE_INFINITY` семантически точно означает «нет верхней границы».

#### `data/remote/dto/NicknameCheckResponseDto.kt`
DTO для запроса/ответа проверки доступности nickname (`POST /auth/check-nickname`).
- `NicknameCheckResponseDto` (data class): `nickname: String`, `isAvailable: Boolean` (`@SerializedName("is_available")`), `message: String`.
- `fun NicknameCheckResponseDto.toDomain(): NicknameCheckResponse` — прямой маппинг 1-в-1.
- `NicknameCheckRequestDto` (data class): `nickname: String` — тело запроса.

#### `data/remote/dto/RefreshTokenRequestDto.kt`
Тело запроса `POST /auth/refresh`.
- `RefreshTokenRequestDto` (data class): `refreshToken: String` (`@SerializedName("refresh_token")`). Комментарий подтверждает: поле передаётся в JSON-теле (FastAPI `Body(...)`), не query-параметром — критический нюанс проекта (см. CLAUDE.md пункт 4).

#### `data/remote/dto/RegisterRequestDto.kt`
DTO запроса `POST /auth/register` вместе с mapper-ом из domain.
- `RegisterRequestDto` (data class): `firstName: String` (`@SerializedName("first_name")`), `username: String` (`@SerializedName("nickname")` — критический нюанс username↔nickname), `birthDate: String` (`@SerializedName("birth_date")`), `gender: String`, `email: String`, `password: String`, `confirmPassword: String` (`@SerializedName("confirm_password")`, обязателен — валидируется бэкендом `UserCreate`), `goalIds: List<Int>` (`@SerializedName("goal_ids")`).
- `fun RegisterRequest.toDto(): RegisterRequestDto` — маппинг: `birthDate.toString()` даёт ISO `yyyy-MM-dd`; `gender.name.lowercase()` даёт `"male"/"female"`; `goalIds` — приоритет: если `roleIds` не пуст — используется он, иначе `purpose.toRoleId()` (ATHLETE→1, TRAINER→2, CLUB_OWNER→3), а для EXPLORING/OTHER, у которых `toRoleId()` возвращает `null`, — дефолт `listOf(1)` (иначе пустой список ломал бэкенд с HTTP 422 из-за `minItems:1`).

#### `data/remote/dto/RegisterResultDto.kt`
DTO ответа `POST /auth/register`.
- `RegisterResultDto` (data class): `message: String`, `email: String`, `expiresIn: Int` (`@SerializedName("expires_in")`). Комментарий явно фиксирует: бэкенд также возвращает `debug_code`, но поле намеренно НЕ включено в DTO (временное поле для отладки, должно быть убрано на бэкенде до продакшена — критический нюанс проекта, см. CLAUDE.md пункт 7).
- `fun RegisterResultDto.toDomain(): RegisterResult` — прямой маппинг `email` и `expiresIn`.

#### `data/remote/dto/ResendCodeResponseDto.kt`
DTO ответа `POST /auth/resend-code`.
- `ResendCodeResponseDto` (data class): `message: String`, `expiresAt: String?` (`@SerializedName("expires_at")`), `remainingSeconds: Int?` (`@SerializedName("remaining_seconds")`) — оба поля `Optional` на бэкенде (`Optional[datetime]`, `Optional[int]`), поэтому nullable в Kotlin.
- `fun ResendCodeResponseDto.toDomain(): ResendResult` — `remainingSeconds ?: 0` (при `null` таймер не стартует, но приложение не падает).

#### `data/remote/dto/ResendResetCodeRequestDto.kt`
DTO запроса `POST /password-reset/resend-verify-code`.
- `ResendResetCodeRequestDto` (data class): `email: String`.

#### `data/remote/dto/ResendResetCodeResponseDto.kt`
DTO ответа `POST /password-reset/resend-verify-code`.
- `ResendResetCodeResponseDto` (data class): `message: String? = null` — бэкенд возвращает пустой `{}` при успехе.
- `fun ResendResetCodeResponseDto.toDomain(): ResendResetCodeResult` — `message ?: "Код отправлен повторно"`.

#### `data/remote/dto/ResetPasswordRequestDto.kt`
DTO запроса `POST /password-reset/confirm` (финальный шаг восстановления пароля).
- `ResetPasswordRequestDto` (data class): `email: String`, `code: String`, `newPassword: String` (`@SerializedName("password")` — расхождение имён: domain использует `newPassword`, API ожидает `password`), `confirmPassword: String` (`@SerializedName("confirm_password")`).

#### `data/remote/dto/ResetPasswordResponseDto.kt`
DTO ответа `POST /password-reset/confirm`; по OpenAPI-схеме эндпоинт возвращает `TokenResponse`.
- `ResetPasswordResponseDto` (data class): `accessToken: String` (`@SerializedName("access_token")`), `refreshToken: String` (`@SerializedName("refresh_token")`), `tokenType: String = "bearer"` (`@SerializedName("token_type")`).
- `fun ResetPasswordResponseDto.toDomain(): ResetPasswordResult` — возвращает фиксированное сообщение `"Пароль успешно изменён"`, `success = true`, `redirectToLogin = false` (комментарий: токены сохраняются репозиторием ДО вызова `toDomain()`, поэтому редиректа на логин не требуется — реализован авто-вход, BUG-2 из тестов).

#### `data/remote/dto/RoleDto.kt`
DTO ответа `GET /role/` и `GET /role/user_roles` (МОБ-6.2, роли пользователя для динамической навигации).
- `RoleDto` (data class): `roleId: Int` (`@SerializedName("role_id")`, 1=ATHLETE, 2=TRAINER, 3=CLUB_OWNER), `name: String` (используется для отладки, не отображается в UI).
- `fun RoleDto.toDomain(): Role` — прямой маппинг 1-в-1.

#### `data/remote/dto/RoleResponseDto.kt`
DTO ответа `GET /role/` (МОБ-6.3, все доступные роли для Step 2 регистрации).
- `RoleResponseDto` (data class): `id: Int` (`@SerializedName("role_id")`), `name: String`, `description: String? = null` (опционально).
- `fun RoleResponseDto.toDomain(): RoleResponse` — `description ?: "$name роль"` (дефолтное описание, если бэкенд не прислал).

#### `data/remote/dto/TrainingHistoryResponseDto.kt`
DTO элемента истории тренировок (`GET /training/history`).
- `TrainingHistoryResponseDto` (data class): `trainingId: String` (`@SerializedName("training_id")`), `typeActivId: Int` (`@SerializedName("type_activ_id")`), `date: String`, `timeStart: String?` (`@SerializedName("time_start")`), `timeEnd: String?` (`@SerializedName("time_end")`), `kilocalories: Double?`, `distanceM: Double?` (`@SerializedName("distance_m")`), `avgSpeed: Double?` (`@SerializedName("avg_speed")`), `elevationGain: Double?` (`@SerializedName("elevation_gain")`).
- `fun TrainingHistoryResponseDto.toDomain(): TrainingHistoryItem` — маппинг 1-в-1, `date` парсится через `LocalDate.parse(date)` (ожидается ISO-8601 `yyyy-MM-dd`; при неверном формате бросит исключение — явного `runCatching` в этом файле нет).

#### `data/remote/dto/TrainingSaveDto.kt`
DTO запроса/ответа `POST /training/{training_id}/save_training`.
- `TrainingSaveRequestDto` (data class): `timeEnd: String` (`@SerializedName("time_end")`, ISO 8601 UTC), `totalDistanceMeters: Double? = null` (`@SerializedName("total_distance_meters")`), `totalKilocalories: Double? = null` (`@SerializedName("total_kilocalories")`) — оба nullable, бэкенд может рассчитать сам из GPS-точек.
- `TrainingSaveResponseDto` (data class): `trainingId: String` (`@SerializedName("training_id")`), `message: String`.
- `fun TrainingSaveResponseDto.toDomain(): SaveTrainingResult` — прямой маппинг.

#### `data/remote/dto/TrainingStartDto.kt`
DTO запроса/ответа `POST /training/start`.
- `TrainingStartRequestDto` (data class): `typeActivId: Int` (`@SerializedName("type_activ_id")`), `timeStart: String? = null` (`@SerializedName("time_start")`) — опционально для офлайн-тренировок с реальным стартовым timestamp; при `null` бэкенд использует время получения запроса (совместимость со старыми клиентами).
- `TrainingStartResponseDto` (data class): `activeTrainingId: String` (`@SerializedName("active_training_id")`), `typeActivId: Int` (`@SerializedName("type_activ_id")`), `timeStart: String` (`@SerializedName("time_start")`), `message: String`, `kilocalories: Double = 0.0`.
- `fun TrainingStartResponseDto.toDomain(): ActiveTrainingResult` — маппинг `activeTrainingId`, `typeActivId`, `timeStart`, `message` (kilocalories в domain-модель этого маппера не идёт).

#### `data/remote/dto/UpdateProfileRequestDto.kt`
DTO тела запроса `PATCH /user/edit`.
- `UpdateProfileRequestDto` (data class): `firstName: String?` (`@SerializedName("first_name")`), `lastName: String?` (`@SerializedName("last_name")`), `middleName: String?` (`@SerializedName("middle_name")`), `birthDate: String?` (`@SerializedName("birth_date")`, ISO 8601 `YYYY-MM-DD`), `weight: Float?`, `height: Float?`, `gender: String?` (`"male"`/`"female"`), `nickname: String?` (без символа `@`). Все поля nullable — бэкенд обновляет только присланные.

#### `data/remote/dto/UserInfoDto.kt`
DTO ответа `GET /user/` — профиль текущего авторизованного пользователя.
- `UserInfoResponseDto` (data class): `firstName: String` (`@SerializedName("first_name")`), `lastName: String?` (`@SerializedName("last_name")`), `middleName: String?` (`@SerializedName("middle_name")`), `birthDate: String` (`@SerializedName("birth_date")`, ISO `YYYY-MM-DD`), `weight: Float?`, `height: Float?` (оба nullable — профиль мог быть не заполнен), `gender: String` (`"male"`/`"female"`), `nickname: String` (в domain — `username`), `imagePath: String? = null` (`@SerializedName("image_path")`).
- `fun UserInfoResponseDto.toDomain(): User` — `id = 0` (этот эндпоинт ID не возвращает), `email = ""` (тоже не возвращается), `username = nickname`; `birthDate` парсится через `runCatching { LocalDate.parse(birthDate) }`, при ошибке — лог через `Log.e` и fallback `LocalDate.EPOCH` (возраст ~55 лет, чтобы расчёт калорий не давал `null`); `gender` — `"female"` → `Gender.FEMALE`, всё остальное (включая неизвестные значения) → `Gender.MALE` (осознанный выбор дефолта во избежание краша); `photoUrl` — если `imagePath` уже абсолютный URL (`http://`/`https://`), используется как есть, иначе конкатенируется с `BuildConfig.BASE_URL`.

#### `data/remote/dto/VerifyResetCodeResponseDto.kt`
DTO ответа `POST /password-reset/verify-code`.
- `VerifyResetCodeResponseDto` (data class): `message: String? = null` — бэкенд возвращает пустой `{}` при успехе. Mapper-функции нет.

---

### Тесты data/remote

#### `AuthTokenFlowTest.kt`
Проверяет через `MockWebServer` поведение РЕАЛЬНОГО auth-интерцептора `buildAuthInterceptor` из `data/remote/AuthInterceptor.kt` (ранее тест держал инлайн-копию логики — она бы молча разъехалась с продакшеном при правке):
- `` `интерцептор добавляет Authorization Bearer если токен есть` `` — заголовок `Bearer abc123` при непустом токене.
- `` `интерцептор не добавляет Authorization если токен null` `` — заголовок отсутствует.
- `` `интерцептор не добавляет Authorization если токен пустая строка` `` — заголовок отсутствует для `""`.
- `` `интерцептор не добавляет Authorization если токен пробельная строка` `` — заголовок отсутствует для `"   "` (проверка `isNullOrBlank()`).
- `` `интерцептор читает токен из TokenStorage при каждом запросе` `` — два последовательных запроса получают разные токены (`token-A`, `token-B`) — токен не кешируется.
- `` `заголовок имеет формат Bearer пробел токен` `` — точный формат строки заголовка.
- `` `интерцептор не добавляет Authorization на чужой хост` `` — host-check: при `apiHost`, отличном от хоста запроса, Bearer не добавляется (токен не утекает на внешние URL картинок).
- `` `интерцептор добавляет Authorization только при совпадении хоста` `` — host-check не ломает основной сценарий.
- `` `сбой хранилища токенов не роняет запрос — уходит без Authorization` `` — `getAccessToken()` бросает RuntimeException (повреждённый Keystore) → запрос завершается 200 без заголовка, исключение не пробрасывается.

#### `TokenRefreshAuthenticatorTest.kt`
Тестирует отказоустойчивость `TokenRefreshAuthenticator` при сбое `TokenStorage` (authenticate() выполняется на OkHttp-потоке — исключение роняет процесс) и штатные отказы refresh. Refresh-эндпоинт мокается `MockWebServer`, authenticator конструируется напрямую с mock-хранилищем:
- `` `сбой чтения refresh-токена возвращает null без исключения` `` — `getRefreshToken()` бросает → `authenticate` возвращает `null`.
- `` `сбой сохранения новых токенов не мешает повторному запросу` `` — `getUserRoles()` бросает после успешного refresh → повторный запрос всё равно построен с `Bearer new-access`.
- `` `401 на refresh завершает сессию без повторного запроса` `` — `signalSessionExpired()` вызван, `saveTokens` — нет.
- `` `5xx на refresh не завершает сессию` `` — `signalSessionExpired()` не вызывается (временная ошибка сервера).

#### `dto/ActivityTypeDtoTest.kt`
Покрывает маппер `toIconKey()`:
- `` `toIconKey возвращает строковое представление id, а не name` ``
- `` `toIconKey не использует name для маппинга` ``
- `` `toIconKey для id=5 (Ходьба) возвращает строку 5` ``
- `` `imagePath null не вызывает исключений при создании DTO` ``
- `` `imagePath не-null сохраняется в DTO` ``

#### `dto/GpsPointDtoTest.kt`
Покрывает маппер `LocationPoint.toGpsPointDto()`:
- `` `recordedAt имеет формат ISO 8601 UTC с суффиксом Z или +00_00` ``
- `` `recordedAt корректно конвертирует epoch millis` ``
- `` `latitude и longitude сохраняются без изменений` ``
- `` `nullable поля accuracy, altitude, speed передаются` ``
- `` `null значения accuracy, altitude, speed остаются null` ``
- `` `recordedAt не null для любого timestampUtc` ``

#### `dto/RegisterRequestDtoTest.kt`
Покрывает маппер `RegisterRequest.toDto()`, особенно логику `goal_ids` (BUG-1):
- `` `ATHLETE purpose с пустым roleIds даёт goalIds = listOf(1)` ``
- `` `TRAINER purpose с пустым roleIds даёт goalIds = listOf(2)` ``
- `` `CLUB_OWNER purpose с пустым roleIds даёт goalIds = listOf(3)` ``
- `` `EXPLORING purpose с пустым roleIds даёт goalIds = listOf(1) (дефолт)` ``
- `` `OTHER purpose с пустым roleIds даёт goalIds = listOf(1) (дефолт)` ``
- `` `явный roleIds имеет приоритет над purpose` ``
- `` `явный roleIds приоритетнее даже если purpose даёт другое значение` ``
- `` `birthDate сериализуется в формат yyyy-MM-dd` ``
- `` `birthDate однозначно и двухзначно для месяца и дня` ``
- `` `MALE gender преобразуется в строку lowercase male` ``
- `` `FEMALE gender преобразуется в строку lowercase female` ``
- `` `username из domain попадает в поле username DTO (серализуется как nickname)` ``

#### `dto/ResendCodeResponseDtoTest.kt`
Покрывает маппер `ResendCodeResponseDto.toDomain()`:
- `` `remainingSeconds null даёт expiresIn = 0` ``
- `` `remainingSeconds 120 маппится в expiresIn = 120` ``
- `` `remainingSeconds 0 маппится в expiresIn = 0` ``
- `` `expiresAt null при существующем remainingSeconds не вызывает исключения` ``

#### `dto/ResetPasswordResponseDtoTest.kt`
Покрывает маппер `ResetPasswordResponseDto.toDomain()` и сценарий авто-входа после сброса пароля (BUG-2):
- `` `toDomain помечает операцию как успешную` ``
- `` `DTO содержит непустые токены для сохранения в TokenStorage` ``
- `` `toDomain выставляет redirectToLogin = false (авто-вход активен)` ``
- `` `toDomain с пустым tokenType не бросает исключений` ``

---

### Итоги по data/remote

Сетевой слой состоит из двух Retrofit-сервисов: `AuthApiService` (18 методов — регистрация, вход, восстановление пароля, роли/цели, профиль, фото) и `TrainingApiService` (9 методов — старт/завершение тренировки, GPS-синхронизация, MET-конфигурация, история, детали тренировки, удаление). Все методы — `suspend fun`, вызываются только из корутин, что соответствует чек-листу проекта. Паттерн маппинга DTO→domain почти везде единообразен: расширение-функция `fun XxxDto.toDomain(): XxxDomainModel` рядом с DTO в том же файле, часто с "защитными" дефолтами на случай null-полей от бэкенда (`ResendCodeResponseDto` — `remainingSeconds ?: 0`, `ForgotPasswordResponseDto`/`ResendResetCodeResponseDto` — дефолтные тексты сообщений, `RoleResponseDto` — дефолтное описание, `METActivityResponseDto` — `speedMax ?: Double.POSITIVE_INFINITY`, `UserInfoResponseDto` — `LocalDate.EPOCH` fallback при ошибке парсинга даты). Обратный маппинг domain→DTO встречается там, где нужно (`RegisterRequestDto`, `LocationPoint.toGpsPointDto()`), с явной обработкой расхождений имён полей (`username`↔`nickname`, `newPassword`↔`password`). Nullable-поля DTO systematically соответствуют `Optional[...]` на бэкенде (FastAPI): `remaining_seconds`, `expires_at`, `weight`/`height`, `last_name`/`middle_name`, `description` у ролей и т.д. — расхождений между кодом и backend-контрактом, зафиксированным в CLAUDE.md, не обнаружено. Обработка ошибок токенов сосредоточена в `TokenRefreshAuthenticator`: он различает постоянные ошибки (4xx → `signalSessionExpired()` и логаут) и временные (5xx/сетевые → просто отказ от повтора без логаута), а `@Synchronized` и подсчёт `priorResponse`-цепочки защищают от гонок и бесконечных циклов обновления токена. Обнаружены и намеренно не вынесены в DTO чувствительные/временные поля — `debug_code` в `RegisterResultDto` явно исключён комментарием с пометкой "убрать до прода" на стороне бэкенда. Самый сложный DTO по логике — `GetTrainingDetailResponseDto` с ручным парсингом GeoJSON LineString и синхронизацией параллельного массива таймстемпов; это прямо связано с TODO проекта о переходе бэкенда на массив объектов с `recorded_at`. Тестовое покрытие (6 файлов) сфокусировано на некритичных, но важных для UX местах: маппинг ключа иконки по ID (не по имени), формат ISO-дат при отправке GPS-точек, корректность `goal_ids` при регистрации (исторический баг с пустым списком → 422), nullable-поля в ответах восстановления кода/пароля и поведение auth-интерцептора при разных состояниях токена.

---

## 2.4 data/repository/, data/work/, data/location/, data/system/, di/


### data/repository

#### `data/repository/AuthRepositoryImpl.kt`
Реализация контракта `AuthRepository` из domain-слоя: переводит вызовы use-case'ов в HTTP-запросы через `AuthApiService` и синхронизирует состояние токенов/профиля/ролей в локальных хранилищах.

- `AuthRepositoryImpl` (class, внедряется через конструктор, регистрируется как `@Singleton` через `@Binds` в `AuthModule`) — зависимости: `AuthApiService`, `TokenStorage`, `UserProfileCache`, `RoleGoalCache`, `RoleConfigStorage`.
  - `suspend fun register(request: RegisterRequest): Result<RegisterResult>` — шаг 1 регистрации (`POST /auth/register`). Токены не сохраняются — пользователь ещё не верифицирован (`is_active=false` на бэкенде).
  - `suspend fun verifyEmail(email: String, code: String): Result<AuthResult>` — шаг 2 регистрации (`POST /auth/verify-email`). Сначала сохраняет токены с пустым списком ролей (чтобы интерцептор смог добавить Bearer в последующий вызов), затем проверяет `RoleConfigStorage.getSelectedRoles()`; если ролей нет — грузит их через `api.getUserRoles()`; в конце пересохраняет токены с финальным списком ролей.
  - `suspend fun resendCode(email: String): Result<ResendResult>` — `POST /auth/resend-code`, токены не трогает.
  - `suspend fun login(email: String, password: String): Result<AuthResult>` — `POST /auth/login`. Тот же паттерн «сначала токены (без ролей), потом getUserRoles(), потом токены с ролями» — критический инвариант, нарушение порядка ведёт к 401 из-за пустого хранилища в момент запроса ролей. Ошибка `getUserRoles()` логируется через `Log.w`, но не прерывает успешный логин (роли остаются пустыми).
  - `suspend fun refreshToken(refreshToken: String): Result<AuthResult>` — `POST /auth/refresh` (JSON body `RefreshTokenRequestDto`). Роли НЕ перезагружаются — берутся текущие из `TokenStorage.getUserRoles()`, чтобы не дёргать сеть при каждом обновлении access-токена.
  - `suspend fun checkNickname(nickname: String): Result<NicknameCheckResponse>` — `POST /auth/check-nickname`, не требует авторизации, результат не кэшируется.
  - `suspend fun getAvailableRoles(): Result<List<RoleResponse>>` — `GET /role/`.
  - `suspend fun getGoalsByRole(roleId: Int?): Result<List<GoalResponse>>` — сначала смотрит in-memory `RoleGoalCache` (TTL 1 час, ключ — `roleId` или null); при промахе кэша грузит все цели через `api.getGoals()` и кладёт в кэш.
  - `suspend fun getUserInfo(): Result<User>` — cache-first: если `UserProfileCache.get()` не null — возвращается мгновенно без сети; иначе `GET /user/` и сохранение в кэш. Кэш прогревается при входе (LoginViewModel/RegisterViewModel), сбрасывается при logout.
  - `suspend fun updateProfile(...): Result<User>` — `PATCH /user/edit`, сразу обновляет `UserProfileCache`.
  - `suspend fun uploadPhoto(file: File): Result<Unit>` — собирает `MultipartBody.Part` (mime `image/png` или `image/jpeg` по расширению файла), отправляет, затем перезапрашивает `GET /user/` для получения нового `image_path` и обновляет кэш.
  - `suspend fun deletePhoto(): Result<Unit>` — удаляет фото на сервере, затем аналогично перечитывает `getUserInfo()` и обновляет кэш.
  - `suspend fun deleteAccount(): Result<Unit>` — `DELETE /user/delete`, затем `tokenStorage.clearAll()` и `userProfileCache.clear()`.
  - `val sessionExpiredFlow: StateFlow<Boolean>` — делегирует `TokenStorage.sessionExpiredFlow`, чтобы ViewModel не зависел напрямую от `TokenStorage`.

#### `data/repository/AllowedEmailDomainsRepositoryImpl.kt`
Реализация `AllowedEmailDomainsRepository` с захардкоженным списком российских почтовых доменов (149-ФЗ).
- `AllowedEmailDomainsRepositoryImpl` (`@Singleton class`, `@Inject constructor()` без зависимостей).
  - `override suspend fun getAllowedDomains(): Set<String>` — возвращает `HARDCODED_RUSSIAN_DOMAINS`.
  - `companion object` — `HARDCODED_RUSSIAN_DOMAINS: Set<String>` — 15 доменов трёх групп: Яндекс (`yandex.ru`, `ya.ru`, `yandex.com`, `narod.ru`), VK/Mail.ru (`mail.ru`, `bk.ru`, `list.ru`, `inbox.ru`, `internet.ru`, `vk.com`), Rambler (`rambler.ru`, `lenta.ru`, `autorambler.ru`, `myrambler.ru`, `ro.ru`).

Особенности: TODO(backend) в коде — план замены на сетевую реализацию (`GET /auth/allowed-email-domains` + кэш в DataStore, хардкод остаётся fallback'ом). Корпоративные/вузовские домены РФ намеренно не входят в список до появления серверного источника. Зеркальная проверка на бэкенде обязательна — клиентская валидация обходится прямым HTTP-запросом.

Особенности: все методы обёрнуты в `runCatching` — единая точка перехвата ошибок сети/HTTP, результат — `Result<T>`. Критический нюанс (CLAUDE.md #12) — порядок «сохранить токены → запросить роли → пересохранить токены с ролями» соблюдён в `login`, `verifyEmail` и (в другом файле) `PasswordRecoveryRepositoryImpl.resetPassword`.

---

#### `data/repository/MockPasswordRecoveryRepository.kt`
Mock-реализация `PasswordRecoveryRepository` для разработки/тестирования без реального backend; в DI не подключена (используется `PasswordRecoveryRepositoryImpl`).

- `MockPasswordRecoveryRepository` (class, `@Inject constructor()`) — не имеет внешних зависимостей, хранит состояние в памяти (`lastResendTime`, `mockValidCodes`).
  - `suspend fun initiateForgotPassword(request: ForgotPasswordRequest): Result<ForgotPasswordResult>` — имитирует задержку 500 мс; при некорректном email или `notfound@example.com` возвращает ошибку.
  - `suspend fun verifyResetCode(email: String, code: String): Result<Unit>` — код должен быть 6 цифр и совпадать с `mockValidCodes[email]` либо быть универсальным `"123456"`.
  - `suspend fun resendResetCode(email: String): Result<ResendResetCodeResult>` — реализует cooldown 120 сек через `lastResendTime`; при преждевременном повторе возвращает ошибку с текстом `RESEND_COOLDOWN: Please wait N seconds`.
  - `suspend fun resetPassword(request: ResetPasswordRequest): Result<ResetPasswordResult>` — валидирует длину пароля (≥8), совпадение паролей, код подтверждения; имитирует задержку 800 мс.

Особенности: чисто in-memory, без сети и Room; предназначен для разработки UI до готовности backend-эндпоинтов.

---

#### `data/repository/MockWorkoutRepository.kt`
Временная мок-реализация `WorkoutRepository`, оставлена в коде, но в DI не используется (заменена `WorkoutRepositoryImpl`).

- `MockWorkoutRepository` (class, `@Inject constructor()`).
  - `val historyChangedFlow: SharedFlow<Unit>` — пустой `MutableSharedFlow`, события не генерируются.
  - `fun workoutTypesFlow(): Flow<List<WorkoutType>>` — возвращает статичный список из 3 типов (Бег/Велосипед/Ходьба) через `flowOf`.
  - `suspend fun startTraining(...): Result<ActiveTrainingResult>` — генерирует случайный `UUID` как `activeTrainingId`.
  - `suspend fun saveTraining(...): Result<SaveTrainingResult>` — всегда `Result.success` с сообщением `"Mock"`.
  - `suspend fun uploadGpsPoints(...): Result<Int>` — возвращает `points.size` без реальной отправки.
  - `suspend fun getActiveTraining(): Result<String>` — случайный `UUID`.
  - `suspend fun getMETActivity(typeActivId: Int): Result<METActivity>` — фиксированный `baseMet=8.0`, без скоростных зон.
  - `suspend fun getTrainingHistory(): Result<List<TrainingHistoryItem>>` — пустой список.
  - `suspend fun getTrainingDetail(trainingId: String): Result<List<LocationPoint>>` — пустой список.
  - `suspend fun deleteCompletedTraining(trainingId: String): Result<Unit>` — всегда успех.
  - `suspend fun savePendingFinish(...)` — no-op, очередь офлайн-финиша не нужна в mock-режиме.

Особенности: используется только как заглушка для разработки/превью UI без backend-эндпоинтов тренировок.

---

#### `data/repository/PasswordRecoveryRepositoryImpl.kt`
Реальная реализация `PasswordRecoveryRepository` через production API; активирована в `AuthModule` через `@Binds`.

- `PasswordRecoveryRepositoryImpl` (class, `@Inject constructor()`, `@Singleton`) — зависимости: `AuthApiService`, `TokenStorage`.
  - `suspend fun initiateForgotPassword(request: ForgotPasswordRequest): Result<ForgotPasswordResult>` — `POST /password-reset/request`.
  - `suspend fun verifyResetCode(email: String, code: String): Result<Unit>` — `POST /password-reset/verify-code`, ответ игнорируется, важен только успех/исключение.
  - `suspend fun resendResetCode(email: String): Result<ResendResetCodeResult>` — `POST /password-reset/resend-verify-code`.
  - `suspend fun resetPassword(request: ResetPasswordRequest): Result<ResetPasswordResult>` — `POST /password-reset/confirm`. После успеха применяет тот же критический паттерн, что и `login()`: сначала `tokenStorage.saveTokens(access, refresh, emptyList())`, затем `api.getUserRoles()` (ошибка логируется, не прерывает поток), затем финальный `saveTokens` с ролями. Возвращает `ResetPasswordResult(redirectToLogin = false)` — токены уже сохранены, повторный логин не нужен (авто-вход).

Особенности: единственный метод с сетевыми побочными эффектами кроме прямого REST-вызова — `resetPassword`, из-за необходимости сразу авторизовать пользователя после смены пароля.

---

#### `data/repository/WorkoutRepositoryImpl.kt`
Основная реализация `WorkoutRepository`: справочные данные (типы активностей, MET-коэффициенты) плюс жизненный цикл тренировки (старт/сохранение/история/удаление).

- `WorkoutRepositoryImpl` (`@Singleton`, class, `@Inject constructor()`) — зависимости: `AuthApiService` (`GET /training/types_activity` на самом деле вызывается через него — см. код, `api.getActivityTypes()`), `TrainingApiService`, `IconCacheManager`, `ActivityTypeDao`, `METActivityDao`, `PendingFinishDao`.
  - Приватное поле `downloadScope: CoroutineScope` — `Dispatchers.IO + SupervisorJob()`, живёт всё время работы синглтона; используется для фоновых загрузок иконок, обновления типов, предзагрузки MET.
  - Приватное поле `activeRefreshJob: Job?` — дебаунс повторных вызовов `refreshFromNetwork` при быстрой навигации.
  - `val historyChangedFlow: SharedFlow<Unit>` — эмитится при успешном `saveTraining` или `deleteCompletedTraining`; `extraBufferCapacity=1` не теряет событие, если подписчик не готов.
  - `fun workoutTypesFlow(): Flow<List<WorkoutType>>` — запускает фоновое обновление (если предыдущее завершилось) и возвращает `activityTypeDao.observeAll()`, замапленный в domain-модели через `iconCache`. Реализует stale-while-revalidate: кэш из Room показывается мгновенно, сеть обновляет его в фоне.
  - `private suspend fun refreshFromNetwork()` — загружает типы активности из сети (`api.getActivityTypes()`), апсертит в Room; для каждого типа: если MET-кэш устарел (TTL 24 ч) — запускает фоновую `fetchAndCacheMET`; если иконки нет или URL сменился — запускает `iconCache.download`. Ошибки сети перехватываются тихо (кэш уже в UI).
  - `private suspend fun fetchAndCacheMET(typeActivId: Int)` — `GET /training/met/{id}`, апсертит `activity`, затем `deleteZones` + `upsertZones` (предотвращает накопление устаревших зон при их сокращении на бэке).
  - `suspend fun startTraining(typeActivId: Int, timeStart: String?): Result<ActiveTrainingResult>` — `POST /training/start`; `IOException` оборачивается в `NetworkUnavailableException`; `HttpException` с кодом 400 — в `ActiveTrainingConflictException` (уже есть активная тренировка на сервере).
  - `suspend fun getActiveTraining(): Result<String>` — `GET /training/active`, возвращает только `activeTrainingId`.
  - `suspend fun saveTraining(trainingId, timeEnd, totalDistanceMeters, totalKilocalories): Result<SaveTrainingResult>` — `POST /training/{id}/save_training`; при успехе эмитит `_historyChangedFlow`; `IOException` → `NetworkUnavailableException`; `HttpException` в диапазоне 400..499 → `TrainingAlreadyClosedException(code)`; 5xx пробрасывается как есть (воркер сделает retry).
  - `suspend fun uploadGpsPoints(trainingId, batchId, points): Result<Int>` — `POST /training/{id}/gps_points`, возвращает `saved`.
  - `suspend fun getMETActivity(typeActivId: Int): Result<METActivity>` — сначала проверяет кэш Room (TTL 24 ч через `MET_CACHE_TTL_MS`); если свежий — возвращает мгновенно; иначе синхронно вызывает `fetchAndCacheMET` и перечитывает из Room (бросает `IllegalStateException`, если запись не появилась после обновления — защита от рассинхронизации).
  - `suspend fun getTrainingHistory(): Result<List<TrainingHistoryItem>>` — `GET /training/history`.
  - `suspend fun getTrainingDetail(trainingId: String): Result<List<LocationPoint>>` — `GET /training/{id}/get_training`, маппинг через `gpsPointsToDomain()` (см. TODO в CLAUDE.md о формате `gps_track` без временных меток).
  - `suspend fun deleteCompletedTraining(trainingId: String): Result<Unit>` — `DELETE /training/{id}/delete`; эмитит `_historyChangedFlow` только при успехе.
  - `suspend fun savePendingFinish(...)` — вставляет `PendingFinishEntity` в Room через `PendingFinishDao.insert` — точка входа офлайн-очереди завершения тренировки.
  - `companion object` — константа `MET_CACHE_TTL_MS = 24 часа`.

Особенности: реализует три независимых кэш-механизма (Room для типов активности и MET, filesDir для иконок) с разными стратегиями обновления (stale-while-revalidate для типов, TTL-инвалидация для MET, сравнение URL для иконок).

---

#### `data/repository/location/LocationRepositoryImpl.kt`
Реализация `LocationRepository` (domain-интерфейс) поверх Room DAO — персистентное хранилище GPS-точек тренировки.

- `LocationRepositoryImpl` (class, `@Inject constructor()`, привязка `@Singleton` через `@Binds` в `AuthModule`) — зависимость: `GpsPointDao`.
  - `suspend fun savePoint(point: LocationPoint)` — вставка одной точки (`dao.insert`).
  - `suspend fun savePoints(points: List<LocationPoint>)` — batch-вставка.
  - `suspend fun getPointsForTraining(trainingId: String): List<LocationPoint>` — все точки тренировки.
  - `suspend fun getUnsentPoints(trainingId: String): List<LocationPoint>` — точки без успешной отправки на сервер (используется воркерами синхронизации).
  - `suspend fun assignBatchId(pointIds: List<Long>, batchId: String)` — присваивает batchId группе точек перед отправкой.
  - `suspend fun markBatchAsSent(batchId: String)` — помечает батч как отправленный.
  - `fun observePointsForTraining(trainingId: String): Flow<List<LocationPoint>>` — реактивный поток точек (для отрисовки трека на карте в реальном времени).
  - `suspend fun getLastKnownPoint(): LocationPoint?` — последняя точка среди всех тренировок (`excludedTrainingId = null`).
  - `suspend fun deletePointsForTraining(trainingId: String)` — удаление всех точек тренировки (используется для очистки discovery-точек).
  - `suspend fun rekeyTrainingId(oldId: String, newId: String)` — переключение принадлежности точек с одного `trainingId` на другой (offline-старт → server UUID).

Особенности: тонкий адаптер без бизнес-логики — вся логика фильтрации и агрегации остаётся в `LocationTrackingService` и use-case'ах; преобразования domain↔entity вынесены в mapper-функции `toEntity()`/`toDomain()`.

---

### data/work

#### `data/work/OfflineFinishScheduler.kt`
Планировщик offline-finish цепочки `SyncGpsPointsWorker → SaveTrainingWorker` — точка входа, откуда ставится в очередь WorkManager доставка тренировки, завершённой без сети.

- `OfflineFinishScheduler` (`@Singleton`, class, `@Inject constructor()`) — зависимости: `@ApplicationContext Context`, `PendingFinishDao`; внутри создаёт `WorkManager.getInstance(context)`.
  - `fun enqueue(trainingId: String)` — строит `Constraints(NetworkType.CONNECTED)`, создаёт `OneTimeWorkRequest` для `SyncGpsPointsWorker` (с `KEY_TRAINING_ID`) и `SaveTrainingWorker`, оба с `BackoffPolicy.EXPONENTIAL` (старт 30 сек). Ставит цепочку через `beginUniqueWork("offline_finish_$trainingId", ExistingWorkPolicy.KEEP, gpsWork).then(saveWork).enqueue()`. `KEEP` гарантирует, что повторный вызов для уже идущей цепочки — no-op.
  - `suspend fun reconcilePending()` — читает все записи `PendingFinishDao.getAll()` и вызывает `enqueue` для каждой. Нужен для починки «умерших» цепочек (например, `SyncGpsPointsWorker` вернул `Result.failure` после `MAX_ATTEMPTS`, пока слот активной тренировки на сервере был занят другой). Вызывается при старте приложения (в `AppViewModel`).

Особенности: `enqueue` вызывается из двух мест — `WorkoutStartViewModel` (немедленный офлайн-финиш) и `AppViewModel` (реконсиляция при старте). Уникальное имя work-цепочки по `trainingId` исключает дублирование.

---

#### `data/work/SaveTrainingWorker.kt`
WorkManager-воркер, доставляющий на сервер офлайн-завершение одной тренировки (второй шаг цепочки `offline_finish_{id}`).

- `SaveTrainingWorker` (`@HiltWorker`, class, `@AssistedInject constructor`, наследует `CoroutineWorker`) — assisted-параметры `Context`, `WorkerParameters`; DI-зависимости: `PendingFinishDao`, `WorkoutRepository`, `LocationRepository` (чистка GPS-точек закрытой тренировки из Room при успехе и при `TrainingAlreadyClosedException`).
  - `override suspend fun doWork(): Result` — читает `trainingId` из inputData (fallback на `resolvedId` от `SyncGpsPointsWorker.KEY_RESOLVED_TRAINING_ID`, если офлайн-тренировка была перерегистрирована с localUUID на serverUUID). Если `runAttemptCount >= MAX_ATTEMPTS` (5) — удаляет запись(и) из `PendingFinishDao` и возвращает `Result.failure()` (не блокирует WorkManager вечно). Иначе ищет конкретную запись по `resolvedId` (или fallback на `getAll()`, если resolvedId устарел после re-key). Для каждой найденной записи вызывает `workoutRepository.saveTraining(...)` с `?: 0.0` фолбэком для nullable `totalDistanceMeters/totalKilocalories` (бэкенд валит 500 при отсутствии этих полей). При успехе — удаляет запись из очереди; при `TrainingAlreadyClosedException` — тоже удаляет (ретрай бессмысленен); при прочих ошибках — не удаляет, флаг `hasTransientFailures=true`. Возвращает `Result.retry()` при наличии транзиентных ошибок, иначе `Result.success()`.
  - `companion object` — `KEY_TRAINING_ID`, `WORK_NAME = "pending_saves_sync"`, `MAX_ATTEMPTS = 5`.

Особенности: обрабатывает race conditions между несколькими параллельными офлайн-тренировками через сочетание точечного поиска по `resolvedId` и fallback на `getAll()` для устаревших localUUID.

---

#### `data/work/SyncGpsPointsWorker.kt`
WorkManager-воркер, загружающий несинхронизированные GPS-точки на сервер (первый шаг цепочки `offline_finish_{id}`, выполняется перед `SaveTrainingWorker`).

- `SyncGpsPointsWorker` (`@HiltWorker`, class, `@AssistedInject constructor`, наследует `CoroutineWorker`) — DI-зависимости: `LocationRepository`, `WorkoutRepository`, `PendingFinishDao`.
  - `override suspend fun doWork(): Result` — читает обязательный `trainingId` из inputData (иначе `Result.failure()`). При `runAttemptCount >= MAX_ATTEMPTS`: если тренировка ещё не зарегистрирована на сервере (`pending.typeActivId != null`) — возвращает `Result.failure()`, чтобы не передать localUUID дальше в `SaveTrainingWorker` (иначе 404 и потеря данных тренировки); иначе — разблокирует цепочку через `Result.success(resolvedTrainingId)`. Вызывает `resolveTrainingId(trainingId)` — если он вернул null, значит регистрация не удалась → `Result.retry()`. Читает `locationRepository.getUnsentPoints(resolvedTrainingId)`; если пусто — `Result.success` с resolvedId в output data. Иначе разбивает точки на две группы: с уже назначенным `batchId` (ретрай со старым ID для идемпотентности) и без него (новые — получают свежий `UUID`, разбиваются на чанки по `LocationConfig.GPS_BATCH_MAX_SIZE`). После загрузки каждого батча — `markBatchAsSent`. Возвращает `Result.retry()` при `hasFailures`, иначе `Result.success()`.
  - `private suspend fun resolveTrainingId(trainingId: String): String?` — если тренировка начата офлайн (`PendingFinishEntity.typeActivId != null`), регистрирует её на сервере через `workoutRepository.startTraining`; при `ActiveTrainingConflictException` (предыдущая тренировка ещё активна) или иной ошибке — возвращает null (сигнал для retry). При успехе — переключает GPS-точки на serverUUID (`locationRepository.rekeyTrainingId`), обновляет `PendingFinishEntity` (удаляет старую запись, вставляет с serverUUID и `typeActivId=null`).
  - `companion object` — `KEY_TRAINING_ID`, `KEY_RESOLVED_TRAINING_ID`, `MAX_ATTEMPTS = 5`.

Особенности: обрабатывает сценарий «тренировка начата полностью офлайн» (пользователь стартовал тренировку без сети) — воркер сам регистрирует её на сервере при первой возможности и делает re-key всех GPS-точек с localUUID на serverUUID.

---

### data/location

#### `data/location/AutopauseDetector.kt`
Чистый (без Android-зависимостей) распознаватель автопаузы: по потоку скоростей GPS-точек решает, когда пользователь остановился и когда снова начал движение. Решения о применении событий (тумблер настроек, приоритет ручной паузы, warmup) принимает `LocationTrackingService`.

- `AutopauseDetector` (class, конструктор с порогами по умолчанию из `LocationConfig`).
  - `enum Event { NONE, PAUSE, RESUME }`
  - `fun onPoint(speedMps: Float?, timestampMs: Long, isRecording: Boolean): Event` — гистерезис: PAUSE при скорости < `AUTOPAUSE_PAUSE_SPEED_MPS` непрерывно ≥ `AUTOPAUSE_MIN_STILL_MS` (по timestamp, не по числу точек); RESUME при скорости ≥ `AUTOPAUSE_RESUME_SPEED_MPS` на `AUTOPAUSE_RESUME_POINTS` точках подряд. `speedMps == null` — точка игнорируется (не сбрасывает серию).
  - `fun reset()` — сброс накопленного состояния (ручные команды, старт сессии).

Особенности: гистерезис (0.5 м/с вход / 1.0 м/с выход) защищает от ложных резюмов на GPS-дрейфе; потокобезопасность не нужна — колбэк GPS и команды сервису приходят на main looper. Тесты — `AutopauseDetectorTest`.

---

#### `data/location/LocationConfig.kt`
Объект-константы всей конфигурации GPS-трекинга: интервалы, пороги точности, тайминги буфера, ключи SharedPreferences и синхронизации.

- `LocationConfig` (object) — не имеет функций, только константы.
  - Интервалы обновления: `INTERVAL_MS_RUNNING=3000`, `INTERVAL_MS_CYCLING=2000`.
  - Пороги точности: `MAX_ACCURACY_RUNNING=20f`, `MAX_ACCURACY_CYCLING=30f`, `MIN_DISTANCE_M=5f`.
  - Многослойная фильтрация: `MIN_TIME_BETWEEN_UPDATES_MS=1000`, `MAX_REALISTIC_SPEED_MPS=50f` (≈180 км/ч), `MIN_DISTANCE_ANTIJITTER_M=3f`, `MIN_SPEED_FOR_BEARING_MPS=0.5f`.
  - Таймауты: `GPS_FIX_TIMEOUT_MS=30000`, `GPS_HINT_TIMEOUT_MS=10000`, `WAKELOCK_TIMEOUT_MS=2 часа`, `LAST_LOCATION_MAX_AGE_MS=60000`.
  - Буферизация: `BUFFER_FLUSH_SIZE=10`, `BUFFER_FLUSH_INTERVAL_MS=5000`.
  - Уведомления: `NOTIFICATION_ID=1001`, `CHANNEL_ID="workout_tracking"`.
  - Crash-recovery ключи SharedPreferences: `PREFS_RECOVERY`, `KEY_ACTIVE_TRAINING`, `KEY_SESSION_STARTED_AT`, `KEY_PAUSED_ACCUMULATED_MS`, `KEY_RECORDED_POINT_COUNT`, `KEY_IS_RECORDING` (пауза переживает рестарт), `KEY_PAUSED_BY_AUTO` (автопауза переживает рестарт — авто-резюм только для неё), `KEY_TRAINING_STARTED_AT`, `KEY_LAST_PERSIST_AT` (heartbeat), `KEY_PAUSE_GAP_INDICES`, `KEY_IS_REGISTERED`, профиль калорий/трекинга (`KEY_TYPE_ACTIV_ID`, `KEY_WEIGHT_KG`, `KEY_HEIGHT_CM`, `KEY_AGE_YEARS`, `KEY_IS_MALE`, `KEY_INTERVAL_MS`, `KEY_ACCURACY_THRESHOLD`), голосовые подсказки (`KEY_ACCUM_DISTANCE_M`, `KEY_LAST_ANNOUNCED_KM`, `KEY_LAST_MILESTONE_ELAPSED_MS`), `RECOVERY_STALE_MS=2 мин` (порог протухания сессии).
  - Автопауза: `AUTOPAUSE_PAUSE_SPEED_MPS=0.5f`, `AUTOPAUSE_RESUME_SPEED_MPS=1.0f` (гистерезис), `AUTOPAUSE_MIN_STILL_MS=5000`, `AUTOPAUSE_RESUME_POINTS=2`, `AUTOPAUSE_WARMUP_MS=10000` (без паузы на старте).
  - Синхронизация: `SYNC_INTERVAL_MS=10000`, `GPS_BATCH_MAX_SIZE=100`.

Особенности: два профиля интервала (бег/ходьба vs велосипед) балансируют точность и энергопотребление; пороги фильтрации выстроены в 4 логических слоя, описанных далее в `LocationTrackingService`.

---

#### `data/location/LocationTrackerFactory.kt`
Фабрика, выбирающая конкретную реализацию GPS-трекера в зависимости от среды выполнения устройства.

- `LocationTrackerFactory` (object).
  - `fun create(context: Context): LocationTracker` — вызывает `RuntimeDetector.detect(context)` и возвращает `GmsLocationTracker`, `HmsLocationTracker` или `AospLocationTracker` соответственно приоритету GMS → HMS → AOSP.

Особенности: вызывается один раз в `LocationTrackingService.startLocationUpdates`; экземпляр хранится в сервисе и освобождается в `onDestroy`.

---

#### `data/location/LocationTrackingService.kt`
Foreground Service — центральный компонент трекинга: получает сырые GPS-точки, фильтрует, сглаживает, считает калории, буферизует и синхронизирует с сервером, показывает live-уведомление.

- `RecordingState` (data class, объявлен в этом же файле) — `isRecording: Boolean`, `recordedPointCount: Int` — событие смены состояния записи для синхронизации с ViewModel.
- `LocationTrackingService` (`@AndroidEntryPoint`, class, наследует `Service`) — инжектируемые поля: `LocationRepository`, `WorkoutRepository`, `OfflineMapManager`, `SettingsStorage`.
  - `override fun onBind(intent: Intent?): IBinder?` — всегда `null` (не bound service).
  - `override fun onCreate()` — живая подписка на `SettingsStorage.settings` (автопауза вкл/выкл, голосовые подсказки, частота) + `initTts()`.
  - `private fun initTts()` — асинхронная инициализация `TextToSpeech` с `Locale("ru")`; голос недоступен → подсказки молча отключены (`ttsReady=false`, лог); `UtteranceProgressListener` отпускает аудиофокус в `onDone`/`onError`.
  - `private fun speak(phrase: String)` — произносит фразу с transient-аудиофокусом `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (музыка приглушается, не останавливается); no-op до готовности TTS или при выключенных подсказках.
  - `override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int` — центральный диспетчер команд через `Intent.hasExtra`, ветвление по типу команды:
    - `EXTRA_PROFILE_UPDATE` — запоздалое обновление профиля пользователя (вес/рост/возраст/пол) для расчёта калорий, без перезапуска трекинга.
    - `EXTRA_TRAINING_ID_UPDATE` — переключение `trainingId` на serverUUID после офлайн-старта; флашит буфер под старым id перед переключением (под mutex).
    - `EXTRA_TRANSITION_TO_WORKOUT` — переход из discovery-режима в полноценную тренировку без пересоздания сервиса: останавливает discovery-трекер, очищает буфер, запускает новый трекер/таймеры/sync-цикл, показывает foreground-уведомление немедленно.
    - `EXTRA_RECORDING` — ручная команда паузы/резюма (в т.ч. из notification) → `applyRecordingChange(newRecording, byAuto = false)`.
  - `private fun applyRecordingChange(newRecording: Boolean, byAuto: Boolean)` — **единый путь смены состояния записи** (ручные команды и автопауза): пересчёт `sessionStartedAt`/`pausedAccumulatedMs` для chronometer, персист gap-индекса паузы, `pausedByAuto = !newRecording && byAuto`, сброс детектора, эмит `_recordingStateFlow`, озвучка «Автопауза»/«Продолжаем» при `byAuto`, перерисовка уведомления. Идемпотентен; ручная команда поверх автопаузы переводит паузу в ручную (авто-резюм отключается).
    - Основная ветка (обычный старт или `START_STICKY` restart после OOM) — восстанавливает `trainingId` из `recoveryPrefs`, если `intent == null`; инициализирует chronometer, запускает `startLocationUpdates`, `startFlushTimer`, `startSyncLoop`, `startHintTimer`; инициализирует MET/CF для расчёта калорий.
  - `private fun startLocationUpdates(intervalMs: Long)` — создаёт `TrackingConfig`, получает трекер через `LocationTrackerFactory.create`, подписывается на колбэк `onLocationReceived`.
  - `private fun startFlushTimer()` — периодический (`BUFFER_FLUSH_INTERVAL_MS`) сброс буфера даже если размер не достиг порога.
  - `private fun startSyncLoop()` — каждые `SYNC_INTERVAL_MS` вызывает `syncUnsentPoints()`; отключён в discovery-режиме (там trainingId не зарегистрирован на сервере).
  - `private suspend fun syncUnsentPoints()` — читает неотправленные точки из Room, разделяет на «ретрай с существующим batchId» и «новые» (получают свежий batchId, чанкуются по `GPS_BATCH_MAX_SIZE`), отправляет через `workoutRepository.uploadGpsPoints`, при успехе — `markBatchAsSent`.
  - `private fun startHintTimer()` — через `GPS_HINT_TIMEOUT_MS` без первого fix'а обновляет уведомление подсказкой "Выйдите на открытое место".
  - `private fun onLocationReceived(location: Location)` — основной обработчик: 4 слоя фильтрации (accuracy → интервал по времени → нереалистичная скорость/телепортация → антидребезг по расстоянию), затем Moving Average сглаживание по окну из 3 точек, обработка первого fix'а (запуск `offlineMapManager.downloadRegionIfNeeded`), запись скорости для уведомления, добавление в буфер (при `isRecording=true`), расчёт калорий через `computeCaloriesForPoint`, инкремент `recordedPointCount`, обновление уведомления. **Автопауза:** детектор (`AutopauseDetector.onPoint`) вызывается МЕЖДУ слоями 3 и 4 — слой 4 отбрасывает стоячие точки, после него остановку не увидеть; условия: не discovery, тумблер включён, прошло `AUTOPAUSE_WARMUP_MS` от старта; скорость — sensor-speed или дистанция/время от prev; `RESUME` применяется только при `pausedByAuto`. **Дистанция + км-подсказки:** после инкремента `recordedPointCount` — приращение `accumulatedDistanceM` (`statsUseCase.distanceBetween`, `prevDistancePoint=null` после resume/рестарта — телепорт не считается), `milestoneTracker.onDistance(...)` → `speak(TtsPhraseFormatter.kilometerCue(...))` + немедленный персист (crash не повторит объявление); трекер двигается независимо от тумблера (включение мид-тренировки не выдаёт пачку пропущенных объявлений).
  - `private fun applyMovingAverage(current: Location): Location` — усредняет lat/lng по `smoothingWindow`.
  - `private fun computeCaloriesForPoint(current: LocationPoint): Double?` — вычисляет калории за интервал от `prevCaloriePoint` до `current` MET-методом; `null` если профиль не инициализирован, это первая точка сессии/после паузы, или интервал ≤0; использует `CalorieCalculator.interpolateMet` для скоростных зон, `CalorieCalculator.energyOver60` для 60+, иначе `CalorieCalculator.energyForInterval`.
  - `private fun flushBuffer()` / `private suspend fun flushBufferLocked()` — сброс `pointBuffer` в Room батчем (`locationRepository.savePoints`), персист состояния chronometer (кроме discovery и shutdown-фазы).
  - `override fun onDestroy()` — останавливает трекер и таймеры, финально флашит буфер и синхронизирует (`syncUnsentPoints`) — либо для discovery удаляет накопленные точки; эмитит `_finishSyncFlow`; снимает foreground-статус, очищает crash-recovery prefs, освобождает WakeLock.
  - `private fun isWifiConnected(): Boolean` — проверка транспорта через `ConnectivityManager`.
  - `private fun createNotificationChannel()` — канал `IMPORTANCE_LOW`, без бейджа.
  - `private fun buildNotification(): Notification` — rich-уведомление с chronometer (только во время записи), текущей скоростью в км/ч, кнопкой Пауза/Продолжить, тапом на MainActivity; на автопаузе текст «Автопауза (HH:MM:SS)» вместо «На паузе…».
  - `private fun rebuildNotification()` — перерисовка уведомления (no-op в discovery-режиме).
  - `private fun persistSessionState()` — сохраняет chronometer-состояние + `pausedByAuto` + состояние км-подсказок (`accumulatedDistanceM`, поля `milestoneTracker`) в `recoveryPrefs`.
  - `companion object` — все Intent extras/actions, request codes, `finishSyncFlow` (`SharedFlow<Unit>` с `replay=1` — сигнал ViewModel'у, что финальный sync завершён и можно закрывать тренировку), `recordingStateFlow` (`replay=0` — только живые события паузы/резюма из notification), `fun setRecording(context, recording: Boolean)` — отправляет команду в работающий сервис.

Особенности: сервис использует `PARTIAL_WAKE_LOCK` с `setReferenceCounted(false)` и таймаутом 2 часа, который **продлевается** каждым тиком flush-таймера (тренировки >2ч не теряют CPU на API 26–28; при краше лок отпустится максимум через 2ч). `foregroundServiceType="location"` освобождает от Doze-троттлинга. Crash-recovery при `START_STICKY`-перезапуске восстанавливает полный контекст сессии: `trainingId`, chronometer, `isRecording` (пауза не превращается в запись), профиль калорий (CF/MET пересчитываются), интервал/порог точности. Flush-таймер пишет heartbeat (`KEY_LAST_PERSIST_AT`) каждые ~5 сек — по нему `readRecoverableSession` в companion отличает живую сессию от протухшей (`RECOVERY_STALE_MS`). Основная ветка `onStartCommand` останавливает предыдущий `activeTracker` перед созданием нового — интент на живой экземпляр не порождает двойные GPS-колбэки. Companion также экспортирует `RecoverableSession` + `readRecoverableSession(context)` — снимок сессии для re-attach ViewModel после смерти процесса (протухшие префы подчищаются на месте). Discovery-режим (поиск GPS до старта тренировки) пишет точки в Room для `gpsStatus`-наблюдателя, но не синхронизирует их с сервером и удаляет по завершении.

---

#### `data/location/OfflineMapManager.kt`
Источник тайлов карты и исторический (сейчас no-op) менеджер офлайн-предзагрузки регионов.

- `OfflineMapManager` (`@Singleton`, class, `@Inject constructor()`) — зависимость: `@ApplicationContext Context` (помечена `@Suppress("unused")`, фактически не используется методами).
  - `fun downloadRegionIfNeeded(center: LatLng, isWifiConnected: Boolean)` — no-op-стаб, сохранён ради совместимости вызова из `LocationTrackingService` при первом GPS-fix.
  - `fun reset()` — no-op-стаб, ранее сбрасывал флаг однократной загрузки за сессию.
  - `companion object` — `STYLE_JSON`: inline MapLibre style spec v8 с raster-источником `tile.gottland.ru/tile/{z}/{x}/{y}.png`, `tileSize=256`, `maxzoom=18`, атрибуция OSM в HTML-формате `<a href="...">`.

Особенности: раньше (при использовании vector-стиля OpenFreeMap) `MapLibre OfflineManager` умел скачивать pyramid-регионы по URL стиля; после перехода на собственный raster XYZ сервер стиль собирается inline JSON, а `OfflineTilePyramidRegionDefinition` требует URL — поэтому офлайн-предзагрузка отключена, но авто-кэш MapLibre (~100 МБ, LRU) продолжает работать прозрачно. Атрибуция OSM обязательна по лицензии ODbL и не может быть отключена (в отличие от логотипа MapLibre).

---

#### `data/location/RuntimeDetector.kt`
Определяет доступную среду выполнения GPS-провайдера (GMS/HMS/AOSP) при старте.

- `RuntimeDetector` (object).
  - `fun detect(context: Context): LocationRuntime` — последовательно проверяет `GoogleApiAvailability.isGooglePlayServicesAvailable` (GMS), затем `HuaweiApiAvailability.isHuaweiMobileServicesAvailable` (HMS, успех = код 0), иначе возвращает `AOSP`. Каждая проверка обёрнута в `try-catch(NoClassDefFoundError)` — если SDK отсутствует в classpath, загрузчик классов бросает именно эту ошибку (не `Exception`), что позволяет безопасно линковать оба SDK в одном APK.

Особенности: результат не кэшируется — вызывается один раз за жизнь `Service`, оверхед нулевой.

---

#### `data/location/TtsPhraseFormatter.kt`
Чистые функции форматирования русских фраз для голосовых подсказок (TTS).

- `TtsPhraseFormatter` (object).
  - `const AUTOPAUSE_CUE = "Автопауза"`, `const RESUME_CUE = "Продолжаем"` — короткие фразы событий автопаузы.
  - `fun kilometerCue(km: Int, lapPaceMsPerKm: Long): String` — «Километр 5. Темп 5 минут 25 секунд.»; `lapPaceMsPerKm <= 0` → фраза без темпа. Темп словами, не «5:25» — TTS читает двоеточие непредсказуемо.
  - `fun pluralRu(n: Long, one: String, few: String, many: String): String` — русские формы множественного числа с исключением 11–14.

Тесты — `TtsPhraseFormatterTest`.

---

#### `data/location/VoiceCueMilestoneTracker.kt`
Чистый трекер километровых рубежей для голосовых подсказок: решает, когда объявлять, и считает темп круга между объявлениями.

- `VoiceCueMilestoneTracker` (class) — состояние: `lastAnnouncedKm: Int`, `lastMilestoneElapsedMs: Long` (public read-only — персистятся сервисом в recovery-префы).
  - `data class Cue(km: Int, lapPaceMsPerKm: Long)`
  - `fun onDistance(accumDistanceM: Double, elapsedMs: Long, intervalKm: Int): Cue?` — рубеж достигнут → `Cue`, иначе null; скачок через несколько рубежей (дыра GPS) = одно объявление последнего с усреднённым темпом; `intervalKm <= 0` → выкл; смена интервала мид-тренировки поддерживается.
  - `fun restore(lastAnnouncedKm: Int, lastMilestoneElapsedMs: Long)` — crash-recovery.
  - `fun reset()` — старт новой тренировки.

Тесты — `VoiceCueMilestoneTrackerTest`.

---

#### `data/location/model/LocationRuntime.kt`
Модель-перечисление среды выполнения GPS.

- `enum class LocationRuntime { GMS, HMS, AOSP }` — GMS (Google Play Services, большинство устройств), HMS (Huawei без GMS), AOSP (кастомные прошивки/эмулятор без обоих сервисов).

---

#### `data/location/model/TrackLocation.kt`
SDK-независимая модель GPS-точки плюс функции конвертации в/из `android.location.Location`.

- `TrackLocation` (data class) — поля `lat`, `lon`, `accuracy`, `speed`, `bearing`, `altitude`, `timestamp` (UTC мс), `elapsedRealtimeNanos` (монотонное время). Не содержит импортов GMS/HMS/android.location.
  - `fun Location.toTrackLocation(): TrackLocation` — extension-функция, конвертирует стандартный `android.location.Location` (используется всеми тремя реализациями трекера).
  - `fun TrackLocation.toAndroidLocation(): Location` — обратная конвертация; используется, чтобы не менять существующую цепочку обработки в `LocationTrackingService` (фильтрация accuracy, запись в Room), которая принимает `android.location.Location`. Провайдер помечается строкой `"tracker"`.

Особенности: изоляция от конкретных SDK нужна, чтобы модель можно было использовать в любом слое без риска `NoClassDefFoundError` на устройствах без одного из SDK.

---

#### `data/location/model/TrackingConfig.kt`
Конфигурация запроса обновлений геолокации, единая для всех трёх провайдеров.

- `enum class TrackingPriority { HIGH_ACCURACY, BALANCED, LOW_POWER }` — маппится на платформенные константы в каждой реализации трекера (GMS `Priority`, HMS `LocationRequest.PRIORITY_*`, AOSP не использует явно).
- `TrackingConfig` (data class) — `intervalMs: Long = 2000`, `minDistanceMeters: Float = 5f`, `priority: TrackingPriority = HIGH_ACCURACY`. Значения по умолчанию соответствуют профилю бега из `LocationConfig`.

---

#### `data/location/tracker/AospLocationTracker.kt`
Реализация `LocationTracker` на базе стандартного `android.location.LocationManager` — fallback, когда GMS и HMS недоступны.

- `AospLocationTracker(context: Context)` (class, implements `LocationTracker`) — держит `LocationManager` и один `LocationListener`.
  - `fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit)` — подписывается сразу на оба провайдера (`GPS_PROVIDER` и `NETWORK_PROVIDER`, если включены), что повышает надёжность в помещениях. Каждый вызов `requestLocationUpdates` обёрнут в `try-catch(SecurityException)`.
  - `fun stopTracking()` — `removeUpdates`, безопасно при null listener (API 26+).
  - `fun getLastKnownLocation(callback: (TrackLocation?) -> Unit)` — берёт наиболее свежую позицию среди GPS и NETWORK провайдеров (`maxByOrNull { it.time }`); если возраст превышает `LocationConfig.LAST_LOCATION_MAX_AGE_MS` — возвращает `null` вместо устаревшей точки.

Особенности: фильтрация по accuracy и запись в Room происходят выше по стеку (в `LocationTrackingService`) — здесь только сырые данные ОС.

---

#### `data/location/tracker/GmsLocationTracker.kt`
Реализация `LocationTracker` на базе Google `FusedLocationProviderClient`.

- `GmsLocationTracker(context: Context)` (class, implements `LocationTracker`) — держит `FusedLocationProviderClient` и `LocationCallback`.
  - `fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit)` — маппит `TrackingPriority` на GMS `Priority` (`PRIORITY_HIGH_ACCURACY=100`, `PRIORITY_BALANCED_POWER_ACCURACY=102`, `PRIORITY_LOW_POWER=104`), строит `LocationRequest.Builder` с `setWaitForAccurateLocation(false)` (не ждать «идеального» fix'а после выхода из Doze — иначе провал в треке на несколько минут; защита от прыжков реализована фильтрами сервиса). `result.locations` может содержать несколько накопленных точек — обрабатывает каждую.
  - `fun stopTracking()` — `removeLocationUpdates`.
  - `fun getLastKnownLocation(callback: (TrackLocation?) -> Unit)` — `client.lastLocation` с проверкой свежести (`LAST_LOCATION_MAX_AGE_MS`), иначе `null` (устаревший кэш хуже отсутствия позиции — иначе камера прыгнет в другой город).

Особенности: все вызовы обёрнуты в `try-catch(SecurityException)` — разрешение может быть отозвано уже после старта; в этом случае ViewModel обнаружит потерю GPS через таймаут.

---

#### `data/location/tracker/HmsLocationTracker.kt`
Реализация `LocationTracker` на базе Huawei Location Kit (для устройств без GMS).

- `HmsLocationTracker(context: Context)` (class, implements `LocationTracker`) — держит `com.huawei.hms.location.LocationServices`-клиент и `LocationCallback`.
  - `fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit)` — HMS `LocationRequest` создаётся через `.create()` + сеттеры (не Builder, в отличие от GMS); маппинг приоритета идентичен GMS по значениям (100/102/104). `LocationResult.locations` возвращает стандартный `android.location.Location`, поэтому переиспользуется тот же `toTrackLocation()`.
  - `fun stopTracking()` — `removeLocationUpdates`.
  - `fun getLastKnownLocation(callback: (TrackLocation?) -> Unit)` — аналогично GMS-версии, с той же проверкой свежести через `LAST_LOCATION_MAX_AGE_MS`.

Особенности: API почти зеркален GMS, отличия — только пакет импортов и способ создания `LocationRequest`.

---

#### `data/location/tracker/LocationTracker.kt`
Контракт (интерфейс) источника геолокации, независимый от конкретного SDK.

- `interface LocationTracker`
  - `fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit)` — начать получение обновлений; вызов безопасен при уже выданных разрешениях (проверяются в `LocationPermissionHandler`); повторный вызов без `stopTracking()` добавляет новую подписку.
  - `fun stopTracking()` — остановить обновления; безопасен для повторного вызова и вызова без предшествующего старта.
  - `fun getLastKnownLocation(callback: (TrackLocation?) -> Unit)` — запросить последнюю известную позицию; может быть устаревшей, использовать только как начальное приближение.

Особенности: три реализации (`AospLocationTracker`, `GmsLocationTracker`, `HmsLocationTracker`) выбираются в `LocationTrackerFactory` на основе `RuntimeDetector.detect`.

---

### data/system

#### `data/system/BatteryOptimizationHelper.kt`
Обёртка над системным API оптимизации батареи (Doze-whitelist) для обеспечения бесперебойного GPS-трекинга при выключенном экране.

- `BatteryOptimizationHelper` (object) — stateless, не требует DI.
  - `fun isIgnoringBatteryOptimizations(context: Context): Boolean` — на API < 23 всегда `true` (Doze отсутствует); иначе делегирует `PowerManager.isIgnoringBatteryOptimizations`.
  - `fun buildRequestIntent(context: Context): Intent` — собирает `Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)` с `data = package:<applicationId>` (обязательно, иначе Android покажет общий список приложений вместо точечного запроса).

Особенности: без Doze-whitelist система может пропускать GPS-callback'и в maintenance windows (обычно через 5-10 мин после выключения экрана) даже при foreground-сервисе и удерживаемом `PARTIAL_WAKE_LOCK`. Требует манифест-разрешение `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, иначе `SecurityException`. Размещён в data-слое как обёртка системного API — domain о ней не знает.

---

### di

#### `di/AuthModule.kt`
Главный Hilt-модуль приложения: связывает все репозитории, хранилища, сетевой стек (Retrofit/OkHttp) и Room.

- `AuthModule` (abstract class, `@Module`, `@InstallIn(SingletonComponent::class)`).

**`@Binds` методы (все `@Singleton`):**
  - `bindTokenStorage(impl: TokenStorageImpl): TokenStorage`
  - `bindRoleConfigStorage(impl: RoleConfigStorageImpl): RoleConfigStorage`
  - `bindSettingsStorage(impl: SettingsStorageImpl): SettingsStorage` — настройки приложения (DataStore Preferences).
  - `bindUserProfileCache(impl: UserProfileCacheImpl): UserProfileCache`
  - `bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository`
  - `bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository` — комментарий подтверждает: реальная реализация через `GET /training/types_activity`, иконки кэшируются в фоне.
  - `bindPasswordRecoveryRepository(impl: PasswordRecoveryRepositoryImpl): PasswordRecoveryRepository` — комментарий перечисляет доступные backend recovery-эндпоинты.
  - `bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository` — через `GpsPointDao` (Room).
  - `bindAllowedEmailDomainsRepository(impl: AllowedEmailDomainsRepositoryImpl): AllowedEmailDomainsRepository` — 149-ФЗ, список российских почтовых доменов для регистрации; сейчас захардкожен, после появления `GET /auth/allowed-email-domains` заменить на сетевую реализацию с кэшем.

**`@Provides` методы (в `companion object`, все `@Singleton` если не указано иное):**
  - `provideBaseUrl(): String` — `@Named("baseUrl")`, значение `BuildConfig.BASE_URL`; нужен для `TokenRefreshAuthenticator`, чтобы разорвать циклическую зависимость Hilt (Authenticator сам создаёт отдельный `OkHttpClient` для вызова `/auth/refresh`).
  - `provideOkHttpClient(tokenStorage: TokenStorage, tokenAuthenticator: TokenRefreshAuthenticator): OkHttpClient` — собирает auth-интерцептор через `buildAuthInterceptor(tokenStorage, apiHost)` из `data/remote/AuthInterceptor.kt` (`apiHost` извлекается из `BuildConfig.BASE_URL`; Bearer добавляется ТОЛЬКО на хост API — клиент общий с Coil/IconCacheManager, токен не должен уходить на внешние URL картинок), устанавливает `tokenAuthenticator` как `.authenticator(...)` (срабатывает на HTTP 401, обновляет токен через `/auth/refresh` и повторяет запрос; при повторном 401 — разлогинивает), добавляет `HttpLoggingInterceptor` с уровнем `BODY` в debug и `NONE` в release + `redactHeader("Authorization")` — токен не попадает даже в debug-logcat (тела auth-запросов при `BODY` всё равно логируются — logcat debug-сборки не передавать третьим лицам).
  - `provideRetrofit(okHttpClient: OkHttpClient): Retrofit` — `baseUrl = BuildConfig.BASE_URL`, `GsonConverterFactory`.
  - `provideAuthApiService(retrofit: Retrofit): AuthApiService`.
  - `provideTrainingApiService(retrofit: Retrofit): TrainingApiService`.
  - `provideDatabase(@ApplicationContext context: Context): SmartTrackerDatabase` — `Room.databaseBuilder` с миграциями `MIGRATION_5_6`, `MIGRATION_6_7`, `MIGRATION_7_8`, `fallbackToDestructiveMigration()` (допустимо, пока данные тренировок некритичны), плюс `RoomDatabase.Callback.onCreate` — сидирует три дефолтных типа активности (`Бег=1`, `Велосипед=3`, `Ходьба=5`) через `INSERT OR IGNORE` при первом создании БД; при последующих запусках таблица дозаполняется из сети через `upsertAll`.
  - `provideGpsPointDao(db): GpsPointDao`
  - `provideActivityTypeDao(db): ActivityTypeDao`
  - `providePendingFinishDao(db): PendingFinishDao`
  - `provideMETActivityDao(db): METActivityDao`

Особенности: единственный модуль в проекте (по списку файлов) — весь DI-граф собран в одном месте. Использует `kapt`, а не KSP (несовместимость с Kotlin 1.9.x — см. общий CLAUDE.md проекта).

---

### Тесты data/repository, data/work и data/location

#### `data/location/AutopauseDetectorTest.kt`
Чистые JUnit-тесты `AutopauseDetector` (9): остановка ≥5 сек → PAUSE; короткая остановка (светофор) не срабатывает; скорость на границе порога не копит остановку; резюм требует 2 движущихся точки подряд; одиночный GPS-скачок не резюмит; скорость между порогами (0.5–1.0 м/с) не резюмит (гистерезис); null-скорость игнорируется без сброса серии; reset; после PAUSE детектор готов к резюму.

#### `data/location/TtsPhraseFormatterTest.kt`
Чистые JUnit-тесты `TtsPhraseFormatter` (10): склонения минут/секунд (1 минута / 2 минуты / 5 минут / 11–14 минут / 21 секунда), ровные минуты без секунд, темп < минуты, отсутствие темпа (≤0), вырожденный темп < секунды, часовой темп.

#### `data/location/VoiceCueMilestoneTrackerTest.kt`
Чистые JUnit-тесты `VoiceCueMilestoneTracker` (9): нет объявлений до 1 км; первый км с темпом от старта; темп круга от прошлого объявления; интервалы 2 км; скачок через несколько рубежей = одно объявление; интервал ≤0 выключает; restore (crash-recovery); reset.

#### `data/repository/AuthRepositoryImplTest.kt`
Покрывает: `login - saveTokens вызывается ДО getUserRoles`; `login - access token сохраняется в TokenStorage`; `login - roleIds из getUserRoles сохраняются в TokenStorage`; `login - возвращает Result_success при успешном ответе API`; `login - возвращает Result_failure при HttpException от api_login`; `login - Result_success даже если getUserRoles бросает исключение`; `verifyEmail - saveTokens вызывается ДО getUserRoles`; `verifyEmail - если RoleConfigStorage не пуст getUserRoles не вызывается`; `refreshToken - roleIds сохраняются из TokenStorage, не сбрасываются`; `refreshToken - новый accessToken сохраняется`; `checkNickname - возвращает isAvailable=false для занятого nickname`; `checkNickname - возвращает isAvailable=true для свободного nickname`.

#### `data/repository/PasswordRecoveryRepositoryImplTest.kt`
Покрывает: `resetPassword - saveTokens вызывается ДО getUserRoles`; `resetPassword - getUserRoles падает, Result_success всё равно возвращается` (плюс проверка `redirectToLogin=false`).

#### `data/repository/WorkoutRepositoryImplTest.kt`
Покрывает: `workoutTypesFlow - iconKey равен id_toString, не name`; `workoutTypesFlow - iconKey не использует name для маппинга`; `workoutTypesFlow - imageUrl null не вызывает исключений`; `workoutTypesFlow - imageUrl сохраняется из imagePath entity`; `workoutTypesFlow - iconFile из кэша используется если файл есть`; `workoutTypesFlow - iconFile null когда кэш пуст`; `workoutTypesFlow - возвращает все типы из DAO`; `uploadGpsPoints - возвращает количество сохранённых точек от API`; `uploadGpsPoints - LocationPoint конвертируется в GpsPointDto без NPE`; `startTraining - возвращает activeTrainingId из API`; `startTraining - typeActivId передаётся в запрос без изменений`; `saveTraining success - возвращает SaveTrainingResult`; `saveTraining IOException - бросает NetworkUnavailableException`; `saveTraining HttpException 400 - бросает TrainingAlreadyClosedException`; `saveTraining HttpException 404 - бросает TrainingAlreadyClosedException с кодом 404`; `saveTraining HttpException 500 - пробрасывается как HttpException без обёртки`.

#### `data/work/SaveTrainingWorkerTest.kt`
Покрывает (Robolectric, `@Config(sdk=[28], application=Application::class)` — обходит инициализацию MapLibre): `doWork - пустая очередь возвращает success без вызова saveTraining`; `doWork - успешный ответ удаляет запись из очереди`; `doWork - TrainingAlreadyClosedException удаляет запись без retry`; `doWork - NetworkUnavailableException возвращает retry и не удаляет запись`; `doWork - каждый воркер обрабатывает только свою запись по trainingId`; `doWork - при MAX_ATTEMPTS запись удаляется и возвращается failure`.

#### `data/work/SyncGpsPointsWorkerTest.kt`
Покрывает (Robolectric, аналогичная конфигурация): `doWork - нет несинхронизированных точек возвращает success`; `doWork - новые точки без batchId назначают batchId и загружают`; `doWork - точки с batchId используют тот же batchId при ретрае`; `doWork - ошибка загрузки возвращает retry и не помечает батч отправленным`; `doWork - MAX_ATTEMPTS достигнут возвращает success чтобы разблокировать цепочку`; `doWork - отсутствует trainingId в inputData возвращает failure`.
Успех сравнивается с `Result.success(workDataOf(KEY_RESOLVED_TRAINING_ID to …))` — воркер кладёт resolved trainingId в output data (контракт с `SaveTrainingWorker`), сравнение голого `Result.success()` падает по `mOutputData`.

---

### Итоги по data/repository, data/work, data/location, di

Архитектура offline-first строится на трёх уровнях: (1) `LocationTrackingService` собирает GPS-точки, применяет 4-слойную фильтрацию и сглаживание, буферизует их в памяти и периодически (каждые 10 сек) пытается синхронизировать неотправленные точки напрямую через `WorkoutRepository.uploadGpsPoints`, пока сервис жив; (2) если тренировка завершается без сети, `WorkoutStartViewModel` кладёт запись в `PendingFinishDao` через `WorkoutRepository.savePendingFinish` и вызывает `OfflineFinishScheduler.enqueue`, которая ставит уникальную WorkManager-цепочку `SyncGpsPointsWorker → SaveTrainingWorker`, привязанную к `NetworkType.CONNECTED`; (3) `AppViewModel` при старте приложения вызывает `OfflineFinishScheduler.reconcilePending()`, которая переоткрывает цепочки для всех ещё не синхронизированных тренировок — защита от «зависших» цепочек, чья ошибка совпала с занятостью слота активной тренировки на сервере. Доменные исключения (`NetworkUnavailableException`, `ActiveTrainingConflictException`, `TrainingAlreadyClosedException`), брошенные из `WorkoutRepositoryImpl`, транслируются воркерами в решения `Result.retry()` / `Result.success()` / `Result.failure()` с чёткими правилами: сеть недоступна — ретрай; тренировка уже закрыта на сервере — удаление записи без ретрая; исчерпаны 5 попыток — либо разблокировать цепочку ценой потери GPS-точек (`SyncGpsPointsWorker`, если тренировка уже зарегистрирована), либо сохранить данные ценой блокировки (если тренировка ещё не зарегистрирована), либо принудительно завершить с потерей записи (`SaveTrainingWorker`). Мультипровайдерный GPS реализован через `RuntimeDetector` (приоритет GMS → HMS → AOSP, с защитой `NoClassDefFoundError` для совместной линковки всех SDK) и `LocationTrackerFactory`, создающую нужную реализацию `LocationTracker`; все три провайдера в итоге возвращают стандартный `android.location.Location`, конвертируемый в SDK-независимый `TrackLocation`. Весь DI-граф собран в одном `AuthModule`: `@Binds` связывает 4 репозитория и 3 хранилища с их интерфейсами, `@Provides` в companion object строит сетевой стек (`OkHttpClient` с auth-интерцептором и `TokenRefreshAuthenticator`, `Retrofit`, оба `ApiService`) и Room (`SmartTrackerDatabase` с тремя миграциями, `fallbackToDestructiveMigration`, сидированием дефолтных типов активности при первом создании, и четырьмя DAO). Тестовое покрытие сфокусировано на критическом инварианте порядка сохранения токенов/загрузки ролей (три репозитория, проверка через `InOrder`) и на полном покрытии ветвлений двух воркеров через Robolectric с ручной инжекцией зависимостей (Hilt не участвует в тестах).

---

## 2.5 presentation/ — корень, auth/, common/, theme/, navigation/, menu/


### Корень presentation

#### `app/src/main/java/com/example/smarttracker/SmartTrackerApp.kt`
Класс `Application`, точка входа Hilt-графа зависимостей и место разовой инициализации сторонних библиотек (MapLibre, Coil, AppMetrica).
- `SmartTrackerApp` (`@HiltAndroidApp class SmartTrackerApp : Application(), Configuration.Provider, ImageLoaderFactory`) — инжектирует `HiltWorkerFactory` и `OkHttpClient` через `@Inject lateinit var`.
  - `fun onCreate(): Unit` — инициализирует `MapLibre.getInstance(...)` (без API-ключа, стандартный tile-сервер), подменяет нативный HTTP-стек MapLibre на `okHttpClient` через `HttpRequestUtil.setOkHttpClient` (MapLibre должен доверять системному Android trust store, а не собственному TLS-bundle), затем вызывает `initAppMetrica()`.
  - `private fun initAppMetrica(): Unit` — активирует AppMetrica (крашрепортинг + аналитика). Ключ из `BuildConfig.APPMETRICA_API_KEY` (gradle-property, вне репо); пустой ключ → no-op (локальная сборка/CI без аналитики). `withLocationTracking(false)` обязателен — геоданные в аналитику Яндекса не передаются (обещание политики конфиденциальности); `withLogs()` только в debug.
  - `fun newImageLoader(): ImageLoader` — строит `ImageLoader` для Coil на основе того же `OkHttpClient` (с auth-интерцептором для Bearer-токена на фото профиля), но убирает `HttpLoggingInterceptor`, чтобы бинарные тела изображений не засоряли logcat; в debug-сборке подключает `DebugLogger`.
  - `val workManagerConfiguration: Configuration` — передаёт `workerFactory` в `Configuration.Builder()`, что даёт WorkManager-воркерам доступ к Hilt DI через `@AssistedInject`.
- Особенность: наличие `Configuration.Provider` отключает автоматическую инициализацию WorkManager из `ContentProvider` — первый вызов `WorkManager.getInstance(context)` лениво инициализируется с этой конфигурацией.

#### `app/src/main/java/com/example/smarttracker/presentation/AppViewModel.kt`
ViewModel уровня приложения — определяет стартовый экран, выполняет логаут и реконсиляцию offline-очередей при старте.
- `AppViewModel` (`@HiltViewModel class AppViewModel(tokenStorage: TokenStorage, userProfileCache: UserProfileCache, offlineFinishScheduler: OfflineFinishScheduler) : ViewModel()`).
  - `val startRoute: String` — вычисляется синхронно в момент создания ViewModel: `Screen.Home.route`, если `tokenStorage.hasTokens()`, иначе `Screen.Login.route`. Позволяет обойтись без splash-экрана.
  - `init { ... }` — в `viewModelScope.launch` вызывает `offlineFinishScheduler.reconcilePending()`, обёрнутый в `runCatching`; ошибка логируется через `Log.w`, но не мешает запуску приложения (реконсиляция перепланирует WorkManager-цепочки для тренировок, завершённых офлайн, чьи цепочки могли «умереть»).
  - `val sessionExpired: StateFlow<Boolean>` — делегирует `tokenStorage.sessionExpiredFlow`; наблюдается глобально в `AppNavGraph` (принудительный переход на Login с любого экрана; раньше подписка жила только в `WorkoutHomeScreen` — 401 на `ProfileEdit` не разлогинивал до возврата на Home).
  - `fun logout(): Unit` — вызывает `tokenStorage.clearAll()` и `userProfileCache.clear()`; сама навигация на Login выполняется в `AppNavGraph` после вызова.
- Особенность: порядок операций в `logout()` важен — оба хранилища чистятся, чтобы данные другого пользователя не «утекли» в следующую сессию.

#### `app/src/main/java/com/example/smarttracker/presentation/MainActivity.kt`
Единственная `Activity` приложения; всё содержимое — Compose-дерево внутри `setContent`.
- `MainActivity` (`@AndroidEntryPoint class MainActivity : ComponentActivity()`) — получает `AppViewModel` через `by viewModels()`.
  - `fun onCreate(savedInstanceState: Bundle?): Unit` — вызывает `enableEdgeToEdge()`, оборачивает `AppNavGraph` в `SmartTrackerTheme` и передаёт `startDestination = appViewModel.startRoute`, `onLogout = appViewModel::logout`, `sessionExpired = appViewModel.sessionExpired`.
- Особенность (см. п.16 CLAUDE.md): `enableEdgeToEdge()` требует, чтобы тема приложения была `Theme.Material3.*` (а не старая `android:Theme.Material.*`), иначе падает `Invalid resource ID 0x00000000` из-за отсутствия `R.attr.isLightTheme`.

---

### presentation/auth/forgot

#### `app/src/main/java/com/example/smarttracker/presentation/auth/forgot/ForgotPasswordEvent.kt`
Sealed-класс одноразовых пользовательских событий для трёхшагового flow восстановления пароля.
- `ForgotPasswordEvent` (`sealed class`) — включает события ввода (`OnEmailChanged`, `OnVerificationCodeChanged`, `OnNewPasswordChanged`, `OnConfirmPasswordChanged`), переходы (`OnContinueFromStep1/2`, `OnResetPassword`), переключатели видимости пароля (`OnToggleNewPasswordVisibility`, `OnToggleConfirmPasswordVisibility`), повтор кода (`OnResendCode`) и навигационные объекты (`OnBackPressed`, `NavigateToLoginAfterReset`, `NavigateToLoginFromBack`, `NavigateToHomeAfterReset`).
- Особенность: `NavigateToHomeAfterReset` — специальный кейс авто-входа после сброса пароля, когда токены уже сохранены в `TokenStorage` репозиторием (не требуется повторный логин).

#### `app/src/main/java/com/example/smarttracker/presentation/auth/forgot/ForgotPasswordUiState.kt`
Единое состояние для всех трёх шагов восстановления пароля.
- `ForgotPasswordUiState` (`data class`) — поля: `currentStep: Int = 1`; шаг 1 — `email`, `emailError`; шаг 2 — `verificationCode`, `verificationCodeError`, `resendCodeCooldown: Int`; шаг 3 — `newPassword`/`confirmPassword` с флагами видимости и ошибками; общие `isLoading`, `generalError`; `verificationCodeExpiresIn: Int = 600` (10 минут — жизнь кода), `codesRemainingSends: Int`.
- Особенность: `resendCodeCooldown` (секунды до разблокировки кнопки «Отправить повторно») и `verificationCodeExpiresIn` (жизнь самого кода) — концептуально разные таймеры, не путать (см. п.6, п.9 CLAUDE.md).

#### `app/src/main/java/com/example/smarttracker/presentation/auth/forgot/ForgotPasswordViewModel.kt`
ViewModel трёхшагового password recovery flow: валидация полей, вызовы `PasswordRecoveryRepository`, управление кулдауном.
- `ForgotPasswordViewModel` (`@HiltViewModel class ForgotPasswordViewModel(passwordRecoveryRepository: PasswordRecoveryRepository, tokenStorage: TokenStorage) : ViewModel()`) — `_uiState: MutableStateFlow<ForgotPasswordUiState>`, `_events: MutableSharedFlow<ForgotPasswordEvent>`.
  - `fun onEvent(event: ForgotPasswordEvent): Unit` — единая точка входа, диспетчеризация по типу события (обновление полей, запуск `submitEmail`/`submitVerificationCode`/`resendCode`/`submitResetPassword`/`navigateBack`).
  - `private fun submitEmail(): Unit` — валидирует email локально (`EmailValidator.isValid`; ранее `android.util.Patterns` — заменён из-за NPE в JVM-тестах), вызывает `passwordRecoveryRepository.initiateForgotPassword`; на успех переводит на шаг 2 и запускает `startCooldown()`. Проверка домена 149-ФЗ намеренно отсутствует — восстановление пароля существующего аккаунта закон не ограничивает.
  - `private fun submitVerificationCode(): Unit` — проверяет длину кода (6 цифр), вызывает `verifyResetCode`; на успех — шаг 3.
  - `private fun resendCode(): Unit` — не отправляет запрос, если `resendCodeCooldown > 0`; иначе вызывает `resendResetCode` и заново запускает кулдаун.
  - `private fun submitResetPassword(): Unit` — валидирует код/пароль/совпадение паролей, вызывает `resetPassword(ResetPasswordRequest(...))`; при `result.getOrNull()?.redirectToLogin` равном `true` — очищает токены (`tokenStorage.clearAll()`) и эмитит `NavigateToLoginAfterReset`, иначе (авто-вход удался) эмитит `NavigateToHomeAfterReset` без очистки токенов.
  - `private fun startCooldown(): Unit` — корутинный цикл `for (seconds in 120 downTo 0)` с `delay(1000)`, обновляет `resendCodeCooldown` каждую секунду.
  - `private fun validatePassword(password: String): String?` — минимум 8 символов.
  - `private fun navigateBack(): Unit` — на шаге > 1 уменьшает `currentStep`, на шаге 1 эмитит `NavigateToLoginFromBack`.
- Особенность: `RESEND_COOLDOWN_SECONDS` жёстко закодирован как `120` внутри `startCooldown()` (не вынесен в константу класса, в отличие от `RegisterViewModel`).

#### `app/src/main/java/com/example/smarttracker/presentation/auth/forgot/ForgotPasswordScreen.kt`
Compose-экран трёхшагового восстановления пароля; рендерит соответствующий шаг по `uiState.currentStep`.
- `@Composable fun ForgotPasswordScreen(viewModel: ForgotPasswordViewModel = hiltViewModel())` — оборачивает состояние в `Box` с фоном `ColorBackground`, делегирует рендер конкретному шагу (`ForgotPasswordStep1Screen`/`Step2`/`Step3`).
- `@Composable private fun ForgotPasswordStep1Screen(...)` — поле email через `StyledTextField`, кнопка «Продолжить» активна при непустом email и отсутствии `emailError`; показывает `emailError` и `generalError`.
- `@Composable private fun ForgotPasswordStep2Screen(...)` — показывает адрес получателя кода, поле кода (`KeyboardType.Number`), таймер кулдауна в формате `%02d:%02d`, кнопку «Отправить код повторно» (`enabled = cooldown == 0 && !isLoading`).
- `@Composable private fun ForgotPasswordStep3Screen(...)` — поля нового и повторного пароля с переключателями видимости, кнопка «Сбросить пароль».
- `@Composable private fun ErrorText(text: String, modifier: Modifier)` — единый стиль текста ошибки (`MaterialTheme.colorScheme.error`, `bodySmall`).
- Особенность: используются общие компоненты `StepScaffold`/`StyledTextField`/`UiTokens` — тот же стиль, что и у `RegisterScreen`.

---

### presentation/auth/login

#### `app/src/main/java/com/example/smarttracker/presentation/auth/login/LoginEvent.kt`
Одноразовые навигационные события экрана входа.
- `LoginEvent` (`sealed interface`) — `NavigateToHome`, `NavigateToRegister`, `NavigateToPasswordRecovery` (все `data object`).

#### `app/src/main/java/com/example/smarttracker/presentation/auth/login/LoginUiState.kt`
Состояние формы логина.
- `LoginUiState` (`data class`) — `email`, `password`, `isLoading`, `errorMessage: String?`, `isPasswordVisible`, `navigateToHome: Boolean` (устаревшее поле — навигация фактически идёт через `SharedFlow` в `LoginViewModel.events`, а не через это булево поле).

#### `app/src/main/java/com/example/smarttracker/presentation/auth/login/LoginViewModel.kt`
ViewModel экрана входа: валидация, вызов `LoginUseCase`, сохранение токенов, эмит событий навигации.
- `LoginViewModel` (`@HiltViewModel class LoginViewModel(loginUseCase: LoginUseCase, authRepository: AuthRepository) : ViewModel()`) — `_state: MutableStateFlow<LoginUiState>`, `_events: MutableSharedFlow<LoginEvent>`.
  - `fun onEmailChange(value: String): Unit`, `fun onPasswordChange(value: String): Unit`, `fun onTogglePasswordVisibility(): Unit` — простые сеттеры состояния.
  - `fun onSubmitLogin(): Unit` — сначала `validateForm()` (быстрый локальный фидбек), затем в `viewModelScope.launch` вызывает `loginUseCase(s.email, s.password)`; на успех эмитит `LoginEvent.NavigateToHome` и параллельно (в отдельном `viewModelScope.launch`, не блокируя навигацию) прогревает кэш профиля через `authRepository.getUserInfo()`; на ошибку — `ApiErrorHandler.getErrorMessage(error)` в `errorMessage`.
  - `fun onNavigateToRegister(): Unit`, `fun onNavigateToPasswordRecovery(): Unit` — эмитят соответствующие `LoginEvent`.
  - `private fun validateForm(): String?` — проверка пустого email, формата (`EmailValidator.isValid`; ранее `Patterns.EMAIL_ADDRESS` — заменён из-за NPE в JVM-тестах), пустого пароля, длины пароля (минимум 8). Домен почты не проверяется — вход существующих аккаунтов с иностранной почтой разрешён (149-ФЗ касается только регистрации).
  - `fun isFormValid(): Boolean` — отдельная (независимая) проверка для disabling кнопки — совпадает по порогу длины пароля (8) с `validateForm()`, но это две независимые реализации (см. тест `LoginViewModelTest` на рассинхронизацию).
- Особенность: прогрев кэша профиля после логина не блокирует переход на Home — ProfileScreen сам повторит запрос при первом открытии, если кэш не успел прогреться.

#### `app/src/main/java/com/example/smarttracker/presentation/auth/login/LoginScreen.kt`
Экран входа по дизайну Figma (node 172:640): логотип, поля email/пароль, ссылка «Забыли пароль?», кнопки, соцсети-заглушки.
- `@Composable fun LoginScreen(state: LoginUiState, onEmailChange, onPasswordChange, onTogglePasswordVisibility, onSubmitLogin, onNavigateToRegister, onNavigateToPasswordRecovery)` — `Scaffold` с `SnackbarHost`; внутри `BoxWithConstraints` вычисляет компактную раскладку при `maxHeight < 760.dp` (уменьшает отступы и размер логотипа/иконок соцсетей для маленьких экранов). Поля email/пароль — `OutlinedTextField` в рамке `ColorPrimary`; пароль с иконкой показать/скрыть (`Icons.Default.Visibility`/`VisibilityOff`). Ошибка и ссылка «Забыли пароль?» в одном `Row`, чтобы кнопка не смещалась при появлении ошибки (место под ошибку не резервируется отдельно). Кнопки: `PrimaryButton("Войти")`, `Button("Создать аккаунт")` (outline-стиль), затем разделитель «Войти с помощью» и три круглые иконки соцсетей (Яндекс/VK/Max) без обработчиков (заглушки `onClick = {}`).
- Также содержит 3 `@Preview`-функции: `LoginScreenPreview`, `LoginScreenLoadingPreview`, `LoginScreenErrorPreview`.
- Особенность: весь текст жёстко на русском, тап по экрану (`detectTapGestures`) скрывает клавиатуру и снимает фокус.

---

### presentation/auth/register

#### `app/src/main/java/com/example/smarttracker/presentation/auth/register/RegisterEvent.kt`
Одноразовые навигационные события регистрации.
- `RegisterEvent` (`sealed interface`) — `NavigateToHome`, `NavigateBack` (`data object`).

#### `app/src/main/java/com/example/smarttracker/presentation/auth/register/RegisterUiState.kt`
Состояние четырёхшаговой регистрации и вспомогательные sealed-классы статусов асинхронной валидации.
- `NicknameCheckStatus` (`sealed class`) — `IDLE`, `CHECKING`, `SUCCESS(message)`, `ERROR(message)` — статус проверки уникальности никнейма через API.
- `BirthDateCheckStatus` (`sealed class`) — `IDLE`, `SUCCESS(message)`, `ERROR(message)` — статус локальной валидации даты рождения.
- `RegisterUiState` (`data class`) — `step: Int = 1`; шаг 1 — `firstName`, `username`, `nicknameCheckStatus`, `birthDate` (8 цифр), `birthDateCheckStatus`, `gender: Gender?`; шаг 2 — `purpose: UserPurpose?` (легаси), `availableGoals: List<GoalResponse>`, `selectedGoalId: Int?`, `isLoadingGoals`; шаг 3 — `email`, `password`, `confirmPassword`, флаги видимости пароля, `termsAccepted`; шаг 4 — `verificationCode`, `resendCooldownSeconds`; общие — `isLoading`, `fieldError` (под конкретным полем), `error` (общая ошибка формы).

#### `app/src/main/java/com/example/smarttracker/presentation/auth/register/LegalScreens.kt`
Два статичных информационных экрана — условия использования и политика конфиденциальности; открываются со Step 3 регистрации по клику на ссылки.
- `@Composable fun TermsOfServiceScreen(onBack: () -> Unit = {})` — `Scaffold` с `TopAppBar` (заголовок «Условия использования», кнопка назад), прокручиваемый `Text` с текстом соглашения: справочный характер показателей тренировок, учётная запись + 149-ФЗ (российские почты), ссылка на политику, ИС (включая ODbL-атрибуцию OSM), ограничение ответственности.
- `@Composable fun PrivacyPolicyScreen(onBack: () -> Unit = {})` — аналогичная структура, текст политики по структуре 152-ФЗ: оператор (⚠️ плейсхолдеры реквизитов), состав ПДн (учётка / профиль / **геоданные отдельным пунктом** — запись только во время активной тренировки / производные / технические данные п. 2.5), цели, правовые основания (ст. 6), хранение в РФ (ст. 18 — локализация) и защита (HTTPS, шифрованное хранилище токенов), передача третьим лицам (только обезличенные технические данные в AppMetrica/ООО «ЯНДЕКС»; геоданные — НЕ передаются), права субъекта (доступ/уточнение/удаление через «Удалить аккаунт»/жалоба в РКН), изменения.
- Особенности: текст обоих экранов — статичные raw-строки (`"""..."""`), не вынесены в ресурсы. ⚠️ Шапка файла содержит чек-лист до релиза: заполнить плейсхолдеры оператора, юр-проверка, публикация канонической версии по URL (BR-15) — тексты в приложении и на сервере должны совпадать.

#### `app/src/main/java/com/example/smarttracker/presentation/auth/register/RegisterViewModel.kt`
ViewModel четырёхшаговой регистрации: валидация каждого шага, debounce-проверка никнейма, загрузка целей, регистрация и подтверждение email.
- `RegisterViewModel` (`@OptIn(FlowPreview::class) @HiltViewModel class RegisterViewModel(registerUseCase: RegisterUseCase, authRepository: AuthRepository, roleConfigStorage: RoleConfigStorage, allowedEmailDomainsRepository: AllowedEmailDomainsRepository) : ViewModel()`) — `_state`, `_events`, `cooldownJob: Job?`, `nicknameCheckDebounceFlow: MutableStateFlow<String>`, `nicknameCheckCache: MutableMap<String, Boolean>` (кэш результатов проверки ника в рамках сессии ViewModel), `nicknameCheckJob: Job?`. Константы: `RESEND_COOLDOWN_SECONDS = 120`, `NICKNAME_CHECK_DEBOUNCE_MILLIS = 700L`.
  - `init { ... }` — подписывается на `nicknameCheckDebounceFlow.debounce(700ms).distinctUntilChanged()`, при длине ≥ 3 вызывает `checkNicknameUniqueAsync`; также с задержкой 500ms (чтобы UI успел отрендериться) вызывает `loadAvailableGoals()` заранее — для устранения мерцания на Step 2.
  - `fun onUsernameChange(value: String): Unit` — обновляет состояние и параллельно триггерит `nicknameCheckDebounceFlow.value = value`.
  - `fun onBirthDateChange(value: String): Unit` — фильтрует только цифры, обрезает до 8 символов; при длине 8 вызывает `validateBirthDate`.
  - `fun loadAvailableGoals(): Unit` — грузит все цели (`authRepository.getGoalsByRole(null)`), не перезапрашивает если уже есть данные.
  - `fun onGoalSelected(goalId: Int): Unit`.
  - `fun onResendCode(): Unit` — вызывает `authRepository.resendCode(email)`, на успех запускает `startCooldown(RESEND_COOLDOWN_SECONDS)` (120 сек, НЕ `result.expiresIn` = 600 сек).
  - `fun onNext(): Unit` — диспетчер по `state.step`: 1→`validateStep1()`, 2→`validateStep2()`, 3→`submitRegistration()`, 4→`verifyEmail()`.
  - `fun onBack(): Unit` — на шаге >1 декремент, на шаге 1 эмитит `RegisterEvent.NavigateBack`.
  - `fun isStep1Complete/isStep2Complete/isStep3Complete/isStep4Complete(): Boolean` — проверки заполненности (не полная валидация) для disabling кнопки «Далее».
  - `private fun validateStep1(): Unit` — проверяет имя/username (мин. 3 символа)/дату/пол, учитывает `birthDateCheckStatus.ERROR`.
  - `private fun validateStep2(): Unit` — требует `selectedGoalId` или `purpose`.
  - `private fun submitRegistration(): Unit` — парсит дату, валидирует email (формат — `EmailValidator.isValid`; домен — `EmailValidator.isAllowedDomain` по списку `allowedEmailDomains`, загруженному в `init`; при пустом списке локальная доменная проверка пропускается — её продублирует `RegisterUseCase`), пароль/совпадение/принятие условий; определяет `roleId` из выбранной цели (`availableGoals.find { it.id == selectedGoalId }?.roleId`); сохраняет роль в `roleConfigStorage.saveSelectedRoles(...)` **до** вызова `registerUseCase`; на успех — `startCooldown(120)` и переход на шаг 4.
  - `private fun verifyEmail(): Unit` — проверяет 6-значный код, вызывает `authRepository.verifyEmail(...)`; на успех отменяет `cooldownJob`, эмитит `NavigateToHome`, асинхронно прогревает кэш профиля.
  - `private fun startCooldown(seconds: Int): Unit` — отменяет предыдущий `cooldownJob`, обратный отсчёт с `delay(1000L)`.
  - `private fun parseBirthDate(dateStr: String): LocalDate?` — парсит 8 цифр в `LocalDate` (день/месяц/год), `null` при ошибке.
  - `private fun validateBirthDate(dateStr: String): Unit` — проверяет возраст 6–120 лет и что дата не в будущем; результат — `BirthDateCheckStatus`.
  - `private fun checkNicknameUniqueAsync(nickname: String): Unit` — сначала проверяет `nicknameCheckCache`, при попадании — мгновенно обновляет статус без сетевого запроса; иначе показывает `CHECKING`, дергает `authRepository.checkNickname(nickname)` и кэширует результат.
  - `override fun onCleared(): Unit` — отменяет `cooldownJob` и `nicknameCheckJob`.
- Особенность: `startCooldown` вызывается с явным аргументом `RESEND_COOLDOWN_SECONDS` (120), в комментариях явно подчёркнуто различие с `result.expiresIn` (600 сек — жизнь кода верификации), см. п.9 CLAUDE.md.

#### `app/src/main/java/com/example/smarttracker/presentation/auth/register/RegisterComponents.kt`
Переиспользуемые Compose-компоненты для шагов регистрации (поле никнейма с индикатором доступности, выбор пола/цели, дата рождения с DatePicker).
- `@Composable internal fun NicknameField(value, onValueChange, checkStatus: NicknameCheckStatus)` — рамка меняет цвет по статусу (`ColorPrimary`/`ColorPlaceholder`/зелёный/красный), справа иконка `Check`/`Close` для `SUCCESS`/`ERROR`.
- `@Composable internal fun GoalSelectionItem(goal: GoalResponse, isSelected: Boolean, onSelect: () -> Unit)` — строка с `Checkbox` и текстом описания цели.
- `@Composable internal fun RegisterScaffold(...)` — тонкая обёртка над `StepScaffold` (алиас для обратной совместимости имён).
- `@Composable internal fun RegisterStepTitle(text: String)` — обёртка над `StepTitle`.
- `@Composable internal fun RegisterStyledTextField(...)` — обёртка над `StyledTextField` (общий компонент из `presentation.common`).
- `@Composable internal fun GenderSelector(selected: Gender?, onSelect: (Gender) -> Unit)` — `RadioButton`-группа «Мужской»/«Женский» с `selectableGroup()`.
- `@Composable internal fun PurposeOption(text: String, selected: Boolean, onClick: () -> Unit)` — легаси-компонент выбора `purpose` (чекбокс + текст); в актуальном UI шаг 2 использует `GoalSelectionItem`, а не этот компонент.
- `@Composable internal fun ErrorText(message: String)` — простой красный текст ошибки с отступом сверху.
- `@Composable internal fun DatePickerField(value, onValueChange, checkStatus: BirthDateCheckStatus)` — поле даты рождения с `DateVisualTransformation`, иконкой календаря, открывающей `DatePickerDialog` (Material3), либо иконкой статуса валидации (`Check`/`Close`) вместо иконки календаря, когда есть результат проверки. При выборе даты в диалоге конвертирует `selectedDateMillis` в строку `"ддммгггг"` через `onValueChange`.
- Особенность: `NicknameField` и `DatePickerField` дублируют часть визуальной логики `StyledTextField`, но с кастомной иконкой статуса — не унифицированы в единый параметризуемый компонент.

#### `app/src/main/java/com/example/smarttracker/presentation/auth/register/RegisterScreen.kt`
Корневой Compose-экран регистрации из 4 шагов; диспетчеризует рендер по `state.step`.
- `@Composable fun RegisterScreen(state: RegisterUiState, много callback-ов, isStep1Complete...isStep4Complete: Boolean = false)` — `when (state.step) { 1 -> RegisterStep1(...); 2 -> RegisterStep2(...); 3 -> RegisterStep3(...); 4 -> RegisterStep4(...) }`.
- `@Composable private fun RegisterStep1(...)` — поля имя/никнейм/дата рождения/пол через компоненты из `RegisterComponents.kt`; показывает `state.fieldError` и `state.error`.
- `@Composable private fun RegisterStep2(...)` — `LaunchedEffect(Unit) { onLoadAvailableGoals() }` подгружает цели при первом отображении шага; список `GoalSelectionItem` по `state.availableGoals`; при пустом списке без загрузки — сообщение об ошибке.
- `@Composable private fun RegisterStep3(...)` — email/пароль/подтверждение пароля, чекбокс согласия с условиями с `LinkAnnotation.Clickable` (два клика — «Условия использования» → `onOpenTermsOfService`, «Политика конфиденциальности» → `onOpenPrivacyPolicy`), построено через `buildAnnotatedString` + `withLink(LinkAnnotation.Clickable(tag, styles, linkInteractionListener))`.
- `@Composable private fun RegisterStep4(...)` — поле кода подтверждения, таймер кулдауна (`%02d:%02d`), кнопка «Отправить код повторно» (`enabled = cooldown == 0 && !state.isLoading`).
- Особенность: используется современный API `LinkAnnotation.Clickable` вместо устаревшего `ClickableText` (см. п.10 CLAUDE.md). Файл не содержит `@Preview`-функций (в отличие от `LoginScreen.kt`).

---

### presentation/common

#### `app/src/main/java/com/example/smarttracker/presentation/common/DateVisualTransformation.kt`
Визуальная трансформация ввода даты рождения: 8 цифр `"DDMMYYYY"` → отображение `"ДД.ММ.ГГГГ"`.
- `DateVisualTransformation` (`internal class : VisualTransformation`).
  - `override fun filter(text: AnnotatedString): TransformedText` — вставляет точки после 2-го и 4-го символа; строит `OffsetMapping` для корректного позиционирования курсора между «сырым» вводом (только цифры) и отображаемым текстом (с точками).
- Особенность (см. п.2 CLAUDE.md): в `state` хранятся только цифры (максимум 8), точки — чисто визуальные через эту трансформацию. Альтернативный подход (программно вставлять точки прямо в строку состояния) содержит баг с позицией курсора — сознательно не используется.

#### `app/src/main/java/com/example/smarttracker/presentation/common/PrimaryButton.kt`
Единая стилизованная основная кнопка действия (заливка `ColorPrimary`, текст белый) с индикатором загрузки.
- `@Composable fun PrimaryButton(text: String, onClick: () -> Unit, isLoading: Boolean = false, isEnabled: Boolean = true, modifier: Modifier = Modifier)` — рендерит `CircularProgressIndicator` вместо текста, когда `isLoading = true`; кнопка недоступна при `isLoading` или `!isEnabled`.
- Используется как в `StepScaffold`, так и напрямую на экранах без него (например `LoginScreen`).

#### `app/src/main/java/com/example/smarttracker/presentation/common/ProfileAvatarImage.kt`
Круглый аватар профиля с загрузкой изображения через Coil.
- `@Composable fun ProfileAvatarImage(photoUrl: String?, photoKey: Long, modifier: Modifier = Modifier)` — строит `ImageRequest` с `memoryCacheKey`/`diskCacheKey`, включающими `photoKey`, чтобы форсировать перезагрузку изображения после аплоада/удаления фото (сервер может отдавать файл по тому же URL); placeholder/error — `R.drawable.ic_profile_2`; обрезка по кругу и рамка `ColorPrimary`.
- Особенность: размер и кликабельность управляются снаружи через `modifier` (компонент не задаёт `size`).

#### `app/src/main/java/com/example/smarttracker/presentation/common/ProfileFieldBox.kt`
Общий визуальный контейнер для полей профиля (просмотр и редактирование).
- `@Composable fun ProfileFieldBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)` — `Box` фиксированной высоты (`UiTokens.ProfileFieldHeight`) с рамкой `ColorPrimary` и скруглением (`UiTokens.ProfileFieldCornerRadius`), контент выравнивается по левому краю по вертикальному центру.

#### `app/src/main/java/com/example/smarttracker/presentation/common/SmartTrackerBottomBar.kt`
Единая нижняя навигационная панель приложения (3 вкладки).
- `AppTab` (`object`) — константы индексов вкладок: `START = 0`, `WORKOUTS = 1`, `MENU = 2`.
- `@Composable fun SmartTrackerBottomBar(selectedIndex: Int, onTabSelected: (Int) -> Unit)` — `NavigationBar` с тремя `NavigationBarItem` (иконки из drawable + подписи из строковых ресурсов `R.string.tab_*`); активная вкладка получает круглый фон `ColorSecondary` вокруг иконки; используется как внутри `WorkoutHomeScreen`, так и на отдельных full-screen экранах (`ProfileScreen`, `ProfileEditScreen`), где нажатие на другую вкладку просто возвращает назад.

#### `app/src/main/java/com/example/smarttracker/presentation/common/StepScaffold.kt`
Универсальный `Scaffold` для всех пошаговых auth-экранов (регистрация, восстановление пароля) — заменил дублирующиеся `RegisterScaffold`/`GenericStepScaffold`.
- `@Composable fun StepScaffold(title: String, onBack: () -> Unit, onNext: () -> Unit, nextLabel: String, isLoading: Boolean, isNextEnabled: Boolean = true, content: @Composable () -> Unit)` — `BackHandler { onBack() }` перехватывает системную кнопку «Назад»; `topBar` — пустой `TopAppBar` только с фоном; `bottomBar` — `PrimaryButton` с `nextLabel`; тело — скроллируемая `Column` с тапом-для-скрытия-клавиатуры (`detectTapGestures`), заголовком через `StepTitle` и `content()`.
- `@Composable fun StepTitle(text: String)` — заголовок в стиле «── Текст ──»: `HorizontalDivider` слева и справа от текста, растянутые по `weight(1f)`.

#### `app/src/main/java/com/example/smarttracker/presentation/common/StyledTextField.kt`
Универсальное текстовое поле для auth-экранов — заменило дублировавшиеся `RegisterStyledTextField` и локальный `StyledTextField`.
- `@Composable fun StyledTextField(value, onValueChange, label, placeholder, keyboardType = Text, capitalization = None, imeAction = Next, visualTransformation = None, isPassword = false, isPasswordVisible = false, onTogglePasswordVisibility: (() -> Unit)? = null)` — метка сверху, `OutlinedTextField` в рамке `ColorPrimary` с плейсхолдером; при `isPassword && !isPasswordVisible` применяет `PasswordVisualTransformation()`; при `isPassword` и заданном колбэке видимости — добавляет `trailingIcon` с переключателем глаза.

#### `app/src/main/java/com/example/smarttracker/presentation/common/UiTokens.kt`
Централизованный объект размеров/отступов UI.
- `UiTokens` (`object`) — константы: `ButtonHeight = 50.dp`, `CornerRadiusMedium = 10.dp`, `BorderWidthThick = 2.dp`, `ButtonLoadingIndicatorSize = 18.dp`, `ScreenHorizontalPadding = 16.dp`, `ContentVerticalPadding = 8.dp`, `SectionSpacing = 16.dp`, `StepTopSpacer = 24.dp`, `BottomActionHorizontalPadding = 16.dp`, `BottomActionVerticalPadding = 36.dp`, `InlineErrorTopPadding = 8.dp`, `InlineErrorStartPadding = 32.dp`, `ProfileFieldHeight = 40.dp`, `ProfileFieldCornerRadius = 5.dp`.

---

### presentation/theme

#### `app/src/main/java/com/example/smarttracker/presentation/theme/ProfileTextStyles.kt`
Текстовые стили для экранов профиля (просмотр/редактирование) — единый 14sp Italic-шрифт с разными цветами для метки/значения/пустого значения.
- `ProfileTextStyles` (`object`) — `fieldLabel: SpanStyle` (`ColorPrimary`), `fieldValue: SpanStyle` (`ColorSecondary`, для заполненных полей), `fieldEmpty: SpanStyle` (`ColorPrimary` с alpha 0.3, для «Не указано»), `fieldInput: TextStyle` (без цвета — для `BasicTextField`, цвет передаётся отдельно через `.copy()`).
- Особенность: `SpanStyle` используется там, где нужна одна `AnnotatedString` с меткой + значением в одном `Text` (просмотр), `TextStyle` — для полей ввода (`BasicTextField` в редактировании).

#### `app/src/main/java/com/example/smarttracker/presentation/theme/SmartTrackerTheme.kt`
Главный файл темы: шрифты, палитра цветов, типографика Material3, корневой Composable темы приложения.
- Определяет `geologicaFontFamily` (Light/Normal) и `geologicaFontFamilyItalic` (только Italic-файл).
- Цвета: `ColorPrimary` (тёмно-синий `#0A1928`), `ColorSecondary` (мятно-бирюзовый `#4DACA7`), `ColorChartLine` (`#2E8C86` — тёмный шаг мятного для тонких линий графиков: ColorSecondary на белом даёт контраст 2.7:1 < WCAG 3:1 для нетекстовой графики, этот — 4.0:1), `ColorPlaceholder`, `ColorLink`, `ColorBackground`/`ColorWhite` (белый), `ColorDestructive` (красный `#FC3F1D` — для удаления/ошибок GPS), `ColorGpsActive`/`ColorGpsInactive` (workout-специфичные), `ColorFieldFill` (серый).
- `SmartTrackerTypography: Typography` — переопределяет `titleLarge` (32sp Italic — заголовок Login), `headlineSmall`/`labelLarge` (20sp Light — заголовки шагов, кнопки), `bodyMedium`/`bodySmall` (16sp Light — плейсхолдеры, второстепенный текст).
- `@Composable fun SmartTrackerTheme(content: @Composable () -> Unit)` — оборачивает `content` в `MaterialTheme` с `lightColorScheme()` (без кастомизации схемы, кроме типографики) и `SmartTrackerTypography`.
- Особенность: приложение использует единственную светлую тему (`lightColorScheme()` без параметров) — тёмная тема не поддерживается.

#### `app/src/main/java/com/example/smarttracker/presentation/theme/WorkoutTextStyles.kt`
Текстовые стили специфичные для модуля workout (start/summary-экраны) — вынесены отдельно от `MaterialTheme.typography`, так как material-имена семантически не подходят под конкретные UI-блоки.
- `WorkoutTextStyles` (`object`) — стили сгруппированы по назначению: таймер тренировки (`timer`, `timerLabel`), статистика на активном экране (`statValue`, `statLabel`), карточки на summary (`statCardValue`, `statCardLabel`), шапка экрана (`screenHeaderDate`), блок активности (`activityName`, `activityPace`), оверлей поверх карты (`statsOverlayValue`), шторка выбора активности (`activityListItem`), кнопки (`primaryButtonLabel`), карточки таймлайна истории (`timelineInfo`, `timelineLabelBold`).
- Особенность: все стили используют единственное семейство `geologicaFontFamily`; Italic получается через `fontStyle = FontStyle.Italic` в том же семействе (Compose сам подбирает нужный файл шрифта).

---

### presentation/navigation

#### `app/src/main/java/com/example/smarttracker/presentation/navigation/AppNavGraph.kt`
Единый `NavHost`, декларирующий все маршруты приложения и переходы между ними, а также подписку каждого экрана на свой `SharedFlow` событий; здесь же — глобальный обработчик истечения сессии.
- `private val AUTH_ROUTES: Set<String>` — маршруты auth-флоу (Login, Register, PasswordRecovery, Terms, Privacy): на них принудительный переход при истечении сессии не выполняется (пользователь и так вне авторизованной зоны, сброс его ввода навредил бы).
- `@Composable fun AppNavGraph(navController: NavHostController = rememberNavController(), startDestination: String = Screen.Login.route, onLogout: () -> Unit = {}, sessionExpired: StateFlow<Boolean> = MutableStateFlow(false))`.
  - Глобальный `LaunchedEffect(navController)` — коллектит `sessionExpired`; при `true` и текущем маршруте вне `AUTH_ROUTES` вызывает `onLogout()` (идемпотентно: токены уже стёрты `signalSessionExpired`, но кэш профиля чистится именно здесь) и переходит на Login с очисткой всего стека. Работает с любого экрана — источник `AppViewModel.sessionExpired` → `TokenStorage.sessionExpiredFlow` → `TokenRefreshAuthenticator`.
  - `composable(Screen.Register.route)` — создаёт `RegisterViewModel` через `hiltViewModel()`, слушает `viewModel.events` в `LaunchedEffect(Unit)`: `NavigateToHome` → переход на `Screen.Home` с `popUpTo(Register) { inclusive = true }`; `NavigateBack` → `popBackStack()`. Пробрасывает в `RegisterScreen` все callback-и ViewModel и `isStepNComplete()`.
  - `composable(Screen.Login.route)` — аналогично для `LoginViewModel`; `NavigateToHome` очищает Login из стека; `NavigateToRegister` — переход без удаления Login (`inclusive = false`); `NavigateToPasswordRecovery` — простой `navigate`.
  - `composable(Screen.PasswordRecovery.route)` — `ForgotPasswordViewModel`; `NavigateToLoginAfterReset`/`NavigateToLoginFromBack` — назад на Login с очисткой `PasswordRecovery`; `NavigateToHomeAfterReset` — переход на Home с очисткой `Login` (авто-вход, токены уже сохранены).
  - `composable(Screen.TermsOfService.route)`/`composable(Screen.PrivacyPolicy.route)` — статичные экраны с `onBack = { navController.popBackStack() }`.
  - `composable(Screen.Home.route)` — `WorkoutHomeScreen` с `onLogout` (вызывает `onLogout()` из параметров графа и очищает весь бэкстек `popUpTo(navController.graph.id) { inclusive = true }`), `onNavigateToProfile` и `onNavigateToSettings`.
  - `composable(Screen.Settings.route)` — `SettingsViewModel` через `hiltViewModel()`, `state` через `collectAsStateWithLifecycle()`; `SettingsScreen` с `onBack = popBackStack` и четырьмя сеттер-колбэками ViewModel.
  - `composable(Screen.Profile.route)` — `ProfileViewModel`; использует `repeatOnLifecycle(Lifecycle.State.RESUMED)` в `LaunchedEffect(lifecycleOwner)`, чтобы вызывать `viewModel.refreshFromCache()` при каждом возврате на экран (в т.ч. после редактирования профиля).
  - `composable(Screen.ProfileEdit.route)` — `ProfileEditViewModel`; слушает `ProfileEditEvent.NavigateBack` (`popBackStack`) и `ProfileEditEvent.AccountDeleted` (логаут + переход на Login с полной очисткой стека).
- Особенность: итоги тренировки (`WorkoutSummary`) показываются оверлеем поверх `WorkoutHomeScreen`, а не отдельным маршрутом — сохраняет ту же инстанцию `MapView` и устраняет краши анимаций MapLibre при навигации через Compose Navigation (комментарий в коде явно это поясняет).

#### `app/src/main/java/com/example/smarttracker/presentation/navigation/Screen.kt`
Реестр всех маршрутов приложения как `sealed class` с текстовым `route`.
- `Screen` (`sealed class Screen(val route: String)`) — auth-экраны: `Register`, `Login`, `PasswordRecovery` (`"forgot_password"`), `TermsOfService`, `PrivacyPolicy`; экраны приложения (требуют авторизации): `Home` (доступен всем ролям), `MyWorkouts`/`MyAthletes`/`MyClub` (для ролей ATHLETE/TRAINER/CLUB_OWNER — пока не подключены к NavGraph), `Profile`, `ProfileEdit`, `Settings`.
- Особенность: `MyWorkouts`/`MyAthletes`/`MyClub` объявлены как маршруты, но в `AppNavGraph.kt` для них нет `composable {}` блоков — заготовка под будущую ролевую навигацию.

---

### presentation/menu

#### `app/src/main/java/com/example/smarttracker/presentation/menu/MenuScreen.kt`
Вкладка «Меню»: навигационная сетка разделов приложения + демо-лента достижений. Рабочие ссылки — «Профиль» и «Настройки».
- `@Composable fun MenuScreen(padding: PaddingValues, onNavigateToProfile: () -> Unit, onNavigateToSettings: () -> Unit = {})` — `Column` со скроллом, включает `MenuHeader()`, `MenuGrid(...)`, `AchievementsSection()`.
- `@Composable private fun MenuHeader()` — иконка + заголовок «Меню».
- `GridItemData` (`private data class`) — модель ячейки сетки (`painter`, `label`, `onClick`).
- `@Composable private fun MenuGrid(onNavigateToProfile: () -> Unit, onNavigateToSettings: () -> Unit)` — 5 пунктов («Клубы», «Достижения», «Шаблоны», «Профиль», «Настройки»); реальный `onClick` — у «Профиль» и «Настройки», остальные — `noOp`; раскладка: первый ряд — 4 элемента, второй ряд — 1 элемент прижат влево.
- `@Composable private fun GridCell(painter: Painter, label: String, onClick: () -> Unit)` — квадратная иконка с рамкой + подпись.
- `AchievementData` (`private data class`) — модель демо-достижения (`iconRes`, `title`, `description`, `timestamp`, `iconBackgroundColor`).
- `@Composable private fun AchievementsSection()` — жёстко закодированный список из 3 демо-достижений («Прощай, лень!», «Человек, а не бот», «Прописка оформлена») с разными цветами фона иконки (жёлтый/серебристый/бронзовый).
- `@Composable private fun AchievementCard(achievement: AchievementData)` — карточка с иконкой, заголовком, описанием, временной меткой.
- Особенность: весь список достижений — статичные заглушки, реальный бэкенд для достижений не подключён.

#### `app/src/main/java/com/example/smarttracker/presentation/menu/settings/SettingsViewModel.kt`
ViewModel экрана «Настройки». UiState — сам `AppSettings` (отдельная обёртка не даёт ничего: DataStore читается мгновенно, ошибки чтения storage деградирует к дефолтам сам).
- `SettingsViewModel` (`@HiltViewModel class SettingsViewModel(settingsStorage: SettingsStorage) : ViewModel()`).
  - `val state: StateFlow<AppSettings>` — `settingsStorage.settings.stateIn(viewModelScope, WhileSubscribed(5000), AppSettings())`.
  - `fun onAutopauseChanged(enabled: Boolean)` / `onVoiceCuesChanged(enabled: Boolean)` / `onVoiceCueIntervalChanged(intervalKm: Int)` / `onKeepScreenOnChanged(enabled: Boolean)` — fire-and-forget записи в storage (DataStore сериализует edit-транзакции; состояние обновится через подписку).

Тесты — `SettingsViewModelTest`.

#### `app/src/main/java/com/example/smarttracker/presentation/menu/settings/SettingsScreen.kt`
Экран «Настройки» (Меню → Настройки): секция «Тренировка», изменения применяются сразу (без кнопки «Сохранить»). Паттерн экрана — как ProfileScreen: `Scaffold` + `CenterAlignedTopAppBar` + `SmartTrackerBottomBar` (вкладка MENU подсвечена, тап по другой вкладке = `onBack`).
- `@Composable fun SettingsScreen(settings: AppSettings, onBack: () -> Unit, onAutopauseChanged, onVoiceCuesChanged, onVoiceCueIntervalChanged, onKeepScreenOnChanged)` — три `SwitchRow` («Автопауза», «Голосовые подсказки», «Не гасить экран») + `IntervalSelectorRow` (частота подсказок 1/2/5 км, виден только при включённых подсказках).
- `@Composable private fun SwitchRow(title, subtitle, checked, onCheckedChange)` — заголовок + подпись + Material3 `Switch` в цветах темы.
- `@Composable private fun IntervalSelectorRow(selectedKm: Int, onSelected: (Int) -> Unit)` — сегменты-пилюли из `AppSettings.ALLOWED_VOICE_INTERVALS`.

#### `app/src/main/java/com/example/smarttracker/presentation/menu/profile/ProfileUiState.kt`
Состояние экрана просмотра профиля — все значения уже приведены к готовым для отображения строкам.
- `ProfileUiState` (`data class`) — `isLoading: Boolean = true`, `firstName` (всегда заполнено), `lastName`/`middleName: String?` (null → «Не указано»), `username` (с «@»), `birthDate` (формат «ДД.ММ.ГГГГ»), `gender` (уже локализовано «Мужской»/«Женский»), `weight`/`height: String?` (целые числа как строки), `photoUrl: String?`, `photoKey: Long` (версия для Coil-кэша), `lastTrainingDate: String?`, `errorMessage: String?`.

#### `app/src/main/java/com/example/smarttracker/presentation/menu/profile/ProfileViewModel.kt`
ViewModel просмотра профиля: загрузка из API, форматирование полей, подгрузка даты последней тренировки.
- `ProfileViewModel` (`@HiltViewModel class ProfileViewModel(authRepository: AuthRepository, workoutRepository: WorkoutRepository) : ViewModel()`) — `dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")`.
  - `init { loadProfile() }`.
  - `private fun loadProfile(): Unit` — вызывает `authRepository.getUserInfo()`; на успех — `applyUser(user)`, затем (не блокируя) `workoutRepository.getTrainingHistory()` для вычисления `lastTrainingDate` = максимум по полю `date`; ошибка загрузки истории не прерывает отображение профиля.
  - `fun refreshFromCache(): Unit` — тихое обновление полей из кэша без индикатора загрузки; вызывается при возврате с экрана редактирования (кэш уже обновлён в `AuthRepositoryImpl.updateProfile` до навигации назад).
  - `private fun applyUser(user: User): Unit` — форматирует все поля; `photoKey` инкрементируется только если `photoUrl` реально изменился (`isPhotoChanged`), что форсирует перезагрузку Coil-кэша.
- Особенность: репозиторий вызывается напрямую без отдельного UseCase — операция тривиальна (один вызов API без побочных эффектов), выделение UseCase было бы бойлерплейтом.

#### `app/src/main/java/com/example/smarttracker/presentation/menu/profile/ProfileEditUiState.kt`
Состояние экрана редактирования профиля — поля хранятся как редактируемые строки (в отличие от `ProfileUiState`).
- `ProfileEditUiState` (`data class`) — `isLoading`, `isSaving`, `firstName`/`lastName`/`middleName` (пустая строка = не указано), `username` (без «@»), `birthDate` (8 цифр «DDMMYYYY», трансформация визуальная), `gender: String = "male"` (`"male"`/`"female"`), `height`/`weight: String`, `photoUrl: String?`, `photoKey: Long`, `isUploadingPhoto: Boolean`, `showDeleteConfirmDialog: Boolean`, `isDeleting: Boolean`, `errorMessage: String?`.

#### `app/src/main/java/com/example/smarttracker/presentation/menu/profile/ProfileEditViewModel.kt`
ViewModel редактирования профиля: загрузка из кэша, сохранение (`PATCH /user/edit`), загрузка/удаление фото, удаление аккаунта.
- `ProfileEditViewModel` (`@HiltViewModel class ProfileEditViewModel(authRepository: AuthRepository, @ApplicationContext context: Context) : ViewModel()`) — `maxPhotoBytes = 5 * 1024 * 1024L` (5 МБ).
  - `init { loadFromCache() }` — вызывает `authRepository.getUserInfo()` (кэш заполнен после логина, отдельного сетевого запроса обычно не требуется); конвертирует `LocalDate` в 8-цифровую строку `"$d$m$y"`.
  - `fun onFirstNameChange/onLastNameChange/onMiddleNameChange/onUsernameChange(v: String): Unit` — простые сеттеры.
  - `fun onBirthDateChange(v: String): Unit` — принимает только цифры, максимум 8.
  - `fun onGenderToggle(): Unit` — переключает `"male"`↔`"female"`.
  - `fun onHeightChange/onWeightChange(v: String): Unit`.
  - `fun onSave(): Unit` — конвертирует «DDMMYYYY» в ISO `"YYYY-MM-DD"` через `runCatching`, вызывает `authRepository.updateProfile(...)` со всеми полями как `takeIf { it.isNotBlank() }` (или `toFloatOrNull()` для роста/веса); на успех эмитит `ProfileEditEvent.NavigateBack`.
  - `fun onPhotoSelected(uri: Uri): Unit` — проверяет MIME-тип (`image/png`/`image/jpeg`), размер файла через `OpenableColumns.SIZE` до копирования, затем через `resolveUriToTempFile` копирует контент в `cacheDir`, вызывает `authRepository.uploadPhoto(file)`, обновляет `photoUrl` из свежего `getUserInfo()`, инкрементирует `photoKey`; временный файл удаляется в `finally`.
  - `private fun resolveUriToTempFile(uri: Uri, extension: String): File?` — копирует поток из `ContentResolver` во временный файл; если URI принадлежит собственному `FileProvider` приложения — удаляет исходный контент после копирования.
  - `fun onDeletePhoto(): Unit` — вызывает `authRepository.deletePhoto()`, обновляет `photoUrl`/`photoKey` аналогично загрузке.
  - `fun onDeleteAccountClick()/onDismissDeleteDialog()` — управление диалогом подтверждения.
  - `fun onConfirmDelete(): Unit` — вызывает `authRepository.deleteAccount()`; на успех эмитит `ProfileEditEvent.AccountDeleted`.
  - `private fun errorMessage(e: Throwable, fallback: String): String` — `IOException` → «Нет соединения...», иначе — сообщение исключения или `fallback`.
- `ProfileEditEvent` (`sealed class`, объявлен в этом же файле) — `NavigateBack`, `AccountDeleted`.
- Особенность: ограничение размера фото (5 МБ) проверяется дважды — по метаданным `ContentResolver` до копирования и по фактическому размеру скопированного файла после (на случай, если провайдер не отдал точный размер).

#### `app/src/main/java/com/example/smarttracker/presentation/menu/profile/ProfileScreen.kt`
Экран просмотра профиля: аватар, дата последней тренировки, поля в режиме "только чтение", кнопка выхода, полноэкранный просмотрщик фото.
- `@Composable fun ProfileScreen(state: ProfileUiState, onBack, onLogout, onEditProfile = {})` — `Scaffold` с `SmartTrackerBottomBar` (подсвечена вкладка MENU; клик на другую вкладку вызывает `onBack()`) и `CenterAlignedTopAppBar` (заголовок «Профиль» + кнопка `EditButton`); тело: при `isLoading` — `CircularProgressIndicator`, при `errorMessage != null` — текст ошибки по центру, иначе — скроллируемый список (`AvatarSection` + `ProfileFields`). Кнопка `LogoutButton` закреплена снизу вне `when {}` — видна во всех трёх состояниях (loading/error/data), чтобы пользователь мог выйти даже при ошибке загрузки. По клику на аватар открывается `PhotoViewerDialog`.
- `@Composable private fun EditButton(onClick: () -> Unit)` — кнопка «Ред.» с иконкой в шапке.
- `@Composable private fun AvatarSection(firstName, photoUrl, photoKey, lastTrainingDate, onAvatarClick)` — `ProfileAvatarImage` (96dp, кликабельная) + имя + текст «Дата последней тренировки: ...» (или «—»).
- `@Composable private fun ProfileFields(state: ProfileUiState)` — три группы полей: (Фамилия, Отчество), (Имя пользователя, Дата рождения, Пол), (Рост, Вес).
- `@Composable private fun ProfileField(label: String, value: String?)` — `ProfileFieldBox` + `AnnotatedString` с `ProfileTextStyles.fieldLabel`/`fieldValue`/`fieldEmpty` в зависимости от того, задано ли значение.
- `@Composable private fun PhotoViewerDialog(photoUrl: String?, onDismiss: () -> Unit)` — полноэкранный `Dialog` с чёрным полупрозрачным фоном (88% альфа), фото по центру через `AsyncImage`; тап в любом месте закрывает.
- `@Composable private fun LogoutButton(onClick: () -> Unit, modifier: Modifier = Modifier)` — кнопка «Выйти» с рамкой.

#### `app/src/main/java/com/example/smarttracker/presentation/menu/profile/ProfileEditScreen.kt`
Экран редактирования профиля: аватар с шторкой выбора источника фото (галерея/камера/удалить), редактируемые поля, диалог подтверждения удаления аккаунта.
- `@Composable fun ProfileEditScreen(state: ProfileEditUiState, множество callback-ов включая onPhotoSelected: (Uri) -> Unit, onDeletePhoto, onSave, onBack, onDeleteAccountClick, onDismissDeleteDialog, onConfirmDelete)` — использует `rememberLauncherForActivityResult` для трёх контрактов: `GetContent()` (выбор фото из галереи), `TakePicture()` (камера, пишет в файл через `FileProvider`), `RequestPermission()` (запрос `Manifest.permission.CAMERA` перед запуском камеры). `Scaffold` — `CenterAlignedTopAppBar` с кнопкой `SaveButton` и `SmartTrackerBottomBar`. При `state.isLoading` — спиннер, иначе — `AvatarEditSection` + скроллируемый список `EditField`/`EditDateField`/`GenderField` для всех редактируемых полей + `DeleteAccountButton`. Показывает `ModalBottomSheet` с тремя пунктами («Выбрать из галереи», «Сделать фото», «Удалить фото») при клике на аватар, и `AlertDialog` подтверждения удаления аккаунта при `state.showDeleteConfirmDialog`.
- `@Composable private fun SaveButton(isSaving: Boolean, onClick: () -> Unit)` — кнопка «Сохр.» с уменьшенной альфой во время сохранения/удаления.
- `@Composable private fun AvatarEditSection(photoUrl, photoKey, isUploading, onAvatarClick)` — аватар с оверлеем `CircularProgressIndicator` во время аплоада, подпись «Загружаем...»/«Изменить фото».
- `@Composable private fun EditField(label, value, onValueChange, keyboardType = Text)` — `BasicTextField` внутри `ProfileFieldBox`; цвет значения зависит от того, заполнено ли поле (`ColorSecondary` vs `ColorPrimary` с alpha 0.3).
- `@Composable private fun EditDateField(label, value, onValueChange)` — то же самое, но с `DateVisualTransformation()` и `KeyboardType.Number`.
- `@Composable private fun GenderField(gender: String, onToggle: () -> Unit)` — клик по всему полю переключает пол (без явного переключателя/радиокнопки).
- `@Composable private fun DeleteAccountButton(enabled, onClick, modifier)` — кнопка «Удалить аккаунт» красным цветом (`ColorDestructive`), блокируется во время сохранения/удаления.
- Особенность: логика фото camera/gallery полностью размещена в самом Composable (а не в ViewModel) — `cameraOutputUri` хранится через `remember { mutableStateOf<Uri?>(null) }`, временный файл для камеры создаётся через `File.createTempFile` + `FileProvider.getUriForFile(context, "${context.packageName}.provider", file)`.

---

### Тесты presentation

#### `app/src/test/java/com/example/smarttracker/presentation/menu/settings/SettingsViewModelTest.kt`
Юнит-тесты `SettingsViewModel` (5, mockito + StandardTestDispatcher): state отражает поток хранилища (через подписку — `stateIn(WhileSubscribed)` не собирает без подписчика); каждый из четырёх обработчиков транслируется в соответствующий вызов `SettingsStorage`.

#### `app/src/test/java/com/example/smarttracker/presentation/AppViewModelTest.kt`
Юнит-тесты `AppViewModel`. Покрывает риск молчаливой поломки авто-логина.
- `` `startRoute равен Home если токены есть` `` — `tokenStorage.hasTokens() = true` → `startRoute == Screen.Home.route`.
- `` `startRoute равен Login если токенов нет` `` — `hasTokens() = false` → `startRoute == Screen.Login.route`.
- `` `logout вызывает clearAll в TokenStorage` `` — проверка вызова `tokenStorage.clearAll()`.
- `` `logout вызывает clear в UserProfileCache` `` — проверка вызова `userProfileCache.clear()`.

#### `app/src/test/java/com/example/smarttracker/presentation/auth/forgot/ForgotPasswordViewModelTest.kt`
Юнит-тесты `ForgotPasswordViewModel`. Использует reflection (`setState`) для прямой установки `_uiState`, минуя `submitEmail()`, так как `android.util.Patterns.EMAIL_ADDRESS` возвращает `null` в JVM-тестах (без Android runtime) и вызывает NPE.
- `` `submitResetPassword - redirectToLogin=false приводит к NavigateToHomeAfterReset` `` — при `redirectToLogin = false` эмитится `NavigateToHomeAfterReset`, а `tokenStorage.clearAll()` не вызывается.
- `` `back на шаге 1 эмитит NavigateToLoginFromBack` ``.
- `` `back на шаге 2 уменьшает currentStep до 1` ``.
- `` `OnVerificationCodeChanged - нецифровые символы отфильтровываются, max 6` `` — проверяет порядок операций `take(6)` затем `filter { isDigit() }` (т.е. `"abc123456".take(6)` = `"abc123"`, после фильтра `"123"`).
- `` `resendCode при cooldown больше нуля не вызывает API` `` — `resendCodeCooldown = 60` блокирует вызов `repository.resendResetCode`.

#### `app/src/test/java/com/example/smarttracker/presentation/auth/login/LoginViewModelTest.kt`
Юнит-тесты `LoginViewModel`, сфокусированные на согласованности двух независимых проверок длины пароля.
- `` `isFormValid false при password длиной 7 символов` ``.
- `` `onSubmitLogin выставляет errorMessage при password длиной 7 символов` ``.

#### `app/src/test/java/com/example/smarttracker/presentation/auth/register/RegisterViewModelTest.kt`
Юнит-тесты `RegisterViewModel` — многошаговая регистрация. Стабы задаются ДО конструктора
(init запускает загрузку доменов и debounce-коллектор); helper'ы `fillStep1()`/`goToStep3()`/
`fillStep3Valid()` прогоняют флоу через публичный API. Покрывает: валидацию шага 1
(имя/username/дата/пол) и переходы; валидацию даты рождения (будущая, возраст 6–120);
шаг 2 (цель); шаг 3 (формат email, домен 149-ФЗ через `EmailValidator.RUSSIAN_EMAIL_REQUIRED_MESSAGE`,
пароль ≥8, совпадение, условия); успешную регистрацию (шаг 4 + кулдаун 120 сек, НЕ
`expiresIn=600` — нюанс 9; `roleId` из выбранной цели → `saveSelectedRoles` + `roleIds`
в `RegisterRequest`); верификацию email (короткий код, событие `NavigateToHome` — коллектор
подписывается до emit, `SharedFlow` без буфера); resend (кулдаун 120 + тик таймера через
`advanceTimeBy`); debounce-проверку nickname (700 мс, отсутствие вызова при len<3).

#### `app/src/test/java/com/example/smarttracker/presentation/menu/profile/ProfileViewModelTest.kt`
Юнит-тесты `ProfileViewModel`. ViewModel создаётся после стабов (init сразу грузит профиль).
Покрывает: форматирование полей для UI (username с «@», дата «ДД.ММ.ГГГГ», пол по-русски,
вес/рост целыми); `lastTrainingDate` = максимум по `date` из истории; ошибку загрузки
профиля (`errorMessage`, `isLoading=false`); ошибку истории, НЕ ломающую профиль;
`refreshFromCache` (тихое обновление, инкремент `photoKey` только при смене `photoUrl`,
ошибка не затирает показанные данные).

---

### Итоги по presentation (auth/common/theme/navigation/menu)

Все auth- и menu-экраны построены по единому MVI-подобному паттерну: `@HiltViewModel` хранит `MutableStateFlow<UiState>` (единый снапшот экрана, читается через `collectAsStateWithLifecycle`) и `MutableSharedFlow<Event>` для одноразовых навигационных команд, которые собираются в `AppNavGraph` через `LaunchedEffect(Unit) { viewModel.events.collect { ... } }` и транслируются в `navController.navigate(...)`. Многошаговые флоу (регистрация — 4 шага, восстановление пароля — 3 шага) хранят номер текущего шага прямо в `UiState.step`/`currentStep`, а переключение экранов делает `when` в корневом Composable, а не отдельные маршруты NavGraph — это упрощает шаринг состояния между шагами и общую валидацию. `AppNavGraph.kt` — единственная точка регистрации маршрутов (`Screen.kt` задаёт их строковые идентификаторы); примечательные детали: экран `Home` инкапсулирует и модуль тренировок, и оверлей итогов (без отдельного маршрута — чтобы не пересоздавать `MapView`), а `Profile` использует `repeatOnLifecycle(RESUMED)` для обновления данных при каждом возврате из редактирования. Переиспользуемые компоненты сосредоточены в `presentation/common`: `StepScaffold`+`StyledTextField`+`PrimaryButton` формируют единый визуальный язык auth-шагов (рамка `ColorPrimary`, скругление 10dp, кнопка снизу с индикатором загрузки), `ProfileFieldBox`+`ProfileAvatarImage` — общий язык экранов профиля, `SmartTrackerBottomBar` — единая нижняя навигация с индексами вкладок `AppTab`. Тема оформления (`SmartTrackerTheme.kt`) задаёт единственную светлую палитру (`ColorPrimary` тёмно-синий, `ColorSecondary` бирюзовый) и кастомную типографику на шрифте Geologica; `WorkoutTextStyles`/`ProfileTextStyles` — доменно-специфичные наборы стилей поверх той же гарнитуры, не привязанные к именам Material3 (`titleLarge` и т.д.), потому что семантика этих экранов не укладывается в стандартные роли типографики. Среди специфичных нюансов реализации: `DateVisualTransformation` хранит в state только цифры и добавляет точки визуально (обход курсорного бага), `LinkAnnotation.Clickable` используется вместо устаревшего `ClickableText` для кликабельных ссылок на условия/политику, debounce на 700ms плюс кэш в памяти защищают проверку уникальности никнейма от лишних запросов, а кулдаун 120 секунд на повторную отправку кода жёстко отделён от 600-секундного времени жизни самого кода. Юнит-тесты по презентационному слою покрывают `AppViewModel`, `ForgotPasswordViewModel`, `LoginViewModel`, `RegisterViewModel` и `ProfileViewModel`; `ProfileEditViewModel` тестами не покрыт (тех-долг).

---

## 2.6 presentation/workout/, presentation/calendar/, utils/


### presentation/workout

#### `presentation/workout/ActivityIcons.kt`
Единая точка маппинга серверного `iconKey` (строковое представление `type_activ_id`) на drawable-ресурсы иконок активности.
- `internal fun activityIconRes(key: String): Int` — `when` по строковому ключу: `"1"` → бег, `"2"` → северная ходьба, `"3"` → велосипед, `"5"` → ходьба, иначе `R.drawable.placeholder`.
Особенности/нюансы: используется как fallback-иконка, когда серверная (`iconFile`/`imageUrl`) недоступна. Единая функция для стартового экрана, оверлея итогов и календаря истории — при добавлении новых типов активностей достаточно поправить один `when`. Маппинг завязан на `type_activ_id`, а не на название (название зависит от языка API — см. пункт 14 CLAUDE.md).

#### `presentation/workout/WorkoutHomeScreen.kt`
Главный экран приложения после авторизации с тремя вкладками (Старт / Тренировки / Меню) через нижний бар.
- `@Composable fun WorkoutHomeScreen(onLogout, onNavigateToProfile, onNavigateToSettings)` — хоистит `WorkoutStartViewModel` на верхнем уровне (не внутри `when`), чтобы оверлей итогов тренировки переживал переключение вкладок. Использует `Scaffold` с `SmartTrackerBottomBar`; рендерит `WorkoutStartScreen`, `TrainingHistoryScreen` или `MenuScreen` в зависимости от выбранной вкладки.
- `private enum class WorkoutTab { START, WORKOUTS, MENU }` — порядок должен совпадать с `AppTab.START/WORKOUTS/MENU` (используется `.ordinal` для связки с `SmartTrackerBottomBar`).
- `@Composable private fun PlaceholderScreen(label, padding)` — заглушка "скоро" для незаконченных вкладок (сейчас не используется активно).
Особенности/нюансы: истечение сессии обрабатывается глобально в `AppNavGraph` (локальная подписка на `sessionExpired` удалена — раньше 401 вне Home не разлогинивал). `LaunchedEffect(currentTab)` закрывает оверлей итогов (`onCloseSummaryOverlay`) при уходе с вкладки «Старт» — пользователь явно покинул экран итогов. Клик по тренировке в истории переключает на вкладку START и вызывает `viewModel.showHistorySummary(item, activityName)`.

#### `presentation/workout/map/MapViewComposable.kt`
Composable-обёртка над `MapLibre MapView`: единственный источник отображения карты для активной тренировки, discovery-режима и оверлея итогов (summary/история).
- `@Composable fun MapViewComposable(modifier, currentLocation, lastKnownLocation, trackPoints, isTracking, isGpsActive, mapTilesFailed, onMapTilesFailed, enableLocationDot, fitToTrackBoundsKey, scrubPoint, startIconRes, attributionTopEnd, recenterTrigger, snapshotRequest, onSnapshot)` — создаёт `MapView` через `AndroidView` (factory/update), подключает жизненный цикл через `DisposableEffect(lifecycleOwner)`, рисует слои трека (`LineLayer`), scrub-маркера (`CircleLayer`), стартовой/финишной точки (`SymbolLayer`), управляет `LocationComponent` (живая GPS-точка) и режимом камеры (`TRACKING`/`NONE`). `snapshotRequest: Int` + `onSnapshot: ((Bitmap) -> Unit)?` — счётчик-триггер снимка карты для шаринга (паттерн `recenterTrigger`): инкремент → `MapLibreMap.snapshot()` → Bitmap в колбэк; в кадр попадает только MapView, Compose-оверлеи — нет; карта не готова → запрос молча игнорируется.
- `private fun activateLocationComponent(map, style, context)` — активирует `LocationComponent` с кастомным drawable `ic_location_dot`, `accuracyAlpha=0`, `pulseEnabled=false`, `cameraMode=TRACKING`, `renderMode=NORMAL`. Помечена `@SuppressLint("MissingPermission")` — разрешение уже выдано на момент вызова (сервис трекинга уже запущен).
- `private fun makeMarkerBitmap(icon, density, primaryArgb, tintIcon): Bitmap` — рисует композитный bitmap маркера старта/финиша: белый круг ~32dp, обводка `ColorPrimary` 1.5dp, иконка ~20dp по центру. `tintIcon=true` тонирует иконку `PorterDuffColorFilter(primaryArgb, SRC_IN)` (для старта — иконка активности), `tintIcon=false` оставляет исходные цвета (для финишного флага).
Особенности/нюансы (ключевые):
- **Жизненный цикл MapView и краши LocationComponent.** `DisposableEffect` синхронно гасит `LocationComponent` (cameraMode=NONE, isLocationComponentEnabled=false) и вызывает `cancelTransitions()` ДО `onDestroy()` — иначе аниматоры bearing/accuracy продолжают тикать после уничтожения стиля и бросают `IllegalStateException("Calling getSourceAs when a newer style is loading")`. То же самое повторяется в `ON_STOP`/`ON_START` наблюдателе (карта продолжает жить в backstack при Compose-навигации, `onDispose` не срабатывает).
- **Discovery-zoom и retained ViewModel.** Логика "zoom 16 при первом GPS-фиксе" дублируется и в `setStyle`-колбэке factory, и в `update`-блоке — потому что `AndroidView.update` может не получить второго шанса, если `isGpsActive` уже было `true` до перезагрузки style (например, при возврате на экран с retained ViewModel).
- **"Голова" трека.** Последняя точка трека всегда = визуальная позиция маркера: при `CameraMode.TRACKING` берётся `cameraPosition.target` (анимированная позиция, а не сырой GPS-фикс — иначе трек прыгал бы вперёд маркера); при `NONE` (пауза) — `lastKnownLocation` LocationComponent.
- **Маркеры старта/финиша** видны только когда `fitToTrackBoundsKey != null` (режим оверлея итогов).
- **Scrub-маркер** (`scrubPoint`) рендерится отдельным `CircleLayer`, передаётся из `WorkoutStartScreen` только когда открыт fullscreen-режим карты — вне fullscreen `null`.
- **Recenter по GPS-бейджу.** Счётчик `recenterTrigger` (не Boolean — два тапа подряд должны сработать дважды) триггерит `LaunchedEffect`, который анимирует камеру к последней известной позиции (`LocationComponent.lastKnownLocation` → `currentLocation` → `lastKnownLocation` пропс) и восстанавливает `TRACKING` в колбэке `onFinish`.
- **Fit-to-bounds** — one-shot анимация камеры на `LatLngBounds` всех `trackPoints` при появлении нового `fitToTrackBoundsKey` (сравнение с `state.lastFittedKey`), padding 40dp, выполняется через `mapView.post {}` (после layout-прохода, чтобы были известны финальные размеры).
- **Offline** — при `mapTilesFailed == true` карта не создаётся, сразу рендерится `OfflineMapFallback`.
- **UI-настройки:** логотип MapLibre скрыт (`isLogoEnabled=false`, разрешено BSD-лицензией), атрибуция OSM (`ODbL`) обязательна и передвигается в top-end при `attributionTopEnd=true` (fullscreen-режим), компас смещён под GPS-бейдж.

#### `presentation/workout/map/OfflineMapFallback.kt`
Текстовая заглушка карты, показываемая когда MapLibre не смог загрузить тайлы (нет сети и нет кэша).
- `@Composable fun OfflineMapFallback(currentLocation, modifier)` — рендерит иконку `Icons.Filled.LocationOn`, координаты текущей позиции (если есть) и тексты «Нет карты офлайн.» / «Трек записывается.».
Особенности/нюансы: показывается только при `mapTilesFailed == true` — пока тайлы грузятся из кэша (в т.ч. в авиарежиме), карта отображается штатно. GPS-трек продолжает записываться даже без визуализации.

#### `presentation/workout/permission/LocationPermissionHandler.kt`
Composable-обработчик системных разрешений для GPS-трекинга (шаги 0–3).
- `@Composable fun LocationPermissionHandler(onLocationGranted, onBackgroundGranted, onDenied, onBatteryOptResult, onPermissionsResult)` — оркестрирует последовательные запросы через `rememberLauncherForActivityResult`.
Особенности/нюансы:
- **Шаг 0** (только если разрешений ещё нет): пояснение об обработке геоданных (152-ФЗ) — `AlertDialog` (строки `geo_consent_dialog_*`) ДО первого системного запроса: когда пишется GPS-трек, ссылка на политику, как удалить данные. «Продолжить» → шаг 1; отказ/закрытие → `onDenied`. При уже выданных разрешениях диалог не показывается.
- **Шаг 1** (при первом запуске): `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` + `POST_NOTIFICATIONS` (Android 13+) одним batch-запросом.
- **Шаг 2** (только Android Q+, после шага 1): `ACCESS_BACKGROUND_LOCATION` отдельным запросом — система требует раздельного запроса, объединение с Fine/Coarse на Q+ приводит к `SecurityException`.
- **Шаг 3** (после шага 2, один раз за сессию экрана): Doze whitelist. Без него Android Doze throttle'ит GPS-callback'и через ~5-10 мин после выключения экрана даже для foreground-сервиса. Показывается объясняющий `AlertDialog` перед системным `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (UX best-practice). Защищено флагом `batteryOptRequestedThisSession` — повторный показ берёт на себя persistent-баннер на `WorkoutStartScreen`.
- Отказ от background-доступа не блокирует шаг 3 (foreground-режим тоже подвержен Doze throttling).
- Если разрешения уже выданы — повторный запрос не показывается (`LaunchedEffect(Unit)` проверяет `ContextCompat.checkSelfPermission`).

#### `presentation/workout/start/WorkoutStartScreen.kt`
Главный композитный экран тренировки: единая composable-функция для трёх визуальных режимов — «до старта», «активная тренировка», «оверлей итогов» (включая fullscreen-карту).
- `@Composable fun WorkoutStartScreen(state, padding, onStartClick, onTypeSelected, onSheetTypeSelected, onPauseClick, onFinishClick, onMapTilesFailed, onToggleFavorite, onSearchQueryChange, onCloseSummary, onToggleFullscreenMap, onDeleteHistoryTraining)` — корневой Composable экрана. Строит `Column` с шапкой (дата), опциональным Doze-баннером, телом (`ActiveBody`/`SummaryBody` наложены через alpha-анимацию, оба в дереве одновременно — фиксирует общую высоту layout), картой `MapViewComposable` с наложенными GPS-бейджем/кнопками/карточкой статистики, и прогресс-баром (только в fullscreen-оверлее). Также рендерит `ModalBottomSheet` выбора типа активности с поиском.
- **Панель деталей summary**: локальный `detailsExpanded by remember(summary)` — чеврон на `StatsRow` разворачивает `SummaryDetailsPanel` (сплиты + график) ПОВЕРХ зоны карты (Box с фоном после интерцептора клика; MapView остаётся в композиции); интерцептор «тап по карте → fullscreen» отключается при развёрнутых деталях; гейт чеврона — `summaryHasDetails(summary)`.
- **Шаринг**: `snapshotTick by remember` — «С картой» инкрементирует счётчик → `onSnapshot` собирает картинку `ShareImageComposer.composeWithMap` и открывает share sheet; «Только статистика» — `composeTrackCard` сразу; обе ветки на `Dispatchers.IO` через `rememberCoroutineScope`.
- **KeepScreenOn**: `DisposableEffect(state.isTracking, state.keepScreenOn)` ставит `view.keepScreenOn` только при активной записи; снимается в `onDispose`.
- `@Composable private fun ActiveHeader(dateDisplay)` — центрированная дата в шапке активной фазы.
- `@Composable private fun ActiveBody(state, onTypeSelected, onMoreClick, isMoreActive, interactive)` — таймер + 3 статистики (дистанция/темп/калории) + ряд из 3 закреплённых типов активности + кнопка "ещё" (`ic_activity_other`).
- `@Composable private fun StatItem(value, label, valueMinWidth)` — одна статистика с фиксированной минимальной шириной значения (от "прыжков" layout при смене длины числа).
- `@Composable private fun WorkoutTypeIcon(iconModel, contentDescription, isActive, enabled, onClick)` — иконка типа активности 42dp с рамкой, активное состояние подсвечивается `ColorSecondary`.
- `@Composable private fun BatteryOptBanner(onConfigureClick)` — предупреждающий баннер (мягкий амбер) о том, что приложение не в Doze whitelist.
Особенности/нюансы:
- **Оверлей итогов — не навигация, а смена состояния** (`state.summaryOverlay != null`). `MapView` остаётся той же composable-инстанцией — устраняет краши LocationComponent-анимаций MapLibre, которые возникали при переходе через `NavCompose`.
- **Scrub-режим только в fullscreen.** `scrubPoint = if (isFullscreen) scrubPoint else null` — маркер scrubbing передаётся в карту только когда открыт полноэкранный режим; в компактном оверлее карта маленькая, маркер отвлекает.
- **trackPoints — освобождение памяти.** `trackPoints = summary?.trackPoints ?: state.trackPoints` — после `onFinishClick` live-список очищается во ViewModel, карта переключается на snapshot из `summaryOverlay`, дублирования ~1800 точек (~180 КБ на час тренировки) не происходит.
- **Doze-баннер** обновляется через `DisposableEffect` + `LifecycleEventObserver` на `ON_RESUME` (юзер мог вернуться из системных настроек после нажатия "Разрешить").
- **Blur карты до старта GPS.** `Modifier.blur(8.dp)` применяется когда `!overlayVisible && !state.isGpsActive` на API 31+ (`Build.VERSION_CODES.S`); для более старых версий — тёмный скрим `Color.Black.copy(alpha=0.60f)` (blur недоступен).
- **BackHandler**: в fullscreen сворачивает карту к обычному оверлею; при развёрнутой панели деталей — сворачивает панель; иначе закрывает оверлей целиком.
- **GPS-бейдж** кликабелен — инкрементирует `recenterTick` (передаётся в `MapViewComposable.recenterTrigger`), цвет фона зависит от `state.isGpsActive` (`ColorGpsActive`/`ColorGpsInactive`).

#### `presentation/workout/start/WorkoutStartViewModel.kt`
Центральный `@HiltViewModel` экрана тренировки: управляет жизненным циклом `LocationTrackingService`, таймером, инкрементальным расчётом статистики (дистанция/темп/калории), состоянием GPS, оффлайн-очередями сохранения и построением снимка итогов тренировки.
- `enum class GpsStatus { SEARCHING, ACQUIRED, UNAVAILABLE }` — статус получения GPS-фикса.
- `data class UiState(...)` — большой стейт: `currentDate`, `workoutTypes`, `pinnedTypes`, `selectedType`, `isTracking`, `isWorkoutStarted`, `gpsStatus`, `elapsedMs`, `timerDisplay`, `distanceDisplay`/`distanceMeters`, `avgSpeedDisplay`, `caloriesDisplay`/`kilocalories`, `trackPoints`, `pauseGapIndices`, `mapTilesFailed`, `isStarting`, `keepScreenOn` (из настроек, флаг ставит экран), `favoriteIds`, `searchQuery`, `lastKnownLocation`, `summaryOverlay: WorkoutSummaryUiState?`, `isMapFullscreen`. Вычисляемые свойства: `isGpsActive`, `filteredAndSortedTypes`. Конструктор дополнительно принимает `SettingsStorage` (подписка на `keepScreenOn` в `init`).
- `val state: StateFlow<UiState>` (сигнал `sessionExpired` из ViewModel удалён — истечение сессии обрабатывается глобально в `AppNavGraph` через `AppViewModel.sessionExpired`).
- `fun onStartWorkoutClick()` — немедленно стартует foreground-сервис (уведомление появляется сразу), генерирует `localUUID`, параллельно регистрирует тренировку на сервере через `POST /training/start`. При `ActiveTrainingConflictException` вызывает `finishOrphanedAndRetryStart`. При ошибке сети остаётся на `localUUID` — `SyncGpsPointsWorker` зарегистрирует позже.
- `private suspend fun finishOrphanedAndRetryStart(typeActivId, localUUID)` — авто-завершает "осиротевшую" активную тренировку на сервере (`GET /training/active` → `save_training` → повторный `start`), приостанавливая запись GPS на время resolve.
- `private fun resumeTracking()` — запускает таймер + сервис; при первом старте фиксирует `trainingStartTimestamp`, переводит discovery-сервис в режим тренировки через `transitionToWorkout` (без цикла stop/start), асинхронно удаляет discovery-точки.
- `fun onPauseClick()` — замораживает таймер, `LocationTrackingService.setRecording(context, false)` (GPS продолжает работать, но точки не пишутся в Room).
- `fun onFinishClick()` — останавливает трекинг, строит `snapshot: WorkoutSummaryUiState` (форматирование через `WorkoutSummaryFormatters`, `calculateElevationGain`, `buildCumulativeData`, `SplitsBuilder.buildSplits`, `pauseGapIndices` — для графика), сохраняет тренировку на сервер fire-and-forget (либо напрямую через `saveTraining`, либо через офлайн-очередь `savePendingFinish` + `offlineFinishScheduler.enqueue`, если тренировка не была зарегистрирована на сервере). При успешном прямом `saveTraining` удаляет локальные GPS-точки тренировки из Room (`deletePointsForTraining` под `NonCancellable`) — иначе неотправленный хвост копился бы вечно. Live-поля (`trackPoints` и т.д.) НЕ сбрасываются здесь — сброс в `onCloseSummaryOverlay`.
- `private fun reattachToRecoveredSession(s: RecoverableSession)` — восстановление UI после смерти процесса: в `init` перед discovery проверяется `LocationTrackingService.readRecoverableSession(context)`; при живой сессии (heartbeat свежее `RECOVERY_STALE_MS`) ViewModel переподключается к работающему сервису — восстанавливает `currentTrainingId`/`isTrainingRegisteredOnServer`/`trainingStartTimestamp`, elapsed-таймер, `pauseGapIndices` (кладутся в state ДО `observeTrackingData` — наблюдатель читает их для расчёта дистанции), тип активности (через `recoveredTypeActivId` в `collectWorkoutTypes`); `observeTrackingData` пересчитывает статистику из Room с нуля. Discovery-GPS при этом НЕ запускается — его интент перезаписал бы `trainingId` живого сервиса.
- `fun onCloseSummaryOverlay()` — поведение зависит от `origin`: `FINISH` — полный сброс live-состояния + рестарт discovery GPS; `HISTORY` — закрывает только оверлей, не трогая параллельную активную тренировку (плюс отмена `historyDetailJob`).
- `fun onToggleFullscreenMap()` — переключает `isMapFullscreen`.
- `fun onDeleteHistoryTraining()` — удаляет тренировку из истории (`DELETE .../delete_completed`), только для `SummaryOrigin.HISTORY`.
- `fun showHistorySummary(item: TrainingHistoryItem, activityName: String)` — показывает оверлей итогов для тренировки из истории: сразу `isLoading=true`, затем асинхронно тянет GPS-трек через `GET /training/{id}/get_training`.
- `private fun calculateElevationGain(points): Double` — суммирует только положительные дельты `altitude` между соседними точками, null-точки пропускаются без разрыва цепочки.
- `private fun buildCumulativeData(points, pauseGapIndices): CumulativeTrackData` — предвычисляет накопленные дистанцию/высоту/время/скорость на каждую точку для O(1) scrub-lookups; симметрична live-расчёту в `observeTrackingData` (одинаковые gap-индексы пропускаются).
- `private fun observeTrackingData(trainingId)` — единый наблюдатель GPS-точек: инкрементальный расчёт (обрабатываются только новые пары точек, не полный проход O(n²)) на `Dispatchers.Default`; GPS-таймаут 30 сек без новых точек → `UNAVAILABLE` (тренировка не останавливается).
- `private fun startDiscoveryGps()` / `startGpsStatusObserver` — discovery-режим GPS до нажатия «Начать»: сервис пишет точки под временным UUID, статистика не считается, только `gpsStatus`.
- `override fun onCleared()` — останавливает сервис и удаляет discovery-точки при уничтожении ViewModel.
- `companion object` — `formatDuration`, `formatPace`, `formatCurrentDate` (статические форматтеры для использования вне ViewModel).
Особенности/нюансы (ключевые):
- **Инкрементальный расчёт дистанции/калорий** — O(n_new) вместо O(n²); `accumulatedDistanceM`/`accumulatedKilocalories` живут в скоупе корутины `observeTrackingData`, не читаются из `_state.value` (иначе задвоение при перезапуске observer'а после re-key `localUUID → serverUUID`).
- **Gap-индексы паузы** (`pauseGapIndices`) — точный индекс первой пост-резюм точки приходит от сервиса (`LocationTrackingService.recordingStateFlow`), а не вычисляется по размеру `trackPoints` (тот отстаёт от буфера сервиса на 1-2 точки). Используются и в live-расчёте, и в `buildCumulativeData` — обе пропускают "телепорт"-пары через паузу.
- **calculateDeltaDistance / calculateTrainingStatsUseCase.distanceBetween** используется попарно (i-1, i) — согласно CLAUDE.md пункту 20, семантика распространяется на весь `CalculateTrainingStatsUseCase`, здесь вызывается напрямую `distanceBetween(points[i-1], points[i])`, что уже корректно (не полная семантика `calculateDeltaDistance(points, fromIndex)`, которая суммирует до конца списка).
- **0.0 явно отправляется на сервер** вместо null для `total_distance_meters`/`total_kilocalories` — Gson дропает null-поля, пустое тело `{"time_end":"..."}` вызывает 500 на бэке (баг сервера, зафиксирован в логах).
- **NonCancellable** обёртки вокруг `savePendingFinish` + `offlineFinishScheduler.enqueue` — гарантируют запись в офлайн-очередь даже если ViewModel уничтожается посреди операции (закрытие приложения).
- **Race condition профиля**: если `loadUserProfile()` завершается после старта сервиса, профиль досылается через `EXTRA_PROFILE_UPDATE` Intent без перезапуска трекинга.
- **MET-скорость/интервалы** зависят от типа активности: `iconKey == "3"` (велосипед) использует другой `intervalMs`/`accuracyThreshold` (`LocationConfig.INTERVAL_MS_CYCLING`/`MAX_ACCURACY_CYCLING`).

#### `presentation/workout/summary/SummaryOverlay.kt`
Набор Composable-компонентов оверлея итогов тренировки — рендерятся поверх `WorkoutStartScreen` без навигации.
- `@Composable fun SummaryHeader(dateDisplay, showDelete, onDeleteClick, onShareWithMap, onShareStatsOnly)` — шапка с центрированной датой; справа — иконка «поделиться» (диалог выбора «С картой / Только статистика», скрыта при null-колбэках) и иконка корзины (с `AlertDialog` подтверждения "Удалить тренировку?") — только при `showDelete=true` (для `SummaryOrigin.HISTORY`).
- `@Composable fun SummaryBody(state: WorkoutSummaryUiState, detailsExpanded: Boolean = false, onToggleDetails: (() -> Unit)? = null)` — обёртка: `ActivityHeader` + `StatsRow`.
- `@Composable private fun ActivityHeader(state)` — иконка активности (74dp рамка) + название + разделитель + темп.
- `@Composable private fun StatsRow(state, detailsExpanded, onToggleDetails)` — три `StatCard` (дистанция/продолжительность/набор высоты) + стрелка-чеврон поверх правой границы третьей карточки: тап разворачивает панель деталей (поворот на 90° анимирован), при `onToggleDetails == null` чеврон декоративный.
- `@Composable private fun StatCard(iconRes, value, label, modifier)` — карточка 120dp высотой с иконкой/значением/лейблом.
- `data class ScrubDisplayStats(speedDisplay, elapsedDisplay, distanceDisplay, elevationDisplay)` — данные для отображения в момент scrubbing, заменяют суммарные значения.
- `@Composable fun StatsOverlayCard(state, modifier, scrubStats)` — компактная карточка 133×100dp из 4 строк статистики поверх карты в fullscreen-режиме (Figma 723:460); если передан `scrubStats`, отображает значения в текущей точке скраба вместо итоговых.
- `@Composable private fun StatsOverlayRow(iconRes, value)` — одна строка карточки (иконка + значение).
- `@Composable fun TrainingProgressBar(progress, modifier, onProgressChange)` — pill-полоса прогресса под fullscreen-картой: заливка `ColorSecondary` слева от бегунка, `ColorPrimary` справа, круглый белый бегунок с обводкой. Обрабатывает тап (прыжок на позицию) и `horizontalDrag` (плавное перемещение) через `awaitEachGesture`.
Особенности/нюансы: `StatsOverlayCard` и `SummaryBody` переиспользуют `WorkoutSummaryUiState` — единая модель для оверлея FINISH и превью HISTORY. Удаление тренировки доступно только из оверлея, открытого из истории (не из только что завершённой).

#### `presentation/workout/summary/WorkoutSummaryFormatters.kt`
`object` с чистыми функциями форматирования для построения `WorkoutSummaryUiState` — не зависят от состояния, вынесены отдельно от ViewModel.
- `fun formatDate(timestampMs: Long): String` — "dd.MM.yyyy (День недели)" в русской локали, системная таймзона.
- `fun formatDistance(km: Float): String` — "1.23 км" (2 знака, точка-разделитель, `Locale.US`).
- `fun formatElevation(m: Float): String` — "12.3 м" (1 знак).
- `fun formatDuration(elapsedMs: Long): String` — "HH:MM:SS" с ведущими нулями.
- `fun formatPace(distanceKm: Float, durationMs: Long): String` — средний темп "M:SS мин/км"; возвращает "—" при нулевой дистанции/длительности.
- `fun formatInstantPace(speedMs: Float?): String` — мгновенный темп из скорости м/с, используется при scrubbing.
Особенности/нюансы: не путать с `WorkoutStartViewModel.formatPace` (принимает скорость м/с напрямую) — используется во время активной тренировки, здесь — только для готового снимка итогов (известны суммарная дистанция и длительность).

#### `presentation/workout/summary/WorkoutSummaryUiState.kt`
Модели данных снимка итогов завершённой/просматриваемой тренировки.
- `data class CumulativeTrackData(distancesKm, elevationsM, elapsedMs, speedsMs: List<Float>)` — предвычисленные накопленные значения трека для O(1) scrub-lookups; индекс `i` соответствует i-й GPS-точке. `speedsMs[i]` — мгновенная скорость на отрезке (i-1→i), для истории вычисляется на клиенте (сервер не отдаёт `speed` в `gps_track`).
- `enum class SummaryOrigin { FINISH, HISTORY }` — источник открытия оверлея, определяет поведение `onCloseSummaryOverlay` (полный сброс live-состояния vs только закрытие оверлея).
- `data class WorkoutSummaryUiState(origin, trainingId, dateDisplay, activityName, activityIconFile, activityIconUrl, activityIconKey, paceDisplay, distanceDisplay, durationDisplay, elevationDisplay, trackPoints, cumulativeData, splits, pauseGapIndices, isLoading)` — итоговый снимок, все строковые поля уже отформатированы на момент построения. `splits: List<SplitUi>` — километровые сплиты для панели деталей (пусто без настоящих временных меток); `pauseGapIndices` — для разрывов графика скорости (для HISTORY всегда пуст).
Особенности/нюансы: `trainingId` заполняется только для `HISTORY` (нужен для `DELETE /training/{id}/delete_completed`); для `FINISH` остаётся `null`, т.к. ID уже был передан в `saveTraining`, а сам оверлей не предлагает удаление.

#### `presentation/workout/summary/SplitsBuilder.kt`
Чистая логика разбивки трека на километровые сплиты по `CumulativeTrackData`.
- `data class SplitUi(label, paceDisplay, relativeSpeed, isPartial)` — строка таблицы «Сплиты»; `relativeSpeed` 0..1 — длина бара относительно самого быстрого круга.
- `SplitsBuilder` (object):
  - `const MIN_PLAUSIBLE_ELAPSED_MS = 10_000L` — гейт настоящего тайминга (история до BR-5 имеет синтетические `timestampUtc = index` → elapsed в единицы мс).
  - `fun hasRealTiming(data: CumulativeTrackData): Boolean` — гейт сплитов и графика скорости; после BR-5 история пройдёт его автоматически.
  - `fun buildSplits(data: CumulativeTrackData): List<SplitUi>` — время пересечения границы километра интерполируется линейно между точками; одна пара точек может пересечь несколько границ (while, не if); неполный хвост ≥ 50 м добавляется с фактическим темпом (`isPartial=true`). Паузы уже учтены построением `CumulativeTrackData` (gap-пары не дают дистанции, время паузы вычтено).

Тесты — `SplitsBuilderTest`.

#### `presentation/workout/summary/TrackChart.kt`
Первый график проекта: Canvas-кривая «скорость/высота по дистанции» для панели деталей. Сторонних chart-библиотек нет.
- `data class ChartPoint(x: Float, y: Float)` — x = дистанция от старта (км).
- `TrackChartData` (object, чистая подготовка данных):
  - `MAX_POINTS = 200`, `SMOOTH_WINDOW = 5`;
  - `fun buildSegments(xs, ys, gapIndices: Set<Int>): List<List<ChartPoint>>` — разрыв линии на gap-парах пауз, сегменты из 1 точки отбрасываются;
  - `fun smooth(points, window)` — центрированное скользящее среднее по y (края — усечённое окно);
  - `fun downsample(points, maxPoints)` — бакеты со средней точкой (среднее и по x, и по y);
  - `fun prepare(xs, ys, gapIndices, smoothWindow)` — конвейер: сегменты → сглаживание → даунсемпл.
- `@Composable fun TrackChart(segments, yLabel: (Float) -> String, modifier)` — Canvas: кривая `ColorChartLine` 2dp + градиентная заливка `ColorSecondary` под ней, пунктирная сетка min/mid/max по Y с подписями (`TextMeasurer`), подписи X (0 и полная дистанция); < 2 точек → плейсхолдер «Недостаточно данных»; плоская линия растягивается на ±1 (нет деления на ноль).

Особенности: `ColorChartLine` (#2E8C86, тёмный шаг мятного) вместо `ColorSecondary` — контраст 4.0:1 на белом против 2.7:1 (< WCAG 3:1 для нетекстовой графики). Одна серия на полотне — переключение чипами вместо двойной Y-оси. Тесты — `TrackChartDataTest`.

#### `presentation/workout/summary/SummaryDetailsPanel.kt`
Панель «Детали» оверлея итогов: сплиты + график профиля тренировки. Разворачивается чевроном `SummaryBody`, рисуется поверх зоны карты в `WorkoutStartScreen` (MapView не убирается из композиции).
- `fun summaryHasDetails(state: WorkoutSummaryUiState): Boolean` — гейт чеврона: сплиты непусты ИЛИ есть настоящий тайминг ИЛИ ≥2 точки с альтиметрией.
- `@Composable fun SummaryDetailsPanel(state, modifier)` — verticalScroll-колонка: секция «Сплиты» (`SplitsHeaderRow` + `SplitRow` — номер км / темп / бар относительной скорости) + секция «Профиль тренировки» (чипы «Скорость/Высота» + `TrackChart`). Скорость: индексы gap-пар и i=0 исключаются из серии (их speed=0 — артефакт расчёта; дистанция на паузе не растёт — пропуск не рвёт ось X). Высота: сырые `trackPoints[i].altitude` (null пропускаются), окно сглаживания 9 (GPS-альтиметр шумит ±5–10 м), непрерывна через паузы. Тапы глушатся `detectTapGestures {}` — под панелью лежит интерцептор «развернуть карту».
- Доступность серий: скорость — только при `hasRealTiming` (для истории после BR-5); высота — и для истории (alt есть в GeoJSON).

#### `presentation/workout/summary/ShareImageComposer.kt`
Сборка шаринг-картинки тренировки (`android.graphics.Canvas`, вне UI-дерева) и отправка через share sheet.
- `data class ShareStats(activityName, dateDisplay, distanceDisplay, durationDisplay, paceDisplay)` — готовые строки из `WorkoutSummaryUiState`.
- `ShareImageComposer` (object):
  - `fun normalizeTrack(points: List<LocationPoint>): List<Pair<Float, Float>>` — чистая нормализация трека в единичный квадрат: cos-коррекция долготы (без неё маршрут сплющивается на северных широтах), сохранение пропорций, инверсия Y, центрирование; < 2 точек или вырожденный трек → пустой список;
  - `fun composeWithMap(context, mapSnapshot: Bitmap, stats): Bitmap` — копия снимка + скруглённая плашка `ColorPrimary` снизу с текстом (Geologica через `ResourcesCompat.getFont`); крупная строка статов ужимается под ширину;
  - `fun composeTrackCard(context, trackPoints, stats): Bitmap` — карточка 1080×1080: фирменный фон, силуэт маршрута `ColorSecondary` (форма без геопривязки — место не раскрывается), статы снизу;
  - `fun shareBitmap(context, bitmap)` — PNG в `cacheDir/share/workout_share.png` (перезапись одним именем) → `FileProvider.getUriForFile` (authority `${packageName}.provider` — тот же, что у камеры) → `ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION` + chooser; вызывать с IO-диспетчера.

Тесты — `ShareImageComposerTest` (normalizeTrack).

### presentation/calendar

#### `presentation/calendar/CalendarComponents.kt`
Переиспользуемые Composable-примитивы и Modifier-расширения для трёх видов таймлайна истории (День/Неделя/Месяц) — строит визуальное "дерево" со стволом и карточками.
- `internal fun Modifier.timelineCardSurface(shape, background): Modifier` — стандартная "поверхность" карточки: рамка `TrunkColor` + clip + фон.
- `internal fun Modifier.drawTrunk(): Modifier` — рисует вертикальный ствол (16dp, `TrunkColor`) по горизонтальному центру через `drawBehind`.
- `@Composable internal fun TrunkNode(isCurrent: Boolean)` — нод дерева: `ic_active_node` (текущий период, `ColorSecondary`) либо `ic_common_node` (`ColorPrimary`).
- `@Composable internal fun TimelineRow(isCardRight, isCurrent, label, modifier, card)` — базовая строка таймлайна: левая половина / нод (32dp) / правая половина; карточка и текстовая метка чередуются местами в зависимости от `isCardRight`.
- `@Composable internal fun TimelineCardWrapper(isCardRight, onClick, content)` — Box-обёртка вокруг карточки, добавляет зазор от ствола и опциональную кликабельность.
- `@Composable internal fun PeriodLabel(text, isCurrent)` — текстовая метка периода (жирный `TealAccent` для текущего, обычный `TrunkColor` для остальных).
- `@Composable internal fun TimelineInfoColumn(modifier, verticalArrangement, content)` — готовая инфо-колонка карточки (белый фон, рамка, скругление справа).
- `@Composable internal fun InfoRow(iconRes, value, textStyle, iconSize)` — строка "иконка + текст" внутри карточки, с `Ellipsis`-обрезкой в одну строку.
- `@Composable internal fun TimelineIconBox(iconRes, bgColor, boxSize, iconSize, cornerRadius)` — квадратная иконка с фоном (используется в стрипах Week/Month и Day-полоске).
Особенности/нюансы: используется во всех трёх видах (`DayTimelineView`, `WeekTimelineView`, `MonthTimelineView`) для единообразного визуального языка "дерева" с чередующимися карточками.

#### `presentation/calendar/CalendarConstants.kt`
Общие константы визуального стиля таймлайна: цвета, форматтеры дат, размеры карточек.
- `internal val TrunkColor: Color` = `ColorPrimary`; `internal val TealAccent: Color` = `ColorSecondary`.
- `internal val DateFmt`, `DateShortFmt: DateTimeFormatter` — "dd.MM.yyyy" и "dd.MM.yy".
- `internal fun activityColorFor(id: Int): Color` — цвет полоски активности по `type_activ_id` (1→красный бег, 2→бирюзовый, 3→голубой вело, 4→зелёный силовая, 5→синий ходьба, иначе серый).
- `internal object TimelineDims` — централизованные размеры: `TrunkWidth` (16dp), `NodeColumnWidth` (32dp), `CornerRadius` (10dp), `BorderThickness` (1dp), `TrunkGap` (12dp), `InfoCardWidth` (120dp), паддинги, `IconBoxCornerRadius` (5dp).
- `internal val TimelineStripShape`, `TimelineInfoShape: RoundedCornerShape` — скругление слева (стрип) и справа (инфо-блок).
Особенности/нюансы: централизованное место размеров — изменения подхватываются во всех трёх view (Day/Week/Month) одновременно.

#### `presentation/calendar/CalendarFormatters.kt`
Чистые функции форматирования времени/дистанций и агрегации статистики для истории тренировок.
- `internal fun formatTime(iso: String?): String` — ISO datetime (UTC) → локальное время "HH:mm" через `ZoneId.systemDefault()`; fallback на substring для нестандартных форматов (юнит-тесты).
- `internal fun formatTimeRange(start, end): String` — "HH:MM - HH:MM".
- `internal fun formatDurationBetween(start, end): String` — разница ISO-datetime → "HH:MM:SS" или "--".
- `internal fun formatSeconds(secs: Long): String` — секунды → "HH:MM:SS".
- `internal fun formatDistanceM(m: Double?): String` — метры → "11,22 км" (запятая) или "350 м" или "--".
- `internal fun formatKcal(kcal: Double?): String` — "536 кКал" или "--".
- `internal fun parseDateTime(iso: String): LocalDateTime` — три уровня fallback парсинга (OffsetDateTime → LocalDateTime → фиктивная дата + время).
- `internal fun TrainingHistoryItem.durationSeconds(): Long` — extension-функция длительности одной тренировки.
- `internal fun totalDurationSeconds(items): Long` — сумма длительностей списка.
- `internal data class PeriodTotals(seconds, distanceM, kilocalories, elevationM)` — агрегированные итоги периода.
- `internal fun aggregateTotals(items): PeriodTotals` — суммирует длительность/дистанцию/калории/набор высоты; nullable-поля → null если сумма 0 (чтобы форматтеры показали "--").
- `internal fun longestTypeIdOf(items): Int?` — `typeActivId` самой длинной тренировки.
- `internal fun dominantType(items): Pair<Int, Float>?` — доминирующий тип активности за период + его доля в процентах от общего времени.
- `internal fun generateWeeksForMonth(monthStart): List<LocalDate>` — список понедельников недель, покрывающих месяц (первый понедельник может быть в предыдущем месяце).
Особенности/нюансы: `formatTime` исправляет прежний баг — раньше время резалось substring без учёта таймзоны, тренировка в 11:44 MSK показывалась как "08:44". `elevationM` в `PeriodTotals` берётся из серверного поля `elevation_gain` (`/training/history`), не пересчитывается на клиенте для агрегатов.

#### `presentation/calendar/DayTimelineView.kt`
Дневной вид истории тренировок — список карточек тренировок конкретного дня, чередующихся лево/право от "ствола".
- `@Composable internal fun DayTimelineView(state, onTrainingClick)` — фильтрует `state.items` по `state.selectedDate`, сортирует по `timeStart`; при пустом списке показывает "Нет тренировок за этот день"; иначе `LazyColumn` с центрированием (`Arrangement.spacedBy(16.dp, CenterVertically)`) если ≤4 тренировок.
- `@Composable private fun DayRow(item, activityName, isCardRight, onTrainingClick)` — одна строка таймлайна (`TimelineRow` + карточка).
- `@Composable private fun DayCard(item, activityName)` — карточка: цветная полоска (`activityColorFor`, иконка активности 20dp) + инфо-блок (название / длительность / дистанция-или-калории — приоритет дистанции, если она известна).
Особенности/нюансы: клик по карточке вызывает `onTrainingClick(item, activityName)` → в `WorkoutHomeScreen` открывает `SummaryOverlay` через `viewModel.showHistorySummary`. Название активности разрешается через `state.workoutTypes.find { it.id == item.typeActivId }`.

#### `presentation/calendar/MonthTimelineView.kt`
Месячный вид истории — один нод = одна неделя (Пн–Вс), обычно 4-5 нодов на месяц.
- `@Composable internal fun MonthTimelineView(state, onWeekSelected)` — генерирует недели месяца через `generateWeeksForMonth`, для каждой фильтрует `items` в диапазон `[weekStart, weekEnd]`.
- `@Composable private fun MonthWeekRow(weekStart, weekEnd, weekItems, isCardRight, isCurrent, onWeekSelected)` — строка недели; если `weekItems` пуст — карточка не рисуется (только нод + метка диапазона дат).
- `@Composable private fun MonthWeekCard(weekItems, weekStart)` — карточка агрегатов недели: стрип из 7 иконок (`MonthActivityStrip`, по дню недели) + инфо-блок из 6 строк ("Тр. - N" жирным, доминирующий тип с процентом, время, дистанция, набор высоты, калории).
- `@Composable private fun MonthActivityStrip(dayTypeIds: List<Int?>)` — 7 квадратных иконок: `null` (нет тренировки) → белый фон + `ic_sleep`; иначе → `TealAccent` фон + иконка активности самой длинной тренировки дня.
Особенности/нюансы: тап по карточке недели → `onWeekSelected(weekStart)` переключает `TrainingHistoryViewModel` в `WEEK`-режим для этой недели. Поле "набор высоты" использует серверное `elevation_gain` через `aggregateTotals`.

#### `presentation/calendar/TrainingHistoryScreen.kt`
Корневой экран истории тренировок с тремя режимами (День/Неделя/Месяц), переключаемыми жестами pinch/spread.
- `@Composable fun TrainingHistoryScreen(padding, onNavigateToStart, onTrainingClick)` — хоистит `TrainingHistoryViewModel`, сбрасывает на "День/сегодня" при каждом входе (`LaunchedEffect(Unit) { viewModel.resetToToday() }`). Обрабатывает `pointerInput { detectTransformGestures }`: накопленный `scale > 1.3` → `onZoomIn()` (углубление в детали DAY←WEEK←MONTH), `scale < 0.7` → `onZoomOut()` (обобщение). `BackHandler` работает, только если `backStack` не пуст.
- `@Composable private fun HistoryHeader(state)` — центрированная метка периода (дата/диапазон недели/диапазон месяца через `periodLabel`).
- `private fun periodLabel(state): String` — форматирует заголовок в зависимости от `viewMode`.
- `@Composable private fun StartWorkoutButton(label, onClick)` — кнопка внизу экрана (текст меняется: "Начать свою тренировку" в DAY-режиме, "Запланировать тренировку" в остальных); белые `Spacer` перекрывают линию ствола до/после кнопки.
Особенности/нюансы: жест инвертирован интуитивно (spread=увеличение=углубление в детали, как zoom на карте/фото). Ствол дерева рисуется `Modifier.drawTrunk()` на весь контентный `Box`, включая область под кнопкой.

#### `presentation/calendar/TrainingHistoryUiState.kt`
Модель режимов просмотра и состояния экрана истории тренировок.
- `enum class HistoryViewMode { DAY, WEEK, MONTH }` — с методами `zoomIn()` (MONTH→WEEK→DAY) и `zoomOut()` (DAY→WEEK→MONTH), в конечных состояниях no-op.
- `data class TrainingHistoryUiState(isLoading, items, workoutTypes, error, viewMode, selectedDate, backStack: List<Pair<HistoryViewMode, LocalDate>>)`.
Особенности/нюансы: `selectedDate` — опорная дата, интерпретируется по-разному в зависимости от `viewMode` (конкретный день / неделя, в которую попадает дата / месяц). `backStack` пушится при каждой навигации, используется `onBack()` для возврата.

#### `presentation/calendar/TrainingHistoryViewModel.kt`
`@HiltViewModel` экрана истории: загрузка списка тренировок, навигация между периодами через zoom/tap/back.
- `fun loadHistory()` — вызывает `workoutRepository.getTrainingHistory()`, обновляет `items`/`error`.
- `fun onZoomIn()` / `fun onZoomOut()` — меняют `viewMode` через `zoomIn()`/`zoomOut()`, пушат текущее состояние в `backStack`; no-op если режим не изменился (уже в конечном состоянии).
- `fun onDaySelected(date: LocalDate)` — переход в DAY для конкретной даты (из Week view), пушит текущее состояние в стек.
- `fun onWeekSelected(weekStart: LocalDate)` — переход в WEEK для конкретной недели (из Month view).
- `fun resetToToday()` — сбрасывает на DAY/сегодня с очисткой `backStack`; вызывается при каждом входе на экран.
- `fun onBack(): Boolean` — pop из `backStack`, возвращает `false` если стек пуст (тогда система сама обработает Back — выход с экрана).
Особенности/нюансы: подписан на `workoutRepository.historyChangedFlow` — автообновление истории при `saveTraining` (в т.ч. из `SaveTrainingWorker` в офлайн-сценарии) или `deleteCompletedTraining`. Также подписан на `workoutTypesFlow()` для резолва названий активностей в UI.

#### `presentation/calendar/WeekTimelineView.kt`
Недельный вид истории — один нод = один день недели (7 нодов, Пн–Вс).
- `@Composable internal fun WeekTimelineView(state, onDaySelected)` — вычисляет `weekStart` через `state.selectedDate.with(DayOfWeek.MONDAY)`, для каждого из 7 дней строит строку.
- `@Composable private fun WeekDayRow(day, dayItems, isCardRight, isCurrent, onDaySelected)` — строка дня; при пустом `dayItems` карточка не рисуется (только нод + метка даты).
- `@Composable private fun WeekDayCard(dayItems)` — карточка агрегатов дня: `WeekActivityStrip` (до 3 иконок первых по времени тренировок) + инфо-блок из 4 строк (время / дистанция / калории / кол-во тренировок).
- `@Composable private fun WeekActivityStrip(iconIds: List<Int>)` — до 3 иконок активности, белый фон, без подсветки (в отличие от Month-стрипа).
Особенности/нюансы: `isCardRight = day.dayOfWeek.value % 2 == 0` — чередование лево/право зависит от номера дня недели, а не от индекса в списке (в отличие от `DayTimelineView`, где чередование по индексу тренировки). Тап по карточке дня → `onDaySelected(day)` переключает в DAY-режим.

### utils

#### `utils/ApiErrorHandler.kt`
`object` для преобразования исключений API (Retrofit/OkHttp) в понятные пользователю сообщения на русском языке и категоризации ошибок для UI-логики.
- `fun getErrorMessage(throwable: Throwable): String` — диспетчер: `HttpException` → `handleHttpException`; `IOException` → "Ошибка подключения. Проверьте интернет"; иначе `throwable.message` или "Неизвестная ошибка".
- `private fun handleHttpException(exception): String` — парсит JSON-тело ошибки (`{"detail": "..."}` или `{"message": "..."}`) через Gson, переводит через `translateError`; при отсутствии тела или ошибке парсинга — generic-сообщение по статус-коду (400/401/403/404/409/422/429/500/503).
- `fun translateError(error: String): String` — словарь `errorTranslations` (англ→рус) с точным совпадением и поиском по подстроке (case-insensitive); специальный случай "Please wait N seconds" — извлекает число регэкспом и подставляет в русский шаблон "Пожалуйста, подождите N секунд перед повторной попыткой".
- `fun getFieldErrors(throwable: Throwable): Map<String, String>` — парсит ошибки по полям, если `detail` — JSON-объект (а не строка).
- `fun categorizeError(errorMessage: String): ErrorCategory` — сопоставляет (англ. и рус.) текст ошибки с категорией по ключевым словам (email/username/nickname/too many/wait/password).
- `enum class ErrorCategory { USERNAME_TAKEN, EMAIL_TAKEN, PASSWORD_ERROR, TOO_MANY_ATTEMPTS, RESEND_COOLDOWN, GENERIC }`.
Особенности/нюансы: словарь `errorTranslations` покрывает типовые сценарии register/verify-email/resend-code (пересекается с пунктами 5, 6, 7 CLAUDE.md — `MAX_VERIFICATION_ATTEMPTS=5`, `RESEND_COOLDOWN_SECONDS=120`, "Please wait N seconds"). Категоризация работает как с английским сырым текстом от бэка, так и с уже переведённым русским (двойная проверка на обоих языках) — что позволяет вызывать `categorizeError` уже после `getErrorMessage`.

#### `utils/ApiErrorScenarios.kt`
Документационный файл (без исполняемого кода, кроме `package`) — текстовые примеры-сценарии типичных ошибок API в формате комментариев, для справки при написании/тестировании `ApiErrorHandler`.
Особенности/нюансы: содержит 7 сценариев (username taken, email taken, invalid email format, too many verification attempts, resend cooldown, server error, network error) с примерами request/response JSON и ожидаемым поведением `ApiErrorHandler`, плюс пример интеграции в `RegisterViewModel`/`RegisterScreen`. Файл не содержит функций — чисто справочный комментарий-блок, используется как живая документация ожидаемых форматов ошибок FastAPI-бэкенда.

#### `utils/DurationFormatter.kt`
Утилитная чистая Kotlin-функция форматирования длительности, без зависимостей от `android.*`.
- `fun formatHhMmSs(elapsedMs: Long): String` — миллисекунды → "HH:MM:SS" с ведущими нулями; отрицательные значения трактуются как 0 (`coerceAtLeast(0L)`).
Особенности/нюансы: используется и в `presentation` (таймер тренировки в `WorkoutStartViewModel`), и в `data` (foreground-уведомление `LocationTrackingService`) — общий чистый код без Android-зависимостей корректно живёт в `utils`, а не в `domain` (хотя мог бы, так как не содержит `android.*`).

### Тесты presentation/workout

#### `app/src/test/java/com/example/smarttracker/presentation/workout/summary/SplitsBuilderTest.kt`
Чистые JUnit-тесты `SplitsBuilder` (9): равномерный трек → равные сплиты с одинаковым темпом; интерполяция границы между точками; неполный хвост с фактическим темпом; хвост < 50 м отбрасывается; трек < 1 км → один partial-сплит; relativeSpeed нормируется на самый быстрый круг; синтетические таймстемпы истории гейтятся (`hasRealTiming`); вырожденные данные не падают; buildSplits доверяет elapsed (паузы уже вычтены).

#### `app/src/test/java/com/example/smarttracker/presentation/workout/summary/TrackChartDataTest.kt`
Чистые JUnit-тесты `TrackChartData` (9): сегментация без пауз/с gap-индексом/односегментные отбрасываются/рассинхрон длин; сглаживание (усреднение пилы, неизменность x и плоской линии); даунсемпл (короткий список как есть, сжатие с сохранением монотонности и диапазона); конвейер `prepare`.

#### `app/src/test/java/com/example/smarttracker/presentation/workout/summary/ShareImageComposerTest.kt`
Чистые JUnit-тесты `ShareImageComposer.normalizeTrack` (6): пустой/одноточечный/вырожденный трек → пустой результат; координаты в [0,1]; инверсия Y (север сверху); центрирование вытянутого трека; сохранение пропорций с cos-коррекцией долготы.

#### `app/src/test/java/com/example/smarttracker/presentation/workout/start/WorkoutStartViewModelTest.kt`
Юнит-тесты `WorkoutStartViewModel`. Robolectric (`@Config(sdk=[28], application=Application::class)`)
даёт настоящие Context/SharedPreferences: recovery-префы пишутся в тесте теми же ключами
`LocationConfig.KEY_*`, какими их пишет сервис (`persistRecoveryState`) — контракт
сервис↔ViewModel (нюанс 28) проверяется честно. `CalculateTrainingStatsUseCase` — реальный
экземпляр (чистый Kotlin), остальные зависимости — моки.
Приоритет — crash-recovery:
- живая сессия реаттачится БЕЗ discovery-интента (иначе перезапись trainingId живого сервиса);
- `pauseGapIndices` восстановлены в state ДО `observeTrackingData`;
- elapsed: на паузе = `pausedAccumulatedMs`, при записи = `now − sessionStartedAt`;
- протухший heartbeat (`RECOVERY_STALE_MS`) → сброс сессии, очистка префов, запуск discovery.
Жизненный цикл: re-key localUUID→serverUUID при успешном `startTraining` (+ перезапуск
наблюдателя на serverUUID); офлайн-старт (`NetworkUnavailableException`) — трекинг продолжается
локально; `onFinishClick` незарегистрированной → `savePendingFinish(typeActivId)` +
`OfflineFinishScheduler.enqueue`, БЕЗ `saveTraining`; зарегистрированной → `saveTraining`
после таймаута 5 сек ожидания `finishSyncFlow` (`advanceTimeBy`); освобождение `trackPoints`
при финише (нюанс 21); блокировка смены типа во время тренировки; форматтеры
`formatPace`/`formatDuration`.
Нюанс тестов: scope ViewModel гасится в `tearDown` (`vm.viewModelScope.cancel()`) — таймер
тренировки (бесконечный `while(isActive){delay(1000)}`) иначе не даёт test-scheduler'у
стать idle и `runTest` виснет в фазе очистки (не падает по таймауту, а висит).

### Тесты utils

#### `app/src/test/java/com/example/smarttracker/utils/ApiErrorHandlerTest.kt`
Юнит-тесты `ApiErrorHandler` — используют `retrofit2.HttpException` с искусственно созданным `Response.error`, без моков и внешних зависимостей (assert через ручной `throw AssertionError`, без JUnit-`assertEquals`).
Покрытые сценарии (по именам `@Test`):
- `testTranslation_EmailAlreadyExists` — перевод "User with this email already exists" → "Пользователь с такой почтой уже существует".
- `testTranslation_UsernameAlreadyExists` — перевод "User with this username already exists" → "Это имя пользователя уже используется".
- `testCategorizeEmail_Translated` — категоризация уже переведённого русского сообщения об email → `EMAIL_TAKEN`.
- `testCategorizeUsername_Translated` — категоризация переведённого русского сообщения об username → `USERNAME_TAKEN`.
- `testTranslation_TooManyAttempts` — перевод "Too many failed attempts..." содержит "Слишком много".
- `testTranslation_CooldownWithSeconds` — "Please wait 87 seconds before resending" → перевод содержит "87" и "секунд".
- `testGetErrorMessage_IOException` — `IOException` → сообщение содержит "Ошибка подключения".
- `testCategorize_RussianEmail` — категоризация русского текста об email → `EMAIL_TAKEN`.
Особенности/нюансы: приватный хелпер `createHttpException(code, responseBody)` строит `HttpException` из `Response.error<Any>(code, body)`, где `body` — `ResponseBody` из сырой JSON-строки через `toResponseBody()`. Тесты не проверяют статус-код напрямую — фокус на парсинге тела и переводе/категоризации.

### Итоги по presentation/workout, presentation/calendar, utils

Поток экрана тренировки построен как единая composable-сессия без навигации между состояниями: `WorkoutStartScreen` рендерит три визуальных режима (ожидание старта, активная тренировка, оверлей итогов) поверх одного и того же `MapViewComposable`, что специально сделано для избежания крашей MapLibre `LocationComponent` при пересоздании `MapView` через Compose-навигацию. Старт тренировки в `WorkoutStartViewModel` немедленно запускает foreground-сервис (уведомление появляется без задержки) и параллельно в фоне регистрирует тренировку на сервере, поддерживая полный офлайн-сценарий: при отсутствии сети тренировка живёт под локальным UUID и позже синхронизируется через `WorkoutManager`-цепочку (`SyncGpsPointsWorker`/`SaveTrainingWorker`). Финиш строит единый снимок `WorkoutSummaryUiState` с предвычисленными накопленными данными (`CumulativeTrackData`) для O(1)-доступа при scrubbing трека, а сама тренировка отправляется на сервер fire-and-forget с офлайн-fallback через `PendingFinishEntity`.

Устройство карты — это `MapView` MapLibre, обёрнутая в `AndroidView` с ручным управлением жизненным циклом через `DisposableEffect`; ключевая инженерная сложность файла `MapViewComposable.kt` — синхронизация `LocationComponent` (живая GPS-точка, независимая от Room) с Compose-жизненным циклом и предотвращение гонки анимаций бэринга/точности после разрушения стиля. Слои поверх raster-тайлов собственного сервера (`tile.gottland.ru`): линия трека, scrub-маркер (только в fullscreen), маркеры старта/финиша (композитные bitmap через `makeMarkerBitmap`, тонированные `ColorPrimary`), видимые только в режиме просмотра итогов. Discovery-GPS (поиск сигнала ещё до нажатия «Начать») даёт мгновенный отклик иконки-бейджа и однократный zoom 16 при первом фиксе.

Календарь истории тренировок (`presentation/calendar`) реализован как визуальное "дерево" с центральным стволом и чередующимися карточками слева/справа, единый для трёх масштабов: День (список тренировок конкретной даты), Неделя (7 нодов по дням, агрегаты за день), Месяц (4-5 нодов по неделям, доминирующий тип активности + агрегаты). Переключение между масштабами идёт через pinch/spread-жест (интуитивно как zoom карты/фото) либо тапом по карточке для перехода на уровень глубже, с полноценным `backStack` для кнопки «Назад». Общие визуальные примитивы и константы (`CalendarComponents.kt`, `CalendarConstants.kt`) переиспользуются во всех трёх view, что упрощает синхронное изменение стиля.

Обработка ошибок API централизована в `ApiErrorHandler`: парсинг JSON-тела ошибки FastAPI (`{"detail": "..."}`), словарный перевод типовых серверных сообщений на русский (включая извлечение чисел из "Please wait N seconds" регэкспом) и категоризация ошибки (`ErrorCategory`) для точечного отображения в UI (под конкретным полем формы, таймер кулдауна, блокировка поля после `TOO_MANY_ATTEMPTS`). Юнит-тесты в `ApiErrorHandlerTest` подтверждают перевод и категоризацию как для английского сырого текста от сервера, так и для уже переведённого русского — что защищает от регрессий при добавлении новых сценариев ошибок регистрации/верификации.

