package com.example.smarttracker.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

        /** JSON-список сохранённых пульсометров ([encodeHrmDevices]). */
        val HRM_DEVICES = stringPreferencesKey("hrm_devices")

        /** Адрес активного пульсометра (последний выбранный пользователем). */
        val HRM_ACTIVE_ADDRESS = stringPreferencesKey("hrm_active_address")

        // Legacy-ключи одиночного датчика (до перехода на список).
        // Читаются только как мягкая миграция; любая запись списка их удаляет.
        val LEGACY_HRM_DEVICE_ADDRESS = stringPreferencesKey("hrm_device_address")
        val LEGACY_HRM_DEVICE_NAME = stringPreferencesKey("hrm_device_name")
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
                hrmDevices = readHrmDevices(prefs),
                // Legacy-датчик был единственным и выбранным — он же активный
                hrmActiveAddress = prefs[Keys.HRM_ACTIVE_ADDRESS]
                    ?: prefs[Keys.LEGACY_HRM_DEVICE_ADDRESS],
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

    override suspend fun addHrmDevice(address: String, name: String?) {
        context.settingsDataStore.edit { prefs ->
            val updated = readHrmDevices(prefs)
                .filterNot { it.address == address } + SavedHrmDevice(address, name)
            writeHrmDevices(prefs, updated)
            prefs[Keys.HRM_ACTIVE_ADDRESS] = address
        }
    }

    override suspend fun removeHrmDevice(address: String) {
        context.settingsDataStore.edit { prefs ->
            // Активность читаем ДО writeHrmDevices: миграция могла держать её
            // только в legacy-ключе, который writeHrmDevices удаляет
            val wasActive = (prefs[Keys.HRM_ACTIVE_ADDRESS]
                ?: prefs[Keys.LEGACY_HRM_DEVICE_ADDRESS]) == address
            val updated = readHrmDevices(prefs).filterNot { it.address == address }
            writeHrmDevices(prefs, updated)
            if (wasActive) prefs.remove(Keys.HRM_ACTIVE_ADDRESS)
        }
    }

    override suspend fun setActiveHrmDevice(address: String) {
        context.settingsDataStore.edit { prefs ->
            // Материализуем список (на случай ещё не мигрированных legacy-ключей)
            writeHrmDevices(prefs, readHrmDevices(prefs))
            prefs[Keys.HRM_ACTIVE_ADDRESS] = address
        }
    }

    /**
     * Список датчиков: новый JSON-ключ, иначе мягкая миграция
     * с legacy-пары «адрес+имя» (одиночный датчик старых версий).
     */
    private fun readHrmDevices(prefs: Preferences): List<SavedHrmDevice> =
        prefs[Keys.HRM_DEVICES]?.let(::decodeHrmDevices)
            ?: prefs[Keys.LEGACY_HRM_DEVICE_ADDRESS]?.let { legacyAddress ->
                listOf(SavedHrmDevice(legacyAddress, prefs[Keys.LEGACY_HRM_DEVICE_NAME]))
            }
            ?: emptyList()

    /** Запись списка всегда материализует новый формат и удаляет legacy-ключи. */
    private fun writeHrmDevices(prefs: MutablePreferences, devices: List<SavedHrmDevice>) {
        prefs[Keys.HRM_DEVICES] = encodeHrmDevices(devices)
        prefs.remove(Keys.LEGACY_HRM_DEVICE_ADDRESS)
        prefs.remove(Keys.LEGACY_HRM_DEVICE_NAME)
    }

    private companion object {
        const val TAG = "SettingsStorage"
    }
}

// ── Сериализация списка датчиков ─────────────────────────────────────────────
// Internal pure-функции — покрыты unit-тестами (HrmDevicesCodecTest).
// Gson вместо самодельного формата: имя BLE-устройства может содержать
// любые разделители.

private val hrmDevicesGson = Gson()

internal fun encodeHrmDevices(devices: List<SavedHrmDevice>): String =
    hrmDevicesGson.toJson(devices)

/** Битый JSON (повреждение/даунгрейд) → пустой список, не краш. */
internal fun decodeHrmDevices(json: String): List<SavedHrmDevice> = try {
    val type = object : TypeToken<List<SavedHrmDevice>>() {}.type
    hrmDevicesGson.fromJson<List<SavedHrmDevice>>(json, type) ?: emptyList()
} catch (e: Exception) {
    emptyList()
}
