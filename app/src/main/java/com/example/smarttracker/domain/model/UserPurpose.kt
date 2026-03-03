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