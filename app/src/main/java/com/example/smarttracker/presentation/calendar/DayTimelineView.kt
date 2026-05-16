package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.workout.activityIconRes

/**
 * Дневной вид истории тренировок.
 *
 * Карточки чередуются лево/право от ствола, вся группа вертикально центрируется.
 * Одна тренировка — в центре экрана; N тренировок — стопка с 16dp зазором.
 * Карточка каждой тренировки: полоска 24dp (скругл. слева) + инфо 120dp (скругл. справа).
 * Зазор между карточкой и стволом: 12dp.
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
        modifier = Modifier.height(96.dp),
        card = {
            Box(
                modifier = Modifier.padding(
                    end   = if (!isCardRight) 12.dp else 0.dp,
                    start = if (isCardRight)  12.dp else 0.dp,
                )
            ) {
                DayCard(item = item, activityName = activityName, onClick = onTrainingClick)
            }
        },
    )
}

/**
 * Карточка одной тренировки (Figma: «Лист»).
 *
 * Структура: [полоска 24dp, скругл. tl/bl 10dp] + [инфо 120dp, скругл. tr/br 10dp].
 * На стыке двух отдельных границ образуется видимая линия 2dp.
 * 3 строки 12sp: название активности / длительность / дистанция или ккал.
 */
@Composable
private fun DayCard(item: TrainingHistoryItem, activityName: String, onClick: () -> Unit) {
    val stripShape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
    val infoShape  = RoundedCornerShape(topEnd   = 10.dp, bottomEnd   = 10.dp)

    Row(modifier = Modifier.height(86.dp).clickable(onClick = onClick)) {
        // Полоска: цветной фон, скругление слева, собственная граница
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .border(1.dp, TrunkColor, stripShape)
                .clip(stripShape)
                .background(activityColorFor(item.typeActivId)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(activityIconRes(item.typeActivId.toString())),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
        }

        // Инфо-область: белый фон, скругление справа, собственная граница
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .border(1.dp, TrunkColor, infoShape)
                .clip(infoShape)
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            InfoRow(
                iconRes = R.drawable.ic_samples,
                value = activityName,
                fontSize = 14.sp,
            )
            InfoRow(
                iconRes = R.drawable.ic_time,
                value = formatDurationBetween(item.timeStart, item.timeEnd),
                fontSize = 14.sp,
            )
            if (item.distanceM != null) {
                InfoRow(
                    iconRes = R.drawable.ic_distance,
                    value = formatDistanceM(item.distanceM),
                    fontSize = 14.sp,
                )
            } else {
                InfoRow(
                    iconRes = R.drawable.ic_kcal,
                    value = formatKcal(item.kilocalories),
                    fontSize = 14.sp,
                )
            }
        }
    }
}
