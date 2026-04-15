package com.example.smarttracker.domain.model

/**
 * Domain-модель MET-конфигурации вида активности.
 *
 * Используется для расчёта расхода калорий методом MET
 * (Compendium of Physical Activities 2024, формула Харриса-Бенедикта).
 *
 * Если [usesSpeedZones] == true — MET берётся из [zones] с линейной интерполяцией
 * по текущей скорости GPS-точки. Если false — используется [baseMet] для всей тренировки.
 *
 * @param baseMet базовый MET-коэффициент (активность без учёта скорости)
 * @param usesSpeedZones нужна ли таблица скоростей для расчёта
 * @param zones список диапазонов скоростей с соответствующими MET-значениями
 */
data class METActivity(
    val baseMet: Double,
    val usesSpeedZones: Boolean,
    val zones: List<MetZone>,
)

/**
 * Одна зона скоростей в MET-таблице.
 *
 * Границы — в км/ч. При интерполяции:
 * MET(v) = MET₁ + (v − v₁) × (MET₂ − MET₁) / (v₂ − v₁)
 *
 * @param speedMin нижняя граница скорости (км/ч)
 * @param speedMax верхняя граница скорости (км/ч).
 *                 Для последней (открытой) зоны используется [Double.POSITIVE_INFINITY] как сентинел —
 *                 бэкенд передаёт null в `speed_max`, что означает «нет верхнего предела».
 *                 Маппинг null→POSITIVE_INFINITY происходит в [MetZoneDto.toDomain].
 * @param metValue MET-коэффициент для данного диапазона
 */
data class MetZone(
    val speedMin: Double,
    val speedMax: Double,
    val metValue: Double,
)
