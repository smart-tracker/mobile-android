package com.example.smarttracker.domain.model

/**
 * Тип тренировки.
 * Подгружается с сервера — на текущем этапе используется мок-репозиторий.
 *
 * @param id      уникальный идентификатор типа (из API)
 * @param name    отображаемое название на русском ("Бег", "Ходьба" и т.д.)
 * @param iconKey ключ иконки для UI: "running", "walking", "cycling", "other"
 */
data class WorkoutType(
    val id: Int,
    val name: String,
    val iconKey: String,
)
