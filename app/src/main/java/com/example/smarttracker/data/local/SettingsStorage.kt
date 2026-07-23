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
     * Способ завершения тренировки: true — кнопку «Завершить» нужно удержать
     * 3 сек (заполнение слева направо), false — завершение по обычному тапу.
     * Дефолт true — защита от случайного нажатия во время тренировки
     * (телефон в кармане, кнопка в нижней зоне под большим пальцем).
     */
    val finishConfirmationHold: Boolean = true,
    /**
     * Показывать бейдж пульса поверх карты (индикатор состояния датчика, как
     * GPS-бейдж: зелёный — подключён, красный — нет связи). Дефолт true — виден
     * сразу, тап открывает список датчиков. Выключается тумблером в Настройках.
     * От наличия датчика НЕ зависит (в отличие от StatItem «Пульс»).
     */
    val showHeartRateBadge: Boolean = true,
    /**
     * Служебный флаг (не в UI настроек): показан ли одноразовый onboarding-coachmark
     * при первом входе в активную тренировку. Ставится в true по кнопке «Понятно».
     */
    val workoutCoachmarkShown: Boolean = false,
    /**
     * Служебный флаг (не в UI настроек): показан ли одноразовый onboarding-coachmark
     * при первом заходе на экран истории тренировок (календарь). Ставится в true по
     * кнопке «Понятно». Повторно открывается кнопкой справки «?» в шапке экрана.
     */
    val calendarCoachmarkShown: Boolean = false,
    /**
     * Сохранённые BLE-пульсометры. Пустой список = датчики не настроены
     * (гейт StatItem «Пульс» и автоподключения). Бейдж пульса гейтится отдельно
     * — [showHeartRateBadge]. Отдельного toggle списка нет: непуст = включено.
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

    /** true — завершение по удержанию 3 сек, false — по обычному тапу. */
    suspend fun setFinishConfirmationHold(enabled: Boolean)

    /** true — бейдж пульса виден поверх карты, false — скрыт. */
    suspend fun setShowHeartRateBadge(enabled: Boolean)

    /** Отметить onboarding-coachmark тренировки как показанный (кнопка «Понятно»). */
    suspend fun setWorkoutCoachmarkShown(shown: Boolean)

    /** Отметить onboarding-coachmark календаря (истории) как показанный (кнопка «Понятно»). */
    suspend fun setCalendarCoachmarkShown(shown: Boolean)

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
