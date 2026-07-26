package com.example.smarttracker.presentation.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.WorkoutTextStyles

/**
 * Корневой экран истории тренировок.
 *
 * Управляет тремя режимами просмотра: День / Неделя / Месяц.
 * Переключение:
 *  - Табы «День/Неделя/Месяц» внизу → прямой выбор уровня
 *  - Пинч-spread/pinch          → смена уровня (ускоритель)
 *  - Тап на день в Week view    → Day view для той даты
 *  - Тап на неделю в Month view → Week view для той недели
 *  - Тап по дате в шапке         → выбор даты (прыжок ленты)
 *  - Системный «Назад»           → предыдущий режим из бэкстека
 *
 * Ствол дерева (16dp, ColorPrimary) рисуется drawBehind на весь контентный Box,
 * включая область табов внизу.
 */
@Composable
fun TrainingHistoryScreen(
    padding: PaddingValues,
    viewModel: TrainingHistoryViewModel = hiltViewModel(),
    onTrainingClick: (com.example.smarttracker.domain.model.TrainingHistoryItem, String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // resetToToday намеренно НЕ вызывается при входе: возврат на экран сохраняет
    // прошлое место (режим + период). Первый заход = дефолт VM (сегодня/День);
    // повторный тап вкладки «Тренировки» → сегодня (в WorkoutHomeScreen).

    BackHandler(enabled = state.backStack.isNotEmpty()) {
        viewModel.onBack()
    }

    var accumulatedScale by remember { mutableStateOf(1f) }

    // ── Onboarding-coachmark ─────────────────────────────────────────────────
    // Авто-показ при первом заходе (флаг persist в SettingsStorage) ИЛИ
    // принудительно по кнопке справки «?» в шапке ([coachmarkForced]).
    // Гейт !isLoading — не мигать поверх спиннера загрузки истории.
    var coachmarkForced by remember { mutableStateOf(false) }
    var coachmarkStep by remember { mutableIntStateOf(0) }
    val coachmarkVisible = coachmarkForced || (!state.isLoading && !state.coachmarkShown)

    // Начало верхнего видимого периода — дата в шапке (обновляется при скролле,
    // «вплывает»). View сообщает его через onVisiblePeriodChanged.
    var visiblePeriod by remember { mutableStateOf(state.selectedDate) }

    // Выбор даты по тапу на шапку (пикер адаптируется под режим). Диапазон
    // ограничен [firstDate … today] — за его пределами данных нет.
    var showDatePicker by remember { mutableStateOf(false) }
    val today = java.time.LocalDate.now()
    val firstDate = state.items.minOfOrNull { it.date } ?: today

    // Box-обёртка — чтобы поверх экрана лёг onboarding-coachmark на весь экран.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.White),
    ) {
        HistoryHeader(
            viewMode = state.viewMode,
            periodStart = visiblePeriod,
            onHelpClick = { coachmarkStep = 0; coachmarkForced = true },
            onDateClick = { showDatePicker = true },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .drawTrunk(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // fillMaxWidth обязателен: без него Box в Column оборачивает
                        // контент по ширине (weight задаёт только высоту) и прижимается
                        // влево — тогда align(Center) спиннера/ошибки центрирует внутри
                        // узкого Box у края, а не по стволу дерева.
                        .fillMaxWidth()
                        .pointerInput(state.viewMode) {
                            // Пинч обрабатываем ТОЛЬКО при 2+ пальцах и потребляем
                            // события лишь тогда — одно-пальцевый вертикальный скролл
                            // уходит в LazyColumn нативно (плавно). detectTransformGestures
                            // перехватывал и одно-пальцевый pan → скролл был резким.
                            // Инвертированный жест: spread (пальцы расходятся) → детали
                            // (zoomIn: MONTH→WEEK→DAY); pinch → обобщение (zoomOut).
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val zoom = event.calculateZoom()
                                        if (zoom != 1f) {
                                            accumulatedScale *= zoom
                                            when {
                                                accumulatedScale > 1.3f -> {
                                                    viewModel.onZoomIn(); accumulatedScale = 1f
                                                }
                                                accumulatedScale < 0.7f -> {
                                                    viewModel.onZoomOut(); accumulatedScale = 1f
                                                }
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    when {
                        state.isLoading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = ColorPrimary,
                        )
                        state.error != null -> HistoryErrorBlock(
                            message = state.error ?: "",
                            onRetry = viewModel::loadHistory,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> when (state.viewMode) {
                            HistoryViewMode.DAY -> DayTimelineView(
                                state = state,
                                onTrainingClick = onTrainingClick,
                                onVisiblePeriodChanged = { visiblePeriod = it },
                            )
                            HistoryViewMode.WEEK -> WeekTimelineView(
                                state = state,
                                onDaySelected = viewModel::onDaySelected,
                                onVisiblePeriodChanged = { visiblePeriod = it },
                            )
                            HistoryViewMode.MONTH -> MonthTimelineView(
                                state = state,
                                onWeekSelected = viewModel::onWeekSelected,
                                onVisiblePeriodChanged = { visiblePeriod = it },
                            )
                        }
                    }
                }

                // Табы режима внизу (белая подложка перекрывает ствол).
                ModeTabs(
                    current = state.viewMode,
                    // anchorDate — верхний видимый период: смена уровня сохраняет время.
                    onSelect = { viewModel.setViewMode(it, visiblePeriod) },
                )
            }
        }
    }

        // ── Onboarding-coachmark поверх экрана ───────────────────────────────
        if (coachmarkVisible) {
            CalendarCoachmark(
                step = coachmarkStep,
                stepCount = COACHMARK_STEPS,
                onNext = { coachmarkStep++ },
                onBack = { if (coachmarkStep > 0) coachmarkStep-- },
                onDismiss = {
                    coachmarkStep = 0
                    coachmarkForced = false
                    viewModel.onCoachmarkDismissed()
                },
            )
        }

        // ── Выбор даты (пикер по тапу на шапку) ──────────────────────────────
        if (showDatePicker) {
            HistoryDatePickerDialog(
                viewMode = state.viewMode,
                currentDate = visiblePeriod,
                firstDate = firstDate,
                today = today,
                onDismiss = { showDatePicker = false },
                onPick = { viewModel.jumpToDate(it) },
            )
        }
    } // конец Box-обёртки
}

// ── Шапка ─────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryHeader(
    viewMode: HistoryViewMode,
    periodStart: java.time.LocalDate,
    onHelpClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Дата кликабельна → выбор даты (пикер адаптируется под режим).
        Text(
            text = periodLabel(viewMode, periodStart),
            style = WorkoutTextStyles.screenHeaderDate,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDateClick)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        // Кнопка справки (левый угол хедера) — открывает онбординг в любой момент.
        Image(
            painter = painterResource(id = R.drawable.ic_help),
            contentDescription = "Справка",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onHelpClick),
        )
    }
    HorizontalDivider(color = ColorPrimary, thickness = 1.dp)
}

