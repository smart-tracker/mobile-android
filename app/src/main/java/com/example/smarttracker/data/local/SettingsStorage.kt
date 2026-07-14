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
     * Сохранённые BLE-пульсометры. Пустой список = датчики не настроены
     * (гейт HR-бейджа и StatItem «Пульс»). Отдельного toggle нет:
     * список непуст = включено.
     */
    val hrmDevices: List<SavedHrmDevice> = emptyList(),
    /**
     * Адрес активного датчика — последний выбранный пользователем
     * (тап по строке списка / добавление через «+»). К нему идёт
     * автоподключение. null при пустом списке или после удаления активного.
     */
    val hrmActiveAddress: String? = null,
) {
    /**
     * Адрес для автоподключения: активный, а если он не выставлен
     * (например, активный удалили) — первый из сохранённых.
     */
    fun autoConnectAddress(): String? =
        hrmActiveAddress ?: hrmDevices.firstOrNull()?.address

    companion object {
        /** Допустимые интервалы голосовых подсказок, км. */
        val ALLOWED_VOICE_INTERVALS = listOf(1, 2, 5)
    }
}

/**
 * Сохранённый BLE-пульсометр.
 *
 * @param address MAC-адрес (ключ уникальности в списке и цель подключения)
 * @param name    имя устройства для отображения; null если датчик его не вещал
 */
data class SavedHrmDevice(
    val address: String,
    val name: String?,
)

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
     * Добавить пульсометр в список (или обновить имя существующего)
     * и сделать его активным — добавление всегда означает «пользователь
     * выбрал этот датчик».
     */
    suspend fun addHrmDevice(address: String, name: String?)

    /**
     * Удалить пульсометр из списка. Если удаляемый был активным —
     * активный сбрасывается (автоконнект уйдёт на первый оставшийся,
     * см. [AppSettings.autoConnectAddress]).
     */
    suspend fun removeHrmDevice(address: String)

    /** Переключить активный датчик (тап по строке списка). */
    suspend fun setActiveHrmDevice(address: String)
}
