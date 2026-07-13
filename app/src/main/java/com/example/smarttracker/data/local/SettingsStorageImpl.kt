package com.example.smarttracker.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [SettingsStorage] на DataStore Preferences.
 *
 * Property-delegate на Context гарантирует единственный экземпляр DataStore на
 * процесс (повторное создание для того же файла кидает IllegalStateException).
 *
 * Ошибки чтения (IOException — повреждённый файл) не роняют поток: эмитятся
 * дефолтные настройки. Прочие исключения пробрасываются — это ошибки
 * программирования, их надо видеть в crash-репортах.
 */
private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStorage {

    private object Keys {
        val AUTOPAUSE_ENABLED = booleanPreferencesKey("autopause_enabled")
        val VOICE_CUES_ENABLED = booleanPreferencesKey("voice_cues_enabled")
        val VOICE_CUE_INTERVAL_KM = intPreferencesKey("voice_cue_interval_km")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HRM_DEVICE_ADDRESS = stringPreferencesKey("hrm_device_address")
        val HRM_DEVICE_NAME = stringPreferencesKey("hrm_device_name")
    }

    // Дефолты — единственный источник в конструкторе AppSettings: prefs без
    // ключа падают на значения по умолчанию data-класса.
    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.w(TAG, "Settings read failed, falling back to defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            AppSettings(
                autopauseEnabled = prefs[Keys.AUTOPAUSE_ENABLED] ?: defaults.autopauseEnabled,
                voiceCuesEnabled = prefs[Keys.VOICE_CUES_ENABLED] ?: defaults.voiceCuesEnabled,
                voiceCueIntervalKm = (prefs[Keys.VOICE_CUE_INTERVAL_KM]
                    ?: defaults.voiceCueIntervalKm).let {
                    if (it in AppSettings.ALLOWED_VOICE_INTERVALS) it else defaults.voiceCueIntervalKm
                },
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
                hrmDeviceAddress = prefs[Keys.HRM_DEVICE_ADDRESS],
                hrmDeviceName = prefs[Keys.HRM_DEVICE_NAME],
            )
        }

    override suspend fun setAutopauseEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTOPAUSE_ENABLED] = enabled }
    }

    override suspend fun setVoiceCuesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VOICE_CUES_ENABLED] = enabled }
    }

    override suspend fun setVoiceCueIntervalKm(intervalKm: Int) {
        val valid = if (intervalKm in AppSettings.ALLOWED_VOICE_INTERVALS) {
            intervalKm
        } else {
            defaults.voiceCueIntervalKm
        }
        context.settingsDataStore.edit { it[Keys.VOICE_CUE_INTERVAL_KM] = valid }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun setHrmDevice(address: String?, name: String?) {
        context.settingsDataStore.edit { prefs ->
            if (address == null) {
                prefs.remove(Keys.HRM_DEVICE_ADDRESS)
                prefs.remove(Keys.HRM_DEVICE_NAME)
            } else {
                prefs[Keys.HRM_DEVICE_ADDRESS] = address
                if (name != null) {
                    prefs[Keys.HRM_DEVICE_NAME] = name
                } else {
                    prefs.remove(Keys.HRM_DEVICE_NAME)
                }
            }
        }
    }

    private companion object {
        const val TAG = "SettingsStorage"
    }
}
