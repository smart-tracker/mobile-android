package com.example.smarttracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import com.example.smarttracker.presentation.menu.profile.ProfileEditEvent
import com.example.smarttracker.presentation.menu.profile.ProfileEditScreen
import com.example.smarttracker.presentation.menu.profile.ProfileEditViewModel
import com.example.smarttracker.presentation.menu.profile.ProfileScreen
import com.example.smarttracker.presentation.menu.profile.ProfileViewModel
import com.example.smarttracker.presentation.menu.sensors.SensorsScreen
import com.example.smarttracker.presentation.menu.sensors.SensorsViewModel
import com.example.smarttracker.presentation.menu.settings.SettingsScreen
import com.example.smarttracker.presentation.menu.settings.SettingsViewModel
import com.example.smarttracker.presentation.workout.WorkoutHomeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Маршруты auth-флоу: на них принудительный переход на Login при истечении
 * сессии не выполняется (пользователь и так вне авторизованной зоны).
 */
private val AUTH_ROUTES = setOf(
    Screen.Login.route,
    Screen.Register.route,
    Screen.PasswordRecovery.route,
    Screen.TermsOfService.route,
    Screen.PrivacyPolicy.route,
)

/** Compose NavHost: декларация всех маршрутов и переходов между экранами. */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route,
    onLogout: () -> Unit = {},
    sessionExpired: StateFlow<Boolean> = MutableStateFlow(false),
) {
    // Глобальный обработчик истечения сессии: срабатывает с ЛЮБОГО экрана
    // (TokenRefreshAuthenticator получил 4xx на /auth/refresh → оба токена мертвы).
    // Раньше подписка жила только в WorkoutHomeScreen — 401 на ProfileEdit
    // не разлогинивал, пока пользователь не вернётся на Home.
    LaunchedEffect(navController) {
        sessionExpired.collect { expired ->
            if (!expired) return@collect
            val current = navController.currentBackStackEntry?.destination?.route
            // На auth-экранах не дёргаем навигацию: пользователь уже логинится,
            // сброс его ввода принудительным переходом только навредит.
            if (current !in AUTH_ROUTES) {
                onLogout() // идемпотентно: токены уже стёрты signalSessionExpired,
                           // но кэш профиля чистится именно здесь
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

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
                        is ForgotPasswordEvent.NavigateToHomeAfterReset -> {
                            // Авто-вход: токены уже сохранены — идём сразу на главный экран,
                            // очищая весь auth-стек (Login + PasswordRecovery)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
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
            // Итоги тренировки показываются оверлеем поверх WorkoutStartScreen —
            // без навигации. Это сохраняет ту же инстанцию MapView и устраняет
            // краши анимаций MapLibre, которые возникали при переходе через NavCompose.
            WorkoutHomeScreen(
                onLogout = {
                    onLogout()
                    // Очищаем весь бэкстек до корня и переходим на Login,
                    // чтобы кнопка «Назад» не возвращала на Home после выхода.
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val settings by viewModel.state.collectAsStateWithLifecycle()

            SettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onAutopauseChanged = viewModel::onAutopauseChanged,
                onVoiceCuesChanged = viewModel::onVoiceCuesChanged,
                onVoiceCueIntervalChanged = viewModel::onVoiceCueIntervalChanged,
                onKeepScreenOnChanged = viewModel::onKeepScreenOnChanged,
                onOpenSensors = { navController.navigate(Screen.Sensors.route) },
            )
        }

        composable(Screen.Sensors.route) {
            val viewModel: SensorsViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            SensorsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPermissionsGranted = viewModel::onPermissionsGranted,
                onPermissionsDenied = viewModel::onPermissionsDenied,
                onScanClick = viewModel::onScanClick,
                onSavedDeviceClick = viewModel::onSavedDeviceClick,
                onRemoveDeviceClick = viewModel::onRemoveDeviceClick,
                onAddDeviceClick = viewModel::onAddDeviceClick,
            )
        }

        composable(Screen.Profile.route) {
            val viewModel: ProfileViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            // Обновляем профиль из кэша при каждом возврате на экран
            // (в т.ч. при возврате с экрана редактирования).
            // repeatOnLifecycle(RESUMED) срабатывает при каждом входе в RESUMED-состояние.
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.refreshFromCache()
                }
            }

            ProfileScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onLogout = {
                    onLogout()
                    // Очищаем весь стек и идём на Login — аналогично выходу из Home
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEditProfile = { navController.navigate(Screen.ProfileEdit.route) },
            )
        }

        composable(Screen.ProfileEdit.route) {
            val viewModel: ProfileEditViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        ProfileEditEvent.NavigateBack -> navController.popBackStack()
                        ProfileEditEvent.AccountDeleted -> {
                            onLogout()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }

            ProfileEditScreen(
                state = state,
                onFirstNameChange = viewModel::onFirstNameChange,
                onLastNameChange = viewModel::onLastNameChange,
                onMiddleNameChange = viewModel::onMiddleNameChange,
                onUsernameChange = viewModel::onUsernameChange,
                onBirthDateChange = viewModel::onBirthDateChange,
                onGenderToggle = viewModel::onGenderToggle,
                onHeightChange = viewModel::onHeightChange,
                onWeightChange = viewModel::onWeightChange,
                onPhotoSelected = viewModel::onPhotoSelected,
                onDeletePhoto = viewModel::onDeletePhoto,
                onSave = viewModel::onSave,
                onBack = { navController.popBackStack() },
                onDeleteAccountClick = viewModel::onDeleteAccountClick,
                onDismissDeleteDialog = viewModel::onDismissDeleteDialog,
                onConfirmDelete = viewModel::onConfirmDelete,
            )
        }
    }
}
