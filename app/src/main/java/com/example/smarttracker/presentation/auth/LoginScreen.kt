package com.example.smarttracker.presentation.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.Image
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.SmartTrackerTheme

// ── Цвета дизайна ────────────────────────────────────────────────────────────
private val ColorPrimary     = Color(0xFF0A1928)
private val ColorSecondary   = Color(0xFF4DACA7)  // бирюзово-зелёный
private val ColorPlaceholder = Color(0xFF525760)
private val ColorBackground  = Color.White
private val ColorWhite       = Color.White
private val ColorLink        = Color(0xFF0066CC)
private val ColorDivider     = Color(0xFFE0E0E0)

/**
 * МОБ-3.2 — Экран входа в приложение.
 *
 * Макет по Figma (node 172:640):
 *   - Логотип вверху
 *   - Название приложения "SmartTracker"
 *   - Поле email
 *   - Поле пароля (с toggle)
 *   - Ссылка "Забыли пароль?" справа
 *   - Кнопка "Войти" (primary)
 *   - Кнопка "Создать аккаунт" (secondary/outlined)
 *   - Разделитель "Войти с помощью"
 *   - Иконки социальных сетей
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ── Логотип ──────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(32.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "SmartTracker Logo",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Название приложения ─────────────────────────────────────────
            Text(
                text = "SmartTracker",
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
                label = { Text("Почта...") },
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

            // ── Password (с toggle и ссылкой "Забыли пароль?") ──────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Пароль...") },
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

                // ── Ссылка "Забыли пароль?" ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onNavigateToPasswordRecovery,
                        enabled = !state.isLoading,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = "Забыли пароль?",
                            color = ColorLink,
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // ── Кнопка "Создать аккаунт" ────────────────────────────────────
            OutlinedButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ColorPrimary
                ),
                enabled = !state.isLoading
            ) {
                Text(
                    text = "Создать аккаунт",
                    color = ColorPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Разделитель "Войти с помощью" ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Divider(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp),
                    color = ColorDivider
                )
                Text(
                    text = " Войти с помощью ",
                    color = ColorPlaceholder,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Divider(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp),
                    color = ColorDivider
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Социальные кнопки ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TODO: Яндекс (Yandex)
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(48.dp),
                    enabled = !state.isLoading
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFFCC00))
                    ) {
                        Text("Я", modifier = Modifier.align(Alignment.Center), fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // TODO: VK
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(48.dp),
                    enabled = !state.isLoading
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF0077FF))
                    ) {
                        Text("VK", modifier = Modifier.align(Alignment.Center), fontSize = 14.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // TODO: Telegram или другой сервис
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(48.dp),
                    enabled = !state.isLoading
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF5B5FCF))
                    ) {
                        Text("P", modifier = Modifier.align(Alignment.Center), fontSize = 20.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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

