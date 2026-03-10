package com.example.smarttracker.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Реализация TokenStorage на базе EncryptedSharedPreferences.
 *
 * Почему EncryptedSharedPreferences:
 * - Ключи шифруются алгоритмом AES256-SIV (детерминированное шифрование,
 *   позволяет искать по ключу), значения — AES256-GCM (аутентифицированное
 *   шифрование). Мастер-ключ хранится в Android Keystore.
 * - Это рекомендуемый AndroidX способ хранения чувствительных строк.
 *
 * Почему prefs — lazy:
 * - EncryptedSharedPreferences.create() выполняет I/O (инициализация Keystore,
 *   чтение файла). Вызов в конструкторе заблокировал бы поток, в котором
 *   создаётся Hilt-граф. lazy откладывает инициализацию до первого обращения
 *   и гарантирует однократное выполнение (SYNCHRONIZED по умолчанию).
 *
 * @param context ApplicationContext — не хранит ссылку на Activity/Fragment,
 *                утечка памяти исключена.
 */
class TokenStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TokenStorage {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun getAccessToken(): String? =
        prefs.getString(KEY_ACCESS_TOKEN, null)

    override fun getRefreshToken(): String? =
        prefs.getString(KEY_REFRESH_TOKEN, null)

    override fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    override fun hasTokens(): Boolean =
        getAccessToken() != null && getRefreshToken() != null

    companion object {
        private const val KEY_ACCESS_TOKEN  = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
