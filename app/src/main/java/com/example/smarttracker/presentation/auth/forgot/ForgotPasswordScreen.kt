package com.example.smarttracker.presentation.auth.forgot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smarttracker.presentation.common.UiTokens
import com.example.smarttracker.presentation.theme.ColorPlaceholder
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorLink
import com.example.smarttracker.presentation.theme.ColorWhite
import java.util.Locale

/**
 * Главный экран восстановления пароля с тремя шагами:
 * 1. Ввод email
 * 2. Ввод кода верификации
 * 3. Ввод нового пароля и финализация
 *
 * Использует тот же стиль и компоненты, что и RegisterScreen.
 */

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
    ) {
        when (uiState.currentStep) {
            1 -> ForgotPasswordStep1Screen(
                email = uiState.email,
                emailError = uiState.emailError,
                isLoading = uiState.isLoading,
                generalError = uiState.generalError,
                onEmailChanged = { viewModel.onEvent(ForgotPasswordEvent.OnEmailChanged(it)) },
                onContinue = { viewModel.onEvent(ForgotPasswordEvent.OnContinueFromStep1) },
                onBack = { viewModel.onEvent(ForgotPasswordEvent.OnBackPressed) }
            )

            2 -> ForgotPasswordStep2Screen(
                email = uiState.email,
                verificationCode = uiState.verificationCode,
                verificationCodeError = uiState.verificationCodeError,
                resendCodeCooldown = uiState.resendCodeCooldown,
                isLoading = uiState.isLoading,
                generalError = uiState.generalError,
                onVerificationCodeChanged = { viewModel.onEvent(ForgotPasswordEvent.OnVerificationCodeChanged(it)) },
                onResendCode = { viewModel.onEvent(ForgotPasswordEvent.OnResendCode) },
                onContinue = { viewModel.onEvent(ForgotPasswordEvent.OnContinueFromStep2) },
                onBack = { viewModel.onEvent(ForgotPasswordEvent.OnBackPressed) }
            )

            3 -> ForgotPasswordStep3Screen(
                newPassword = uiState.newPassword,
                newPasswordError = uiState.newPasswordError,
                newPasswordVisibility = uiState.newPasswordVisibility,
                confirmPassword = uiState.confirmPassword,
                confirmPasswordError = uiState.confirmPasswordError,
                confirmPasswordVisibility = uiState.confirmPasswordVisibility,
                isLoading = uiState.isLoading,
                generalError = uiState.generalError,
                onNewPasswordChanged = { viewModel.onEvent(ForgotPasswordEvent.OnNewPasswordChanged(it)) },
                onConfirmPasswordChanged = { viewModel.onEvent(ForgotPasswordEvent.OnConfirmPasswordChanged(it)) },
                onToggleNewPasswordVisibility = { viewModel.onEvent(ForgotPasswordEvent.OnToggleNewPasswordVisibility) },
                onToggleConfirmPasswordVisibility = { viewModel.onEvent(ForgotPasswordEvent.OnToggleConfirmPasswordVisibility) },
                onResetPassword = { viewModel.onEvent(ForgotPasswordEvent.OnResetPassword) },
                onBack = { viewModel.onEvent(ForgotPasswordEvent.OnBackPressed) }
            )
        }
    }
}

/**
 * Экран 1: Ввод email для восстановления пароля
 */
@Composable
private fun ForgotPasswordStep1Screen(
    email: String,
    emailError: String?,
    isLoading: Boolean,
    generalError: String?,
    onEmailChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    GenericStepScaffold(
        title = "Восстановление пароля (1/3)",
        onBack = onBack,
        onNext = onContinue,
        isLoading = isLoading,
        nextLabel = "Продолжить",
        isNextEnabled = email.isNotBlank() && emailError == null
    ) {
        StyledTextField(
            value = email,
            onValueChange = onEmailChanged,
            label = "Введите почту",
            placeholder = "Почта...",
            keyboardType = KeyboardType.Email,
        )

        if (emailError != null) {
            ErrorText(
                emailError,
                modifier = Modifier.padding(
                    top = UiTokens.InlineErrorTopPadding,
                    start = UiTokens.InlineErrorStartPadding,
                ),
            )
        }

        if (generalError != null) {
            ErrorText(generalError, modifier = Modifier.padding(top = 16.dp, start = 32.dp))
        }
    }
}

/**
 * Экран 2: Верификация кода
 */
@Composable
private fun ForgotPasswordStep2Screen(
    email: String,
    verificationCode: String,
    verificationCodeError: String?,
    resendCodeCooldown: Int,
    isLoading: Boolean,
    generalError: String?,
    onVerificationCodeChanged: (String) -> Unit,
    onResendCode: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val cooldown = resendCodeCooldown
    val cooldownFormatted = String.format(Locale.ROOT, "%02d:%02d", cooldown / 60, cooldown % 60)

    GenericStepScaffold(
        title = "Восстановление пароля (2/3)",
        onBack = onBack,
        onNext = onContinue,
        isLoading = isLoading,
        nextLabel = "Продолжить",
        isNextEnabled = verificationCode.isNotBlank() && verificationCode.length == 6
            && verificationCodeError == null
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Код отправлен на ",
                style = MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = ColorLink,
                textAlign = TextAlign.Center,
            )
        }

        StyledTextField(
            value = verificationCode,
            onValueChange = onVerificationCodeChanged,
            label = "Введите код подтверждения",
            placeholder = "Код подтверждения...",
            keyboardType = KeyboardType.Number,
        )

        Spacer(Modifier.height(25.dp))

        if (cooldown > 0) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Запросить новый код можно через ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = cooldownFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(25.dp))

        Button(
            onClick = onResendCode,
            enabled = cooldown == 0 && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(UiTokens.ButtonHeight),
            shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorBackground,
                contentColor = ColorPrimary,
                disabledContainerColor = ColorPlaceholder,
                disabledContentColor = ColorWhite,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = UiTokens.BorderWidthThick,
                color = if (cooldown == 0) ColorPrimary else ColorPlaceholder,
            ),
        ) {
            Text(
                text = "Отправить код повторно",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (verificationCodeError != null) {
            ErrorText(
                verificationCodeError,
                modifier = Modifier.padding(
                    top = UiTokens.InlineErrorTopPadding,
                    start = UiTokens.InlineErrorStartPadding,
                ),
            )
        }

        if (generalError != null) {
            ErrorText(generalError, modifier = Modifier.padding(top = 16.dp, start = 32.dp))
        }
    }
}