/** Лейбл верхнего видимого периода для шапки (те же форматы, что у плашек-разделителей). */
private fun periodLabel(viewMode: HistoryViewMode, periodStart: java.time.LocalDate): String =
    when (viewMode) {
        HistoryViewMode.DAY -> dayHeaderLabel(periodStart)
        HistoryViewMode.WEEK -> weekHeaderLabel(periodStartOf(HistoryViewMode.WEEK, periodStart))
        HistoryViewMode.MONTH -> monthHeaderLabel(periodStartOf(HistoryViewMode.MONTH, periodStart))
    }

// ── Блок ошибки загрузки истории ───────────────────────────────────────────────

/**
 * Понятный блок ошибки вместо сырого текста: белая карточка (перекрывает ствол),
 * иконка-предупреждение, заголовок, переведённое сообщение и кнопка «Повторить».
 * [message] уже на русском (ApiErrorHandler.getErrorMessage во ViewModel).
 */
@Composable
private fun HistoryErrorBlock(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, ColorPrimary, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = ColorPrimary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Не удалось загрузить историю",
            style = WorkoutTextStyles.screenHeaderDate,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = ColorPrimary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Повторить",
                style = WorkoutTextStyles.primaryButtonLabel,
                color = Color.White,
            )
        }
    }
}

// ── Табы режима (сегмент-контрол День/Неделя/Месяц) ────────────────────────────

/**
 * Переключатель режима: единый сегмент-контрол из трёх равных по ширине сегментов,
 * активный залит `ColorSecondary`. Видимый индикатор текущего уровня + альтернатива
 * пинчу (находка №3). Компактная тонкая полоса под шапкой.
 */
@Composable
private fun ModeTabs(current: HistoryViewMode, onSelect: (HistoryViewMode) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)
            .height(30.dp)
            .clip(shape)
            .border(1.dp, ColorPrimary, shape),
    ) {
        SegmentTab("День", current == HistoryViewMode.DAY, Modifier.weight(1f)) { onSelect(HistoryViewMode.DAY) }
        SegmentDivider()
        SegmentTab("Неделя", current == HistoryViewMode.WEEK, Modifier.weight(1f)) { onSelect(HistoryViewMode.WEEK) }
        SegmentDivider()
        SegmentTab("Месяц", current == HistoryViewMode.MONTH, Modifier.weight(1f)) { onSelect(HistoryViewMode.MONTH) }
    }
}

@Composable
private fun SegmentTab(label: String, isActive: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isActive) ColorSecondary else Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isActive) Color.White else ColorPrimary,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Тонкий вертикальный разделитель между сегментами. */
@Composable
private fun SegmentDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(ColorPrimary.copy(alpha = 0.4f)),
    )
}
