package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Недельный вид истории тренировок.
 * Один нод = один день недели (7 нодов, Пн–Вс).
 * Дни без тренировок: только нод и метка даты.
 * Тап по строке → [onDaySelected] (переход в Day view).
 */
@Composable
internal fun WeekTimelineView(
    state: TrainingHistoryUiState,
    onDaySelected: (LocalDate) -> Unit,
) {
    val weekStart = state.selectedDate.with(DayOfWeek.MONDAY)
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val today = LocalDate.now()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(weekDays) { _, day ->
            WeekDayRow(
                day = day,
                dayItems = state.items.filter { it.date == day },
                isCardRight = day.dayOfWeek.value % 2 == 0,
                isCurrent = day == today,
                onDaySelected = onDaySelected,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
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
        modifier = Modifier
            .height(if (dayItems.isEmpty()) 60.dp else 100.dp)
            .clickable { onDaySelected(day) },
        card = if (dayItems.isNotEmpty()) {
            { WeekDayCard(dayItems = dayItems, isCardRight = isCardRight) }
        } else null,
    )
}

/**
 * Карточка агрегированных данных за день (Week view).
 * Показывает: длительность / дистанция / ккал / количество тренировок.
 */
@Composable
private fun WeekDayCard(dayItems: List<TrainingHistoryItem>, isCardRight: Boolean) {
    val shape = if (isCardRight) {
        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
    } else {
        RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
    }
    val typeIds = dayItems.map { it.typeActivId }
    val totalSecs = totalDurationSeconds(dayItems)
    val totalDist = dayItems.mapNotNull { it.distanceM }.sum().takeIf { it > 0.0 }
    val totalKcal = dayItems.mapNotNull { it.kilocalories }.sum().takeIf { it > 0.0 }

    Row(
        modifier = Modifier
            .height(90.dp)
            .background(Color.White, shape)
            .border(1.dp, TrunkColor, shape)
            .clip(shape),
    ) {
        if (!isCardRight) MultiActivityStrip(typeIds, 90)
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .weight(1f),
        ) {
            InfoRow(R.drawable.ic_time, formatSeconds(totalSecs))
            InfoRow(R.drawable.ic_distance, formatDistanceM(totalDist))
            InfoRow(R.drawable.ic_kcal, formatKcal(totalKcal))
            Text(
                text = "Тр. - ${dayItems.size}",
                fontSize = 12.sp,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Normal,
                color = TrunkColor,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
        if (isCardRight) MultiActivityStrip(typeIds, 90)
    }
}
