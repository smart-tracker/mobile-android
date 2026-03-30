package com.example.smarttracker.presentation.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Общие UI-токены приложения SmartTracker.
 *
 * Здесь хранятся переиспользуемые размеры/параметры интерфейса,
 * чтобы изменять их централизованно в одном месте.
 */
object UiTokens {
    /** Стандартная высота кнопки в auth-flow. */
    val ButtonHeight: Dp = 50.dp

    /** Стандартный радиус скругления полей и кнопок. */
    val CornerRadiusMedium: Dp = 10.dp

    /** Стандартная толщина акцентной рамки/границы. */
    val BorderWidthThick: Dp = 2.dp

    /** Размер индикатора загрузки внутри кнопок. */
    val ButtonLoadingIndicatorSize: Dp = 18.dp

    /** Горизонтальные поля основных экранов. */
    val ScreenHorizontalPadding: Dp = 16.dp

    /** Вертикальный внутренний отступ контент-областей шагов. */
    val ContentVerticalPadding: Dp = 8.dp

    /** Отступ секции между крупными блоками. */
    val SectionSpacing: Dp = 16.dp

    /** Верхний отступ перед заголовком шага в пошаговых auth-экранах. */
    val StepTopSpacer: Dp = 24.dp

    /** Отступ контейнера основной кнопки внизу (горизонталь). */
    val BottomActionHorizontalPadding: Dp = 16.dp

    /** Отступ контейнера основной кнопки внизу (вертикаль). */
    val BottomActionVerticalPadding: Dp = 36.dp

    /** Отступ сверху для inline-ошибок у полей. */
    val InlineErrorTopPadding: Dp = 8.dp

    /** Отступ слева для inline-ошибок у полей. */
    val InlineErrorStartPadding: Dp = 32.dp
}
