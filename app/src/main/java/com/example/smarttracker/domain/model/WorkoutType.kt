package com.example.smarttracker.domain.model

import java.io.File

/**
 * Тип тренировки.
 * Загружается с сервера через GET /training/types_activity.
 *
 * @param id       уникальный идентификатор типа (из API)
 * @param name     отображаемое название ("Бег", "Ходьба" и т.д.)
 * @param iconKey  ключ для fallback-иконки из drawable — строковое представление type_activ_id
 *                 (например, "1" = Бег, "2" = Северная ходьба, "3" = Велосипед).
 *                 Маппинг id → drawable находится в iconResForKey() в WorkoutStartScreen.
 * @param iconFile скачанный файл иконки в filesDir; null — иконка ещё не загружена,
 *                 UI покажет drawable по iconKey (или placeholder.png если нет своей иконки)
 */
data class WorkoutType(
    val id: Int,
    val name: String,
    val iconKey: String,
    val iconFile: File? = null,
    /**
     * URL иконки с сервера (image_path из API).
     * Используется как резервная модель для Coil, если iconFile ещё не скачан:
     * Coil загружает изображение по URL и показывает его сразу (без ожидания следующего запуска).
     * Null означает, что бэкенд не вернул URL для этого типа.
     */
    val imageUrl: String? = null,
)
