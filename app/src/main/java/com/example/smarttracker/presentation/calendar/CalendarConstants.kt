package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

// ── Размеры карточек таймлайна ───────────────────────────────────────────────

/**
 * Общие размеры для карточек таймлайна (Day/Week/Month).
 * Меняй централизованно — изменения подхватятся во всех трёх view.
 */
internal object TimelineDims {
    /** Ширина ствола. */
    val TrunkWidth: Dp = 16.dp

    /** Ширина области с нодом в [TimelineRow]. */
    val NodeColumnWidth: Dp = 32.dp

    /** Радиус скругления внешних углов карточек. */
    val CornerRadius: Dp = 10.dp

    /** Толщина рамок стрипа и инфо-блока. */
    val BorderThickness: Dp = 1.dp

    /** Зазор между карточкой и стволом дерева. */
    val TrunkGap: Dp = 12.dp

    /** Стандартная ширина инфо-блока. */
    val InfoCardWidth: Dp = 120.dp

    /** Горизонтальный паддинг инфо-блока. */
    val InfoPaddingHorizontal: Dp = 8.dp

    /** Вертикальный паддинг инфо-блока. */
    val InfoPaddingVertical: Dp = 4.dp

    /** Радиус скругления квадратных иконок активности на стрипах. */
    val IconBoxCornerRadius: Dp = 5.dp
}

/** Скругление слева — для стрипов карточек (всегда у внешнего края). */
internal val TimelineStripShape = RoundedCornerShape(
    topStart = TimelineDims.CornerRadius,
    bottomStart = TimelineDims.CornerRadius,
)

/** Скругление справа — для инфо-блоков карточек. */
internal val TimelineInfoShape = RoundedCornerShape(
    topEnd = TimelineDims.CornerRadius,
    bottomEnd = TimelineDims.CornerRadius,
)
