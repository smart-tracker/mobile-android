package com.example.smarttracker.presentation.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
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
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.UserPurpose
import com.example.smarttracker.presentation.theme.SmartTrackerTheme

// ── Цвета дизайна ────────────────────────────────────────────────────────────
private val ColorPrimary     = Color(0xFF0A1928)
private val ColorPlaceholder = Color(0xFF525760)
private val ColorBackground  = Color.White
private val ColorWhite       = Color.White
private val ColorCheckboxChecked = Color(0xFF4DACA7)  // бирюзово-зелёный

// ── Корневой composable ───────────────────────────────────────────────────────

@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onFirstNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onPurposeChange: (UserPurpose) -> Unit,
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
            onPurposeChange = onPurposeChange,
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

// ── Компонент: Поле для nickname с проверкой уникальности ──────────────────

@Composable
private fun NicknameField(
    value: String,
    onValueChange: (String) -> Unit,
    checkStatus: NicknameCheckStatus,
) {
    val borderColor = when (checkStatus) {
        NicknameCheckStatus.IDLE -> ColorPrimary
        NicknameCheckStatus.CHECKING -> ColorPlaceholder
        is NicknameCheckStatus.SUCCESS -> Color(0xFF4CAF50)  // Зелёный
        is NicknameCheckStatus.ERROR -> Color(0xFFE74C3C)    // Красный
    }

    Column {
        Text(
            text = "Имя пользователя",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier
                .padding(start = 15.dp),
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, borderColor, RoundedCornerShape(10.dp))
                .background(Color.White)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        "@Имя пользователя...",
                        color = ColorPlaceholder,
                        fontSize = 14.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedTextColor = ColorPrimary,
                    unfocusedTextColor = ColorPrimary,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    when (checkStatus) {
                        is NicknameCheckStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Никнейм доступен",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        is NicknameCheckStatus.ERROR -> {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Никнейм занят",
                                tint = Color(0xFFE74C3C),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        else -> {}  // IDLE и CHECKING ничего не показываем
                    }
                },
            )
        }
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
        StyledTextField(
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
    onPurposeChange: (UserPurpose) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    isNextEnabled: Boolean = false,
) {
    RegisterScaffold(
        title = "Цель использования (2/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Продолжить",
        isLoading = state.isLoading,
        isNextEnabled = isNextEnabled,
    ) {
        Text(
            text = "Почему вы установили приложение?",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier
                .padding(start = 15.dp),
        )

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.selectableGroup()) {
            PurposeOption(
                text = "Я тренируюсь - мне нужен трекер для занятий",
                selected = state.purpose == UserPurpose.ATHLETE,
                onClick = { onPurposeChange(UserPurpose.ATHLETE) },
            )
            PurposeOption(
                text = "Я тренирую - мне нужно средство для организации тренировок",
                selected = state.purpose == UserPurpose.TRAINER,
                onClick = { onPurposeChange(UserPurpose.TRAINER) },
            )
            PurposeOption(
                text = "Я организатор клуба - мне нужен инструмент для контроля его работы.",
                selected = state.purpose == UserPurpose.CLUB_OWNER,
                onClick = { onPurposeChange(UserPurpose.CLUB_OWNER) },
            )
            PurposeOption(
                text = "Просто посмотреть",
                selected = state.purpose == UserPurpose.EXPLORING,
                onClick = { onPurposeChange(UserPurpose.EXPLORING) },
            )
        }

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
        StyledTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Почта",
            placeholder = "Почта...",
            keyboardType = KeyboardType.Email,
        )

        Spacer(Modifier.height(16.dp))

        StyledTextField(
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

        StyledTextField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Повторите пароль",
            placeholder = "Повторите пароль...",
            keyboardType = KeyboardType.Password,
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
            
            val linkColor = Color(0xFF00BCD4)  // Бирюзовый цвет для ссылок
            val annotatedText = buildAnnotatedString {
                append("Продолжая, вы соглашаетесь с ")
                
                pushStringAnnotation(tag = "TERMS", annotation = "true")
                withStyle(style = SpanStyle(color = linkColor)) {
                    append("Условиями использования")
                }
                pop()
                
                append(" и ")
                
                pushStringAnnotation(tag = "PRIVACY", annotation = "true")
                withStyle(style = SpanStyle(color = linkColor)) {
                    append("Политикой конфиденциальности")
                }
                pop()
            }
            
            ClickableText(
                text = annotatedText,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(color = ColorPrimary),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .padding(start = 8.dp),
                onClick = { offset: Int ->
                    annotatedText.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
                        .firstOrNull()?.let {
                            onOpenTermsOfService()
                        }
                    annotatedText.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
                        .firstOrNull()?.let {
                            onOpenPrivacyPolicy()
                        }
                }
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
    val cooldownFormatted = String.format("%02d:%02d", cooldown / 60, cooldown % 60)

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
                color = Color(0xFF00BCD4),  // Бирюзовый для email
                textAlign = TextAlign.Center,
            )
            Text(
                text = ".",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                textAlign = TextAlign.Center,
            )
        }

        StyledTextField(
            value = state.verificationCode,
            onValueChange = onVerificationCodeChange,
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
                .height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorBackground,
                contentColor = ColorPrimary,
                disabledContainerColor = ColorPlaceholder,
                disabledContentColor = ColorWhite,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
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

// ── Общие composable-компоненты ───────────────────────────────────────────────

/** Каркас экрана: TopBar, форма (scroll), кнопка снизу */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterScaffold(
    title: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    isLoading: Boolean,
    isNextEnabled: Boolean = true,
    content: @Composable () -> Unit,
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
                    .padding(horizontal = 16.dp, vertical = 50.dp),
            ) {
                Button(
                    onClick = onNext,
                    enabled = !isLoading && isNextEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = ColorWhite,
                        disabledContainerColor = ColorPlaceholder,
                        disabledContentColor = ColorWhite,
                    ),
                ) {
                    Text(
                        text = if (isLoading) "Загрузка..." else nextLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Spacer(Modifier.height(80.dp))
            StepTitle(title)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

/** Заголовок шага с горизонтальными линиями по бокам */
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
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
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
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier
                .padding(start = 15.dp),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, ColorPrimary, RoundedCornerShape(10.dp))
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
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
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility
                                              else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Скрыть пароль"
                                                     else "Показать пароль",
                                tint = ColorPlaceholder,
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
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
                    .height(50.dp),
            )
        }
    }
}

