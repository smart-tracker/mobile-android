package com.example.smarttracker.data.location

/**
 * Трекер километровых рубежей для голосовых подсказок.
 *
 * Чистый Kotlin — покрыт юнит-тестами [VoiceCueMilestoneTrackerTest]. Сервис
 * зовёт [onDistance] после каждой записанной GPS-точки; трекер решает, пора ли
 * объявлять рубеж, и считает темп круга между объявлениями.
 *
 * Устойчивость:
 *  - скачок сразу через несколько рубежей (дыра GPS) → одно объявление
 *    последнего достигнутого километра, темп усредняется по всем пройденным;
 *  - смена интервала настроек мид-тренировки → следующий рубеж считается
 *    от последнего объявленного километра с новым шагом;
 *  - crash-recovery: состояние восстанавливается через [restore].
 */
class VoiceCueMilestoneTracker {

    /** Последний объявленный километр (0 = ещё не объявляли). */
    var lastAnnouncedKm: Int = 0
        private set

    /** Elapsed записи на момент последнего объявления (мс) — база темпа круга. */
    var lastMilestoneElapsedMs: Long = 0L
        private set

    /**
     * Подсказка к объявлению.
     *
     * @param km             достигнутый километр (произносится)
     * @param lapPaceMsPerKm средний темп с прошлого объявления, мс/км; 0 = нет данных
     */
    data class Cue(val km: Int, val lapPaceMsPerKm: Long)

    /**
     * @param accumDistanceM накопленная дистанция записи, м (на паузе не растёт)
     * @param elapsedMs      elapsed записи, мс (без пауз)
     * @param intervalKm     шаг объявлений из настроек (1/2/5); <= 0 → выкл
     * @return подсказка или null, если рубеж не достигнут
     */
    fun onDistance(accumDistanceM: Double, elapsedMs: Long, intervalKm: Int): Cue? {
        if (intervalKm <= 0) return null
        val kmNow = (accumDistanceM / 1000.0).toInt()
        if (kmNow < lastAnnouncedKm + intervalKm) return null
        val lapKm = kmNow - lastAnnouncedKm
        val lapMs = elapsedMs - lastMilestoneElapsedMs
        val pace = if (lapKm > 0 && lapMs > 0) lapMs / lapKm else 0L
        lastAnnouncedKm = kmNow
        lastMilestoneElapsedMs = elapsedMs
        return Cue(km = kmNow, lapPaceMsPerKm = pace)
    }

    /** Восстановление после crash-recovery (значения из recovery-префов). */
    fun restore(lastAnnouncedKm: Int, lastMilestoneElapsedMs: Long) {
        this.lastAnnouncedKm = lastAnnouncedKm
        this.lastMilestoneElapsedMs = lastMilestoneElapsedMs
    }

    /** Сброс при старте новой тренировки. */
    fun reset() {
        lastAnnouncedKm = 0
        lastMilestoneElapsedMs = 0L
    }
}
