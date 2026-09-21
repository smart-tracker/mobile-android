package com.example.smarttracker.presentation.menu.profile

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.presentation.common.AppTab
import com.example.smarttracker.presentation.common.ProfileAvatarImage
import com.example.smarttracker.presentation.common.ProfileFieldBox
import com.example.smarttracker.presentation.common.DateVisualTransformation
import com.example.smarttracker.presentation.common.SmartTrackerBottomBar
import com.example.smarttracker.presentation.theme.ColorDestructive
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.ProfileTextStyles
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import com.example.smarttracker.presentation.theme.geologicaFontFamilyItalic
import java.io.File
import kotlinx.coroutines.launch

/**
 * Экран редактирования профиля пользователя.
 *
 * Поля предзаполнены из [ProfileEditUiState] (данные берутся из кэша при создании ViewModel).
 * Кнопка «Сохр.» вызывает [onSave]. После успешного сохранения ViewModel отправляет
 * [ProfileEditEvent.NavigateBack], навигация обрабатывается в [AppNavGraph].
 *
 * Системная кнопка «Назад» = [navController.popBackStack()] (без сохранения).
 * Нажатие на другую вкладку нижнего бара тоже уводит назад через [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    state: ProfileEditUiState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onMiddleNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onGenderToggle: () -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onDeletePhoto: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showPhotoSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onPhotoSelected(it) }
    }

    // URI временного файла, куда камера сохранит снимок
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraOutputUri?.let { onPhotoSelected(it) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File.createTempFile("camera_photo_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        }
    }
    Scaffold(
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
                    SaveButton(
                        isSaving = state.isSaving || state.isDeleting,
                        onClick = onSave,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
        bottomBar = {
            SmartTrackerBottomBar(
                selectedIndex = AppTab.MENU,
                onTabSelected = { index -> if (index != AppTab.MENU) onBack() },
            )
        },
        containerColor = Color.White,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = ColorSecondary,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AvatarEditSection(
                        photoUrl = state.photoUrl,
                        photoKey = state.photoKey,
                        isUploading = state.isUploadingPhoto,
                        onAvatarClick = { showPhotoSheet = true },
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        EditField(
                            label = "Имя",
                            value = state.firstName,
                            onValueChange = onFirstNameChange,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditField(
                            label = "Фамилия",
                            value = state.lastName,
                            onValueChange = onLastNameChange,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditField(
                            label = "Отчество",
                            value = state.middleName,
                            onValueChange = onMiddleNameChange,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditField(
                            label = "@Username",
                            value = state.username,
                            onValueChange = onUsernameChange,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditDateField(
                            label = "ДД.ММ.ГГГГ",
                            value = state.birthDate,
                            onValueChange = onBirthDateChange,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GenderField(
                            gender = state.gender,
                            onToggle = onGenderToggle,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditField(
                            label = "Рост (см)",
                            value = state.height,
                            onValueChange = onHeightChange,
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditField(
                            label = "Вес (кг)",
                            value = state.weight,
                            onValueChange = onWeightChange,
                            keyboardType = KeyboardType.Number,
                        )

                        if (state.errorMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.errorMessage,
                                fontFamily = geologicaFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 13.sp,
                                color = ColorDestructive,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    DeleteAccountButton(
                        enabled = !state.isDeleting && !state.isSaving,
                        onClick = onDeleteAccountClick,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showPhotoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = "Фото профиля",
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ColorPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                HorizontalDivider(color = ColorPrimary.copy(alpha = 0.12f))
                Text(
                    text = "Выбрать из галереи",
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    color = ColorPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showPhotoSheet = false
                                photoPicker.launch("image/*")
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
                HorizontalDivider(color = ColorPrimary.copy(alpha = 0.12f))
                Text(
                    text = "Сделать фото",
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    color = ColorPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showPhotoSheet = false
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
                HorizontalDivider(color = ColorPrimary.copy(alpha = 0.12f))
                Text(
                    text = "Удалить фото",
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    color = ColorDestructive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showPhotoSheet = false
                                onDeletePhoto()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }

    if (state.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = {
                Text(
                    text = "Удалить аккаунт?",
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = ColorPrimary,
                )
            },
            text = {
                Text(
                    text = "Это действие нельзя отменить. Все данные будут удалены.",
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Light,
                    color = ColorPrimary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    enabled = !state.isDeleting,
                ) {
                    Text(
                        text = "Удалить",
                        fontFamily = geologicaFontFamily,
                        color = ColorDestructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) {
                    Text(
                        text = "Отмена",
                        fontFamily = geologicaFontFamily,
                        color = ColorPrimary,
                    )
                }
            },
        )
    }
}

// ── Кнопка «Сохр.» ────────────────────────────────────────────────────────────

@Composable
private fun SaveButton(isSaving: Boolean, onClick: () -> Unit) {
    val alpha = if (isSaving) 0.4f else 1f
    Row(
        modifier = Modifier
            .padding(end = 16.dp)
            .height(40.dp)
            .width(85.dp)
            .border(1.dp, ColorPrimary.copy(alpha = alpha), RoundedCornerShape(5.dp))
            .clickable(enabled = !isSaving, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_save),
            contentDescription = null,
            tint = ColorPrimary.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Сохр.",
            fontFamily = geologicaFontFamilyItalic,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = ColorPrimary.copy(alpha = alpha),
        )
    }
}

// ── Аватар + «Загрузить фото» ─────────────────────────────────────────────────

@Composable
private fun AvatarEditSection(
    photoUrl: String?,
    photoKey: Long,
    isUploading: Boolean,
    onAvatarClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = !isUploading, onClick = onAvatarClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProfileAvatarImage(
                photoUrl = photoUrl,
                photoKey = photoKey,
                modifier = Modifier.size(96.dp),
            )
            if (isUploading) {
                CircularProgressIndicator(
                    color = ColorSecondary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isUploading) "Загружаем..." else "Изменить фото",
            fontFamily = geologicaFontFamily,
            fontStyle = FontStyle.Normal,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            color = ColorPrimary,
        )
    }
}

// ── Редактируемое поле ────────────────────────────────────────────────────────

/**
 * Поле ввода в стиле ProfileScreen: рамка + метка + текст.
 * Метка отображается тёмным курсивом, значение — бирюзовым (или приглушённым если пусто).
 */
