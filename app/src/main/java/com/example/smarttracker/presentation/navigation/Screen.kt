package com.example.smarttracker.presentation.navigation

/** Все экраны приложения */
sealed class Screen(val route: String) {
    data object Register : Screen("register")
    data object Login    : Screen("login")
    data object PasswordRecovery : Screen("forgot_password")
    data object TermsOfService : Screen("terms_of_service")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object Home     : Screen("home")
}
