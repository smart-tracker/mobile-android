package com.example.smarttracker.presentation.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorPlaceholder
import com.example.smarttracker.presentation.theme.ColorPrimary

/**
 * Универсальное текстовое поле для auth-экранов.
 *
 * Объединяет дублировавшиеся RegisterStyledTextField и StyledTextField.
 * Стилизованное поле с меткой сверху, акцентной рамкой и поддержкой пароля.
 */
@Composable
fun StyledTextField(
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val appliedTransformation = when {
        isPassword && !isPasswordVisible -> PasswordVisualTransformation()
        else -> visualTransformation
    }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ColorPrimary,
            modifier = Modifier.padding(start = 15.dp),
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
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                ),
                trailingIcon = if (isPassword && onTogglePasswordVisibility != null) {
                    {
                        IconButton(
                            onClick = onTogglePasswordVisibility,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility
                                              else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Скрыть пароль"
                                                     else "Показать пароль",
                                tint = ColorPlaceholder,
                                modifier = Modifier.size(24.dp),
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
