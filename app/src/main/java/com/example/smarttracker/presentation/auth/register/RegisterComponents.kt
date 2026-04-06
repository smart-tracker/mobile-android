package com.example.smarttracker.presentation.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.GoalResponse
import com.example.smarttracker.presentation.common.StepScaffold
import com.example.smarttracker.presentation.common.StepTitle
import com.example.smarttracker.presentation.common.StyledTextField
import com.example.smarttracker.presentation.common.UiTokens
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorPlaceholder
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import java.util.Calendar

/** Переиспользуемые Compose-компоненты для экранов многошаговой регистрации. */
@Composable
internal fun NicknameField(
    value: String,
    onValueChange: (String) -> Unit,
    checkStatus: NicknameCheckStatus,
) {
    val focusManager = LocalFocusManager.current

    val borderColor = when (checkStatus) {
        NicknameCheckStatus.IDLE -> ColorPrimary
        NicknameCheckStatus.CHECKING -> ColorPlaceholder
        is NicknameCheckStatus.SUCCESS -> Color(0xFF4CAF50)
        is NicknameCheckStatus.ERROR -> Color(0xFFE74C3C)
    }

    Column {
        Text(
            text = "Имя пользователя",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier.padding(start = 15.dp),
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(UiTokens.BorderWidthThick, borderColor, RoundedCornerShape(UiTokens.CornerRadiusMedium))
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
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                trailingIcon = {
                    when (checkStatus) {
                        is NicknameCheckStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Никнейм доступен",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        is NicknameCheckStatus.ERROR -> {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Никнейм занят",
                                tint = Color(0xFFE74C3C),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        else -> Unit
                    }
                },
            )
        }
    }
}

@Composable
internal fun GoalSelectionItem(
    goal: GoalResponse,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .border(UiTokens.BorderWidthThick, ColorPrimary)
                .size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ColorSecondary,
                    uncheckedColor = Color.Transparent,
                    checkmarkColor = Color.Black,
                ),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = goal.description,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun RegisterScaffold(
    title: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    isLoading: Boolean,
    isNextEnabled: Boolean = true,
    content: @Composable () -> Unit,
) = StepScaffold(title, onBack, onNext, nextLabel, isLoading, isNextEnabled, content)

@Composable
internal fun RegisterStepTitle(text: String) = StepTitle(text)

@Composable
internal fun RegisterStyledTextField(
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
) = StyledTextField(value, onValueChange, label, placeholder, keyboardType, capitalization, imeAction, visualTransformation, isPassword, isPasswordVisible, onTogglePasswordVisibility)

@Composable
internal fun GenderSelector(
    selected: Gender?,
    onSelect: (Gender) -> Unit,
) {
    Column {
        Text(
            text = "Выберите пол",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier.padding(start = 15.dp),
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

@Composable
internal fun PurposeOption(
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
            modifier = if (selected) Modifier.border(UiTokens.BorderWidthThick, Color.Black) else Modifier,
            colors = CheckboxDefaults.colors(
                checkedColor = ColorSecondary,
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

@Composable
internal fun ErrorText(message: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        fontSize = 14.sp,
        color = Color.Red,
        modifier = Modifier.fillMaxWidth(),
    )
}

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
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset == 3 -> 2
                offset <= 5 -> offset - 1
                offset == 6 -> 4
                else -> offset - 2
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    checkStatus: BirthDateCheckStatus = BirthDateCheckStatus.IDLE,
) {
    val focusManager = LocalFocusManager.current

    var showPicker by remember { mutableStateOf(false) }

    val borderColor = when (checkStatus) {
        BirthDateCheckStatus.IDLE -> ColorPrimary
        is BirthDateCheckStatus.SUCCESS -> Color(0xFF4CAF50)
        is BirthDateCheckStatus.ERROR -> Color(0xFFE74C3C)
    }

    Column {
        Text(
            text = "Дата рождения",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier.padding(start = 15.dp),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(UiTokens.BorderWidthThick, borderColor, RoundedCornerShape(UiTokens.CornerRadiusMedium))
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
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                trailingIcon = {
                    when (checkStatus) {
                        is BirthDateCheckStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Дата корректна",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        is BirthDateCheckStatus.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Некорректная дата",
                                tint = Color(0xFFE74C3C),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        else -> {
                            IconButton(onClick = { showPicker = true }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Выбрать дату",
                                    tint = ColorPlaceholder,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = borderColor,
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

    if (showPicker) {
        val initialMillis: Long? = if (value.length == 8) {
            try {
                val day = value.substring(0, 2).toInt()
                val month = value.substring(2, 4).toInt()
                val year = value.substring(4, 8).toInt()
                val cal = Calendar.getInstance().apply {
                    set(year, month - 1, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            } catch (e: Exception) {
                null
            }
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
