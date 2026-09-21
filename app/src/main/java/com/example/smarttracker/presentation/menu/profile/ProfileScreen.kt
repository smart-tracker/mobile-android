package com.example.smarttracker.presentation.menu.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.tooling.preview.Preview
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smarttracker.R
import com.example.smarttracker.presentation.common.AppTab
import com.example.smarttracker.presentation.common.ProfileAvatarImage
import com.example.smarttracker.presentation.common.ProfileFieldBox
import com.example.smarttracker.presentation.common.SmartTrackerBottomBar
import com.example.smarttracker.presentation.common.UiTokens
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.ProfileTextStyles
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
    var showPhotoViewer by remember { mutableStateOf(false) }

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
                        AvatarSection(
                            firstName = state.firstName,
                            photoUrl = state.photoUrl,
                            photoKey = state.photoKey,
                            lastTrainingDate = state.lastTrainingDate,
                            onAvatarClick = { showPhotoViewer = true },
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        ProfileFields(state = state)
                    }
                }
            }
            // ── Кнопка «Выйти» закреплена внизу всегда — видна во всех состояниях ──
            // Намеренно снаружи when{}: при ошибке загрузки (нет сети / 5xx) или во время
            // spinner-а пользователь должен иметь возможность выйти из аккаунта.
            LogoutButton(
                onClick = onLogout,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showPhotoViewer) {
        PhotoViewerDialog(
            photoUrl = state.photoUrl,
            onDismiss = { showPhotoViewer = false },
        )
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
 * @param photoUrl URL фото профиля (из image_path). Всегда не-null после логина.
 */
@Composable
private fun AvatarSection(
    firstName: String,
    photoUrl: String?,
    photoKey: Long,
    lastTrainingDate: String?,
    onAvatarClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileAvatarImage(
            photoUrl = photoUrl,
            photoKey = photoKey,
            modifier = Modifier
                .size(96.dp)
                .clickable(onClick = onAvatarClick),
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
private fun ProfileFields(state: ProfileUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        ProfileField("Фамилия",          value = state.lastName)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Отчество",         value = state.middleName)

        Spacer(modifier = Modifier.height(24.dp))

        ProfileField("Имя пользователя", value = state.username)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Дата рождения",    value = state.birthDate)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Пол",              value = state.gender)

        Spacer(modifier = Modifier.height(24.dp))

        ProfileField("Рост (см)",        value = state.height)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileField("Вес (кг)",         value = state.weight)
    }
}

@Composable
private fun ProfileField(label: String, value: String?) {
    ProfileFieldBox {
        Text(
            modifier = Modifier.padding(start = 12.dp),
            text = buildAnnotatedString {
                withStyle(ProfileTextStyles.fieldLabel) { append("$label: ") }
                withStyle(if (value != null) ProfileTextStyles.fieldValue else ProfileTextStyles.fieldEmpty) {
                    append(value ?: "Не указано")
                }
            },
        )
    }
}

// ── Просмотр фото в полном размере ────────────────────────────────────────────

/**
 * Полноэкранный просмотрщик фото профиля.
 * Тёмный фон + фото по центру. Тап в любом месте закрывает.
 */
@Composable
private fun PhotoViewerDialog(photoUrl: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Фото профиля",
                contentScale = ContentScale.Fit,
                error = painterResource(R.drawable.ic_profile_2),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Профиль — загружен")
@Composable
private fun ProfileScreenPreview() {
    SmartTrackerTheme {
        ProfileScreen(
            state = ProfileUiState(
                isLoading = false,
                firstName = "Иван", lastName = "Петров", middleName = "Сергеевич",
                username = "@ivan_run", birthDate = "04.05.2004", gender = "Мужской",
                weight = "72", height = "180", lastTrainingDate = "20.07.2026",
            ),
            onBack = {}, onLogout = {}, onEditProfile = {},
        )
    }
}

@Preview(showBackground = true, name = "Профиль — загрузка")
@Composable
private fun ProfileScreenLoadingPreview() {
    SmartTrackerTheme {
        ProfileScreen(state = ProfileUiState(isLoading = true), onBack = {}, onLogout = {}, onEditProfile = {})
    }
}