/**
 * Экран 3: Ввод нового пароля и финализация
 */
@Composable
private fun ForgotPasswordStep3Screen(
    newPassword: String,
    newPasswordError: String?,
    newPasswordVisibility: Boolean,
    confirmPassword: String,
    confirmPasswordError: String?,
    confirmPasswordVisibility: Boolean,
    isLoading: Boolean,
    generalError: String?,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onToggleNewPasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onResetPassword: () -> Unit,
    onBack: () -> Unit
) {
    GenericStepScaffold(
        title = "Восстановление пароля (3/3)",
        onBack = onBack,
        onNext = onResetPassword,
        isLoading = isLoading,
        nextLabel = "Сбросить пароль",
        isNextEnabled = newPassword.isNotBlank() && confirmPassword.isNotBlank() 
            && newPasswordError == null && confirmPasswordError == null
    ) {
        StyledTextField(
            value = newPassword,
            onValueChange = onNewPasswordChanged,
            label = "Введите новый пароль",
            placeholder = "Пароль...",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            isPasswordVisible = newPasswordVisibility,
            onTogglePasswordVisibility = onToggleNewPasswordVisibility,
        )

        if (newPasswordError != null) {
            ErrorText(
                newPasswordError,
                modifier = Modifier.padding(
                    top = UiTokens.InlineErrorTopPadding,
                    start = UiTokens.InlineErrorStartPadding,
                ),
            )
        }

        Spacer(Modifier.height(16.dp))

        StyledTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChanged,
            label = "Повторите новый пароль",
            placeholder = "Повторите пароль...",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            isPasswordVisible = confirmPasswordVisibility,
            onTogglePasswordVisibility = onToggleConfirmPasswordVisibility,
        )

        if (confirmPasswordError != null) {
            ErrorText(
                confirmPasswordError,
                modifier = Modifier.padding(
                    top = UiTokens.InlineErrorTopPadding,
                    start = UiTokens.InlineErrorStartPadding,
                ),
            )
        }

        if (generalError != null) {
            ErrorText(generalError, modifier = Modifier.padding(top = 16.dp, start = 32.dp))
        }
    }
}

/** Текстовое поле с лейблом в стиле макета */
@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
) {
    val appliedTransformation = when {
        isPassword && !isPasswordVisible -> PasswordVisualTransformation()
        else -> visualTransformation
    }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier
                .padding(start = 15.dp),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(UiTokens.BorderWidthThick, ColorPrimary, RoundedCornerShape(UiTokens.CornerRadiusMedium))
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorPlaceholder,
                    )
                },
                visualTransformation = appliedTransformation,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = capitalization,
                    imeAction = imeAction,
                ),
                trailingIcon = if (isPassword && onTogglePasswordVisibility != null) {
                    {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility
                                              else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Скрыть пароль"
                                                     else "Показать пароль",
                                tint = ColorPlaceholder,
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorPrimary,
                    focusedContainerColor = ColorBackground,
                    unfocusedContainerColor = ColorBackground,
                    cursorColor = ColorPrimary,
                    focusedTextColor = ColorPrimary,
                    unfocusedTextColor = ColorPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(UiTokens.ButtonHeight),
            )
        }
    }
}

/**
 * Общий Scaffold для всех шагов восстановления пароля
 * Содержит TopBar, контент и кнопку внизу (как в RegisterScreen)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericStepScaffold(
    title: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    isLoading: Boolean,
    nextLabel: String,
    isNextEnabled: Boolean,
    content: @Composable () -> Unit
) {
    BackHandler { onBack() }
    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorBackground,
                ),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = UiTokens.BottomActionHorizontalPadding,
                        vertical = UiTokens.BottomActionVerticalPadding,
                    ),
            ) {
                Button(
                    onClick = onNext,
                    enabled = !isLoading && isNextEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(UiTokens.ButtonHeight),
                    shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = ColorWhite,
                        disabledContainerColor = ColorPlaceholder,
                        disabledContentColor = ColorWhite,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(UiTokens.ButtonLoadingIndicatorSize),
                            color = ColorWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = nextLabel,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = UiTokens.ScreenHorizontalPadding,
                    vertical = UiTokens.ContentVerticalPadding,
                ),
        ) {
            Spacer(Modifier.height(80.dp))
            StepTitle(title)
            Spacer(Modifier.height(UiTokens.SectionSpacing))
            content()
        }
    }
}

/**
 * Компонент заголовка с горизонтальными разделителями по бокам
 * (как в RegisterScreen)
 */
@Composable
private fun StepTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = ColorPrimary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = ColorPrimary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = ColorPrimary,
        )
    }
}

/**
 * Компонент текста ошибки
 */
@Composable
private fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
    )
}
