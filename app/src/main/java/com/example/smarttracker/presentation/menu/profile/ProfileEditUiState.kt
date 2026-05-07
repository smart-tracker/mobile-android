package com.example.smarttracker.presentation.menu.profile

/**
 * UI-состояние экрана редактирования профиля.
 *
 * Поля хранятся как строки — напрямую отображаются в текстовых полях.
 * [birthDate] — 8 цифр без разделителей "DDMMYYYY"; точки добавляются
 * визуально через DateVisualTransformation (как в RegisterScreen).
 * [gender] — "male" | "female"; отображается как "Мужской" | "Женский".
 */
data class ProfileEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,

    val firstName: String = "",
    val lastName: String = "",
    val middleName: String = "",

    /** Никнейм без символа «@». */
    val username: String = "",

    /** 8 цифр: "04052004" → отображается "04.05.2004". */
    val birthDate: String = "",

    /** "male" или "female". */
    val gender: String = "male",

    /** Пустая строка = не указано. */
    val height: String = "",

    /** Пустая строка = не указано. */
    val weight: String = "",

    val showDeleteConfirmDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
)
