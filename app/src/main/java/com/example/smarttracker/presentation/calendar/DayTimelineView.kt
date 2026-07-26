package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
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
 * Дневной вид истории — бесконечный скролл-лента по дням. Порядок как в чате:
 * сегодня внизу, старые дни выше (`reverseLayout`), прокрутка вверх — в прошлое
 * до первой тренировки. Каждый день: [PeriodHeader] (дата) + карточки тренировок
 * (чередуются лево/право от ствола) либо пометка «нет тренировок».
 * Прокрутка к [TrainingHistoryUiState.selectedDate] при drill-down/сбросе.
 * [onVisiblePeriodChanged] — верхний видимый день (для даты в шапке экрана).
 */
@Composable
internal fun DayTimelineView(
    state: TrainingHistoryUiState,
    onTrainingClick: (TrainingHistoryItem, String) -> Unit = { _, _ -> },
    onVisiblePeriodChanged: (LocalDate) -> Unit = {},
) {
    val today = LocalDate.now()
    val firstDate = state.items.minOfOrNull { it.date } ?: today
    val count = periodCount(HistoryViewMode.DAY, firstDate, today)
    // Группировка по дате — один проход; период берёт свои тренировки за O(1).
    val itemsByDate = remember(state.items) { state.items.groupBy { it.date } }
    // Стартовый паритет чередования лево/право для каждого дня = число тренировок
    // во всех днях старше (выше по ленте) % 2. Даёт НЕПРЕРЫВНОе чередование через
    // границы дней (иначе каждый день начинал бы с левой карточки → сбой на стыке).
    val startParityByDate = remember(state.items) {
        var cum = 0
        itemsByDate.keys.sorted().associateWith { d ->
            val parity = cum % 2
            cum += itemsByDate[d]!!.size
            parity
        }
    }
    val listState = rememberLazyListState()

    // Прокрутка к выбранному дню — только при явной навигации (scrollTick изменился).
    // При возврате на экран (restore listState) scrollTick == сохранённый → скролла
    // нет, точная пиксельная позиция сохраняется. rememberSaveable переживает restore.
    var lastScrollTick by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(state.scrollTick) {
        if (state.scrollTick != lastScrollTick) {
            val idx = periodIndexOf(HistoryViewMode.DAY, state.selectedDate, today)
                .coerceIn(0, count - 1)
            listState.scrollToItem(idx)
            lastScrollTick = state.scrollTick
        }
    }

    // Верхний видимый день. При reverseLayout самый большой индекс среди видимых
    // (visibleItemsInfo.last) — это верх экрана (старая дата).
    val topDay by remember(count) {
        derivedStateOf {
            val idx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            periodStartAt(HistoryViewMode.DAY, today, idx.coerceIn(0, count - 1))
        }
    }
    LaunchedEffect(topDay) { onVisiblePeriodChanged(topDay) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        reverseLayout = true,
        flingBehavior = rememberSnappyFling(),
    ) {
        items(count = count) { i ->
            val day = periodStartAt(HistoryViewMode.DAY, today, i)
            DayPeriod(
                day = day,
                isCurrent = day == today,
                dayItems = itemsByDate[day].orEmpty(),
                startParity = startParityByDate[day] ?: 0,
                workoutTypes = state.workoutTypes,
                onTrainingClick = onTrainingClick,
            )
        }
    }
}

/** Один день в бесконечном скролле: заголовок + карточки тренировок или пометка. */
@Composable
private fun DayPeriod(
    day: LocalDate,
    isCurrent: Boolean,
    dayItems: List<TrainingHistoryItem>,
    startParity: Int,
    workoutTypes: List<WorkoutType>,
    onTrainingClick: (TrainingHistoryItem, String) -> Unit,
) {
    val sorted = dayItems.sortedBy { it.timeStart }
    Column {
        PeriodHeader(label = dayHeaderLabel(day), isCurrent = isCurrent)
        if (sorted.isEmpty()) {
            PeriodEmptyNote()
        } else {
            sorted.forEachIndexed { index, item ->
                val activityName = workoutTypes.find { it.id == item.typeActivId }?.name ?: "—"
                DayRow(
                    item = item,
                    activityName = activityName,
                    // Непрерывное чередование через границы дней (startParity — сдвиг).
                    isCardRight = (startParity + index) % 2 != 0,
                    onTrainingClick = { onTrainingClick(item, activityName) },
                )
                if (index < sorted.lastIndex) Spacer(Modifier.height(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
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
 * Тренировки в нескольких днях/неделях/месяцах (май–июль 2026) — превью показывает
 * бесконечный скролл с несколькими периодами и заголовками-разделителями.
 * selectedDate = 22.07.2026 (среда) — якорь начальной прокрутки.
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
        TrainingHistoryItem("t4", 3, LocalDate.of(2026, 6, 15),
            "2026-06-15T19:00:00+00:00", "2026-06-15T19:45:00+00:00", 380.0, 12000.0, 4.4, 45.0),
        TrainingHistoryItem("t5", 1, LocalDate.of(2026, 5, 3),
            "2026-05-03T07:30:00+00:00", "2026-05-03T08:10:00+00:00", 290.0, 5800.0, 2.4, 28.0),
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
