package com.example.smarttracker.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная Activity в приложении.
 * Вся навигация — через NavGraph в Compose.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartTrackerTheme {
                // TODO: МОБ-5.2 — подключить NavGraph
            }
        }
    }
}
