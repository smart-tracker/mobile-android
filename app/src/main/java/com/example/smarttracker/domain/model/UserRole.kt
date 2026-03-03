package com.example.smarttracker.domain.model

enum class UserRole {
    /** Спортсмен — цель ATHLETE */
    ATHLETE,

    /** Тренер — цель TRAINER */
    TRAINER,

    /** Владелец клуба — цель CLUB_OWNER */
    CLUB_OWNER,

    /** Обычный пользователь — цель EXPLORING или OTHER */
    USER
}