/** Выбор пола: Мужской / Женский */
@Composable
private fun GenderSelector(
    selected: Gender?,
    onSelect: (Gender) -> Unit,
) {
    Column {
        Text(
            text = "Выберите пол",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier
                .padding(start = 15.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(start = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Gender.entries.forEach { gender ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .selectable(
                            selected = selected == gender,
                            onClick = { onSelect(gender) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = selected == gender,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ColorPrimary,
                            unselectedColor = ColorPrimary,
                        ),
                    )
                    Text(
                        text = if (gender == Gender.MALE) "Мужской" else "Женский",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = ColorPrimary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Одна строка выбора цели использования */
@Composable
private fun PurposeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            modifier = if (selected) {
                Modifier.border(2.dp, Color.Black)
            } else {
                Modifier
            },
            colors = CheckboxDefaults.colors(
                checkedColor = ColorCheckboxChecked,
                uncheckedColor = Color.Black,
                checkmarkColor = Color.Black,
            ),
        )
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Текст ошибки */
@Composable
private fun ErrorText(message: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        fontSize = 14.sp,
        color = Color.Red,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Визуальная трансформация для поля даты: state хранит цифры "04052004", отображается "04.05.2004" */
private class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = buildString {
            for (i in digits.indices) {
                if (i == 2 || i == 4) append('.')
                append(digits[i])
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else        -> offset + 2
            }
            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset == 3 -> 2
                offset <= 5 -> offset - 1
                offset == 6 -> 4
                else        -> offset - 2
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

/**
 * Поле даты рождения: текстовый ввод + кнопка календаря.
 * [value] — цифры "04052004", [onValueChange] — коллбэк аналогично полю даты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Дата рождения",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier
                .padding(start = 15.dp),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, ColorPrimary, RoundedCornerShape(10.dp))
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(8)
                    onValueChange(digits)
                },
                placeholder = {
                    Text(
                        text = "дд.мм.гггг",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = ColorPlaceholder,
                    )
                },
                visualTransformation = DateVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Выбрать дату",
                            tint = ColorPlaceholder,
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
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
                    .height(50.dp),
            )
        }
    }

    if (showPicker) {
        // Установить начальную дату из уже введённых цифр (если есть)
        val initialMillis: Long? = if (value.length == 8) {
            try {
                val day   = value.substring(0, 2).toInt()
                val month = value.substring(2, 4).toInt()
                val year  = value.substring(4, 8).toInt()
                val cal = Calendar.getInstance().apply {
                    set(year, month - 1, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            } catch (e: Exception) { null }
        } else null

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance().apply { timeInMillis = millis }
                        val d = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                        val m = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                        val y = cal.get(Calendar.YEAR).toString()
                        onValueChange("$d$m$y")
                    }
                }) { Text("Ок") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewCallbacks = object {
    val onString: (String) -> Unit = {}
    val onBool: (Boolean) -> Unit = {}
    val onGender: (Gender) -> Unit = {}
    val onPurpose: (UserPurpose) -> Unit = {}
    val onUnit: () -> Unit = {}
}

// ── Документы ───────────────────────────────────────────────────────────────────

/**
 * Экран "Условия использования"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Условия использования",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = ColorPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = ColorPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorBackground,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Условия использования SmartTracker",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = """Добро пожаловать в SmartTracker!

Наше мобильное приложение предоставляет услуги для отслеживания Вашей физической активности и здоровья. 

Используя приложение, Вы согласны с данными Условиями использования. Пожалуйста, внимательно прочитайте все условия перед использованием приложения.

1. Использование приложения

Вы используете приложение SmartTracker на собственный риск. Мы не несем ответственность за любые прямые или косвенные убытки, вызванные использованием приложения.

2. Конфиденциальность данных

Ваши личные данные обрабатываются в соответствии с нашей Политикой конфиденциальности. Мы не передаем Ваши данные третьим лицам без Вашего согласия.

3. Интеллектуальная собственность

Все содержимое приложения, включая текст, графику, логотипы и изображения, защищено авторским правом и не может быть использовано без нашего разрешения.

4. Ограничение ответственности

SmartTracker не несет ответственность за любые технические сбои, потерю данных или прерывание услуги.

5. Изменение условий

Мы оставляем право изменять данные условия в любое время. Ваше продолжение использования приложения после изменения условий означает Ваше согласие с ними.

Дата последнего обновления: 18 марта 2026 г.
Вопросы? Свяжитесь с нами: support@smarttracker.com
""",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

/**
 * Экран "Политика конфиденциальности"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Политика конфиденциальности",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = ColorPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = ColorPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorBackground,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Политика конфиденциальности SmartTracker",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = """SmartTracker уважает Вашу приватность и обеспечивает защиту Ваших личных данных.

1. Сбор информации

Мы собираем следующую информацию:
- Персональные данные (имя, адрес электронной почты, дата рождения)
- Данные о физической активности
- Целевые показатели здоровья
- Технические данные устройства (тип устройства, операционная система)

2. Использование информации

Ваша информация используется для:
- Предоставления и улучшения услуг приложения
- Анализа статистики использования
- Отправки обновлений и уведомлений
- Поддержания безопасности и предотвращения мошенничества

3. Защита данных

Мы используем современные методы шифрования для защиты Ваших данных. Ваша информация хранится на защищенных серверах.

4. Передача данных третьим лицам

Мы НЕ продаем и НЕ передаем Вашу личную информацию третьим лицам, кроме случаев, когда это требуется законом или с Вашего прямого согласия.

5. Cookies и аналитика

Приложение может использовать технологии отслеживания для анализа использования и улучшения пользовательского опыта.

6. Контакт с нами

Если у Вас есть вопросы о данной политике или о том, как мы обрабатываем Ваши данные, пожалуйста, свяжитесь с нами:

Email: privacy@smarttracker.com

7. Изменение политики

Мы оставляем право изменять данную политику. Изменения вступают в силу после опубликования на приложении.

Дата последнего обновления: 18 марта 2026 г.
""",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Preview(name = "Шаг 1 — Личные данные", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewStep1() {
    SmartTrackerTheme {
        RegisterScreen(
            state = RegisterUiState(step = 1, firstName = "", gender = Gender.MALE),
            onFirstNameChange = previewCallbacks.onString,
            onUsernameChange = previewCallbacks.onString,
            onBirthDateChange = previewCallbacks.onString,
            onGenderChange = previewCallbacks.onGender,
            onPurposeChange = previewCallbacks.onPurpose,
            onEmailChange = previewCallbacks.onString,
            onPasswordChange = previewCallbacks.onString,
            onConfirmPasswordChange = previewCallbacks.onString,
            onTogglePasswordVisibility = previewCallbacks.onUnit,
            onToggleConfirmPasswordVisibility = previewCallbacks.onUnit,
            onTermsAcceptedChange = previewCallbacks.onBool,
            onVerificationCodeChange = previewCallbacks.onString,
            onResendCode = previewCallbacks.onUnit,
            onNext = previewCallbacks.onUnit,
            onBack = previewCallbacks.onUnit,
            isStep1Complete = false,
        )
    }
}

@Preview(name = "Шаг 2 — Цель использования", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewStep2() {
    SmartTrackerTheme {
        RegisterScreen(
            state = RegisterUiState(step = 2, purpose = UserPurpose.ATHLETE),
            onFirstNameChange = previewCallbacks.onString,
            onUsernameChange = previewCallbacks.onString,
            onBirthDateChange = previewCallbacks.onString,
            onGenderChange = previewCallbacks.onGender,
            onPurposeChange = previewCallbacks.onPurpose,
            onEmailChange = previewCallbacks.onString,
            onPasswordChange = previewCallbacks.onString,
            onConfirmPasswordChange = previewCallbacks.onString,
            onTogglePasswordVisibility = previewCallbacks.onUnit,
            onToggleConfirmPasswordVisibility = previewCallbacks.onUnit,
            onTermsAcceptedChange = previewCallbacks.onBool,
            onVerificationCodeChange = previewCallbacks.onString,
            onResendCode = previewCallbacks.onUnit,
            onNext = previewCallbacks.onUnit,
            onBack = previewCallbacks.onUnit,
            isStep2Complete = true,
        )
    }
}

@Preview(name = "Шаг 3 — Безопасность", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewStep3() {
    SmartTrackerTheme {
        RegisterScreen(
            state = RegisterUiState(step = 3, termsAccepted = true),
            onFirstNameChange = previewCallbacks.onString,
            onUsernameChange = previewCallbacks.onString,
            onBirthDateChange = previewCallbacks.onString,
            onGenderChange = previewCallbacks.onGender,
            onPurposeChange = previewCallbacks.onPurpose,
            onEmailChange = previewCallbacks.onString,
            onPasswordChange = previewCallbacks.onString,
            onConfirmPasswordChange = previewCallbacks.onString,
            onTogglePasswordVisibility = previewCallbacks.onUnit,
            onToggleConfirmPasswordVisibility = previewCallbacks.onUnit,
            onTermsAcceptedChange = previewCallbacks.onBool,
            onVerificationCodeChange = previewCallbacks.onString,
            onResendCode = previewCallbacks.onUnit,
            onNext = previewCallbacks.onUnit,
            onBack = previewCallbacks.onUnit,
            isStep3Complete = false,
        )
    }
}

@Preview(name = "Шаг 4 — Подтверждение почты", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewStep4() {
    SmartTrackerTheme {
        RegisterScreen(
            state = RegisterUiState(
                step = 4,
                email = "user@example.com",
                resendCooldownSeconds = 97,
            ),
            onFirstNameChange = previewCallbacks.onString,
            onUsernameChange = previewCallbacks.onString,
            onBirthDateChange = previewCallbacks.onString,
            onGenderChange = previewCallbacks.onGender,
            onPurposeChange = previewCallbacks.onPurpose,
            onEmailChange = previewCallbacks.onString,
            onPasswordChange = previewCallbacks.onString,
            onConfirmPasswordChange = previewCallbacks.onString,
            onTogglePasswordVisibility = previewCallbacks.onUnit,
            onToggleConfirmPasswordVisibility = previewCallbacks.onUnit,
            onTermsAcceptedChange = previewCallbacks.onBool,
            onVerificationCodeChange = previewCallbacks.onString,
            onResendCode = previewCallbacks.onUnit,
            onNext = previewCallbacks.onUnit,
            onBack = previewCallbacks.onUnit,
            isStep4Complete = false,
        )
    }
}

