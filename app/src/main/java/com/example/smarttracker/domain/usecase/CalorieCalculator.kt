package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.MetZone

/**
 * Расчёт расхода калорий методом MET (Compendium of Physical Activities 2024).
 *
 * Основан на формуле Харриса-Бенедикта для базового обмена веществ (RMR)
 * и персональном поправочном коэффициенте CF.
 *
 * ## Алгоритм (18–59 лет):
 * 1. RMR (ккал/сут): по формуле Харриса-Бенедикта (разная для муж./жен.)
 * 2. VO2rest = (RMR × 1000) / (1440 × 5 × W)  — индивидуальный метаболизм покоя (мл/кг/мин)
 * 3. CF = 3.5 / VO2rest  — поправочный коэффициент
 * 4. E = MET × CF × W × (t / 60)  — расход ккал за интервал t минут
 *
 * ## Для 60+ лет (любой пол):
 * E = (MET × (3.5 / 2.7)) × 2.7 × W / 200 × t
 *
 * ## Переменная скорость:
 * CF не меняется в ходе тренировки — зависит только от профиля пользователя.
 * MET для скоростных видов (бег, вело) берётся из таблицы зон с интерполяцией:
 * MET(v) = MET₁ + (v − v₁) × (MET₂ − MET₁) / (v₂ − v₁)
 *
 * Источник: https://pacompendium.com/ (Compendium 2024, авторы Ainsworth & Haskell)
 *
 * **Не содержит импортов android.*, retrofit2.*, gson.* — domain-слой чистый Kotlin.**
 */
object CalorieCalculator {

    /**
     * Вычисляет поправочный коэффициент CF для пользователя.
     *
     * CF = 3.5 / VO2rest, где VO2rest = (RMR × 1000) / (1440 × 5 × W).
     * CF зависит только от профиля — вычислять один раз перед тренировкой.
     *
     * @param weightKg масса тела (кг)
     * @param heightCm рост (см)
     * @param ageYears возраст (лет); для 60+ использовать [energyOver60]
     * @param gender пол пользователя
     * @return поправочный коэффициент CF (безразмерный)
     */
    fun computeCF(weightKg: Float, heightCm: Float, ageYears: Int, gender: Gender): Double {
        val w = weightKg.toDouble()
        val h = heightCm.toDouble()
        val a = ageYears.toDouble()

        // Формула Харриса-Бенедикта для базового обмена веществ (ккал/сут)
        val rmr = when (gender) {
            Gender.MALE   -> 66.473 + 5.003 * h + 13.7516 * w - 6.755 * a
            Gender.FEMALE -> 655.0955 + 1.8496 * h + 9.5634 * w - 4.6756 * a
        }

        // VO2rest — индивидуальный метаболизм покоя (мл/кг/мин)
        val vo2rest = (rmr * 1000.0) / (1440.0 * 5.0 * w)

        // CF: поправка от стандартного 3.5 к персональному VO2rest
        return 3.5 / vo2rest
    }

    /**
     * Расход ккал за один интервал для возраста 18–59 лет.
     *
     * E = MET × CF × W × (t / 60)
     *
     * @param met MET-коэффициент для текущей активности/скорости
     * @param cf поправочный коэффициент из [computeCF]
     * @param weightKg масса тела (кг)
     * @param durationMin длительность интервала (минуты)
     * @return расход энергии (ккал)
     */
    fun energyForInterval(met: Double, cf: Double, weightKg: Float, durationMin: Double): Double {
        return met * cf * weightKg.toDouble() * (durationMin / 60.0)
    }

    /**
     * Расход ккал за интервал для пользователей 60+ лет (любой пол).
     *
     * Упрощённая формула без персонального BMR:
     * E = (MET × (3.5 / 2.7)) × 2.7 × W / 200 × t
     *
     * Для 60+ стандартный VO2rest принят равным 2.7 мл/кг/мин
     * (сниженный по сравнению с молодёжным 3.5).
     *
     * @param met MET-коэффициент для текущей активности/скорости
     * @param weightKg масса тела (кг)
     * @param durationMin длительность интервала (минуты)
     * @return расход энергии (ккал)
     */
    fun energyOver60(met: Double, weightKg: Float, durationMin: Double): Double {
        return (met * (3.5 / 2.7)) * 2.7 * weightKg.toDouble() / 200.0 * durationMin
    }

    /**
     * Определяет MET-коэффициент для заданной скорости по таблице зон.
     *
     * Если скорость ниже минимальной зоны — берётся MET первой зоны.
     * Если выше максимальной — берётся MET последней зоны.
     * Для промежуточных значений — линейная интерполяция:
     * MET(v) = MET₁ + (v − v₁) × (MET₂ − MET₁) / (v₂ − v₁)
     *
     * @param speedKmh текущая скорость (км/ч)
     * @param zones список зон скоростей из [com.example.smarttracker.domain.model.METActivity]
     * @return интерполированный MET-коэффициент
     */
    fun interpolateMet(speedKmh: Double, zones: List<MetZone>): Double {
        if (zones.isEmpty()) return 0.0

        // Сортировка гарантирует корректный поиск даже если сервер вернул зоны не по порядку.
        // sortedBy создаёт новый список — исходный zones не мутируется.
        val sorted = zones.sortedBy { it.speedMin }

        // Скорость ниже нижней границы первой зоны
        if (speedKmh <= sorted.first().speedMin) return sorted.first().metValue

        // Скорость выше верхней границы последней зоны
        if (speedKmh >= sorted.last().speedMax) return sorted.last().metValue

        // Найти зону, в которую попадает скорость.
        // Правая граница exclusive (< z2.speedMin) чтобы скорость ровно на границе зон
        // попадала в СЛЕДУЮЩУЮ зону (корректное поведение для плавного ускорения).
        for (i in 0 until sorted.size - 1) {
            val z1 = sorted[i]
            val z2 = sorted[i + 1]
            if (speedKmh >= z1.speedMin && speedKmh < z2.speedMin) {
                // Линейная интерполяция между верхней границей z1 и нижней z2
                val v1 = z1.speedMax
                val v2 = z2.speedMin
                if (v2 == v1) return z1.metValue
                return z1.metValue + (speedKmh - v1) * (z2.metValue - z1.metValue) / (v2 - v1)
            }
        }

        // Скорость внутри последней зоны
        return sorted.last().metValue
    }
}
