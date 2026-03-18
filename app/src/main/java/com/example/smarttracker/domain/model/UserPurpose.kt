package com.example.smarttracker.domain.model

enum class UserPurpose {
    /** Я спортсмен и хочу отслеживать свои тренировки */
    ATHLETE,

    /** Я тренер и хочу создать свой клуб */
    TRAINER,

    /** Я владелец клуба и хочу создать свой клуб */
    CLUB_OWNER,

    /** Я хочу ознакомиться с функционалом приложения */
    EXPLORING,

    /** Ни одна из причин не подходит */
    OTHER
}

/**
 * Маппинг цели использования приложения в роль пользователя.
 * Роль определяет доступный функционал в интерфейсе.
 * В будущем роль можно сменить в настройках профиля.
 */
fun UserPurpose.toUserRole(): UserRole = when (this) {
    UserPurpose.ATHLETE    -> UserRole.ATHLETE
    UserPurpose.TRAINER    -> UserRole.TRAINER
    UserPurpose.CLUB_OWNER -> UserRole.CLUB_OWNER
    UserPurpose.EXPLORING  -> UserRole.USER
    UserPurpose.OTHER      -> UserRole.USER
}

/**
 * Конвертирует цель использования в номер роли для API.
 * Соответствие с таблицей БД (role):
 * - 1 = sportsman (ATHLETE)
 * - 2 = trainer (TRAINER)
 * - 3 = club_organizer (CLUB_OWNER)
 * - EXPLORING и OTHER → роль не отправляется (пользователь просто исследует)
 */
fun UserPurpose.toRoleId(): Int? = when (this) {
    UserPurpose.ATHLETE    -> 1
    UserPurpose.TRAINER    -> 2
    UserPurpose.CLUB_OWNER -> 3
    UserPurpose.EXPLORING  -> null
    UserPurpose.OTHER      -> null
}