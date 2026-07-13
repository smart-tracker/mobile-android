package com.example.smarttracker.data.hrm.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты парсера характеристики Heart Rate Measurement (0x2A37).
 *
 * Payload собирается вручную по спецификации Bluetooth SIG:
 * байт 0 — flags (бит 0 = формат bpm), дальше — значение.
 */
class HrmParserTest {

    @Test
    fun `UINT8 - обычный пульс`() {
        // flags=0x00 (бит0=0 → UINT8), bpm=72
        val bpm = HrmParser.parseHeartRateMeasurement(byteArrayOf(0x00, 72))
        assertEquals(72, bpm)
    }

    @Test
    fun `UINT8 - значение 255 не портится знаковым расширением байта`() {
        // 255 в байте = -1 в JVM signed byte; парсер обязан маскировать 0xFF
        val bpm = HrmParser.parseHeartRateMeasurement(byteArrayOf(0x00, 0xFF.toByte()))
        assertEquals(255, bpm)
    }

    @Test
    fun `UINT16 little-endian - пульс больше 255`() {
        // flags=0x01 (бит0=1 → UINT16 LE), bpm=300 = 0x012C → байты 0x2C, 0x01
        val bpm = HrmParser.parseHeartRateMeasurement(byteArrayOf(0x01, 0x2C, 0x01))
        assertEquals(300, bpm)
    }

    @Test
    fun `UINT16 - обычное значение в двухбайтовом формате`() {
        // Некоторые датчики всегда шлют UINT16, даже для bpm < 256
        val bpm = HrmParser.parseHeartRateMeasurement(byteArrayOf(0x01, 148.toByte(), 0x00))
        assertEquals(148, bpm)
    }

    @Test
    fun `флаги sensor contact, energy и RR не сдвигают смещение bpm`() {
        // flags=0x16: бит1-2 (sensor contact) + бит3 (energy) + бит4 (RR) — бит0=0 → UINT8.
        // Дополнительные поля идут ПОСЛЕ bpm — значение всё равно в байте 1.
        val payload = byteArrayOf(0x16, 130.toByte(), 0x34, 0x12, 0x20, 0x03)
        assertEquals(130, HrmParser.parseHeartRateMeasurement(payload))
    }

    @Test
    fun `пустой payload - null`() {
        assertNull(HrmParser.parseHeartRateMeasurement(byteArrayOf()))
    }

    @Test
    fun `только flags без значения - null`() {
        assertNull(HrmParser.parseHeartRateMeasurement(byteArrayOf(0x00)))
    }

    @Test
    fun `обрезанный UINT16 - null`() {
        // Заявлен двухбайтовый формат, но второго байта значения нет
        assertNull(HrmParser.parseHeartRateMeasurement(byteArrayOf(0x01, 0x2C)))
    }

    @Test
    fun `нулевой пульс парсится как 0`() {
        // Датчик без контакта с кожей может слать 0 — это валидное значение формата
        assertEquals(0, HrmParser.parseHeartRateMeasurement(byteArrayOf(0x00, 0x00)))
    }
}
