package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.Role
import com.google.gson.annotations.SerializedName

/**
 * DTO для ответа API endpoint GET /role/ и GET /role/user_roles.
 *
 * МОБ-6.2 — Загрузка ролей пользователя для динамической навигации.
 *
 * Маппинг полей API → DTO:
 * - role_id (Int) → roleId
 * - name (String) → name (например "sportsman", "trainer", "club_organizer")
 *
 * @param roleId Идентификатор роли (1=ATHLETE, 2=TRAINER, 3=CLUB_OWNER)
 * @param name Название роли (используется для отладки, не отображается в UI)
 */
data class RoleDto(
    @SerializedName("role_id")
    val roleId: Int,
    val name: String
)

/**
 * Маппер DTO → Domain модель.
 * Преобразует RoleDto (из API) в Role (для использования в бизнес-логике).
 */
fun RoleDto.toDomain(): Role = Role(
    roleId = roleId,
    name = name
)
