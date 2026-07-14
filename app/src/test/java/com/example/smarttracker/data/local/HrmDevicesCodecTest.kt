package com.example.smarttracker.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip тесты сериализации списка пульсометров
 * (encodeHrmDevices / decodeHrmDevices в SettingsStorageImpl).
 */
class HrmDevicesCodecTest {

    @Test
    fun `round-trip списка из нескольких датчиков`() {
        val devices = listOf(
            SavedHrmDevice("AA:BB:CC:DD:EE:FF", "Polar H10"),
            SavedHrmDevice("11:22:33:44:55:66", "Magene H64"),
        )
        assertEquals(devices, decodeHrmDevices(encodeHrmDevices(devices)))
    }

    @Test
    fun `null-имя переживает round-trip`() {
        val devices = listOf(SavedHrmDevice("AA:BB:CC:DD:EE:FF", null))
        assertEquals(devices, decodeHrmDevices(encodeHrmDevices(devices)))
    }

    @Test
    fun `имя с разделителями и кавычками переживает round-trip`() {
        // Причина выбора JSON вместо самодельного формата
        val devices = listOf(SavedHrmDevice("AA:BB", """Weird "HRM"; v|2"""))
        assertEquals(devices, decodeHrmDevices(encodeHrmDevices(devices)))
    }

    @Test
    fun `пустой список - пустой список`() {
        assertEquals(emptyList<SavedHrmDevice>(), decodeHrmDevices(encodeHrmDevices(emptyList())))
    }

    @Test
    fun `битый JSON - пустой список, не краш`() {
        assertTrue(decodeHrmDevices("{corrupted").isEmpty())
        assertTrue(decodeHrmDevices("").isEmpty())
    }
}
