package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import com.example.smarttracker.presentation.workout.activityIconRes
import java.time.LocalDate

/**
 * Дневной вид истории тренировок.
 *
 * Карточки чередуются лево/право от ствола, вся группа вертикально центрируется.
 * Одна тренировка — в центре экрана; N тренировок — стопка с 16dp зазором.
 * Карточка каждой тренировки: цветная полоска [DayStripWidth] (скругл. слева) +
 * инфо [TimelineDims.InfoCardWidth] (скругл. справа).
 */
@Composable
internal fun DayTimelineView(
    state: TrainingHistoryUiState,
    onTrainingClick: (TrainingHistoryItem, String) -> Unit = { _, _ -> },
) {
    val dayItems = state.items
        .filter { it.date == state.selectedDate }
        .sortedBy { it.timeStart }

    if (dayItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CalendarEmptyBlock(text = "Нет тренировок за этот день")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = if (dayItems.size <= 4)
                Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            else
                Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            itemsIndexed(dayItems) { index, item ->
                val activityName = state.workoutTypes
                    .find { it.id == item.typeActivId }?.name ?: "—"
                DayRow(
                    item = item,
                    activityName = activityName,
                    isCardRight = index % 2 != 0,
                    onTrainingClick = { onTrainingClick(item, activityName) },
                )
            }
            if (dayItems.size > 4) item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DayRow(
    item: TrainingHistoryItem,
    activityName: String,
    isCardRight: Boolean,
    onTrainingClick: () -> Unit,
) {
    TimelineRow(
        isCardRight = isCardRight,
        isCurrent = false,
        label = formatTimeRange(item.timeStart, item.timeEnd),
        modifier = Modifier.height(DayRowHeight),
        card = {
            TimelineCardWrapper(isCardRight = isCardRight, onClick = onTrainingClick) {
                DayCard(item = item, activityName = activityName)
            }
        },
    )
}

/**
 * Карточка одной тренировки (Figma: «Лист»).
 * Структура: [цветная полоска] + [инфо-блок].
 * 3 строки 14sp: название активности / длительность / дистанция или ккал.
 */
@Composable
private fun DayCard(item: TrainingHistoryItem, activityName: String) {
    Row(modifier = Modifier.height(DayCardHeight)) {
        // Цветная полоска: bg = activityColorFor, одна иконка 20dp по центру.
        Box(
            modifier = Modifier
                .width(DayStripWidth)
                .fillMaxHeight()
                .timelineCardSurface(TimelineStripShape, activityColorFor(item.typeActivId)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(activityIconRes(item.typeActivId.toString())),
                contentDescription = null,
                modifier = Modifier.size(DayStripIconSize),
                tint = Color.Unspecified,
            )
        }

        TimelineInfoColumn(
            modifier = Modifier.width(TimelineDims.InfoCardWidth).fillMaxHeight(),
        ) {
            InfoRow(R.drawable.ic_samples, activityName)
            InfoRow(R.drawable.ic_time,    formatDurationBetween(item.timeStart, item.timeEnd))
            if (item.distanceM != null) {
                InfoRow(R.drawable.ic_distance, formatDistanceM(item.distanceM))
            } else {
                InfoRow(R.drawable.ic_kcal, formatKcal(item.kilocalories))
            }
        }
    }
}

// ── Размеры Day-карточки ─────────────────────────────────────────────────────

private val DayRowHeight = 96.dp
private val DayCardHeight = 86.dp
private val DayStripWidth = 28.dp
private val DayStripIconSize = 20.dp

// ── Preview ──────────────────────────────────────────────────────────────────

/**
 * Общий фейк для превью всех трёх timeline-view (Day/Week/Month).
 * selectedDate = 22.07.2026 (среда): t1 в этот день, t2 днём раньше (та же неделя),
 * t3 в начале месяца — покрывает и день, и неделю, и месяц.
 */
internal fun previewHistoryState(mode: HistoryViewMode = HistoryViewMode.DAY) = TrainingHistoryUiState(
    isLoading = false,
    viewMode = mode,
    selectedDate = LocalDate.of(2026, 7, 22),
    workoutTypes = listOf(
        WorkoutType(id = 1, name = "Бег", iconKey = "1"),
        WorkoutType(id = 3, name = "Велосипед", iconKey = "3"),
    ),
    items = listOf(
        TrainingHistoryItem("t1", 1, LocalDate.of(2026, 7, 22),
            "2026-07-22T08:00:00+00:00", "2026-07-22T08:35:00+00:00", 320.0, 5200.0, 2.6, 24.0),
        TrainingHistoryItem("t2", 3, LocalDate.of(2026, 7, 21),
            "2026-07-21T18:00:00+00:00", "2026-07-21T18:50:00+00:00", 410.0, 15000.0, 5.0, 60.0),
        TrainingHistoryItem("t3", 1, LocalDate.of(2026, 7, 18),
            "2026-07-18T07:00:00+00:00", "2026-07-18T07:40:00+00:00", 300.0, 6000.0, 2.5, 30.0),
    ),
)

@Preview(showBackground = true, name = "История — день")
@Composable
private fun DayTimelineViewPreview() {
    SmartTrackerTheme { DayTimelineView(state = previewHistoryState(HistoryViewMode.DAY)) }
}

@Preview(showBackground = true, name = "История — пустой день")
@Composable
private fun DayTimelineViewEmptyPreview() {
    SmartTrackerTheme {
        DayTimelineView(state = TrainingHistoryUiState(isLoading = false, selectedDate = LocalDate.of(2026, 7, 22)))
    }
}
