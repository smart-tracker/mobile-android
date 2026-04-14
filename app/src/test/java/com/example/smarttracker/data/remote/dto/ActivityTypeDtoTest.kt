package com.example.smarttracker.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit-тесты ActivityTypeDto и маппера toIconKey().
 *
 * Покрывает:
 * - toIconKey() возвращает id.toString(), а НЕ name
 * - imagePath=null не вызывает краш при маппинге
 *
 * Важно: iconKey должен быть строковым ID, а не именем активности.
 * Имя зависит от языка API и может меняться; ID — стабильный идентификатор.
 */
class ActivityTypeDtoTest {

    @Test
    fun `toIconKey возвращает строковое представление id, а не name`() {
        val dto = ActivityTypeDto(id = 1, name = "Бег", imagePath = null)
        assertEquals("1", dto.toIconKey())
    }

    @Test
    fun `toIconKey не использует name для маппинга`() {
        val dto = ActivityTypeDto(id = 3, name = "Велосипед", imagePath = null)
        // iconKey должен быть "3", а не "Велосипед"
        assertEquals("3", dto.toIconKey())
    }

    @Test
    fun `toIconKey для id=5 (Ходьба) возвращает строку 5`() {
        // id=5 (Ходьба) не имеет drawable в iconResForKey() → fallback на placeholder.
        // Тест фиксирует, что ключ передаётся корректно.
        val dto = ActivityTypeDto(id = 5, name = "Ходьба", imagePath = null)
        assertEquals("5", dto.toIconKey())
    }

    @Test
    fun `imagePath null не вызывает исключений при создании DTO`() {
        val dto = ActivityTypeDto(id = 2, name = "Северная ходьба", imagePath = null)
        assertNull(dto.imagePath)
        assertEquals("2", dto.toIconKey())
    }

    @Test
    fun `imagePath не-null сохраняется в DTO`() {
        val url = "https://runtastic.gottland.ru/icons/run.png"
        val dto = ActivityTypeDto(id = 1, name = "Бег", imagePath = url)
        assertEquals(url, dto.imagePath)
    }
}
