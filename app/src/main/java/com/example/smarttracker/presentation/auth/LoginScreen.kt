package com.example.smarttracker.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.example.smarttracker.presentation.common.UiTokens
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorPlaceholder
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorWhite
import com.example.smarttracker.presentation.theme.SmartTrackerTheme

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
                .padding(horizontal = UiTokens.ScreenHorizontalPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ── Логотип ──────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(64.dp))

            Box(
                modifier = Modifier
                    .border(UiTokens.BorderWidthThick, Color.Black, RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "SmartTracker Logo",
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(UiTokens.SectionSpacing))

            // ── Название приложения ─────────────────────────────────────────
            Text(
                text = "SmartTracker",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = ColorPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Email ────────────────────────────────────────────────────────
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(UiTokens.BorderWidthThick, ColorPrimary, RoundedCornerShape(UiTokens.CornerRadiusMedium))
                ) {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        placeholder = {
                            Text(
                                text = "Почта...",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = ColorPlaceholder,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorPrimary,
                            unfocusedBorderColor = ColorPrimary,
                            cursorColor = ColorPrimary
                        ),
                        enabled = !state.isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password (с toggle и ссылкой "Забыли пароль?") ──────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(UiTokens.BorderWidthThick, ColorPrimary, RoundedCornerShape(UiTokens.CornerRadiusMedium))
                ) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        placeholder = {
                            Text(
                                text = "Пароль...",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = ColorPlaceholder,
                            )
                        },
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
                        shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorPrimary,
                            unfocusedBorderColor = ColorPrimary,
                            cursorColor = ColorPrimary
                        ),
                        enabled = !state.isLoading
                    )
                }

                // ── Ссылка "Забыли пароль?" ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiTokens.InlineErrorTopPadding),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onNavigateToPasswordRecovery,
                        enabled = !state.isLoading,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = "Забыли пароль?",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            textDecoration = TextDecoration.None
                        )
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(12.dp))

            // ── Кнопка "Войти" ───────────────────────────────────────────────
            Button(
                onClick = onSubmitLogin,
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
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(UiTokens.ButtonLoadingIndicatorSize),
                        color = ColorWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Войти",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Кнопка "Создать аккаунт" ────────────────────────────────────
            Button(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(UiTokens.ButtonHeight)
                    .border(UiTokens.BorderWidthThick, ColorPrimary, RoundedCornerShape(UiTokens.CornerRadiusMedium)),
                shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorBackground,
                    contentColor = ColorPrimary,
                    disabledContainerColor = ColorBackground,
                    disabledContentColor = ColorPlaceholder,
                ),
                enabled = !state.isLoading
            ) {
                Text(
                    text = "Создать аккаунт",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(UiTokens.SectionSpacing))

            // ── Разделитель "Войти с помощью" ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
//                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = ColorPrimary
                )
                Text(
                    text = " Войти с помощью ",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = ColorPrimary
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = ColorPrimary
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
                // ── Яндекс
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(56.dp),
                    enabled = !state.isLoading
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_yandex),
                        contentDescription = "Yandex",
                        modifier = Modifier
                            .size(56.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ── VK
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(56.dp),
                    enabled = !state.isLoading
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_vk),
                        contentDescription = "VK",
                        modifier = Modifier
                            .size(56.dp)
                            ,
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ── Max
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(56.dp),
                    enabled = !state.isLoading
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_max),
                        contentDescription = "Max",
                        modifier = Modifier
                            .size(56.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(UiTokens.SectionSpacing))
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

