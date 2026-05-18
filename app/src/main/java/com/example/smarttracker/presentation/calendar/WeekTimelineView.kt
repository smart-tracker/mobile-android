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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.presentation.workout.activityIconRes
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Недельный вид истории тренировок.
 * Один нод = один день недели (7 нодов, Пн–Вс).
 * Дни без тренировок: только нод и метка даты (без карточки).
 * Тап по карточке → [onDaySelected] (переход в Day view).
 */
@Composable
internal fun WeekTimelineView(
    state: TrainingHistoryUiState,
    onDaySelected: (LocalDate) -> Unit,
) {
    val weekStart = state.selectedDate.with(DayOfWeek.MONDAY)
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val today = LocalDate.now()

    // SpaceEvenly: 7 строк равномерно на всю доступную высоту.
    // Если на маленьком экране строки не влезают — LazyColumn скроллится.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        itemsIndexed(weekDays) { _, day ->
            WeekDayRow(
                day = day,
                dayItems = state.items.filter { it.date == day },
                isCardRight = day.dayOfWeek.value % 2 == 0,
                isCurrent = day == today,
                onDaySelected = onDaySelected,
            )
        }
    }
}

@Composable
private fun WeekDayRow(
    day: LocalDate,
    dayItems: List<TrainingHistoryItem>,
    isCardRight: Boolean,
    isCurrent: Boolean,
    onDaySelected: (LocalDate) -> Unit,
) {
    TimelineRow(
        isCardRight = isCardRight,
        isCurrent = isCurrent,
        label = day.format(DateShortFmt),
        modifier = Modifier.height(WeekRowHeight),
        card = if (dayItems.isNotEmpty()) ({
            TimelineCardWrapper(isCardRight = isCardRight, onClick = { onDaySelected(day) }) {
                WeekDayCard(dayItems = dayItems)
            }
        }) else null,
    )
}

/**
 * Карточка агрегированных данных за день (Week view).
 *
 * [WeekActivityStrip] (слева) + [TimelineInfoColumn] (справа).
 * Инфо: 4 строки — длительность / дистанция / ккал / кол-во тренировок.
 */
@Composable
private fun WeekDayCard(dayItems: List<TrainingHistoryItem>) {
    val totals = aggregateTotals(dayItems)
    // Первые 3 тренировки в хронологическом порядке (как в DayTimelineView) —
    // сортируем по timeStart, чтобы Week-превью совпадало с порядком Day view.
    val stripIconIds = dayItems.sortedBy { it.timeStart }.take(3).map { it.typeActivId }

    Row(modifier = Modifier.height(WeekCardHeight)) {
        WeekActivityStrip(iconIds = stripIconIds)

        TimelineInfoColumn(
            modifier = Modifier
                .width(TimelineDims.InfoCardWidth)
                .height(WeekCardHeight),
        ) {
            InfoRow(R.drawable.ic_time,     formatSeconds(totals.seconds))
            InfoRow(R.drawable.ic_distance, formatDistanceM(totals.distanceM))
            InfoRow(R.drawable.ic_kcal,     formatKcal(totals.kilocalories))
            InfoRow(R.drawable.ic_samples,  "Тр. - ${dayItems.size}")
        }
    }
}

/**
 * Стрип с иконками активностей (недельный вид).
 *
 * Белый фон, рамка [TrunkColor], скругление слева ([TimelineStripShape]).
 * До трёх иконок (первые тренировки дня) — без фоновой подсветки.
 */
@Composable
private fun WeekActivityStrip(iconIds: List<Int>) {
    Column(
        modifier = Modifier
            .width(WeekStripWidth)
            .height(WeekCardHeight)
            .timelineCardSurface(TimelineStripShape)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        iconIds.forEach { id ->
            TimelineIconBox(
                iconRes = activityIconRes(id.toString()),
                bgColor = Color.White,
                boxSize = WeekStripIconSize,
            )
        }
    }
}

// ── Размеры Week-карточки ────────────────────────────────────────────────────

private val WeekRowHeight = 110.dp
private val WeekCardHeight = 110.dp
private val WeekStripWidth = 36.dp
private val WeekStripIconSize = 26.dp
