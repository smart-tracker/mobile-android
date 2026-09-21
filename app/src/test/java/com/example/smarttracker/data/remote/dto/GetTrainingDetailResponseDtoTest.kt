package com.example.smarttracker.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit-тесты маппера GetTrainingDetailResponseDto.gpsPointsToDomain().
 *
 * Фокус — параллельные массивы ответа GET /training/{id}/get_training:
 * - gps_points_heart_rates (BR-16): значения и null-элементы привязываются
 *   к точкам по индексу; отсутствие массива и массив короче координат
 *   не ломают разбор (heartRate = null);
 * - gps_points_timestamps: ISO-строки → epoch millis, fallback на индекс.
 *
 * JSON собирается строкой и парсится реальным Gson — проверяем и
 * @SerializedName-контракт, и маппер разом (как это делает Retrofit).
 */
class GetTrainingDetailResponseDtoTest {

    private val gson = Gson()

    /** Каркас ответа: 3 точки трека; массивы пульса/времени подставляются параметрами. */
    private fun detailJson(heartRatesJson: String? = null, timestampsJson: String? = null): String {
        val extra = buildString {
            if (timestampsJson != null) append(""","gps_points_timestamps": $timestampsJson""")
            if (heartRatesJson != null) append(""","gps_points_heart_rates": $heartRatesJson""")
        }
        return """
            {
              "training_id": "t-1",
              "type_activ_id": 1,
              "date": "2026-07-20",
              "time_start": "2026-07-20T10:00:00Z",
              "time_end": "2026-07-20T10:30:00Z",
              "kilocalories": 200.0,
              "distance_m": 5000.0,
              "avg_speed": 2.8,
              "elevation_gain": 12.5,
              "gps_track": {
                "type": "LineString",
                "coordinates": [[34.379, 61.774, 5.0], [34.380, 61.775, 6.0], [34.381, 61.776, 7.0]]
              }
              $extra
            }
        """.trimIndent()
    }

    private fun parse(json: String): GetTrainingDetailResponseDto =
        gson.fromJson(json, GetTrainingDetailResponseDto::class.java)

    @Test
    fun `heart_rates с null-элементом - пульс привязан к точкам по индексу`() {
        val dto = parse(detailJson(heartRatesJson = "[150, null, 160]"))
        val points = dto.gpsPointsToDomain()

        assertEquals(3, points.size)
        assertEquals(150, points[0].heartRate)
        assertNull("null-элемент = датчик не был подключён", points[1].heartRate)
        assertEquals(160, points[2].heartRate)
    }

    @Test
    fun `без массива heart_rates - у всех точек heartRate null`() {
        val dto = parse(detailJson())
        val points = dto.gpsPointsToDomain()

        assertEquals(3, points.size)
        points.forEach { assertNull("тренировка до BR-16 — пульса нет", it.heartRate) }
    }

    @Test
    fun `массив heart_rates короче координат - хвост точек получает null, разбор не падает`() {
        val dto = parse(detailJson(heartRatesJson = "[150]"))
        val points = dto.gpsPointsToDomain()

        assertEquals(3, points.size)
        assertEquals(150, points[0].heartRate)
        assertNull(points[1].heartRate)
        assertNull(points[2].heartRate)
    }

    @Test
    fun `агрегаты avg и max пульса парсятся из ответа`() {
        val json = detailJson().replace(
            "\"elevation_gain\": 12.5,",
            "\"elevation_gain\": 12.5, \"avg_heart_rate\": 155.0, \"max_heart_rate\": 172,",
        )
        val dto = parse(json)

        assertEquals(155.0, dto.avgHeartRate!!, 0.001)
        assertEquals(172, dto.maxHeartRate)
    }

    @Test
    fun `timestamps парсятся в epoch millis, пульс и время идут по одному индексу`() {
        val dto = parse(detailJson(
            heartRatesJson = "[150, 155, 160]",
            timestampsJson = """["2026-07-20T10:00:00Z", "2026-07-20T10:00:05Z", "2026-07-20T10:00:10Z"]""",
        ))
        val points = dto.gpsPointsToDomain()

        assertEquals(3, points.size)
        // elapsed между соседними точками — 5 секунд
        assertEquals(5_000L, points[1].timestampUtc - points[0].timestampUtc)
        assertEquals(155, points[1].heartRate)
    }
}
