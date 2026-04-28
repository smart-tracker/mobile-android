package com.example.smarttracker.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Текстовые стили для экранов модуля workout (start/summary).
 *
 * Вынесены в отдельный объект, а не в `MaterialTheme.typography`, потому что
 * material-имена (titleMedium, headlineSmall и т.д.) для этих сочетаний
 * семантически невыразительны — здесь имена соответствуют конкретному UI-блоку.
 *
 * Цвет (`ColorPrimary` в большинстве случаев) включён в стиль, чтобы не
 * повторять его в каждом `Text(...)`. Если на конкретном элементе нужен другой
 * цвет — переопределяется параметром `color = ...` у `Text`.
 *
 * Все стили используют семейство `geologicaFontFamily` — единственный шрифт
 * приложения. Italic-варианты построены поверх того же семейства через
 * `fontStyle = FontStyle.Italic` (а не отдельным семейством, потому что
 * Compose сам подбирает нужный файл шрифта по `FontStyle` через FontFamily).
 */
object WorkoutTextStyles {

    // ── Таймер активной тренировки ──────────────────────────────────────────

    /** Большой таймер "HH:MM:SS" — 32sp Bold. */
    val timer = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = ColorPrimary,
    )

    /** Подпись «Длительность» под таймером — 14sp Thin Italic. */
    val timerLabel = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Thin,
        fontStyle = FontStyle.Italic,
        fontSize = 14.sp,
    )

    // ── Ряд статистики на active-экране (StatItem) ──────────────────────────

    /** Значение в StatItem (дистанция/темп/калории) — 20sp Bold. */
    val statValue = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = ColorPrimary,
    )

    /** Подпись под значением StatItem — 10sp Thin Italic. */
    val statLabel = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Thin,
        fontStyle = FontStyle.Italic,
        fontSize = 10.sp,
    )

    // ── Карточки статистики на summary-экране (StatCard) ────────────────────

    /** Значение в StatCard — 20sp Bold Italic. */
    val statCardValue = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 20.sp,
        color = ColorPrimary,
    )

    /** Подпись под значением StatCard — 10sp Light Italic. */
    val statCardLabel = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Light,
        fontStyle = FontStyle.Italic,
        fontSize = 10.sp,
        color = ColorPrimary,
    )

    // ── Шапка экрана ─────────────────────────────────────────────────────────

    /** Дата по центру в шапке — 16sp Normal. */
    val screenHeaderDate = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = ColorPrimary,
    )

    // ── Блок активности на summary-экране ───────────────────────────────────

    /** Название активности рядом с иконкой — 20sp Normal. */
    val activityName = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        color = ColorPrimary,
    )

    /** Темп под названием активности — 16sp Normal Italic. */
    val activityPace = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp,
    )

    // ── Карточка статистики поверх карты в полноэкранном режиме ─────────────

    /** Значения в StatsOverlayCard — 14sp Normal. */
    val statsOverlayValue = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = ColorPrimary,
    )

    // ── Шторка выбора активности (ModalBottomSheet) ─────────────────────────

    /** Название активности в списке шторки — 14sp Normal. */
    val activityListItem = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = ColorPrimary,
    )

    // ── Кнопки на active-экране (Начать / Пауза / Завершить) ────────────────

    /** Подпись на основной кнопке (например, «Начать тренировку») — 20sp Light. */
    val primaryButtonLabel = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
    )
}
