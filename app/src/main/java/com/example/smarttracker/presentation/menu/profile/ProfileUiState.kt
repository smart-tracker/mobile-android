package com.example.smarttracker.presentation.menu.profile

/**
 * UI-состояние экрана профиля.
 *
 * Все данные приведены к строкам, готовым к отображению в [ProfileScreen]:
 * - null в строковых полях означает «не заполнено» → поле покажет «Не указано».
 * - [birthDate] форматируется в ViewModel как "ДД.ММ.ГГГГ".
 * - [weight] / [height] форматируются как целые числа ("72", "178") или null.
 * - [gender] — "Мужской" / "Женский" (локализация в ViewModel).
 */
data class ProfileUiState(
    val isLoading: Boolean = true,

    /** Имя — всегда заполнено после загрузки, используется как заголовок аватара. */
    val firstName: String = "",

    /** Фамилия — необязательное поле, null → «Не указано». */
    val lastName: String? = null,

    /** Отчество — необязательное поле, null → «Не указано». */
    val middleName: String? = null,

    /** Никнейм с символом «@», например «@john_doe». */
    val username: String = "",

    /** Дата рождения в формате «ДД.ММ.ГГГГ». */
    val birthDate: String = "",

    /** Пол: «Мужской» или «Женский». */
    val gender: String = "",

    /** Вес в кг как целое число, например «72». null → «Не указано». */
    val weight: String? = null,

    /** Рост в см как целое число, например «178». null → «Не указано». */
    val height: String? = null,

    /** URL фото профиля (из image_path бэкенда). null до первой загрузки данных. */
    val photoUrl: String? = null,

    /**
     * Версия фото — увеличивается при каждом обновлении профиля.
     * Используется как часть ключа Coil-кэша, чтобы гарантировать загрузку
     * свежего фото даже если URL не изменился (сервер заменяет файл по тому же пути).
     */
    val photoKey: Long = 0L,

    /** Дата последней тренировки в формате "ДД.ММ.ГГГГ" или null если нет тренировок. */
    val lastTrainingDate: String? = null,
    /** Текст ошибки при неудачной загрузке. null → ошибки нет. */
    val errorMessage: String? = null,
)
