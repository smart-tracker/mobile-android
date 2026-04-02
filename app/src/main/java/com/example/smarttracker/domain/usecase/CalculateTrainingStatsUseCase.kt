package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.LocationPoint
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Результат расчёта статистики тренировки.
 *
 * Намеренно не содержит импортов android.* — domain-слой остаётся чистым Kotlin.
 */
data class TrainingStats(
    val distanceMeters: Double,
    /** Средняя скорость: distanceMeters / durationSeconds, м/с */
    val avgSpeedMps: Double,
    val kilocalories: Float,
    /** Продолжительность по временным меткам GPS-точек (первая → последняя), секунды */
    val durationSeconds: Long,
)

/**
 * UseCase расчёта статистики тренировки по списку GPS-точек.
 *
 * Дистанция: сумма haversine-расстояний между последовательными точками.
 * Скорость: средняя (дистанция / продолжительность).
 * Калории: упрощённая формула distanceKm * 70 (усреднённый вес 70 кг).
 */
class CalculateTrainingStatsUseCase @Inject constructor() {

    /**
     * Вычисляет дополнительную дистанцию в метрах только для новых точек.
     *
     * Принимает полный список точек и количество уже обработанных.
     * Обрабатывает только пары начиная с (fromIndex - 1), включая последнюю «старую» точку,
     * чтобы не потерять отрезок между ней и первой новой.
     *
     * Используется для инкрементального расчёта статистики: O(n_new) вместо O(n_total).
     *
     * @param points   полный список GPS-точек тренировки
     * @param fromIndex количество точек, уже обработанных в предыдущем вызове
     * @return дельта дистанции в метрах для новых пар точек
     */
    fun calculateDeltaDistance(points: List<LocationPoint>, fromIndex: Int): Double {
        if (points.size < 2) return 0.0
        // Начинаем с (fromIndex - 1), чтобы соединить последнюю старую точку с первой новой
        val startIdx = maxOf(0, fromIndex - 1)
        if (startIdx >= points.size - 1) return 0.0
        return points.subList(startIdx, points.size)
            .zipWithNext()
            .sumOf { (p1, p2) -> haversineMeters(p1, p2) }
    }

    fun execute(points: List<LocationPoint>): TrainingStats {
        if (points.size < 2) {
            return TrainingStats(
                distanceMeters = 0.0,
                avgSpeedMps    = 0.0,
                kilocalories   = 0f,
                durationSeconds = 0L,
            )
        }

        // Дистанция — сумма расстояний между соседними точками
        val distanceMeters = points.zipWithNext().sumOf { (p1, p2) -> haversineMeters(p1, p2) }

        // Продолжительность — по UTC-меткам крайних точек
        val durationSeconds = (points.last().timestampUtc - points.first().timestampUtc) / 1000L

        val avgSpeedMps = if (durationSeconds > 0) distanceMeters / durationSeconds else 0.0

        // Калории: distanceKm * 70 кКал/км (константа для среднего веса 70 кг)
        val kilocalories = ((distanceMeters / 1000.0) * 70.0).toFloat()

        return TrainingStats(
            distanceMeters  = distanceMeters,
            avgSpeedMps     = avgSpeedMps,
            kilocalories    = kilocalories,
            durationSeconds = durationSeconds,
        )
    }

    /**
     * Расстояние между двумя GPS-точками по формуле гаверсинусов (Haversine).
     *
     * Формула даёт точность ~0.5% для дистанций до нескольких тысяч км.
     * Земля аппроксимируется сферой радиусом 6 371 000 м.
     */
    private fun haversineMeters(p1: LocationPoint, p2: LocationPoint): Double {
        val earthRadiusM = 6_371_000.0

        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusM * c
    }
}
