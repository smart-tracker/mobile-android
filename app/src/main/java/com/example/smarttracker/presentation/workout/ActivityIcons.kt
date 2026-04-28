package com.example.smarttracker.presentation.workout

import com.example.smarttracker.R

/**
 * Маппинг `iconKey` (= `type_activ_id` в строковом виде) → drawable resource id
 * для иконки активности.
 *
 * Используется как fallback, когда серверная иконка не доступна (`iconFile == null`
 * **и** `imageUrl == null`). Единая точка для start- и summary-пакетов, чтобы при
 * добавлении новых типов активностей не править два разных файла.
 *
 * Текущий список API (`GET /training/types_activity`):
 *  1  — Бег
 *  2  — Северная ходьба
 *  3  — Велосипед
 *  4  — Силовая
 *  5  — Ходьба
 *  6  — Спортивное ориентирование бегом
 *  7  — Спортивное ориентирование на лыжах
 *  8  — Спортивное ориентирование на велосипеде
 *  9  — Свободное катание на лыжах
 *  10 — Классическое катание на лыжах
 *  11 — Свободное катание на роллерах
 *  12 — Классическое катание на роллерах
 *  13 — Бег на беговой дорожке
 *
 * При добавлении новой иконки — дополнить только этот `when`.
 */
internal fun activityIconRes(key: String): Int = when (key) {
    "1"  -> R.drawable.ic_activity_running
    "2"  -> R.drawable.ic_activity_north_walking
    "3"  -> R.drawable.ic_activity_cycling
    "5"  -> R.drawable.ic_activity_walking
    else -> R.drawable.placeholder
}
