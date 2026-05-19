package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.workout.activityIconRes

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
            Text(
                text = "Нет тренировок за этот день",
                style = WorkoutTextStyles.screenHeaderDate,
                modifier = Modifier
                    .padding(16.dp)
                    .background(Color.White),
            )
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
