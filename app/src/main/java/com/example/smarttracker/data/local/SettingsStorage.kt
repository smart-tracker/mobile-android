package com.example.smarttracker.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Пользовательские настройки приложения (экран «Меню → Настройки»).
 *
 * Дефолты выбраны осознанно:
 *  - [autopauseEnabled] = false — автопауза меняет поведение записи тренировки,
 *    пользователь включает её сам (меньше сюрпризов «трекер сам остановился»);
 *  - [voiceCuesEnabled] = true — подсказки не влияют на записываемые данные,
 *    легко выключить; фича заметна сразу;
 *  - [voiceCueIntervalKm] = 1 — стандарт беговых приложений;
 *  - [keepScreenOn] = false — экономия батареи по умолчанию.
 *
 * Громкости подсказок здесь НЕТ намеренно: слайдер «Громкость» на экране
 * настроек управляет системной громкостью медиа (STREAM_MUSIC) напрямую —
 * её персистит сам Android, отдельная настройка создала бы вторую
 * независимую ручку (слайдер и кнопки громкости расходились бы).
 */
data class AppSettings(
    val autopauseEnabled: Boolean = false,
    val voiceCuesEnabled: Boolean = true,
    val voiceCueIntervalKm: Int = 1,
    val keepScreenOn: Boolean = false,
    /**
     * MAC-адрес сохранённого BLE-пульсометра. null = датчик не настроен.
     * Отдельного toggle «включить пульсометр» нет: адрес сохранён = включено,
     * «Забыть датчик» = выключено.
     */
    val hrmDeviceAddress: String? = null,
    /** Имя сохранённого пульсометра — только для отображения в UI. */
    val hrmDeviceName: String? = null,
) {
    companion object {
        /** Допустимые интервалы голосовых подсказок, км. */
        val ALLOWED_VOICE_INTERVALS = listOf(1, 2, 5)
    }
}

/**
 * Контракт хранилища настроек. Реализация — [SettingsStorageImpl] на
 * DataStore Preferences (первое использование DataStore в проекте; настройки
 * не чувствительные — шифрование, как у токенов, не требуется).
 *
 * Потребители:
 *  - SettingsViewModel (экран настроек) — чтение + запись;
 *  - LocationTrackingService — чтение (автопауза, голосовые подсказки,
 *    адрес пульсометра для автоподключения);
 *  - WorkoutStartViewModel — чтение (keepScreenOn, наличие пульсометра);
 *  - SensorsViewModel (экран «Датчики») — чтение + запись пульсометра.
 */
interface SettingsStorage {

    /**
     * Поток настроек: эмитит текущее значение и все последующие изменения.
     * Изменение настройки во время активной тренировки подхватывается сервисом
     * без перезапуска записи.
     */
    val settings: Flow<AppSettings>

    suspend fun setAutopauseEnabled(enabled: Boolean)

    suspend fun setVoiceCuesEnabled(enabled: Boolean)

    /** Значения вне [AppSettings.ALLOWED_VOICE_INTERVALS] приводятся к дефолту (1 км). */
    suspend fun setVoiceCueIntervalKm(intervalKm: Int)

    suspend fun setKeepScreenOn(enabled: Boolean)

    /**
     * Сохранить выбранный пульсометр. address = null стирает оба поля
     * («Забыть датчик»). Имя без адреса не сохраняется.
     */
    suspend fun setHrmDevice(address: String?, name: String?)
}
