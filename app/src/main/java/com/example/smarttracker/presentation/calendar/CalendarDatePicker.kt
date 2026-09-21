package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import java.time.LocalDate

/**
 * Выбор даты в шапке истории — адаптивный под текущий режим:
 *  - DAY   → Material-календарь (выбор дня);
 *  - WEEK  → список недель (диапазоны «20.07 – 26.07.2026»);
 *  - MONTH → сетка месяцев + переключение года.
 *
 * Результат — [onPick] с любой датой внутри периода; ViewModel.jumpToDate ставит
 * её якорем прокрутки, view нормализует к своему периоду и листает ленту.
 * Диапазон ограничен `[firstDate … today]` — за его пределами данных нет.
 */
@Composable
internal fun HistoryDatePickerDialog(
    viewMode: HistoryViewMode,
    currentDate: LocalDate,
    firstDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    when (viewMode) {
        HistoryViewMode.DAY -> DayPickerDialog(currentDate, firstDate, today, onDismiss, onPick)
        HistoryViewMode.WEEK -> WeekPickerDialog(firstDate, today, onDismiss, onPick)
        HistoryViewMode.MONTH -> MonthYearPickerDialog(currentDate, firstDate, today, onDismiss, onPick)
    }
}

// ── День: кастомная сетка-календарь (в стиле остальных пикеров) ──────────────

private val ShortWeekDays = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
private fun DayPickerDialog(
    currentDate: LocalDate,
    firstDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    var monthStart by remember { mutableStateOf(currentDate.withDayOfMonth(1)) }
    val firstMonth = firstDate.withDayOfMonth(1)
    val lastMonth = today.withDayOfMonth(1)

    PickerScaffold(title = "Выберите день", onDismiss = onDismiss) {
        // Переключатель месяца.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val canPrev = monthStart.isAfter(firstMonth)
            val canNext = monthStart.isBefore(lastMonth)
            IconButton(onClick = { if (canPrev) monthStart = monthStart.minusMonths(1) }, enabled = canPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Предыдущий месяц",
                    tint = if (canPrev) ColorPrimary else ColorPrimary.copy(alpha = 0.3f))
            }
            Text(
                text = monthHeaderLabel(monthStart),
                color = ColorPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(150.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { if (canNext) monthStart = monthStart.plusMonths(1) }, enabled = canNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Следующий месяц",
                    tint = if (canNext) ColorPrimary else ColorPrimary.copy(alpha = 0.3f))
            }
        }
        Spacer(Modifier.size(4.dp))
        // Заголовки дней недели.
        Row(modifier = Modifier.fillMaxWidth()) {
            ShortWeekDays.forEach { d ->
                Text(
                    text = d,
                    color = ColorPrimary.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        // Сетка дней: пустые ячейки до первого дня недели месяца (Пн=1), затем дни.
        val firstDow = monthStart.dayOfWeek.value // 1..7
        val daysInMonth = monthStart.lengthOfMonth()
        val cells: List<LocalDate?> =
            List(firstDow - 1) { null } + (1..daysInMonth).map { monthStart.withDayOfMonth(it) }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val enabled = !day.isBefore(firstDate) && !day.isAfter(today)
                        DayCell(
                            day = day.dayOfMonth,
                            enabled = enabled,
                            isSelected = day == currentDate,
                            isToday = day == today,
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(day); onDismiss() },
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    enabled: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (isSelected) ColorSecondary else Color.White)
                .then(
                    if (isToday && !isSelected) Modifier.border(1.dp, ColorSecondary, RoundedCornerShape(percent = 50))
                    else Modifier
                )
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                color = when {
                    isSelected -> Color.White
                    enabled -> ColorPrimary
                    else -> ColorPrimary.copy(alpha = 0.25f)
                },
                fontSize = 15.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

// ── Неделя: список диапазонов ────────────────────────────────────────────────

@Composable
private fun WeekPickerDialog(
    firstDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val weeks = remember(firstDate, today) {
        val count = periodCount(HistoryViewMode.WEEK, firstDate, today)
        (0 until count).map { periodStartAt(HistoryViewMode.WEEK, today, it) }
    }
    PickerScaffold(title = "Выберите неделю", onDismiss = onDismiss) {
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(weeks) { weekStart ->
                Text(
                    text = weekHeaderLabel(weekStart),
                    color = ColorPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(weekStart); onDismiss() }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                )
            }
        }
    }
}

// ── Месяц: сетка месяцев + год ───────────────────────────────────────────────

private val ShortMonths = arrayOf(
    "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
)

@Composable
private fun MonthYearPickerDialog(
    currentDate: LocalDate,
    firstDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    var year by remember { mutableIntStateOf(currentDate.year) }
    val minYear = firstDate.year
    val maxYear = today.year
    val firstMonth = firstDate.withDayOfMonth(1)
    val lastMonth = today.withDayOfMonth(1)

    PickerScaffold(title = "Выберите месяц", onDismiss = onDismiss) {
        // Переключатель года.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = { if (year > minYear) year-- }, enabled = year > minYear) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Предыдущий год",
                    tint = if (year > minYear) ColorPrimary else ColorPrimary.copy(alpha = 0.3f))
            }
            Text(
                text = year.toString(),
                color = ColorPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(80.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { if (year < maxYear) year++ }, enabled = year < maxYear) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Следующий год",
                    tint = if (year < maxYear) ColorPrimary else ColorPrimary.copy(alpha = 0.3f))
            }
        }
        Spacer(Modifier.size(8.dp))
        // Сетка 3×4 месяцев.
        for (rowIdx in 0 until 4) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (colIdx in 0 until 3) {
                    val month = rowIdx * 3 + colIdx + 1
                    val monthStart = LocalDate.of(year, month, 1)
                    val enabled = !monthStart.isBefore(firstMonth) && !monthStart.isAfter(lastMonth)
                    val isCurrent = monthStart == currentDate.withDayOfMonth(1)
                    MonthCell(
                        label = ShortMonths[month - 1],
                        enabled = enabled,
                        isCurrent = isCurrent,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(monthStart); onDismiss() },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun MonthCell(
    label: String,
    enabled: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) ColorSecondary else Color.White)
            .border(1.dp, if (isCurrent) ColorSecondary else ColorPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                isCurrent -> Color.White
                enabled -> ColorPrimary
                else -> ColorPrimary.copy(alpha = 0.3f)
            },
            fontSize = 15.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ── Общий каркас кастомного диалога (неделя/месяц) ───────────────────────────

@Composable
private fun PickerScaffold(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    color = ColorPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(12.dp))
                content()
                Spacer(Modifier.size(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = ColorPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
