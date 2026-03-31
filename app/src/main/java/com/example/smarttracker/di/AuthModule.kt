package com.example.smarttracker.di

import com.example.smarttracker.BuildConfig
import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.data.local.RoleConfigStorageImpl
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.local.TokenStorageImpl
import okhttp3.Interceptor
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.repository.AuthRepositoryImpl
import com.example.smarttracker.data.repository.WorkoutRepositoryImpl
import com.example.smarttracker.data.repository.PasswordRecoveryRepositoryImpl
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.repository.PasswordRecoveryRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

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

    companion object {

        @Provides
        @Singleton
        fun provideOkHttpClient(tokenStorage: TokenStorage): OkHttpClient {
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
    }
}
