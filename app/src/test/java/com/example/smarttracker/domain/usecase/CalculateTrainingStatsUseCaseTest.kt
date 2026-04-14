package com.example.smarttracker.domain.usecase

import com.example.smarttracker.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты CalculateTrainingStatsUseCase.
 *
 * Покрывает:
 * - Пустой список точек → нулевая статистика (не краш)
 * - Одна точка → нулевая статистика
 * - Дистанция по формуле Гаверсинуса для известных координат
 * - Фильтр по accuracy (точки внутри "пятна неопределённости" не учитываются)
 * - calculateDeltaDistance инкрементальный расчёт
 * - Среднее время и скорость
 */
class CalculateTrainingStatsUseCaseTest {

    private lateinit var useCase: CalculateTrainingStatsUseCase

    @Before
    fun setUp() {
        useCase = CalculateTrainingStatsUseCase()
    }

    // ── Граничные случаи ──────────────────────────────────────────────────────

    @Test
    fun `пустой список точек возвращает нулевую статистику`() {
        val stats = useCase.execute(emptyList())
        assertEquals(0.0, stats.distanceMeters, 0.001)
        assertEquals(0.0, stats.avgSpeedMps, 0.001)
        assertEquals(0f, stats.kilocalories)
        assertEquals(0L, stats.durationSeconds)
    }

    @Test
    fun `одна точка возвращает нулевую статистику`() {
        val stats = useCase.execute(listOf(makePoint(55.7558, 37.6173, timestamp = 0L)))
        assertEquals(0.0, stats.distanceMeters, 0.001)
        assertEquals(0L, stats.durationSeconds)
    }

    // ── Дистанция (Haversine) ─────────────────────────────────────────────────

    @Test
    fun `дистанция между двумя точками приблизительно верна`() {
        // Москва: Красная площадь (55.7539, 37.6208) → Кремль (55.7520, 37.6175)
        // Реальное расстояние ~230 метров
        val points = listOf(
            makePoint(lat = 55.7539, lon = 37.6208, timestamp = 0L),
            makePoint(lat = 55.7520, lon = 37.6175, timestamp = 60_000L)
        )
        val stats = useCase.execute(points)
        // Допуск ±50 метров (погрешность сферической модели Земли)
        // Haversine даёт ~295м для этих координат
        assertTrue(
            "Ожидалось ~295м, получено ${stats.distanceMeters}м",
            stats.distanceMeters in 240.0..350.0
        )
    }

    @Test
    fun `дистанция трёх точек равна сумме двух сегментов`() {
        val p1 = makePoint(55.7539, 37.6208, timestamp = 0L)
        val p2 = makePoint(55.7520, 37.6175, timestamp = 60_000L)
        val p3 = makePoint(55.7500, 37.6140, timestamp = 120_000L)

        val total = useCase.execute(listOf(p1, p2, p3)).distanceMeters
        val segment1 = useCase.execute(listOf(p1, p2)).distanceMeters
        val segment2 = useCase.execute(listOf(p2, p3)).distanceMeters

        assertEquals(segment1 + segment2, total, 0.001)
    }

    @Test
    fun `идентичные координаты дают нулевую дистанцию`() {
        val p1 = makePoint(55.7558, 37.6173, timestamp = 0L)
        val p2 = makePoint(55.7558, 37.6173, timestamp = 60_000L)
        val stats = useCase.execute(listOf(p1, p2))
        assertEquals(0.0, stats.distanceMeters, 0.001)
    }

    // ── Фильтр по accuracy ────────────────────────────────────────────────────

    @Test
    fun `сегмент короче accuracy пятна не учитывается в дистанции`() {
        // Движение на 1 метр, но accuracy = 10м → сегмент внутри пятна → 0
        val p1 = makePoint(55.75000, 37.61730, timestamp = 0L,     accuracy = 10f)
        val p2 = makePoint(55.75001, 37.61731, timestamp = 1_000L, accuracy = 10f)
        val stats = useCase.execute(listOf(p1, p2))
        // Расстояние ~1.5м < max(10, 10) = 10м → должно быть 0
        assertEquals(0.0, stats.distanceMeters, 0.001)
    }

    @Test
    fun `сегмент длиннее accuracy пятна учитывается`() {
        // Движение на ~230м, accuracy = 5м → сегмент длиннее пятна → учитывается
        val p1 = makePoint(55.7539, 37.6208, timestamp = 0L,     accuracy = 5f)
        val p2 = makePoint(55.7520, 37.6175, timestamp = 60_000L, accuracy = 5f)
        val stats = useCase.execute(listOf(p1, p2))
        assertTrue("Дистанция должна быть > 0: ${stats.distanceMeters}", stats.distanceMeters > 0)
    }

