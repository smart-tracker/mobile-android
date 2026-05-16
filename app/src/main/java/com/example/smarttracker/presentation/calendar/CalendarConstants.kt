package com.example.smarttracker.presentation.calendar

import androidx.compose.ui.graphics.Color
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import java.time.format.DateTimeFormatter

/** Цвет ствола и рамок карточек — ColorPrimary темы. */
internal val TrunkColor = ColorPrimary

/** Цвет активного нода и меток текущего периода — ColorSecondary темы. */
internal val TealAccent = ColorSecondary

internal val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
internal val DateShortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

/** Цвет полоски активности по идентификатору типа. */
internal fun activityColorFor(id: Int): Color = when (id) {
    1    -> Color(0xFFF44336)
    2    -> Color(0xFF2AC3B8)
    3    -> Color(0xFF00BFFF)
    4    -> Color(0xFF4CAF50)
    5    -> Color(0xFF2196F3)
    else -> Color(0xFF9E9E9E)
}
