package com.example.smarttracker.di

import com.example.smarttracker.BuildConfig
import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.data.local.RoleConfigStorageImpl
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.local.TokenStorageImpl
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.repository.AuthRepositoryImpl
import com.example.smarttracker.data.repository.MockPasswordRecoveryRepository
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.repository.PasswordRecoveryRepository
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
    abstract fun bindPasswordRecoveryRepository(impl: MockPasswordRecoveryRepository): PasswordRecoveryRepository

    companion object {

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            }
            return OkHttpClient.Builder()
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
