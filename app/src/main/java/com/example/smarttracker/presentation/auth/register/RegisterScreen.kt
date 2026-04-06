package com.example.smarttracker.presentation.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.GoalResponse
import com.example.smarttracker.domain.model.RoleResponse
import com.example.smarttracker.domain.model.UserPurpose
import com.example.smarttracker.presentation.common.UiTokens
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorLink
import com.example.smarttracker.presentation.theme.ColorPlaceholder
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.ColorWhite
import com.example.smarttracker.presentation.theme.SmartTrackerTheme

/** Многошаговый экран регистрации: email, пароль, данные профиля, верификация. */
// ── Корневой composable ───────────────────────────────────────────────────────

@Composable
@Suppress("UNUSED_PARAMETER")
fun RegisterScreen(
    state: RegisterUiState,
    onFirstNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onPurposeChange: (UserPurpose) -> Unit,
    onLoadAvailableGoals: () -> Unit = {},
    onGoalSelected: (Int) -> Unit = {},
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onVerificationCodeChange: (String) -> Unit,
    onResendCode: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenTermsOfService: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    isStep1Complete: Boolean = false,
    isStep2Complete: Boolean = false,
    isStep3Complete: Boolean = false,
    isStep4Complete: Boolean = false,
) {
    when (state.step) {
        1 -> RegisterStep1(
            state = state,
            onFirstNameChange = onFirstNameChange,
            onUsernameChange = onUsernameChange,
            onBirthDateChange = onBirthDateChange,
            onGenderChange = onGenderChange,
            onNext = onNext,
            onBack = onBack,
            isNextEnabled = isStep1Complete,
        )
        2 -> RegisterStep2(
            state = state,
            onLoadAvailableGoals = onLoadAvailableGoals,
            onGoalSelected = onGoalSelected,
            onNext = onNext,
            onBack = onBack,
            isNextEnabled = isStep2Complete,
        )
        3 -> RegisterStep3(
            state = state,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
            onToggleConfirmPasswordVisibility = onToggleConfirmPasswordVisibility,
            onTermsAcceptedChange = onTermsAcceptedChange,
            onOpenTermsOfService = onOpenTermsOfService,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onNext = onNext,
            onBack = onBack,
            isNextEnabled = isStep3Complete,
        )
        4 -> RegisterStep4(
            state = state,
            onVerificationCodeChange = onVerificationCodeChange,
            onResendCode = onResendCode,
            onNext = onNext,
            onBack = onBack,
            isNextEnabled = isStep4Complete,
        )
    }
}

// ── Шаг 1: Личные данные ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterStep1(
    state: RegisterUiState,
    onFirstNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    isNextEnabled: Boolean = false,
) {
    RegisterScaffold(
        title = "Личные данные (1/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Продолжить",
        isLoading = state.isLoading,
        isNextEnabled = isNextEnabled,
    ) {
        RegisterStyledTextField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            label = "Имя",
            placeholder = "Имя...",
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words,
        )

        Spacer(Modifier.height(16.dp))

        NicknameField(
            value = state.username,
            onValueChange = onUsernameChange,
            checkStatus = state.nicknameCheckStatus,
        )

        Spacer(Modifier.height(16.dp))

        DatePickerField(
            value = state.birthDate,
            onValueChange = onBirthDateChange,
            checkStatus = state.birthDateCheckStatus,
        )

        Spacer(Modifier.height(16.dp))

        GenderSelector(
            selected = state.gender,
            onSelect = onGenderChange,
        )

        state.fieldError?.let { ErrorText(it) }
        state.error?.let { ErrorText(it) }
    }
}

