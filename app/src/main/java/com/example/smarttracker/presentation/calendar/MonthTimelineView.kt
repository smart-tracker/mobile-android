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
 * Месячный вид истории тренировок.
 * Один нод = одна неделя (Пн–Вс). Нодов обычно 4–5.
 * Недели без тренировок: только нод и метка диапазона дат.
 * Тап по строке → [onWeekSelected] (переход в Week view).
 */
@Composable
internal fun MonthTimelineView(
    state: TrainingHistoryUiState,
    onWeekSelected: (LocalDate) -> Unit,
) {
    val monthStart = state.selectedDate.withDayOfMonth(1)
    val weeks = generateWeeksForMonth(monthStart)
    val currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
        item { Spacer(Modifier.height(16.dp)) }
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
        modifier = Modifier
            .height(if (weekItems.isEmpty()) 60.dp else 120.dp)
            .clickable { onWeekSelected(weekStart) },
        card = if (weekItems.isNotEmpty()) {
            { MonthWeekCard(weekItems = weekItems, isCardRight = isCardRight) }
        } else null,
    )
}

/**
 * Карточка агрегированных данных за неделю (Month view).
 * Показывает: количество тренировок (Bold) / длительность / дистанция / ккал.
 */
@Composable
private fun MonthWeekCard(weekItems: List<TrainingHistoryItem>, isCardRight: Boolean) {
    val shape = if (isCardRight) {
        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
    } else {
        RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
    }
    val typeIds = weekItems.map { it.typeActivId }
    val totalSecs = totalDurationSeconds(weekItems)
    val totalDist = weekItems.mapNotNull { it.distanceM }.sum().takeIf { it > 0.0 }
    val totalKcal = weekItems.mapNotNull { it.kilocalories }.sum().takeIf { it > 0.0 }

    Row(
        modifier = Modifier
            .height(110.dp)
            .background(Color.White, shape)
            .border(1.dp, TrunkColor, shape)
            .clip(shape),
    ) {
        if (!isCardRight) MultiActivityStrip(typeIds, 110)
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .weight(1f),
        ) {
            Text(
                text = "Тр. - ${weekItems.size}",
                fontSize = 12.sp,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Bold,
                color = TrunkColor,
                modifier = Modifier.padding(vertical = 1.dp),
            )
            InfoRow(R.drawable.ic_time, formatSeconds(totalSecs))
            InfoRow(R.drawable.ic_distance, formatDistanceM(totalDist))
            InfoRow(R.drawable.ic_kcal, formatKcal(totalKcal))
        }
        if (isCardRight) MultiActivityStrip(typeIds, 110)
    }
}