    @Test
    fun `точки без accuracy не фильтруются`() {
        // accuracy=null → maxAccuracy=0 → фильтр не применяется
        val p1 = makePoint(55.7539, 37.6208, timestamp = 0L,     accuracy = null)
        val p2 = makePoint(55.7520, 37.6175, timestamp = 60_000L, accuracy = null)
        val stats = useCase.execute(listOf(p1, p2))
        assertTrue("Дистанция без accuracy > 0", stats.distanceMeters > 0)
    }

    // ── Продолжительность и скорость ──────────────────────────────────────────

    @Test
    fun `durationSeconds рассчитывается по метке времени крайних точек`() {
        val p1 = makePoint(55.7539, 37.6208, timestamp = 1000L)
        val p2 = makePoint(55.7520, 37.6175, timestamp = 61_000L)  // 60 сек спустя
        val stats = useCase.execute(listOf(p1, p2))
        assertEquals(60L, stats.durationSeconds)
    }

    @Test
    fun `avgSpeedMps = distanceMeters div durationSeconds`() {
        val p1 = makePoint(55.7539, 37.6208, timestamp = 0L)
        val p2 = makePoint(55.7520, 37.6175, timestamp = 60_000L)
        val stats = useCase.execute(listOf(p1, p2))
        if (stats.durationSeconds > 0) {
            val expectedSpeed = stats.distanceMeters / stats.durationSeconds.toDouble()
            assertEquals(expectedSpeed, stats.avgSpeedMps, 0.001)
        }
    }

    // ── Калории ───────────────────────────────────────────────────────────────

    @Test
    fun `kilocalories рассчитывается как distanceKm умножить на 70`() {
        val p1 = makePoint(55.7539, 37.6208, timestamp = 0L)
        val p2 = makePoint(55.7520, 37.6175, timestamp = 60_000L)
        val stats = useCase.execute(listOf(p1, p2))
        val expectedKcal = ((stats.distanceMeters / 1000.0) * 70.0).toFloat()
        assertEquals(expectedKcal, stats.kilocalories, 0.01f)
    }

    // ── calculateDeltaDistance ────────────────────────────────────────────────

    @Test
    fun `calculateDeltaDistance с fromIndex=0 равен полной дистанции`() {
        val points = listOf(
            makePoint(55.7539, 37.6208, 0L),
            makePoint(55.7520, 37.6175, 60_000L),
            makePoint(55.7500, 37.6140, 120_000L),
        )
        val delta = useCase.calculateDeltaDistance(points, fromIndex = 0)
        val total = useCase.execute(points).distanceMeters
        assertEquals(total, delta, 0.001)
    }

    @Test
    fun `calculateDeltaDistance с fromIndex=points_size возвращает 0`() {
        val points = listOf(
            makePoint(55.7539, 37.6208, 0L),
            makePoint(55.7520, 37.6175, 60_000L),
        )
        val delta = useCase.calculateDeltaDistance(points, fromIndex = points.size)
        assertEquals(0.0, delta, 0.001)
    }

    @Test
    fun `calculateDeltaDistance включает отрезок между последней старой и первой новой точкой`() {
        val points = listOf(
            makePoint(55.7539, 37.6208, 0L),          // старая
            makePoint(55.7520, 37.6175, 60_000L),      // последняя старая (index=1)
            makePoint(55.7500, 37.6140, 120_000L),     // первая новая
        )
        // fromIndex=2 → обрабатывает пару (index=1, index=2)
        val delta = useCase.calculateDeltaDistance(points, fromIndex = 2)
        val segment = useCase.execute(listOf(points[1], points[2])).distanceMeters
        assertEquals(segment, delta, 0.001)
    }

    @Test
    fun `calculateDeltaDistance с одной точкой возвращает 0`() {
        val points = listOf(makePoint(55.7539, 37.6208, 0L))
        val delta = useCase.calculateDeltaDistance(points, fromIndex = 0)
        assertEquals(0.0, delta, 0.001)
    }

    // ── Хелпер ───────────────────────────────────────────────────────────────

    private fun makePoint(
        lat: Double,
        lon: Double,
        timestamp: Long,
        accuracy: Float? = null,
    ) = LocationPoint(
        trainingId   = "test",
        timestampUtc = timestamp,
        elapsedNanos = timestamp * 1_000_000L,
        latitude     = lat,
        longitude    = lon,
        altitude     = null,
        speed        = null,
        accuracy     = accuracy,
    )
}
