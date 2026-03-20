package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.GoalResponse
import com.google.gson.annotations.SerializedName

/**
 * МОБ-6 — DTO для ответа API endpoint GET /goal/.
 *
 * Загрузка целей для выбора на этапе регистрации (Step 2).
 * Каждая цель автоматически привязана к роли (id_role).
 * 
 * Маппинг полей API → DTO:
 * - goal_id (Int) → id (уникальный идентификатор цели)
 * - description (String) → description (описание цели)
 * - id_role (Int) → roleId (роль пользователя для этой цели)
 *
 * @param id Уникальный идентификатор цели (goal_id в JSON)
 * @param description Описание цели (что пользователь получит от приложения)
 * @param roleId Идентификатор роли (id_role в JSON)
 */
data class GoalResponseDto(
    @SerializedName("goal_id")
    val id: Int,
    val description: String,
    @SerializedName("id_role")
    val roleId: Int
)

/**
 * Маппер DTO → Domain модель.
 * Преобразует GoalResponseDto (из API GET /goal/) в GoalResponse (для бизнес-логики).
 */
fun GoalResponseDto.toDomain(): GoalResponse = GoalResponse(
    id = id,
    description = description,
    roleId = roleId
)
