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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.UserPurpose

// ── Цвета дизайна ────────────────────────────────────────────────────────────
private val ColorPrimary     = Color(0xFF0A1928)
private val ColorPlaceholder = Color(0xFF525760)
private val ColorBackground  = Color.White
private val ColorWhite       = Color.White

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
        )
        2 -> RegisterStep2(
            state = state,
            onPurposeChange = onPurposeChange,
            onNext = onNext,
            onBack = onBack,
        )
        3 -> RegisterStep3(
            state = state,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
            onToggleConfirmPasswordVisibility = onToggleConfirmPasswordVisibility,
            onTermsAcceptedChange = onTermsAcceptedChange,
            onNext = onNext,
            onBack = onBack,
        )
        4 -> RegisterStep4(
            state = state,
            onVerificationCodeChange = onVerificationCodeChange,
            onResendCode = onResendCode,
            onNext = onNext,
            onBack = onBack,
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
) {
    RegisterScaffold(
        title = "Личные данные (1/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Продолжить",
        isLoading = state.isLoading,
    ) {
        StyledTextField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            label = "Имя",
            placeholder = "Имя...",
            keyboardType = KeyboardType.Text,
        )

        Spacer(Modifier.height(16.dp))

        StyledTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = "Имя пользователя",
            placeholder = "@Имя пользователя...",
            keyboardType = KeyboardType.Text,
        )

        Spacer(Modifier.height(16.dp))

        StyledTextField(
            value = state.birthDate,
            onValueChange = onBirthDateChange,
            label = "Дата рождения",
            placeholder = "дд.мм.гггг",
            keyboardType = KeyboardType.Number,
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
) {
    RegisterScaffold(
        title = "Цель использования (2/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Продолжить",
        isLoading = state.isLoading,
    ) {
        Text(
            text = "Почему вы установили приложение?",
            fontSize = 16.sp,
            color = ColorPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.selectableGroup()) {
            PurposeOption(
                text = "Я спортсмен и хочу отслеживать свои тренировки",
                selected = state.purpose == UserPurpose.ATHLETE,
                onClick = { onPurposeChange(UserPurpose.ATHLETE) },
            )
            PurposeOption(
                text = "Я хочу ознакомиться с функционалом приложения",
                selected = state.purpose == UserPurpose.EXPLORING,
                onClick = { onPurposeChange(UserPurpose.EXPLORING) },
            )
            PurposeOption(
                text = "Я тренер и хочу создать свой клуб",
                selected = state.purpose == UserPurpose.TRAINER,
                onClick = { onPurposeChange(UserPurpose.TRAINER) },
            )
            PurposeOption(
                text = "Я владелец клуба и хочу создать свой клуб",
                selected = state.purpose == UserPurpose.CLUB_OWNER,
                onClick = { onPurposeChange(UserPurpose.CLUB_OWNER) },
            )
            PurposeOption(
                text = "Ни одна из причин не подходит",
                selected = state.purpose == UserPurpose.OTHER,
                onClick = { onPurposeChange(UserPurpose.OTHER) },
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
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    RegisterScaffold(
        title = "Безопасность и доступ (3/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Продолжить",
        isLoading = state.isLoading,
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
            Text(
                text = "Продолжая, вы соглашаетесь с Условиями использования и Политикой конфиденциальности",
                fontSize = 14.sp,
                color = ColorPrimary,
                modifier = Modifier.padding(top = 12.dp),
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
) {
    val cooldown = state.resendCooldownSeconds
    val cooldownFormatted = String.format("%02d:%02d", cooldown / 60, cooldown % 60)

    RegisterScaffold(
        title = "Подтверждение почты (4/4)",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Создать аккаунт",
        isLoading = state.isLoading,
    ) {
        Text(
            text = "Код отправлен на ${state.email}.",
            fontSize = 16.sp,
            color = ColorPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )

        StyledTextField(
            value = state.verificationCode,
            onValueChange = onVerificationCodeChange,
            label = "Введите код подтверждения",
            placeholder = "Код подтверждения...",
            keyboardType = KeyboardType.Number,
        )

        Spacer(Modifier.height(8.dp))

        if (cooldown > 0) {
            Text(
                text = "Запросить новый код можно через $cooldownFormatted",
                fontSize = 14.sp,
                color = ColorPlaceholder,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

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
                disabledContainerColor = ColorBackground,
                disabledContentColor = ColorPlaceholder,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (cooldown == 0) ColorPrimary else ColorPlaceholder,
            ),
        ) {
            Text(
                text = "Отправить код повторно",
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
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
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {},
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
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Button(
                    onClick = onNext,
                    enabled = !isLoading,
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
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
            fontSize = 20.sp,
            color = ColorPrimary,
            fontWeight = FontWeight.Light,
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
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
) {
    val visualTransformation = if (isPassword && !isPasswordVisible)
        PasswordVisualTransformation() else VisualTransformation.None

    Column {
        Text(
            text = label,
            fontSize = 16.sp,
            color = ColorPrimary,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 16.sp,
                    color = ColorPlaceholder,
                )
            },
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
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

/** Выбор пола: Мужской / Женский */
@Composable
private fun GenderSelector(
    selected: Gender?,
    onSelect: (Gender) -> Unit,
) {
    Column {
        Text(
            text = "Выберите пол",
            fontSize = 16.sp,
            color = ColorPrimary,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.selectableGroup(),
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
                        fontSize = 16.sp,
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
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = ColorPrimary,
                unselectedColor = ColorPrimary,
            ),
        )
        Text(
            text = text,
            fontSize = 16.sp,
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
