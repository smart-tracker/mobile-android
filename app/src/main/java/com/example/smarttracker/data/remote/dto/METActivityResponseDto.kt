package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.model.MetZone
import com.google.gson.annotations.SerializedName

/**
 * DTO ответа GET /training/met/{type_activ_id}.
 *
 * Возвращает MET-коэффициенты для расчёта расхода калорий по методу
 * Compendium of Physical Activities 2024.
 *
 * Если [usesSpeedZones] == true — MET зависит от скорости; используются [zones]
 * с линейной интерполяцией между записями.
 * Если false — применять [baseMet] для всего интервала.
 *
 * @param baseMet базовый MET-коэффициент (используется когда скорость не важна)
 * @param usesSpeedZones признак: нужна ли таблица скоростей для расчёта
 * @param zones список зон скоростей с соответствующими MET-значениями
 */
data class METActivityResponseDto(
    @SerializedName("base_met")
    val baseMet: Double,
    @SerializedName("uses_speed_zones")
    val usesSpeedZones: Boolean,
    @SerializedName("zones")
    val zones: List<MetZoneDto>,
)

/**
 * DTO одной зоны скоростей для MET-таблицы.
 *
 * @param speedMin нижняя граница скорости (км/ч)
 * @param speedMax верхняя граница скорости (км/ч); null для последней (открытой) зоны —
 *                 бэкенд передаёт null как признак «нет верхнего предела»
 * @param metValue MET-коэффициент для данного диапазона скоростей
 */
data class MetZoneDto(
    @SerializedName("speed_min")
    val speedMin: Double,
    @SerializedName("speed_max")
    val speedMax: Double?,      // nullable: последняя зона открытая, у неё нет верхней границы
    @SerializedName("met_value")
    val metValue: Double,
)

/** Маппинг DTO → domain-модель */
fun METActivityResponseDto.toDomain(): METActivity = METActivity(
    baseMet        = baseMet,
    usesSpeedZones = usesSpeedZones,
    // null-speedMax → Double.POSITIVE_INFINITY-сентинел: «нет верхней границы».
    // Это предотвращает ложное срабатывание guard-check'а в CalorieCalculator.interpolateMet:
    //   if (speedKmh >= zones.last().speedMax)  — с null→0.0 это всегда true!
    // POSITIVE_INFINITY семантически точнее MAX_VALUE: "нет предела" = бесконечность.
    zones = zones.map { MetZone(it.speedMin, it.speedMax ?: Double.POSITIVE_INFINITY, it.metValue) },
)
