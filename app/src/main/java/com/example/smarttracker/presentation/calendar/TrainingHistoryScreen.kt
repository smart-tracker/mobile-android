package com.example.smarttracker.presentation.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import java.time.DayOfWeek

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.White),
    ) {
        HistoryHeader(state)

        Box(
            modifier = Modifier
                .weight(1f)
                .drawBehind {
                    drawRect(
                        color = TrunkColor,
                        topLeft = Offset(size.width / 2f - 8.dp.toPx(), 0f),
                        size = Size(16.dp.toPx(), size.height),
                    )
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(state.viewMode) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                accumulatedScale *= zoomChange
                                if (accumulatedScale > 1.3f) {
                                    viewModel.onZoomOut()
                                    accumulatedScale = 1f
                                } else if (accumulatedScale < 0.7f) {
                                    viewModel.onZoomIn()
                                    accumulatedScale = 1f
                                }
                            }
                        },
                ) {
                    when {
                        state.isLoading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = ColorPrimary,
                        )
                        state.error != null -> Text(
                            text = state.error ?: "",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            style = WorkoutTextStyles.screenHeaderDate,
                        )
                        else -> when (state.viewMode) {
                            HistoryViewMode.DAY -> DayTimelineView(
                                state = state,
                                onTrainingClick = onTrainingClick,
                            )
                            HistoryViewMode.WEEK -> WeekTimelineView(
                                state = state,
                                onDaySelected = viewModel::onDaySelected,
                            )
                            HistoryViewMode.MONTH -> MonthTimelineView(
                                state = state,
                                onWeekSelected = viewModel::onWeekSelected,
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
}

// ── Шапка ─────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryHeader(state: TrainingHistoryUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = periodLabel(state),
            style = WorkoutTextStyles.screenHeaderDate,
        )
    }
    HorizontalDivider(color = ColorPrimary, thickness = 1.dp)
}

private fun periodLabel(state: TrainingHistoryUiState): String {
    val date = state.selectedDate
    return when (state.viewMode) {
        HistoryViewMode.DAY -> date.format(DateFmt)
        HistoryViewMode.WEEK -> {
            val mon = date.with(DayOfWeek.MONDAY)
            val sun = mon.plusDays(6)
            "${mon.format(DateFmt)} - ${sun.format(DateFmt)}"
        }
        HistoryViewMode.MONTH -> {
            val first = date.withDayOfMonth(1)
            val last = date.withDayOfMonth(date.lengthOfMonth())
            "${first.format(DateFmt)} - ${last.format(DateFmt)}"
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
            color = Color.White,
            fontSize = 20.sp,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
        )
    }
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.White),
    )
}
