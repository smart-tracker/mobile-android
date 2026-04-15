package com.example.smarttracker.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // ── Session-expired сигнал ───────────────────────────────────────────────────
    private val _sessionExpiredFlow = MutableStateFlow(false)

    /**
     * UI наблюдает этот flow: при `true` автоматически переходит на Login
     * и очищает back stack. После успешного сохранения новых токенов в
     * [saveTokens] значение сбрасывается в `false`, что означает активную сессию.
     */
    override val sessionExpiredFlow: StateFlow<Boolean> = _sessionExpiredFlow.asStateFlow()

    override fun saveTokens(accessToken: String, refreshToken: String, roleIds: List<Int>) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ROLE_IDS, roleIds.joinToString(","))
            .apply()
        // Новые токены = активная сессия. Сбрасываем флаг на случай если ранее
        // signalSessionExpired() поднял его в true (до повторного логина пользователя).
        // MutableStateFlow не эмитирует при записи того же значения (false == false),
        // поэтому в штатных сценариях (без предшествующего expiry) это no-op.
        _sessionExpiredFlow.value = false
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

    /**
     * Очищает токены и поднимает [sessionExpiredFlow] в true.
     * Вызывается из [com.example.smarttracker.data.remote.TokenRefreshAuthenticator]
     * при получении 401 на refresh-запросе.
     */
    override fun signalSessionExpired() {
        clearAll()
        _sessionExpiredFlow.value = true
    }

    override fun hasTokens(): Boolean =
        getAccessToken() != null && getRefreshToken() != null

    companion object {
        private const val KEY_ACCESS_TOKEN  = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ROLE_IDS      = "role_ids"
    }
}
