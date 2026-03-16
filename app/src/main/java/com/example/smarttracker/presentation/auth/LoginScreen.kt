package com.example.smarttracker.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.presentation.theme.SmartTrackerTheme

// ── Цвета дизайна ────────────────────────────────────────────────────────────
private val ColorPrimary     = Color(0xFF0A1928)
private val ColorPlaceholder = Color(0xFF525760)
private val ColorBackground  = Color.White
private val ColorWhite       = Color.White
private val ColorLink        = Color(0xFF0066CC)

/**
 * МОБ-3.2 — Экран входа в приложение.
 *
 * Макет:
 *   - Заголовок "Вход в SmartTracker"
 *   - Поле email
 *   - Поле пароля (с иконкой показать/скрыть)
 *   - Ошибка (если есть)
 *   - Кнопка "Войти" (disabled при пустых полях)
 *   - Индикатор загрузки при isLoading
 *   - Ссылка "Нет аккаунта? Зарегистрируйтесь"
 *   - Ссылка "Забыли пароль?"
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmitLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToPasswordRecovery: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ColorBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ── Заголовок ────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(80.dp))

            Text(
                text = "Вход в SmartTracker",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Email ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                placeholder = { Text("example@mail.com", color = ColorPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorPlaceholder,
                    focusedLabelColor = ColorPrimary,
                    unfocusedLabelColor = ColorPlaceholder,
                    cursorColor = ColorPrimary
                ),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Пароль") },
                placeholder = { Text("Минимум 8 символов", color = ColorPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (state.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        enabled = !state.isLoading
                    ) {
                        Icon(
                            imageVector = if (state.isPasswordVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (state.isPasswordVisible) "Hide password" else "Show password",
                            tint = ColorPrimary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorPlaceholder,
                    focusedLabelColor = ColorPrimary,
                    unfocusedLabelColor = ColorPlaceholder,
                    cursorColor = ColorPrimary
                ),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Error Message ────────────────────────────────────────────────
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Кнопка "Войти" ───────────────────────────────────────────────
            Button(
                onClick = onSubmitLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorPrimary,
                    disabledContainerColor = ColorPlaceholder
                ),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        color = ColorWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Войти",
                        color = ColorWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Ссылка "Забыли пароль?" ─────────────────────────────────────
            TextButton(
                onClick = onNavigateToPasswordRecovery,
                enabled = !state.isLoading
            ) {
                Text(
                    text = "Забыли пароль?",
                    color = ColorLink,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Ссылка "Нет аккаунта? Зарегистрируйтесь" ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Нет аккаунта? ",
                    color = ColorPrimary,
                    fontSize = 14.sp
                )
                TextButton(
                    onClick = onNavigateToRegister,
                    enabled = !state.isLoading,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text(
                        text = "Зарегистрируйтесь",
                        color = ColorLink,
                        fontSize = 14.sp,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    SmartTrackerTheme {
        LoginScreen(
            state = LoginUiState(),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmitLogin = {},
            onNavigateToRegister = {},
            onNavigateToPasswordRecovery = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenLoadingPreview() {
    SmartTrackerTheme {
        LoginScreen(
            state = LoginUiState(isLoading = true),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmitLogin = {},
            onNavigateToRegister = {},
            onNavigateToPasswordRecovery = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenErrorPreview() {
    SmartTrackerTheme {
        LoginScreen(
            state = LoginUiState(
                email = "user@example.com",
                errorMessage = "Неверный пароль"
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmitLogin = {},
            onNavigateToRegister = {},
            onNavigateToPasswordRecovery = {}
        )
    }
}
