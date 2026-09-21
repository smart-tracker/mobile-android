package com.example.smarttracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.example.smarttracker.BuildConfig
import com.example.smarttracker.data.hrm.HrmManager
import com.example.smarttracker.data.hrm.HrmManagerImpl
import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.data.local.RoleConfigStorageImpl
import com.example.smarttracker.data.local.SettingsStorage
import com.example.smarttracker.data.local.SettingsStorageImpl
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.local.TokenStorageImpl
import com.example.smarttracker.data.local.UserProfileCache
import com.example.smarttracker.data.local.UserProfileCacheImpl
import com.example.smarttracker.data.local.db.ActivityTypeDao
import com.example.smarttracker.data.local.db.ActivityTypeEntity
import com.example.smarttracker.data.local.db.GpsPointDao
import com.example.smarttracker.data.local.db.METActivityDao
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.data.local.db.SmartTrackerDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.buildAuthInterceptor
import com.example.smarttracker.data.remote.TokenRefreshAuthenticator
import com.example.smarttracker.data.remote.TrainingApiService
import com.example.smarttracker.data.repository.AllowedEmailDomainsRepositoryImpl
import com.example.smarttracker.data.repository.allowedEmailDomainsDataStore
import com.example.smarttracker.data.repository.AuthRepositoryImpl
import com.example.smarttracker.data.repository.WorkoutRepositoryImpl
import com.example.smarttracker.data.repository.PasswordRecoveryRepositoryImpl
import com.example.smarttracker.data.repository.location.LocationRepositoryImpl
import com.example.smarttracker.domain.repository.AllowedEmailDomainsRepository
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.repository.LocationRepository
import com.example.smarttracker.domain.repository.PasswordRecoveryRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

