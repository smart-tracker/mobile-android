package com.example.smarttracker.presentation.menu.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.presentation.common.AppTab
import com.example.smarttracker.presentation.common.SmartTrackerBottomBar
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import com.example.smarttracker.presentation.theme.geologicaFontFamilyItalic

/**
 * Экран профиля пользователя.
 *
 * Данные загружаются из API через [ProfileViewModel] и передаются сюда
 * как [ProfileUiState]. Пока идёт загрузка — показывается [CircularProgressIndicator].
 *
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit = {},
) {
    // Цвет заполненных полей: из Figma #2AC3B8 (чуть отличается от ColorSecondary)
    val colorFieldValue = ColorSecondary
    // Цвет незаполненных полей: ColorPrimary 30% прозрачности
    val colorFieldEmpty = ColorPrimary.copy(alpha = 0.3f)

    Scaffold(
        bottomBar = {
            // «Меню» подсвечено — ProfileScreen открывается из вкладки Меню.
            // Нажатие на другую вкладку — возврат назад.
            SmartTrackerBottomBar(
                selectedIndex = AppTab.MENU,
                onTabSelected = { index -> if (index != AppTab.MENU) onBack() },
            )
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Профиль",
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Normal,
                        fontSize = 22.sp,
                        color = ColorPrimary,
                    )
                },
                actions = {
                    EditButton(onClick = onEditProfile)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
        containerColor = Color.White,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                // ── Загрузка ──────────────────────────────────────────────
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ColorSecondary,
                    )
                }

                // ── Ошибка ────────────────────────────────────────────────
                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage,
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                        color = ColorPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                    )
                }

                // ── Данные загружены ──────────────────────────────────────
                else -> {
                    // Прокручиваемое содержимое
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            // Нижний отступ чтобы контент не уходил за кнопку «Выйти»
                            .padding(bottom = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AvatarSection(firstName = state.firstName, lastTrainingDate = state.lastTrainingDate)
                        Spacer(modifier = Modifier.height(24.dp))
                        ProfileFields(
                            state = state,
                            colorFieldValue = colorFieldValue,
                            colorFieldEmpty = colorFieldEmpty,
                        )
                    }
                    // ── Кнопка «Выйти» закреплена внизу всегда ───────────────────
                    Spacer(modifier = Modifier.height(16.dp))
                    LogoutButton(
                        onClick = onLogout,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

// ── Кнопка «Ред.» в шапке ─────────────────────────────────────────────────────

@Composable
private fun EditButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(end = 16.dp)
            .height(40.dp)
            .width(85.dp)
            .border(1.dp, ColorPrimary, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_editing),
            contentDescription = null,
            tint = ColorPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = "Ред.",
            fontFamily = geologicaFontFamilyItalic,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = ColorPrimary,
        )
    }
}

// ── Аватар + имя + дата последней тренировки ──────────────────────────────────

/**
 * @param firstName Имя пользователя — отображается крупным текстом под аватаром.
 */
@Composable
private fun AvatarSection(firstName: String, lastTrainingDate: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Тёмный круг с иконкой профиля — имитация дефолтного аватара
        Icon(
            painter = painterResource(R.drawable.ic_profile_2),
            contentDescription = null,
            modifier = Modifier.size(94.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = firstName,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = ColorPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Дата последней тренировки — из состояния. Если null — показать «—».
        Text(
            text = "Дата последней тренировки: ${lastTrainingDate ?: "—"}",
            fontFamily = geologicaFontFamilyItalic,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = ColorPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Поля профиля ──────────────────────────────────────────────────────────────

/**
 * Отображает все поля профиля тремя группами:
 * 1. Необязательные личные данные (фамилия, отчество)
 * 2. Данные из регистрации (никнейм, дата рождения, пол)
 * 3. Физические параметры (рост, вес)
 */
@Composable
private fun ProfileFields(
    state: ProfileUiState,
    colorFieldValue: Color,
    colorFieldEmpty: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Группа 1: необязательные личные данные
        ProfileField("Фамилия",          value = state.lastName,   colorValue = colorFieldValue, colorEmpty = colorFieldEmpty)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Отчество",         value = state.middleName, colorValue = colorFieldValue, colorEmpty = colorFieldEmpty)

        Spacer(modifier = Modifier.height(24.dp))

        // Группа 2: данные из регистрации (всегда заполнены)
        ProfileField("Имя пользователя", value = state.username,   colorValue = colorFieldValue, colorEmpty = colorFieldEmpty)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Дата рождения",    value = state.birthDate,  colorValue = colorFieldValue, colorEmpty = colorFieldEmpty)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Пол",              value = state.gender,     colorValue = colorFieldValue, colorEmpty = colorFieldEmpty)

        Spacer(modifier = Modifier.height(24.dp))

        // Группа 3: физические параметры (необязательные)
        ProfileField("Рост (см)",        value = state.height,     colorValue = colorFieldValue, colorEmpty = colorFieldEmpty)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Вес (кг)",         value = state.weight,     colorValue = colorFieldValue, colorEmpty = colorFieldEmpty)
    }
}

/**
 * Одна строка профиля в рамке.
 *
 * @param label  Название поля (курсив, тёмный цвет)
 * @param value  Значение поля: если null — показывается «Не указано» приглушённым цветом;
 *               если строка — отображается бирюзовым (данные из API)
 */
@Composable
private fun ProfileField(
    label: String,
    value: String?,
    colorValue: Color,
    colorEmpty: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, ColorPrimary, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            modifier = Modifier.padding(start = 12.dp),
            text = buildAnnotatedString {
                // Метка поля — тёмный цвет, курсив
                withStyle(
                    SpanStyle(
                        fontFamily = geologicaFontFamilyItalic,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = ColorPrimary,
                    )
                ) {
                    append("$label: ")
                }
                // Значение — бирюзовый если заполнено, приглушённый если нет
                withStyle(
                    SpanStyle(
                        fontFamily = geologicaFontFamilyItalic,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = if (value != null) colorValue else colorEmpty,
                    )
                ) {
                    append(value ?: "Не указано")
                }
            },
        )
    }
}

// ── Кнопка «Выйти» ────────────────────────────────────────────────────────────

@Composable
private fun LogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .height(50.dp)
            .border(1.dp, ColorPrimary, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Выйти",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 20.sp,
            color = ColorPrimary,
        )
    }
}
