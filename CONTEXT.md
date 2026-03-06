# Контекст проекта SmartTracker — Android

## Что это за проект
Android-приложение для создания, трекинга и анализа тренировок.
Организация на GitHub: `smart-tracker`
Репозиторий: `smart-tracker/mobile-android`, ветка `main`

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

### 🔜 Следующий шаг — МОБ-2.2 (начинать отсюда)
`data/remote/AuthApiService.kt` — Retrofit интерфейс с 5 методами

### ❌ Не начато

---

#### МОБ-2.1 — DTO модели
`→ data/remote/dto/`

Создать 5 файлов:

**`RegisterRequestDto.kt`**
```kotlin
data class RegisterRequestDto(
    @SerializedName("first_name")  val firstName: String,
    @SerializedName("nickname")    val username: String,   // ← username→nickname!
    @SerializedName("birth_date")  val birthDate: String,  // формат "yyyy-MM-dd"
    val gender: String,                                     // "male" / "female"
    val email: String,
    val password: String
    // confirmPassword — НЕ включать, только клиентская проверка
)
```

**`RegisterResultDto.kt`**
```kotlin
data class RegisterResultDto(
    val message: String,
    val email: String,
    @SerializedName("expires_in") val expiresIn: Int
    // debug_code — НЕ включать намеренно (временное поле бэкенда)
)
```

**`AuthResponseDto.kt`**
```kotlin
data class AuthResponseDto(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type")    val tokenType: String = "bearer"
)
```

**`EmailVerificationDto.kt`**
```kotlin
data class EmailVerificationDto(
    val email: String,
    val code: String
)
```

**`ResendCodeResponseDto.kt`**
```kotlin
data class ResendCodeResponseDto(
    val message: String,
    @SerializedName("expires_at")       val expiresAt: String?,
    @SerializedName("remaining_seconds") val remainingSeconds: Int
)
```

Также нужны mappers (можно как extension-функции в тех же файлах):
- `RegisterRequestDto.kt` — `RegisterRequest.toDto(): RegisterRequestDto`
- `RegisterResultDto.kt` — `RegisterResultDto.toDomain(): RegisterResult`
- `AuthResponseDto.kt` — `AuthResponseDto.toDomain(): AuthResult`
- `ResendCodeResponseDto.kt` — `ResendCodeResponseDto.toDomain(): ResendResult`

---

#### МОБ-2.2 — AuthApiService (Retrofit)
`→ data/remote/AuthApiService.kt`

Интерфейс Retrofit с 5 методами:
```kotlin
interface AuthApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): RegisterResultDto

    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body request: EmailVerificationDto): AuthResponseDto

    @POST("auth/resend-code")
    suspend fun resendCode(@Body request: ResendEmailDto): ResendCodeResponseDto
    // ResendEmailDto — просто data class с полем email: String

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto
    // LoginRequestDto — email: String, password: String

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenDto): AuthResponseDto
    // RefreshTokenDto — refresh_token: String
}
```
Эти дополнительные мелкие DTO (`ResendEmailDto`, `LoginRequestDto`, `RefreshTokenDto`) создать там же в папке `dto/` или в отдельном файле `RequestDtos.kt`.

---

#### МОБ-2.4 — TokenStorage
`→ data/local/TokenStorage.kt`

Интерфейс + реализация. Хранит access и refresh токены в `EncryptedSharedPreferences`.
```kotlin
interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clearTokens()
    fun hasTokens(): Boolean
}
```
Реализация `TokenStorageImpl` использует:
```kotlin
EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```
Ключи: `"access_token"`, `"refresh_token"`.

---

#### МОБ-2.3 — AuthRepositoryImpl
`→ data/repository/AuthRepositoryImpl.kt`

