package com.example.smarttracker.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smarttracker.presentation.auth.LoginEvent
import com.example.smarttracker.presentation.auth.LoginScreen
import com.example.smarttracker.presentation.auth.LoginViewModel
import com.example.smarttracker.presentation.auth.RegisterEvent
import com.example.smarttracker.presentation.auth.RegisterScreen
import com.example.smarttracker.presentation.auth.RegisterViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route,
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
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
                onToggleConfirmPasswordVisibility = viewModel::onToggleConfirmPasswordVisibility,
                onTermsAcceptedChange = viewModel::onTermsAcceptedChange,
                onVerificationCodeChange = viewModel::onVerificationCodeChange,
                onResendCode = viewModel::onResendCode,
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
                            // TODO: МОБ-5.x — PasswordRecoveryScreen
                            // navController.navigate(Screen.PasswordRecovery.route)
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

        composable(Screen.Home.route) {
            // TODO: МОБ-6.x — HomeScreen
            Text(text = "Главный экран (в разработке)")
        }
    }
}
