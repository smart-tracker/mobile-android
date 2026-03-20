package com.example.smarttracker.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smarttracker.domain.model.NavigationConfig
import com.example.smarttracker.domain.model.BottomNavItem

/**
 * МОБ-6 — Главный экран приложения с динамической навигацией.
 *
 * Структура:
 * - HomeScreen (этот composable) — скелет с BottomNavigation
 * - Внутренние экраны: Home, Workouts, Athletes, Club, Profile
 *
 * BottomNavigation генерируется динамически на основе ролей пользователя:
 * - Все видят: Home, Profile
 * - ATHLETE видят: Workouts
 * - TRAINER видят: Athletes
 * - CLUB_OWNER видят: Club
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navigationConfig by viewModel.navigationConfig.collectAsStateWithLifecycle()
    
    // Текущий выбранный экран
    var currentRoute by remember { mutableStateOf("home") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomAppBar(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            ) {
                navigationConfig.bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { currentRoute = item.route },
                        icon = {
                            Icon(
                                imageVector = item.getIcon(),
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(text = item.label)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (currentRoute) {
                "home" -> HomeMainScreen()
                "workouts" -> WorkoutsScreen()
                "athletes" -> AthletesScreen()
                "club" -> ClubScreen()
                "profile" -> ProfileScreen()
            }
        }
    }
}

/**
 * Главный экран (Home / Dashboard).
 * Отображается для всех пользователей.
 */
@Composable
fun HomeMainScreen() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = "Добро пожаловать в SmartTracker!",
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

/**
 * Расширение для получения Icon на основе строкового ID.
 * Здесь используются Material Design Icons.
 */
fun BottomNavItem.getIcon(): ImageVector = when (this.id) {
    "home" -> Icons.Filled.Home
    "my_workouts" -> Icons.Filled.FitnessCenter
    "my_athletes" -> Icons.Filled.People
    "my_club" -> Icons.Filled.People  // TODO: заменить на custom icon для клуба
    "profile" -> Icons.Filled.AccountCircle
    else -> Icons.Filled.Home  // Fallback
}
