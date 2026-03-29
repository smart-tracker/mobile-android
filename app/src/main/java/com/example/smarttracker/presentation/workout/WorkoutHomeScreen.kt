package com.example.smarttracker.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import com.example.smarttracker.presentation.workout.start.WorkoutStartScreen
import com.example.smarttracker.presentation.workout.start.WorkoutStartViewModel

/**
 * Главный экран приложения после авторизации.
 * Содержит три вкладки: Старт, Тренировки, Меню.
 *
 * Заменяет замороженный HomeScreen — активируется через AppNavGraph по маршруту Screen.Home.
 */
@Composable
fun WorkoutHomeScreen(
    onBack: () -> Unit = {},
) {
    var currentTab by remember { mutableStateOf(WorkoutTab.START) }

    Scaffold(
        bottomBar = {
            WorkoutBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
            )
        },
    ) { padding ->
        when (currentTab) {
            WorkoutTab.START -> {
                val viewModel: WorkoutStartViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                WorkoutStartScreen(
                    state = state,
                    padding = padding,
                    onBack = onBack,
                    onStartClick = viewModel::onStartWorkoutClick,
                    onTypeSelected = viewModel::onWorkoutTypeSelected,
                    onPauseClick = viewModel::onPauseClick,
                    onFinishClick = viewModel::onFinishClick,
                )
            }
            WorkoutTab.WORKOUTS -> PlaceholderScreen(label = "Тренировки", padding = padding)
            WorkoutTab.MENU    -> PlaceholderScreen(label = "Меню", padding = padding)
        }
    }
}

// ── Нижний бар ────────────────────────────────────────────────────────────────

private enum class WorkoutTab { START, WORKOUTS, MENU }

@Composable
private fun WorkoutBottomBar(
    currentTab: WorkoutTab,
    onTabSelected: (WorkoutTab) -> Unit,
) {
    NavigationBar(containerColor = Color.White) {

        // Все три вкладки имеют одинаковое поведение:
        // при выборе — иконка обёрнута в бирюзовый круг, цвет иконки остаётся тёмным
        listOf(
            Triple(WorkoutTab.START,    R.drawable.ic_nav_start,    "Старт"),
            Triple(WorkoutTab.WORKOUTS, R.drawable.ic_nav_workouts, "Тренировки"),
            Triple(WorkoutTab.MENU,     R.drawable.ic_nav_menu,     "Меню"),
        ).forEach { (tab, iconRes, label) ->
        NavigationBarItem(
            selected = currentTab == tab,
            onClick = { onTabSelected(tab) },
            icon = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (currentTab == tab) ColorSecondary else Color.Transparent,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = ColorPrimary,
                        modifier = Modifier.size(38.dp),
                    )
                }
            },
            label = {
                Text(
                    text = label,
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    color = ColorPrimary,
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
            ),
        )
        }
    }
}

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
