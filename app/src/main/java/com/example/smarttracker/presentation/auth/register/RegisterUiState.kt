package com.example.smarttracker.presentation.auth.register

import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.GoalResponse
import com.example.smarttracker.domain.model.RoleResponse
import com.example.smarttracker.domain.model.UserPurpose

/** Статус проверки уникальности никнейма */
sealed class NicknameCheckStatus {
    object IDLE : NicknameCheckStatus()
    object CHECKING : NicknameCheckStatus()
    data class SUCCESS(val message: String) : NicknameCheckStatus()
    data class ERROR(val message: String) : NicknameCheckStatus()
}

/** Статус валидации даты рождения */
sealed class BirthDateCheckStatus {
    object IDLE : BirthDateCheckStatus()
    data class SUCCESS(val message: String) : BirthDateCheckStatus()
    data class ERROR(val message: String) : BirthDateCheckStatus()
}

/**
 * UI-состояние многошагового экрана регистрации.
 * Шаги:
 *  1 — Личные данные (имя, ник, дата рождения, пол)
 *  2 — Цель использования приложения
 *  3 — Безопасность и доступ (почта, пароль)
 *  4 — Подтверждение почты (код верификации)
 */
data class RegisterUiState(

    val step: Int = 1,

    // ── Шаг 1: Личные данные ────────────────────────────────────────────────
    val firstName: String = "",
    val username: String = "",
    val nicknameCheckStatus: NicknameCheckStatus = NicknameCheckStatus.IDLE,
    /** Дата в формате дд.мм.гггг (строка для поля ввода) */
    val birthDate: String = "",
    val birthDateCheckStatus: BirthDateCheckStatus = BirthDateCheckStatus.IDLE,
    val gender: Gender? = null,

    // ── Шаг 2: Цель использования ───────────────────────────────────────────
    val purpose: UserPurpose? = null,

    // ── Шаг 2: Динамические цели (МОБ-6) ────────────────────────────────────
    /** Доступные цели из API (GET /goal/) */
    val availableGoals: List<GoalResponse> = emptyList(),
    /** ID выбранной цели (автоматически определяет роль пользователя) */
    val selectedGoalId: Int? = null,
    /** Loading состояние для загрузки целей на Step 2 */
    val isLoadingGoals: Boolean = false,

    // ── Шаг 3: Безопасность и доступ ────────────────────────────────────────
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val termsAccepted: Boolean = false,

    // ── Шаг 4: Подтверждение почты ──────────────────────────────────────────
    val verificationCode: String = "",
    /** Секунды до разблокировки кнопки "Отправить повторно" */
    val resendCooldownSeconds: Int = 0,

    // ── Общее ────────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    /** Ошибка конкретного поля (показывается под полем) */
    val fieldError: String? = null,
    /** Общая ошибка запроса (показывается внизу формы) */
    val error: String? = null,
)