/** Hilt-модуль: привязки репозиториев, хранилищ, Retrofit, OkHttpClient и Room. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenStorage(impl: TokenStorageImpl): TokenStorage

    @Binds
    @Singleton
    abstract fun bindRoleConfigStorage(impl: RoleConfigStorageImpl): RoleConfigStorage

    @Binds
    @Singleton
    // Настройки приложения (автопауза, голосовые подсказки, keepScreenOn) —
    // DataStore Preferences, см. SettingsStorageImpl.
    abstract fun bindSettingsStorage(impl: SettingsStorageImpl): SettingsStorage

    @Binds
    @Singleton
    abstract fun bindUserProfileCache(impl: UserProfileCacheImpl): UserProfileCache

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    // Реальная реализация через GET /training/types_activity.
    // Иконки скачиваются в фоне и кэшируются в filesDir при наличии image_url от бэкенда.
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    // Backend recovery endpoints доступны в production API:
    // POST /password-reset/request
    // POST /password-reset/verify-code
    // POST /password-reset/resend-verify-code
    // POST /password-reset/confirm
    abstract fun bindPasswordRecoveryRepository(impl: PasswordRecoveryRepositoryImpl): PasswordRecoveryRepository

    @Binds
    @Singleton
    // Реализация через GpsPointDao (Room). Используется в Этапах 2–5.
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    // 149-ФЗ — список российских почтовых доменов для регистрации.
    // Сетевая реализация (GET /auth/allowed-email-domains, BR-4) с кэшем
    // в памяти + DataStore; зашитый список — fallback без сети.
    abstract fun bindAllowedEmailDomainsRepository(
        impl: AllowedEmailDomainsRepositoryImpl
    ): AllowedEmailDomainsRepository

    @Binds
    @Singleton
    // Внешний BLE-пульсометр: singleton владеет GATT-соединением,
    // его делят экран «Датчики» и LocationTrackingService.
    abstract fun bindHrmManager(impl: HrmManagerImpl): HrmManager

    companion object {

        /**
         * Провайдер BASE_URL для инъекции в TokenRefreshAuthenticator.
         *
         * Authenticator нужен URL для прямого вызова /auth/refresh через отдельный
         * OkHttpClient (чтобы разорвать циклическую зависимость Hilt).
         * @Named("baseUrl") позволяет отличить String-зависимость от других строк.
         */
        @Provides
        @Named("baseUrl")
        fun provideBaseUrl(): String = BuildConfig.BASE_URL

        /**
         * DataStore кэша серверного списка почтовых доменов (BR-4, 149-ФЗ).
         *
         * Выделен в провайдер (а не Context внутри репозитория), чтобы юнит-тесты
         * подставляли DataStore на временном файле через PreferenceDataStoreFactory
         * без Robolectric. @Named — на случай будущих DataStore<Preferences>
         * других подсистем.
         */
        @Provides
        @Singleton
        @Named("allowedEmailDomains")
        fun provideAllowedEmailDomainsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.allowedEmailDomainsDataStore

        @Provides
        @Singleton
        fun provideOkHttpClient(
            tokenStorage: TokenStorage,
            tokenAuthenticator: TokenRefreshAuthenticator,
        ): OkHttpClient {
            // Интерцептор авторизации: добавляет Bearer-токен ТОЛЬКО к запросам
            // на хост API (см. buildAuthInterceptor — этот же клиент используется
            // Coil/IconCacheManager для внешних URL картинок, токен не должен
            // уходить на чужие хосты). Токен читается из TokenStorage в момент
            // каждого запроса.
            val apiHost = BuildConfig.BASE_URL.toHttpUrl().host
            val authInterceptor = buildAuthInterceptor(tokenStorage, apiHost)
            // Идентифицирующий User-Agent вместо дефолтного "okhttp/4.x".
            // Требование OSMF Tile Usage Policy: MapLibre качает OSM-тайлы через
            // этот же клиент (HttpRequestUtil.setOkHttpClient в SmartTrackerApp),
            // анонимные UA там режутся. Для своего API и Coil — просто полезная
            // диагностика (видно версию приложения в логах сервера).
            val userAgent =
                "SmartTracker/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID}; Android)"
            val userAgentInterceptor = Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("User-Agent", userAgent).build()
                )
            }
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
                // Токен не должен попадать даже в debug-logcat: лог легко
                // уходит наружу вместе с bug-репортом. ⚠️ Тела auth-запросов
                // (пароли, коды верификации) при Level.BODY всё равно логируются —
                // logcat debug-сборки не передавать третьим лицам.
                redactHeader("Authorization")
            }
            return OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor)
                .addInterceptor(authInterceptor)
                // Authenticator срабатывает при HTTP 401: обновляет токен и повторяет запрос.
                // Если refresh тоже вернул 401 — очищает хранилище (принудительный выход).
                .authenticator(tokenAuthenticator)
                .addInterceptor(logging)
                .build()
        }

        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
            Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        @Provides
        @Singleton
        fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
            retrofit.create(AuthApiService::class.java)

        @Provides
        @Singleton
        fun provideTrainingApiService(retrofit: Retrofit): TrainingApiService =
            retrofit.create(TrainingApiService::class.java)

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SmartTrackerDatabase =
            Room.databaseBuilder(
                context,
                SmartTrackerDatabase::class.java,
                "smart_tracker.db"
            )
            .addMigrations(
                SmartTrackerDatabase.MIGRATION_5_6,
                SmartTrackerDatabase.MIGRATION_6_7,
                SmartTrackerDatabase.MIGRATION_7_8,
                SmartTrackerDatabase.MIGRATION_8_9,
            )
            // Деструктивная миграция допустима пока данные тренировок не критичны
            // (production-миграция — в Этапе 5).
            .fallbackToDestructiveMigration()
            // Дефолтные типы вставляются один раз при первом создании БД.
            // При последующих запусках таблица заполняется из сети через upsertAll.
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    listOf(
                        ActivityTypeEntity(id = 1, name = "Бег",       imagePath = null),
                        ActivityTypeEntity(id = 3, name = "Велосипед", imagePath = null),
                        ActivityTypeEntity(id = 5, name = "Ходьба",    imagePath = null),
                    ).forEach { e ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO activity_types(id, name, imagePath) VALUES(?,?,?)",
                            arrayOf(e.id, e.name, e.imagePath)
                        )
                    }
                }
            })
            .build()

        @Provides
        @Singleton
        fun provideGpsPointDao(db: SmartTrackerDatabase): GpsPointDao = db.gpsPointDao()

        @Provides
        @Singleton
        fun provideActivityTypeDao(db: SmartTrackerDatabase): ActivityTypeDao = db.activityTypeDao()

        @Provides
        @Singleton
        fun providePendingFinishDao(db: SmartTrackerDatabase): PendingFinishDao = db.pendingFinishDao()

        @Provides
        @Singleton
        fun provideMETActivityDao(db: SmartTrackerDatabase): METActivityDao = db.metActivityDao()
    }
}
