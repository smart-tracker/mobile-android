package com.example.smarttracker.domain.model

/**
 * МОБ-6.2 — Роль пользователя в приложении.
 *
 * После регистрации пользователь имеет список ролей (может быть несколько).
 * Каждая роль определяет:
 * - Какие экраны видны в BottomNavigation (RoleConfig.getNavigationConfig)
 * - Какой интерфейс показывается на HomeScreen и других экранах
 *
 * Соответствие с БД (таблица roles):
 * - roleId = 1: "sportsman" (ATHLETE) — спортсмен
 * - roleId = 2: "trainer" (TRAINER) — тренер
 * - roleId = 3: "club_organizer" (CLUB_OWNER) — владелец клуба
 *
 * @param roleId Уникальный идентификатор роли
 * @param name Название роли (из БД)
 */
data class Role(
    val roleId: Int,
    val name: String
)
