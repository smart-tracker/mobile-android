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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
        StyledTextField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            label = "Имя",
            placeholder = "Имя...",
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words,
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
            fontSize = 16.sp,
            color = ColorPrimary,
            modifier = Modifier.fillMaxWidth(),
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
            fontSize = 20.sp,
            color = ColorPrimary,
            fontWeight = FontWeight.SemiBold,
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
            fontSize = 16.sp,
            color = ColorPrimary,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(8)
                onValueChange(digits)
            },
            placeholder = {
                Text(
                    text = "дд.мм.гггг",
                    fontSize = 16.sp,
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

