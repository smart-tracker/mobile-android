package com.example.smarttracker.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.inject.Inject

/**
 * Реализация TokenStorage на базе EncryptedSharedPreferences.
 * МОБ-6.1 — Сохранение и получение JWT-токенов + ролей пользователя.
 *
 * **Отказоустойчивость (контракт [TokenStorage]):** ни один метод не бросает.
 * EncryptedSharedPreferences известна RuntimeException'ами:
 * - AEADBadTagException — повреждённые префы (прерванная запись; восстановление
 *   из бэкапа, когда данные скопировались, а Keystore-ключ не переносится);
 * - KeyStoreException / UnrecoverableKeyException — глюки Android Keystore
 *   после OTA-обновления или смены экрана блокировки (Samsung/Xiaomi).
 * Хранилище вызывается из OkHttp-интерцептора на каждом запросе — необработанное
 * исключение там роняет процесс. Поэтому:
 * 1. Создание prefs — с самовосстановлением: при сбое повреждённый файл и
 *    master-key удаляются, хранилище пересоздаётся (цена — один перелогин,
 *    вместо вечного краша, лечащегося только очисткой данных приложения).
 * 2. Все операции обёрнуты: чтение при сбое возвращает null/emptyList
 *    (деградация до «не авторизован» → штатный 401 → штатный logout-флоу),
 *    запись/очистка — логируются.
 */
class TokenStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TokenStorage {

    /**
     * null = хранилище не удалось создать даже после пересоздания
     * (Keystore полностью недоступен). Все операции деградируют до no-op/null.
     */
    private val prefs: SharedPreferences? by lazy { createPrefsWithRecovery() }

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun createPrefsWithRecovery(): SharedPreferences? =
        try {
            createPrefs()
        } catch (e: Exception) {
            Log.e(TAG, "Хранилище токенов повреждено (${e.javaClass.simpleName}) — пересоздаём", e)
            try {
                // Удаляем и файл префов, и master-key: повреждена может быть любая
                // из сторон (файл после прерванной записи, ключ после OTA/бэкапа).
                context.deleteSharedPreferences(PREFS_FILE)
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                createPrefs()
            } catch (e2: Exception) {
                Log.e(TAG, "Не удалось пересоздать хранилище токенов — работаем без него", e2)
                null
            }
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
        try {
            prefs?.edit()
                ?.putString(KEY_ACCESS_TOKEN, accessToken)
                ?.putString(KEY_REFRESH_TOKEN, refreshToken)
                ?.putString(KEY_ROLE_IDS, roleIds.joinToString(","))
                ?.apply()
        } catch (e: Exception) {
            // Сбой записи после refresh = потеря сессии при следующем истечении
            // access-токена (сервер уже ротировал refresh). Крашить нельзя —
            // вызывается с OkHttp-потока Authenticator'а.
            Log.e(TAG, "Не удалось сохранить токены (${e.javaClass.simpleName})", e)
        }
        // Новые токены = активная сессия. Сбрасываем флаг на случай если ранее
        // signalSessionExpired() поднял его в true (до повторного логина пользователя).
        // MutableStateFlow не эмитирует при записи того же значения (false == false),
        // поэтому в штатных сценариях (без предшествующего expiry) это no-op.
        _sessionExpiredFlow.value = false
    }

    override fun getAccessToken(): String? =
        try {
            prefs?.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать access token (${e.javaClass.simpleName})", e)
            null
        }

    override fun getRefreshToken(): String? =
        try {
            prefs?.getString(KEY_REFRESH_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать refresh token (${e.javaClass.simpleName})", e)
            null
        }

    override fun getUserRoles(): List<Int> {
        val rolesString = try {
            prefs?.getString(KEY_ROLE_IDS, null)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать роли (${e.javaClass.simpleName})", e)
            null
        } ?: return emptyList()

        return if (rolesString.isEmpty()) {
            emptyList()
        } else {
            rolesString
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
        }
    }

    override fun clearAll() {
        try {
            prefs?.edit()
                ?.remove(KEY_ACCESS_TOKEN)
                ?.remove(KEY_REFRESH_TOKEN)
                ?.remove(KEY_ROLE_IDS)
                ?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось очистить токены (${e.javaClass.simpleName})", e)
        }
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
        private const val TAG = "TokenStorage"

        private const val PREFS_FILE        = "secure_prefs"
        private const val ANDROID_KEYSTORE  = "AndroidKeyStore"

        private const val KEY_ACCESS_TOKEN  = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ROLE_IDS      = "role_ids"
    }
}
