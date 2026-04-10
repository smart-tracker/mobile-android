package com.example.smarttracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smarttracker.presentation.auth.forgot.ForgotPasswordEvent
import com.example.smarttracker.presentation.auth.forgot.ForgotPasswordScreen
import com.example.smarttracker.presentation.auth.forgot.ForgotPasswordViewModel
import com.example.smarttracker.presentation.auth.login.LoginEvent
import com.example.smarttracker.presentation.auth.login.LoginScreen
import com.example.smarttracker.presentation.auth.login.LoginViewModel
import com.example.smarttracker.presentation.auth.register.PrivacyPolicyScreen
import com.example.smarttracker.presentation.auth.register.RegisterEvent
import com.example.smarttracker.presentation.auth.register.RegisterScreen
import com.example.smarttracker.presentation.auth.register.RegisterViewModel
import com.example.smarttracker.presentation.auth.register.TermsOfServiceScreen
import com.example.smarttracker.presentation.workout.WorkoutHomeScreen

/** Compose NavHost: декларация всех маршрутов и переходов между экранами. */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route,
    onLogout: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {

        composable(Screen.Register.route) {
            val viewModel: RegisterViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        is RegisterEvent.NavigateToHome -> {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Register.route) { inclusive = true }
                            }
                        }
                        is RegisterEvent.NavigateBack -> navController.popBackStack()
                    }
                }
            }

            RegisterScreen(
                state = state,
                onFirstNameChange = viewModel::onFirstNameChange,
                onUsernameChange = viewModel::onUsernameChange,
                onBirthDateChange = viewModel::onBirthDateChange,
                onGenderChange = viewModel::onGenderChange,
                onPurposeChange = viewModel::onPurposeChange,
                onLoadAvailableGoals = viewModel::loadAvailableGoals,
                onGoalSelected = viewModel::onGoalSelected,
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
                onToggleConfirmPasswordVisibility = viewModel::onToggleConfirmPasswordVisibility,
                onTermsAcceptedChange = viewModel::onTermsAcceptedChange,
                onVerificationCodeChange = viewModel::onVerificationCodeChange,
                onResendCode = viewModel::onResendCode,
                onOpenTermsOfService = { navController.navigate(Screen.TermsOfService.route) },
                onOpenPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                onNext = viewModel::onNext,
                onBack = viewModel::onBack,
                isStep1Complete = viewModel.isStep1Complete(),
                isStep2Complete = viewModel.isStep2Complete(),
                isStep3Complete = viewModel.isStep3Complete(),
                isStep4Complete = viewModel.isStep4Complete(),
            )
        }

        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        is LoginEvent.NavigateToHome -> {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                        is LoginEvent.NavigateToRegister -> {
                            navController.navigate(Screen.Register.route) {
                                popUpTo(Screen.Login.route) { inclusive = false }
                            }
                        }
                        is LoginEvent.NavigateToPasswordRecovery -> {
                            navController.navigate(Screen.PasswordRecovery.route)
                        }
                    }
                }
            }

            LoginScreen(
                state = state,
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
                onSubmitLogin = viewModel::onSubmitLogin,
                onNavigateToRegister = viewModel::onNavigateToRegister,
                onNavigateToPasswordRecovery = viewModel::onNavigateToPasswordRecovery
            )
        }

        composable(Screen.PasswordRecovery.route) {
            val viewModel: ForgotPasswordViewModel = hiltViewModel()

            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ForgotPasswordEvent.NavigateToLoginAfterReset -> {
                            // Navigate back to login after successful password reset
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.PasswordRecovery.route) { inclusive = true }
                            }
                        }
                        is ForgotPasswordEvent.NavigateToLoginFromBack -> {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.PasswordRecovery.route) { inclusive = true }
                            }
                        }
                        else -> {} // Other events handled within the screen
                    }
                }
            }

            ForgotPasswordScreen(viewModel = viewModel)
        }

        composable(Screen.TermsOfService.route) {
            TermsOfServiceScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            WorkoutHomeScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    onLogout()
                    // Очищаем весь бэкстек до корня и переходим на Login,
                    // чтобы кнопка «Назад» не возвращала на Home после выхода.
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