Реализует `AuthRepository`. Зависит от `AuthApiService` и `TokenStorage`.
Каждый метод оборачивает вызов в `runCatching { }` для возврата `Result<T>`:
```kotlin
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    override suspend fun register(request: RegisterRequest): Result<RegisterResult> =
        runCatching { api.register(request.toDto()).toDomain() }

    override suspend fun verifyEmail(email: String, code: String): Result<AuthResult> =
        runCatching {
            val result = api.verifyEmail(EmailVerificationDto(email, code)).toDomain()
            tokenStorage.saveTokens(result.accessToken, result.refreshToken)
            result
        }
    // ... остальные методы аналогично
}
```

---

#### МОБ-5.1 — Hilt AuthModule
`→ di/AuthModule.kt`

Предоставляет зависимости для всего auth-потока:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient { ... logging interceptor ... }

    @Provides @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        // BASE_URL = "https://api.smarttracker.example.com/" (уточнить у команды бэка)
    }

    @Provides @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService

    @Provides @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage =
        TokenStorageImpl(context)

    @Binds
    fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Provides
    fun provideRegisterUseCase(repo: AuthRepository): RegisterUseCase =
        RegisterUseCase(repo)
}
```
BASE_URL вынести в `BuildConfig` через `buildConfigField` в `app/build.gradle.kts`.

---

#### МОБ-3.1 — RegisterScreen (верстка)
`→ presentation/register/RegisterScreen.kt`

Только UI, без логики (stateless). Все данные и колбэки — параметры функции.
Экран состоит из 4 шагов (по UX макету Figma):
- Шаг 1: Имя, @username, Дата рождения, Пол
- Шаг 2: Цель использования (UserPurpose)
- Шаг 3: Email, Пароль, Подтверждение пароля
- Шаг 4: Чекбокс условий + кнопка "Создать аккаунт"

Либо один длинный скролл — уточнить по макету Figma.
Ссылку на макет получить от дизайнера перед началом.

---

#### МОБ-4.1 — RegisterUiState
`→ presentation/register/RegisterUiState.kt`

```kotlin
data class RegisterUiState(
    // Поля формы
    val firstName: String = "",
    val username: String = "",
    val birthDate: LocalDate? = null,
    val gender: Gender? = null,
    val purpose: UserPurpose? = null,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isTermsAccepted: Boolean = false,

    // Ошибки валидации под каждым полем
    val firstNameError: String? = null,
    val usernameError: String? = null,
    val birthDateError: String? = null,
    val genderError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,

    // Состояние экрана
    val isLoading: Boolean = false,
    val serverError: String? = null,   // ошибка от сервера (400, 409 и т.д.)
    val isSuccess: Boolean = false     // регистрация прошла, перейти на verify-email
)
```

---

#### МОБ-4.2 — RegisterViewModel
`→ presentation/register/RegisterViewModel.kt`

```kotlin
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFirstNameChange(value: String) { ... валидация в реальном времени ... }
    fun onUsernameChange(value: String) { ... }
    fun onEmailChange(value: String) { ... }
    fun onPasswordChange(value: String) { ... }
    fun onConfirmPasswordChange(value: String) { ... }
    fun onBirthDateChange(value: LocalDate) { ... }
    fun onGenderSelect(value: Gender) { ... }
    fun onPurposeSelect(value: UserPurpose) { ... }
    fun onTermsToggle() { ... }

    fun onRegisterClick() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, serverError = null) }
            val result = registerUseCase(buildRequest())
            result.fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, isSuccess = true) } },
                onFailure = { _uiState.update { it.copy(isLoading = false, serverError = it.message) } }
            )
        }
    }
}
```

---

#### МОБ-4.3 — Подключение логики к RegisterScreen
`→ presentation/register/RegisterScreen.kt (обновление)`

- Поля ввода читают значения из `uiState`, вызывают `onXxxChange`
- Ошибки под полями: `if (uiState.firstNameError != null) Text(uiState.firstNameError)`
- Кнопка неактивна пока форма невалидна: `enabled = !uiState.isLoading && uiState.isTermsAccepted`
- `CircularProgressIndicator` при `isLoading = true`
- `LaunchedEffect(uiState.isSuccess)` — при успехе навигация на экран verify-email
- `Snackbar` или `Toast` при `serverError != null`

---

#### МОБ-5.2 — NavGraph
`→ presentation/navigation/NavGraph.kt`

Маршруты:
```kotlin
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val VERIFY_EMAIL = "verify_email/{email}"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
}
```
Логика стартового экрана:
```kotlin
val startDestination = if (tokenStorage.hasTokens()) Routes.HOME else Routes.LOGIN
```
Переходы:
- `register` → `verify_email` (передаём email как аргумент)
- `verify_email` → `onboarding` (после успешной верификации)
- `login` → `home`
- Ссылка "Уже есть аккаунт?" → `login`

---

## Важные нюансы (не забыть)

1. **`username` vs `nickname`** — в Android domain это `username`, в API БД поле `nickname`. В `RegisterRequestDto` используется `@SerializedName("nickname")`. ✅ Учтено.

2. **`UserPurpose`** — в Android есть, в API нет. Ждём решения от Артёма: сохранять в БД или только клиентское. До ответа не трогать. В `RegisterRequestDto` поле НЕ включено.

3. **`debug_code`** — в ответе `POST /auth/register` сервер возвращает код верификации открытым текстом. Временное поле, убрать до прода. В `RegisterResultDto` не включено. ✅ Учтено.

4. **`resendCode` ответ** — API возвращает объект `{message, expires_at, remaining_seconds}`, не просто число. Уже исправлено: `Result<ResendResult>`. ✅ Учтено.

5. **`confirm_password` обязателен в API** — FastAPI-схема `UserCreate` содержит `confirm_password` как обязательное поле с валидатором. В `RegisterRequestDto` включён. ✅ Расхождение с CONTEXT.md исправлено.

6. **`remaining_seconds` — nullable** — `EmailVerificationResponse.remaining_seconds: Optional[int]` на бэкенде. В `ResendCodeResponseDto` тип `Int?`. ✅ Расхождение исправлено.

7. **`/auth/refresh` — query param, не body** — FastAPI без явного `Body(...)` трактует `refresh_token: str` как query parameter. В `AuthApiService` (МОБ-2.2) использовать `@POST("auth/refresh") suspend fun refreshToken(@Query("refresh_token") token: String): AuthResponseDto`.

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

## Соглашения по коммитам

Формат: `[МОБ-X.X] тип: описание на русском языке`

Примеры:
```
[МОБ-2.1] feat: добавлены DTO модели и mappers для auth-слоя
[МОБ-2.2] feat: добавлен AuthApiService (Retrofit интерфейс)
[МОБ-2.4] feat: добавлено хранилище токенов TokenStorage
[МОБ-2.3] feat: реализован AuthRepositoryImpl
[МОБ-5.1] feat: добавлен Hilt AuthModule
chore: обновлён CONTEXT.md — зафиксированы расхождения с API
fix: исправлена nullable-типизация в ResendCodeResponseDto
```

Типы: `feat` — новая функция, `fix` — исправление, `refactor` — рефакторинг, `chore` — служебное, `docs` — документация.

> ⚠️ **PowerShell**: квадратные скобки в сообщении коммита нужно оборачивать в одинарные кавычки, иначе `[МОБ-X.X]` → `[-X.X]`:
> ```powershell
> git commit -m '[МОБ-2.2] feat: описание'
> ```

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
│   ├── local/                  (TokenStorage — не создан)
│   ├── remote/
│   │   ├── dto/                (✅ DTO созданы — 5 файлов + mappers)
│   │   └── AuthApiService.kt   (не создан — следующий шаг)
│   └── repository/             (AuthRepositoryImpl — не создан)
├── di/                         (AuthModule — не создан)
├── domain/
│   ├── model/                  (все модели созданы)
│   ├── repository/             (AuthRepository — создан)
│   └── usecase/                (RegisterUseCase — создан)
├── presentation/
│   ├── MainActivity.kt
│   ├── common/
│   ├── navigation/             (NavGraph — не создан)
│   └── theme/
│       └── SmartTrackerTheme.kt
└── utils/
```
