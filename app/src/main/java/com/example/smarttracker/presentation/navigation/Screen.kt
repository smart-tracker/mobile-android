package com.example.smarttracker.presentation.navigation

/** Все экраны приложения */
sealed class Screen(val route: String) {
    data object Register : Screen("register")
    data object Login    : Screen("login")
    data object Home     : Screen("home")
}