@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val valueColor = if (value.isNotEmpty()) ColorSecondary else ColorPrimary.copy(alpha = 0.3f)
    ProfileFieldBox {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = ProfileTextStyles.fieldInput.copy(color = valueColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(ColorSecondary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$label: ",
                        style = ProfileTextStyles.fieldInput,
                        color = ColorPrimary,
                    )
                    innerTextField()
                }
            },
        )
    }
}

// ── Поле даты с маской ДД.ММ.ГГГГ ────────────────────────────────────────────

@Composable
private fun EditDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val valueColor = if (value.isNotEmpty()) ColorSecondary else ColorPrimary.copy(alpha = 0.3f)
    ProfileFieldBox {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = ProfileTextStyles.fieldInput.copy(color = valueColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = DateVisualTransformation(),
            cursorBrush = SolidColor(ColorSecondary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$label: ",
                        style = ProfileTextStyles.fieldInput,
                        color = ColorPrimary,
                    )
                    innerTextField()
                }
            },
        )
    }
}

// ── Поле «Пол» — клик переключает Мужской/Женский ────────────────────────────

@Composable
private fun GenderField(gender: String, onToggle: () -> Unit) {
    val genderText = if (gender == "male") "Мужской" else "Женский"
    ProfileFieldBox(modifier = Modifier.clickable(onClick = onToggle)) {
        Text(
            modifier = Modifier.padding(start = 12.dp),
            text = buildAnnotatedString {
                withStyle(ProfileTextStyles.fieldLabel) { append("Пол: ") }
                withStyle(ProfileTextStyles.fieldValue) { append(genderText) }
            },
        )
    }
}

// ── Кнопка «Удалить аккаунт» ──────────────────────────────────────────────────

@Composable
private fun DeleteAccountButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(1.dp, ColorDestructive.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Удалить аккаунт",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 18.sp,
            color = ColorDestructive.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Редактирование профиля")
@Composable
private fun ProfileEditScreenPreview() {
    SmartTrackerTheme {
        ProfileEditScreen(
            state = ProfileEditUiState(
                isLoading = false,
                firstName = "Иван", lastName = "Петров", middleName = "Сергеевич",
                username = "ivan_run", birthDate = "04.05.2004", gender = "male",
                height = "180", weight = "72",
            ),
            onFirstNameChange = {}, onLastNameChange = {}, onMiddleNameChange = {},
            onUsernameChange = {}, onBirthDateChange = {}, onGenderToggle = {},
            onHeightChange = {}, onWeightChange = {}, onPhotoSelected = {}, onDeletePhoto = {},
            onSave = {}, onBack = {}, onDeleteAccountClick = {}, onDismissDeleteDialog = {},
            onConfirmDelete = {},
        )
    }
}
