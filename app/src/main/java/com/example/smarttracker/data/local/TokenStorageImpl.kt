package com.example.smarttracker.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Реализация TokenStorage на базе EncryptedSharedPreferences.
 * МОБ-6.1 — Сохранение и получение JWT-токенов + ролей пользователя.
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

    override fun saveTokens(accessToken: String, refreshToken: String, roleIds: List<Int>) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ROLE_IDS, roleIds.joinToString(","))
            .apply()
    }

    override fun getAccessToken(): String? =
        prefs.getString(KEY_ACCESS_TOKEN, null)

    override fun getRefreshToken(): String? =
        prefs.getString(KEY_REFRESH_TOKEN, null)

    override fun getUserRoles(): List<Int> {
        val rolesString = prefs.getString(KEY_ROLE_IDS, null)
                ?: return emptyList()

        return if (rolesString.isEmpty()) {
            emptyList()
        } else {
            rolesString
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
        }
    }

    override fun clearAll() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ROLE_IDS)
            .apply()
    }

    override fun hasTokens(): Boolean =
        getAccessToken() != null && getRefreshToken() != null

    companion object {
        private const val KEY_ACCESS_TOKEN  = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ROLE_IDS      = "role_ids"
    }
}
