package com.example.smarttracker.presentation.navigation

/** Все экраны приложения */
sealed class Screen(val route: String) {
    // ── Authorization screens ────────────────────────────────────────────────
    data object Register : Screen("register")
    data object Login    : Screen("login")
    data object PasswordRecovery : Screen("forgot_password")
    data object TermsOfService : Screen("terms_of_service")
    data object PrivacyPolicy : Screen("privacy_policy")
    
    // ── App screens (require authorization) ─────────────────────────────────
    data object Home     : Screen("home")           // Available for all roles
    data object MyWorkouts : Screen("workouts")    // For ATHLETE role (role_id = 1)
    data object MyAthletes : Screen("athletes")    // For TRAINER role (role_id = 2)
    data object MyClub   : Screen("club")          // For CLUB_OWNER role (role_id = 3)
    data object Profile  : Screen("profile")       // Available for all roles
}
