package com.example.smarttracker.presentation.calendar

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.workout.activityIconRes
import java.time.LocalDate

/**
 * Строчная раскладка истории ([HistoryLayout.LIST]) — альтернатива «дереву».
 *
 * Тот же бесконечный скролл по периодам (`periodCount`/`periodStartAt`), та же
 * прокрутка по `scrollTick`, но плоский рендер без ствола:
 *  - **День** — заголовок дня + [TrainingRow] на каждую тренировку;
 *  - **Неделя/Месяц** — сворачиваемый блок периода: развёрнут (по умолчанию) —
 *    детальная статистика + тренировки строками; свёрнут — одна строка с итогами.
 *
 * Фильтр по видам применяется ДО агрегации (иначе итоги периода соврут),
 * сортировка — внутри периода; направление [TrainingHistoryUiState.sortAsc]
 * задаёт и порядок самих периодов по времени.
 */
@Composable
internal fun HistoryListView(
    state: TrainingHistoryUiState,
    onTrainingClick: (TrainingHistoryItem, String) -> Unit = { _, _ -> },
    onVisiblePeriodChanged: (LocalDate) -> Unit = {},
) {
    val mode = state.viewMode
    val today = LocalDate.now()
    val firstDate = state.items.minOfOrNull { it.date } ?: today
    val count = periodCount(mode, firstDate, today)
    val listState = rememberLazyListState()

    // Фильтр по видам + группировка — один проход на изменение данных/фильтра.
    val itemsByDate = remember(state.items, state.selectedTypeIds) {
        filterByTypes(state.items, state.selectedTypeIds).groupBy { it.date }
    }

    // Свёрнутые периоды (Неделя/Месяц). Храним epochDay: Set<Long> сериализуем
    // для rememberSaveable → состояние переживает уход с вкладки.
    var collapsed by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    // При sortAsc=true старые периоды сверху → индекс инвертируется.
    fun periodAt(index: Int): LocalDate =
        periodStartAt(mode, today, if (state.sortAsc) count - 1 - index else index)

    fun listIndexOf(date: LocalDate): Int {
        val raw = periodIndexOf(mode, date, today).coerceIn(0, count - 1)
        return if (state.sortAsc) count - 1 - raw else raw
    }

    // Прокрутка только по явной навигации (см. scrollTick в UiState).
    var lastScrollTick by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(state.scrollTick, state.sortAsc) {
        if (state.scrollTick != lastScrollTick) {
            listState.scrollToItem(listIndexOf(state.selectedDate))
            lastScrollTick = state.scrollTick
        }
    }

    // Верхний видимый период → дата в шапке экрана.
    val topPeriod by remember(count, state.sortAsc) {
        derivedStateOf {
            periodAt(listState.firstVisibleItemIndex.coerceIn(0, count - 1))
        }
    }
    LaunchedEffect(topPeriod) { onVisiblePeriodChanged(topPeriod) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        flingBehavior = rememberSnappyFling(),
    ) {
        items(count = count) { index ->
            val periodStart = periodAt(index)
            // Тренировки периода: день — свой список, неделя/месяц — все дни диапазона.
            val periodItems = remember(periodStart, itemsByDate, mode) {
                collectPeriodItems(mode, periodStart, itemsByDate)
            }
            val sorted = remember(periodItems, state.sortBy, state.sortAsc) {
                sortTrainings(periodItems, state.sortBy, state.sortAsc)
            }

            when (mode) {
                HistoryViewMode.DAY -> DayListBlock(
                    day = periodStart,
                    isToday = periodStart == today,
                    items = sorted,
                    workoutTypes = state.workoutTypes,
                    onTrainingClick = onTrainingClick,
                )
                else -> {
                    val key = periodStart.toEpochDay()
                    CollapsiblePeriodBlock(
                        label = if (mode == HistoryViewMode.WEEK) {
                            weekHeaderLabel(periodStart)
                        } else {
                            monthHeaderLabel(periodStart)
                        },
                        isCurrent = periodStart == periodStartOf(mode, today),
                        items = sorted,
                        workoutTypes = state.workoutTypes,
                        collapsed = key in collapsed,
                        onToggle = {
                            collapsed = if (key in collapsed) collapsed - key else collapsed + key
                        },
                        onTrainingClick = onTrainingClick,
                    )
                }
            }
        }
    }
}

/** Тренировки периода из готовой группировки по датам (фильтр уже применён). */
private fun collectPeriodItems(
    mode: HistoryViewMode,
    periodStart: LocalDate,
    itemsByDate: Map<LocalDate, List<TrainingHistoryItem>>,
): List<TrainingHistoryItem> = when (mode) {
    HistoryViewMode.DAY -> itemsByDate[periodStart].orEmpty()
    HistoryViewMode.WEEK -> (0..6).flatMap { itemsByDate[periodStart.plusDays(it.toLong())].orEmpty() }
    HistoryViewMode.MONTH -> (0 until periodStart.lengthOfMonth())
        .flatMap { itemsByDate[periodStart.plusDays(it.toLong())].orEmpty() }
}

