package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.workout.activityIconRes
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Месячный вид истории тренировок.
 * Один нод = одна неделя (Пн–Вс). Нодов обычно 4–5.
 * Недели без тренировок: только нод и метка диапазона дат (без карточки).
 * Тап по карточке → [onWeekSelected] (переход в Week view).
 */
@Composable
internal fun MonthTimelineView(
    state: TrainingHistoryUiState,
    onWeekSelected: (LocalDate) -> Unit,
) {
    val monthStart = state.selectedDate.withDayOfMonth(1)
    val weeks = generateWeeksForMonth(monthStart)
    val currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        itemsIndexed(weeks) { index, weekStart ->
            val weekEnd = weekStart.plusDays(6)
            MonthWeekRow(
                weekStart = weekStart,
                weekEnd = weekEnd,
                weekItems = state.items.filter { it.date >= weekStart && it.date <= weekEnd },
                isCardRight = index % 2 != 0,
                isCurrent = weekStart == currentWeekStart,
                onWeekSelected = onWeekSelected,
            )
        }
    }
}

@Composable
private fun MonthWeekRow(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    weekItems: List<TrainingHistoryItem>,
    isCardRight: Boolean,
    isCurrent: Boolean,
    onWeekSelected: (LocalDate) -> Unit,
) {
    val label = "${weekStart.format(DateShortFmt)} - ${weekEnd.format(DateShortFmt)}"
    TimelineRow(
        isCardRight = isCardRight,
        isCurrent = isCurrent,
        label = label,
        modifier = Modifier.height(MonthRowHeight),
        card = if (weekItems.isNotEmpty()) ({
            TimelineCardWrapper(isCardRight = isCardRight, onClick = { onWeekSelected(weekStart) }) {
                MonthWeekCard(weekItems = weekItems, weekStart = weekStart)
            }
        }) else null,
    )
}

/**
 * Карточка агрегированных данных за неделю (Month view).
 *
 * Стрип: 7 иконок (по одной на каждый день Пн–Вс) через [MonthActivityStrip].
 * Инфо (6 строк): "Тр. - N" (Bold) + доминирующий тип / время / расстояние / высота / ккал.
 *
 * Поле «Набор высоты» = "--", т.к. данных нет в [TrainingHistoryItem] (нет в API /training/history).
 */
@Composable
private fun MonthWeekCard(
    weekItems: List<TrainingHistoryItem>,
    weekStart: LocalDate,
) {
    val totals = aggregateTotals(weekItems)
    val dominant = dominantType(weekItems)  // Pair<typeActivId, percent>?

    // 7 иконок: для каждого дня — самая длинная тренировка или null (нет тренировки).
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val dayTypeIds: List<Int?> = weekDays.map { day ->
        longestTypeIdOf(weekItems.filter { it.date == day })
    }

    Row(modifier = Modifier.height(MonthCardHeight)) {
        MonthActivityStrip(dayTypeIds = dayTypeIds)

        TimelineInfoColumn(
            modifier = Modifier
                .width(MonthInfoWidth)
                .height(MonthCardHeight),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = "Тр. - ${weekItems.size}",
                style = WorkoutTextStyles.timelineLabelBold,
                modifier = Modifier.padding(vertical = 1.dp, horizontal = 5.dp),
            )
            dominant?.let { (typeId, pct) ->
                InfoRow(
                    iconRes = activityIconRes(typeId.toString()),
                    value   = "- ${"%.1f".format(pct)}%",
                )
            }
            InfoRow(R.drawable.ic_time,      formatSeconds(totals.seconds))
            InfoRow(R.drawable.ic_distance,  formatDistanceM(totals.distanceM))
            // Набор высоты — серверное поле elevation_gain из /training/history.
            // formatDistanceM подходит идеально: "350 м" / "1.23 км" / "--" при null.
            InfoRow(R.drawable.ic_elevation, formatDistanceM(totals.elevationM))
            InfoRow(R.drawable.ic_kcal,      formatKcal(totals.kilocalories))
        }
    }
}

/**
 * Стрип 7 иконок — по одной на каждый день недели (Пн–Вс).
 *
 * [dayTypeIds] — список из 7 элементов (typeActivId или null = нет тренировки).
 *  - null         → белый фон + [R.drawable.ic_sleep]
 *  - typeActivId  → фон [TealAccent] + иконка активности
 *
 * Размеры (Figma 350:368): width=[MonthStripWidth], padding vertical=10dp/horizontal=4dp,
 * иконки [MonthStripIconSize], расстояния через [Arrangement.SpaceEvenly].
 */
@Composable
private fun MonthActivityStrip(dayTypeIds: List<Int?>) {
    Column(
        modifier = Modifier
            .width(MonthStripWidth)
            .height(MonthCardHeight)
            .timelineCardSurface(TimelineStripShape)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.Start,
    ) {
        dayTypeIds.forEach { typeActivId ->
            TimelineIconBox(
                iconRes = if (typeActivId != null) activityIconRes(typeActivId.toString())
                          else R.drawable.ic_sleep,
                bgColor = if (typeActivId != null) TealAccent else Color.White,
                boxSize = MonthStripIconSize,
            )
        }
    }
}

// ── Размеры Month-карточки ───────────────────────────────────────────────────

private val MonthRowHeight = 160.dp
private val MonthCardHeight = 160.dp
private val MonthStripWidth = 24.dp
private val MonthStripIconSize = 16.dp
private val MonthInfoWidth = 140.dp
