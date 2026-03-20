package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.RoleResponse
import com.google.gson.annotations.SerializedName

/**
 * МОБ-6.3 — DTO для ответа API endpoint GET /role/.
 *
 * Загрузка доступных ролей (любые роли, которые может выбрать пользователь).
 * Используется на экране регистрации Step 2 для динамического заполнения 
 * списка целей/ролей.
 *
 * Маппинг полей API → DTO:
 * - role_id (Int) → id (через @SerializedName)
 * - name (String) → name (например "sportsman", "trainer", "club_organizer")
 * - description (String, optional) → description (дополнительная информация)
 *
 * @param id Уникальный идентификатор роли (приходит как role_id в JSON)
 * @param name Название роли для отображения в UI
 * @param description Описание роли для пользователя (опциональное поле)
 */
data class RoleResponseDto(
    @SerializedName("role_id")
    val id: Int,
    val name: String,
    val description: String? = null
)

/**
 * Маппер DTO → Domain модель.
 * Преобразует RoleResponseDto (из API GET /role/) в RoleResponse (для бизнес-логики).
 */
fun RoleResponseDto.toDomain(): RoleResponse = RoleResponse(
    id = id,
    name = name,
    description = description ?: "$name роль"  // Дефолт если не отправлено
)
