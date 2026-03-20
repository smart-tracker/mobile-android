package com.example.smarttracker.domain.model

/**
 * МОБ-6 — Domain модель для цели регистрации (Step 2).
 *
 * На этапе регистрации пользователь выбирает цель.
 * Каждая цель автоматически привязана к роли (через roleId).
 *
 * Пример:
 * - Goal(id=1, description="Я тренируюсь...", roleId=1) → SPORTSMAN
 * - Goal(id=2, description="Я тренирую...", roleId=2) → TRAINER
 * - Goal(id=3, description="Я организатор...", roleId=3) → CLUB_ORGANIZER
 *
 * @param id Уникальный идентификатор цели (goal_id из API)
 * @param description Описание цели (что пользователь получит от приложения)
 * @param roleId Идентификатор роли (автоматически определяется из id_role)
 */
data class GoalResponse(
    val id: Int,
    val description: String,
    val roleId: Int
)
