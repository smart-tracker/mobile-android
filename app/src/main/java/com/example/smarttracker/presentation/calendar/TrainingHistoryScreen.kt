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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.WorkoutTextStyles

/**
 * Корневой экран истории тренировок.
 *
 * Управляет тремя режимами просмотра: День / Неделя / Месяц.
 * Переключение:
 *  - Пинч-spread (scale > 1.3) → zoom out (DAY→WEEK→MONTH)
 *  - Пинч-pinch  (scale < 0.7) → zoom in  (MONTH→WEEK→DAY)
 *  - Тап на день в Week view   → Day view для той даты
 *  - Тап на неделю в Month view → Week view для той недели
 *  - Системный «Назад»          → предыдущий режим из бэкстека
 *
 * Ствол дерева (16dp, ColorPrimary) рисуется drawBehind на весь контентный Box,
 * включая область кнопки внизу.
 */
@Composable
fun TrainingHistoryScreen(
    padding: PaddingValues,
    onNavigateToStart: () -> Unit,
    onTrainingClick: (com.example.smarttracker.domain.model.TrainingHistoryItem, String) -> Unit = { _, _ -> },
) {
    val viewModel: TrainingHistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // При каждом входе на экран сбрасываем на День / сегодня
    LaunchedEffect(Unit) {
        viewModel.resetToToday()
    }

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

                StartWorkoutButton(
                    label = if (state.viewMode == HistoryViewMode.DAY) {
                        "Начать свою тренировку"
                    } else {
                        "Запланировать тренировку"
                    },
                    onClick = onNavigateToStart,
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
    } // конец Box-обёртки
}

// ── Шапка ─────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryHeader(
    viewMode: HistoryViewMode,
    periodStart: java.time.LocalDate,
    onHelpClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = periodLabel(viewMode, periodStart),
            style = WorkoutTextStyles.screenHeaderDate,
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

// ── Кнопка внизу ──────────────────────────────────────────────────────────────

@Composable
private fun StartWorkoutButton(label: String, onClick: () -> Unit) {
    // Белые Spacer перекрывают ствол до и после кнопки (drawBehind рисуется ДО детей)
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.White),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TrunkColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = WorkoutTextStyles.primaryButtonLabel,
            color = Color.White,
        )
    }
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.White),
    )
}
