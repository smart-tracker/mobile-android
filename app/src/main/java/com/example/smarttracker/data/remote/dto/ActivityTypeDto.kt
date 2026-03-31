package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO ответа GET /training/types_activity.
 *
 * Поля JSON → DTO:
 * - type_activ_id (Int)     → id
 * - name (String)           → name
 * - image_url (String|null) → imageUrl  ← бэкенд добавит позже; пока всегда null
 *
 * @param id       уникальный идентификатор типа активности
 * @param name     название типа (язык зависит от бэкенда)
 * @param imageUrl полный URL иконки для скачивания; null — иконка не назначена
 */
data class ActivityTypeDto(
    @SerializedName("type_activ_id")
    val id: Int,
    val name: String,
    @SerializedName("image_path")
    val imagePath: String? = null,
)

/**
 * Ключ для маппинга на локальный drawable — строковое представление type_activ_id.
 *
 * Используем ID, а не имя: ID стабилен, имя может измениться или быть на любом языке.
 * Маппинг "ID → drawable" живёт в iconResForKey() в WorkoutStartScreen.
 * Если для данного ID нет drawable — iconResForKey вернёт ic_activity_other (placeholder).
 */
fun ActivityTypeDto.toIconKey(): String = id.toString()