// ── Шаг 2: Цель использования ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterStep2(
    state: RegisterUiState,
    onLoadAvailableGoals: () -> Unit = {},
    onGoalSelected: (Int) -> Unit = {},
    onNext: () -> Unit,
    onBack: () -> Unit,
    isNextEnabled: Boolean = false,
) {
    // МОБ-6 — Загрузить доступные цели при первом отображении
    LaunchedEffect(Unit) {
        onLoadAvailableGoals()
    }

    RegisterScaffold(
        title = "Цель использования (2/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Продолжить",
        isLoading = state.isLoadingGoals,
        isNextEnabled = isNextEnabled,
    ) {
        Text(
            text = "Почему вы установили приложение?",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
//            modifier = Modifier.padding(start = 15.dp),
        )

        Spacer(Modifier.height(16.dp))

        // ── Список доступных целей ────────────────────────────────────────
        if (state.availableGoals.isEmpty() && !state.isLoadingGoals) {
            Text(
                text = "Ошибка загрузки целей. Попробуйте позже.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Color(0xFFE74C3C),
//                modifier = Modifier.padding(start = 15.dp),
            )
        } else {
            Column {
                state.availableGoals.forEach { goal ->
                    GoalSelectionItem(
                        goal = goal,
                        isSelected = state.selectedGoalId == goal.id,
                        onSelect = { onGoalSelected(goal.id) },
                    )
                }
            }
        }

        // ── Ошибки ───────────────────────────────────────────────────────────
        state.fieldError?.let { ErrorText(it) }
        state.error?.let { ErrorText(it) }
    }
}

// ── Шаг 3: Безопасность и доступ ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterStep3(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onOpenTermsOfService: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onNext: () -> Unit,
    onBack: () -> Unit,
    isNextEnabled: Boolean = false,
) {
    RegisterScaffold(
        title = "Безопасность и доступ (3/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Продолжить",
        isLoading = state.isLoading,
        isNextEnabled = isNextEnabled,
    ) {
        RegisterStyledTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Почта",
            placeholder = "Почта...",
            keyboardType = KeyboardType.Email,
        )

        Spacer(Modifier.height(16.dp))

        RegisterStyledTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Пароль",
            placeholder = "Пароль...",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            isPasswordVisible = state.isPasswordVisible,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
        )

        Spacer(Modifier.height(16.dp))

        RegisterStyledTextField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Повторите пароль",
            placeholder = "Повторите пароль...",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isPassword = true,
            isPasswordVisible = state.isConfirmPasswordVisible,
            onTogglePasswordVisibility = onToggleConfirmPasswordVisibility,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = state.termsAccepted,
                onCheckedChange = onTermsAcceptedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = ColorPrimary,
                    uncheckedColor = ColorPrimary,
                ),
            )
            
            val linkColor = ColorLink
            val annotatedText = buildAnnotatedString {
                append("Продолжая, вы соглашаетесь с ")

                withLink(
                    LinkAnnotation.Clickable(
                        tag = "TERMS",
                        styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                        linkInteractionListener = { onOpenTermsOfService() },
                    )
                ) {
                    append("Условиями использования")
                }

                append(" и ")

                withLink(
                    LinkAnnotation.Clickable(
                        tag = "PRIVACY",
                        styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                        linkInteractionListener = { onOpenPrivacyPolicy() },
                    )
                ) {
                    append("Политикой конфиденциальности")
                }
            }

            Text(
                text = annotatedText,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(color = ColorPrimary),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .padding(start = 8.dp),
            )
        }

        state.fieldError?.let { ErrorText(it) }
        state.error?.let { ErrorText(it) }
    }
}

// ── Шаг 4: Подтверждение почты ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterStep4(
    state: RegisterUiState,
    onVerificationCodeChange: (String) -> Unit,
    onResendCode: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    isNextEnabled: Boolean = false,
) {
    val cooldown = state.resendCooldownSeconds
    val cooldownFormatted = String.format(Locale.ROOT, "%02d:%02d", cooldown / 60, cooldown % 60)

    RegisterScaffold(
        title = "Подтверждение почты (4/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Создать аккаунт",
        isLoading = state.isLoading,
        isNextEnabled = isNextEnabled,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Код отправлен на ",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.email,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorLink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = ".",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                textAlign = TextAlign.Center,
            )
        }

        RegisterStyledTextField(
            value = state.verificationCode,
            onValueChange = onVerificationCodeChange,
            label = "Введите код подтверждения",
            placeholder = "Код подтверждения...",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        )

        Spacer(Modifier.height(25.dp))

        if (cooldown > 0) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Запросить новый код можно через ",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = ColorPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = cooldownFormatted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = ColorPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(25.dp))

        Button(
            onClick = onResendCode,
            enabled = cooldown == 0 && !state.isLoading,
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
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
        }

        state.fieldError?.let { ErrorText(it) }
        state.error?.let { ErrorText(it) }
    }
}


