package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.MetZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-тесты для [CalorieCalculator].
 *
 * Фиксируют ключевые граничные сценарии:
 * - пустой список зон
 * - границы speedMin/speedMax
 * - скорость на стыке зон
 * - формула 60+ [CalorieCalculator.energyOver60]
 */
class CalorieCalculatorTest {

    @Test
    fun `interpolateMet с пустыми зонами возвращает 0`() {
        val met = CalorieCalculator.interpolateMet(speedKmh = 9.5, zones = emptyList())
        assertEquals(0.0, met, 0.0)
    }

    @Test
    fun `interpolateMet корректно обрабатывает границы speedMin и speedMax`() {
        val zones = listOf(
            MetZone(speedMin = 5.0, speedMax = 7.0, metValue = 4.0),
            MetZone(speedMin = 9.0, speedMax = 11.0, metValue = 8.0),
        )

        val atMin = CalorieCalculator.interpolateMet(speedKmh = 5.0, zones = zones)
        val atFirstMax = CalorieCalculator.interpolateMet(speedKmh = 7.0, zones = zones)
        val aboveLastMax = CalorieCalculator.interpolateMet(speedKmh = 12.0, zones = zones)

        assertEquals(4.0, atMin, 0.0)
        assertEquals(4.0, atFirstMax, 0.0)
        assertEquals(8.0, aboveLastMax, 0.0)
    }

    @Test
    fun `interpolateMet на стыке зон возвращает MET следующей зоны`() {
        val zones = listOf(
            MetZone(speedMin = 6.0, speedMax = 8.0, metValue = 5.0),
            MetZone(speedMin = 8.0, speedMax = 10.0, metValue = 7.0),
        )

        val metAtJunction = CalorieCalculator.interpolateMet(speedKmh = 8.0, zones = zones)

        assertEquals(7.0, metAtJunction, 0.0)
    }

    @Test
    fun `energyOver60 рассчитывается по формуле 60 плюс`() {
        val met = 6.0
        val weightKg = 70f
        val durationMin = 10.0
        // Для 60+ CalorieCalculator использует отдельную MET-формулу с фиксированным CF:
        // (3.5 / 2.7), где 2.7 — референсный VO2rest для 60+ по Compendium 2024.
        val vo2RestReferenceMlKgMin = 3.5
        // MET-конверсия: деление на 200 переводит значение ккал/мин.
        val metConversionFactor = 200.0

        val actual = CalorieCalculator.energyOver60(
            met = met,
            weightKg = weightKg,
            durationMin = durationMin,
        )

        // Алгебраически эквивалентно формуле в прод-коде:
        // (MET * (3.5 / 2.7)) * 2.7 * W / 200 * t = MET * 3.5 * W / 200 * t.
        val expected = met * vo2RestReferenceMlKgMin * weightKg.toDouble() / metConversionFactor * durationMin
        assertEquals(expected, actual, 1e-9)
    }
}
