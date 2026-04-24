package com.example.smarttracker.di

import android.content.Context
import androidx.room.Room
import com.example.smarttracker.BuildConfig
import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.data.local.RoleConfigStorageImpl
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.local.TokenStorageImpl
import com.example.smarttracker.data.local.db.ActivityTypeDao
import com.example.smarttracker.data.local.db.ActivityTypeEntity
import com.example.smarttracker.data.local.db.GpsPointDao
import com.example.smarttracker.data.local.db.PendingFinishDao
import com.example.smarttracker.data.local.db.SmartTrackerDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import okhttp3.Interceptor
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.TokenRefreshAuthenticator
import com.example.smarttracker.data.remote.TrainingApiService
import com.example.smarttracker.data.repository.AuthRepositoryImpl
import com.example.smarttracker.data.repository.WorkoutRepositoryImpl
import com.example.smarttracker.data.repository.PasswordRecoveryRepositoryImpl
import com.example.smarttracker.data.repository.location.LocationRepositoryImpl
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

        @Provides
        @Singleton
        fun provideOkHttpClient(
            tokenStorage: TokenStorage,
            tokenAuthenticator: TokenRefreshAuthenticator,
        ): OkHttpClient {
            // Интерцептор авторизации: добавляет Bearer-токен ко всем запросам.
            // Читает токен из TokenStorage — он уже должен быть сохранён к моменту вызова.
            // Порядок важен: authInterceptor добавляется ДО logging, чтобы Authorization-заголовок
            // был виден в логах.
            val authInterceptor = Interceptor { chain ->
                val token = tokenStorage.getAccessToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            }
            return OkHttpClient.Builder()
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
    }
}