// ── День: заголовок + строки тренировок ──────────────────────────────────────

@Composable
private fun DayListBlock(
    day: LocalDate,
    isToday: Boolean,
    items: List<TrainingHistoryItem>,
    workoutTypes: List<WorkoutType>,
    onTrainingClick: (TrainingHistoryItem, String) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = dayHeaderLabel(day),
            color = if (isToday) ColorSecondary else ColorPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        if (items.isEmpty()) {
            EmptyRow()
        } else {
            items.forEach { item ->
                TrainingRow(item, workoutTypes, onTrainingClick)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ── Неделя/Месяц: сворачиваемый блок ─────────────────────────────────────────

@Composable
private fun CollapsiblePeriodBlock(
    label: String,
    isCurrent: Boolean,
    items: List<TrainingHistoryItem>,
    workoutTypes: List<WorkoutType>,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onTrainingClick: (TrainingHistoryItem, String) -> Unit,
) {
    val accent = if (isCurrent) ColorSecondary else ColorPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(Color.White),
    ) {
        // Шапка блока — всегда видна, тап сворачивает/разворачивает.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                // В свёрнутом виде — краткие итоги прямо в строке.
                if (collapsed) {
                    val totals = aggregateTotals(items)
                    Text(
                        text = if (items.isEmpty()) {
                            "Нет тренировок"
                        } else {
                            "${formatTrainingCountFull(items.size)} · " +
                                "${formatSeconds(totals.seconds)} · ${formatDistanceM(totals.distanceM)}"
                        },
                        color = ColorPrimary.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (collapsed) "Развернуть" else "Свернуть",
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }

        AnimatedVisibility(visible = !collapsed) {
            Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                if (items.isEmpty()) {
                    EmptyRow()
                } else {
                    PeriodStatsBlock(items = items, workoutTypes = workoutTypes)
                    Spacer(Modifier.height(10.dp))
                    items.forEach { item ->
                        TrainingRow(item, workoutTypes, onTrainingClick)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ── Детальная статистика периода ─────────────────────────────────────────────

/**
 * Подробные итоги периода: суммы + разбивка по видам активности.
 * Считается по уже отфильтрованному списку [items].
 */
@Composable
private fun PeriodStatsBlock(items: List<TrainingHistoryItem>, workoutTypes: List<WorkoutType>) {
    val totals = aggregateTotals(items)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ColorPrimary.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = formatTrainingCountFull(items.size),
            color = ColorPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        StatLine(R.drawable.ic_time, "Время", formatSeconds(totals.seconds))
        StatLine(R.drawable.ic_distance, "Дистанция", formatDistanceM(totals.distanceM))
        StatLine(R.drawable.ic_kcal, "Калории", formatKcal(totals.kilocalories))
        StatLine(R.drawable.ic_elevation, "Набор высоты", formatDistanceM(totals.elevationM))

        // Разбивка по видам: «Бег — 3, 12,40 км».
        val byType = items.groupBy { it.typeActivId }
        if (byType.size > 1) {
            Spacer(Modifier.height(2.dp))
            byType.entries
                .sortedByDescending { (_, list) -> totalDurationSeconds(list) }
                .forEach { (typeId, list) ->
                    val name = workoutTypes.find { it.id == typeId }?.name ?: "—"
                    val sub = aggregateTotals(list)
                    StatLine(
                        iconRes = activityIconRes(typeId.toString()),
                        label = name,
                        value = "${list.size} · ${formatDistanceM(sub.distanceM)}",
                    )
                }
        }
    }
}

@Composable
private fun StatLine(iconRes: Int, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = ColorPrimary.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = ColorPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Строка одной тренировки ──────────────────────────────────────────────────

/**
 * Компактная строка тренировки: цветная полоска вида + иконка + название/метрики,
 * справа — время проведения. Тап открывает детали (тот же обработчик, что в дереве).
 */
@Composable
private fun TrainingRow(
    item: TrainingHistoryItem,
    workoutTypes: List<WorkoutType>,
    onTrainingClick: (TrainingHistoryItem, String) -> Unit,
) {
    val activityName = workoutTypes.find { it.id == item.typeActivId }?.name ?: "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, ColorPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { onTrainingClick(item, activityName) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Цветная полоска вида активности (как в дереве) + иконка.
        Box(
            modifier = Modifier
                .width(38.dp)
                .fillMaxHeight()
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = activityName,
                color = ColorPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(formatDurationBetween(item.timeStart, item.timeEnd))
                    if (item.distanceM != null) append(" · ${formatDistanceM(item.distanceM)}")
                    if (item.kilocalories != null) append(" · ${formatKcal(item.kilocalories)}")
                },
                color = ColorPrimary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatTime(item.timeStart),
            color = ColorPrimary.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}

/** Пустой период/день — компактная серая пометка. */
@Composable
private fun EmptyRow() {
    Text(
        text = "Нет тренировок",
        color = ColorPrimary.copy(alpha = 0.45f),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
    )
}
