package com.example.smarttracker.presentation.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smarttracker.R
import com.example.smarttracker.presentation.common.AppTab
import com.example.smarttracker.presentation.common.SmartTrackerBottomBar
import com.example.smarttracker.presentation.menu.MenuScreen
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import com.example.smarttracker.presentation.calendar.TrainingHistoryScreen
import com.example.smarttracker.presentation.workout.start.WorkoutStartScreen
import com.example.smarttracker.presentation.workout.start.WorkoutStartViewModel

/**
 * Главный экран приложения после авторизации.
 * Содержит три вкладки: Старт, Тренировки, Меню.
 *
 * ViewModel хоистится здесь (а не внутри ветки when), чтобы:
 *  - сохранять оверлей итогов тренировки между переключениями вкладок;
 *  - закрывать оверлей при выборе соседней вкладки.
 */
@Composable
fun WorkoutHomeScreen(
    onLogout: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
) {
    var currentTab by remember { mutableStateOf(WorkoutTab.START) }

    // ViewModel живёт на всём времени Home — оверлей итогов переживает переключения
    // вкладок, а закрытие оверлея при смене вкладки делается ниже через side-effect.
    val viewModel: WorkoutStartViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Принудительный выход при истечении сессии (оба токена невалидны).
    // Срабатывает когда TokenRefreshAuthenticator получает 401 на /auth/refresh.
    val sessionExpired by viewModel.sessionExpired.collectAsStateWithLifecycle()
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) onLogout()
    }

    // Закрываем оверлей при переходе с вкладки «Старт» на любую другую —
    // пользователь явно ушёл с экрана итогов, состояние сбрасывается на «можно начать».
    LaunchedEffect(currentTab) {
        if (currentTab != WorkoutTab.START) viewModel.onCloseSummaryOverlay()
    }

    Scaffold(
        bottomBar = {
            // Используем общий компонент; ordinal совпадает с AppTab.START/WORKOUTS/MENU
            SmartTrackerBottomBar(
                selectedIndex = currentTab.ordinal,
                onTabSelected = { currentTab = WorkoutTab.entries[it] },
            )
        },
    ) { padding ->
        when (currentTab) {
            WorkoutTab.START -> {
                WorkoutStartScreen(
                    state = state,
                    padding = padding,
                    onStartClick = viewModel::onStartWorkoutClick,
                    onTypeSelected = viewModel::onQuickTypeSelected,
                    onSheetTypeSelected = viewModel::onSheetTypeSelected,
                    onPauseClick = viewModel::onPauseClick,
                    onFinishClick = viewModel::onFinishClick,
                    onMapTilesFailed = viewModel::onMapTilesFailed,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onCloseSummary = viewModel::onCloseSummaryOverlay,
                    onToggleFullscreenMap = viewModel::onToggleFullscreenMap,
                    onDeleteHistoryTraining = viewModel::onDeleteHistoryTraining,
                )
            }
            WorkoutTab.WORKOUTS -> TrainingHistoryScreen(
                    padding = padding,
                    onNavigateToStart = { currentTab = WorkoutTab.START },
                    onTrainingClick = { item, activityName ->
                        currentTab = WorkoutTab.START
                        viewModel.showHistorySummary(item, activityName)
                    },
                )
            WorkoutTab.MENU    -> MenuScreen(
                padding = padding,
                onNavigateToProfile = onNavigateToProfile,
            )
        }
    }
}

// ── Вкладки главного экрана ───────────────────────────────────────────────────
// Порядок должен совпадать с AppTab.START/WORKOUTS/MENU (используется .ordinal)

private enum class WorkoutTab { START, WORKOUTS, MENU }

// ── Заглушка для незаконченных вкладок ────────────────────────────────────────

@Composable
private fun PlaceholderScreen(
    label: String,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label\n(скоро)",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 18.sp,
            color = ColorPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

