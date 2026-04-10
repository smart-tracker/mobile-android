package com.example.smarttracker.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.smarttracker.presentation.navigation.AppNavGraph
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная Activity в приложении.
 * Вся навигация — через NavGraph в Compose.
 *
 * AppViewModel определяет стартовый маршрут (Login или Home) и предоставляет
 * функцию logout, которая пробрасывается через AppNavGraph → WorkoutHomeScreen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartTrackerTheme {
                AppNavGraph(
                    startDestination = appViewModel.startRoute,
                    onLogout = appViewModel::logout,
                )
            }
        }
    }
}
