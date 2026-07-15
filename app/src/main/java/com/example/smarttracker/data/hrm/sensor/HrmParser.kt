package com.example.smarttracker.data.hrm.sensor

/**
 * Парсер характеристики Heart Rate Measurement (UUID 0x2A37, Bluetooth SIG).
 *
 * Формат payload (GATT Specification Supplement):
 * - Байт 0 — flags:
 *   - бит 0: формат значения пульса (0 = UINT8, 1 = UINT16 little-endian)
 *   - биты 1-2: sensor contact status
 *   - бит 3: присутствует поле Energy Expended
 *   - бит 4: присутствуют RR-интервалы
 * - Байт 1 (и 2 при UINT16) — значение пульса.
 *
 * Поля Energy Expended и RR-интервалы идут ПОСЛЕ значения пульса,
 * поэтому их наличие не сдвигает смещение bpm — v1 их игнорирует.
 *
 * Чистая функция без Android-зависимостей — покрыта unit-тестами.
 */
object HrmParser {

    /**
     * Извлечь пульс (уд/мин) из payload характеристики 0x2A37.
     *
     * @return bpm, или null если payload пустой/обрезанный (датчик прислал мусор —
     *   такой сэмпл молча отбрасывается, соединение не рвётся)
     */
    fun parseHeartRateMeasurement(value: ByteArray): Int? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt()
        return if (flags and 0x01 == 0) {
            // UINT8: один байт после flags
            if (value.size < 2) null else value[1].toInt() and 0xFF
        } else {
            // UINT16 little-endian: два байта после flags
            if (value.size < 3) null
            else (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        }
    }
}
