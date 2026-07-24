package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
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
 * Недельный вид истории — бесконечный скролл по неделям (сверху текущая, вниз до
 * первой тренировки). Каждая неделя: [PeriodHeader] (диапазон дат) + 7 строк-дней
 * (Пн–Вс; день без тренировок — только нод и метка даты).
 * Тап по карточке дня → [onDaySelected] (переход в Day view).
 */
@Composable
internal fun WeekTimelineView(
    state: TrainingHistoryUiState,
    onDaySelected: (LocalDate) -> Unit,
    onVisiblePeriodChanged: (LocalDate) -> Unit = {},
) {
    val today = LocalDate.now()
    val currentWeekStart = today.with(DayOfWeek.MONDAY)
    val firstDate = state.items.minOfOrNull { it.date } ?: today
    val count = periodCount(HistoryViewMode.WEEK, firstDate, today)
    val itemsByDate = remember(state.items) { state.items.groupBy { it.date } }
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedDate) {
        val idx = periodIndexOf(HistoryViewMode.WEEK, state.selectedDate, today)
            .coerceIn(0, count - 1)
        listState.scrollToItem(idx)
    }

    // Верхняя видимая неделя → дата в шапке экрана (обычный порядок: firstVisible = верх).
    val topWeek by remember(count) {
        derivedStateOf {
            periodStartAt(HistoryViewMode.WEEK, today, listState.firstVisibleItemIndex.coerceIn(0, count - 1))
        }
    }
    LaunchedEffect(topWeek) { onVisiblePeriodChanged(topWeek) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        flingBehavior = rememberSnappyFling(),
    ) {
        items(count = count) { i ->
            val weekStart = periodStartAt(HistoryViewMode.WEEK, today, i)
            Column {
                PeriodHeader(
                    label = weekHeaderLabel(weekStart),
                    isCurrent = weekStart == currentWeekStart,
                )
                (0..6).forEach { d ->
                    val day = weekStart.plusDays(d.toLong())
                    WeekDayRow(
                        day = day,
                        dayItems = itemsByDate[day].orEmpty(),
                        isCardRight = day.dayOfWeek.value % 2 == 0,
                        isCurrent = day == today,
                        onDaySelected = onDaySelected,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
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
            InfoRow(R.drawable.ic_samples,  formatTrainingCount(dayItems.size))
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

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "История — неделя")
@Composable
private fun WeekTimelineViewPreview() {
    SmartTrackerTheme {
        WeekTimelineView(state = previewHistoryState(HistoryViewMode.WEEK), onDaySelected = {})
    }
}
