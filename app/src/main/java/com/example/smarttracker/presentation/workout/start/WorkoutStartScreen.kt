package com.example.smarttracker.presentation.workout.start

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.workout.map.MapViewComposable
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import com.example.smarttracker.presentation.workout.permission.LocationPermissionHandler

/**
 * Экран начала / активной тренировки.
 *
 * Два режима, определяются [WorkoutStartViewModel.UiState.isTracking]:
 * - isTracking = false → кнопка «Начать тренировку» поверх карты
 * - isTracking = true  → кнопки «Пауза» и «Завершить» поверх карты
 *
 * Тип активности выбирается кликом напрямую по иконке в строке активностей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutStartScreen(
    state: WorkoutStartViewModel.UiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onStartClick: () -> Unit,
    onTypeSelected: (WorkoutType) -> Unit,
    onSheetTypeSelected: (WorkoutType) -> Unit,
    onPauseClick: () -> Unit,
    onFinishClick: () -> Unit,
    onMapTilesFailed: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    // Запрашиваем разрешения при открытии экрана.
    // Результат в ViewModel не передаётся: сервис сам обработает SecurityException при отказе,
    // а ViewModel переведёт gpsStatus в UNAVAILABLE по таймауту.
    LocationPermissionHandler(onPermissionsResult = { /* обработка в сервисе */ })

    // Локальное состояние шторки выбора активности — чисто UI, не нужно в ViewModel
    var showTypeSelector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .padding(padding),
    ) {
        // ── Шапка: дата по центру ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.currentDate,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = ColorPrimary,
            )
        }
        HorizontalDivider(color = ColorPrimary, thickness = 1.dp)

        // ── Таймер ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.timerDisplay,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = ColorPrimary,
            )
            Text(
                text = stringResource(R.string.workout_duration),
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Thin,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = Color.Black,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Статистика ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // valueMinWidth фиксирует ширину блока по самому длинному ожидаемому значению,
            // чтобы SpaceEvenly не прыгал при смене "0.99" → "1.00", "9" → "10" и т.д.
            StatItem(value = state.distanceDisplay, label = stringResource(R.string.workout_distance), valueMinWidth = 100.dp)
            StatItem(value = state.avgSpeedDisplay, label = stringResource(R.string.workout_avg_speed), valueMinWidth = 140.dp)
            StatItem(value = state.caloriesDisplay, label = stringResource(R.string.workout_calories), valueMinWidth = 110.dp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Переключатель типа активности ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, ColorPrimary, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Первые 3 из pinnedTypes — быстрый выбор; порядок меняется при выборе.
            // Во время тренировки (isWorkoutStarted) иконки визуально отключены.
            state.pinnedTypes.forEach { type ->
                WorkoutTypeIcon(
                    // Приоритет: локальный файл → URL (Coil загружает сам) → drawable fallback
                    iconModel = type.iconFile ?: type.imageUrl ?: iconResForKey(type.iconKey),
                    contentDescription = type.name,
                    isActive = !showTypeSelector && type.id == state.selectedType?.id,
                    enabled = !state.isWorkoutStarted,
                    onClick = { onTypeSelected(type) },
                )
            }
            // 4-я кнопка — всегда ic_activity_other, открывает полный список
            // Подсвечивается бирюзовым пока шторка открыта
            WorkoutTypeIcon(
                iconModel = R.drawable.ic_activity_other,
                contentDescription = stringResource(R.string.workout_more),
                isActive = showTypeSelector,
                enabled = !state.isWorkoutStarted,
                onClick = { showTypeSelector = true },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Карта + кнопка поверх неё ─────────────────────────────────────────
        // Карта занимает всё оставшееся место, кнопка рендерится поверх неё снизу
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Карта — занимает весь Box включая область под кнопкой.
            // На API 31+ блюрится через Modifier.blur когда GPS не получен.
            // На старых API — блюр недоступен, используется скрим ниже.
            MapViewComposable(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!state.isGpsActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Modifier.blur(8.dp)
                        else
                            Modifier
                    ),
                currentLocation = state.trackPoints.lastOrNull(),
                lastKnownLocation = state.lastKnownLocation,
                trackPoints = state.trackPoints,
                isTracking = state.isTracking,
                mapTilesFailed = state.mapTilesFailed,
                onMapTilesFailed = onMapTilesFailed,
            )

            // ── Скрим-заглушка для API < 31 (blur недоступен) ─────────────────
            if (!state.isGpsActive && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000)),
                )
            }

            // ── GPS-бейдж — всегда виден в правом верхнем углу карты ─────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .border(
                        width = 1.dp,
                        color = Color(0xFF0A1928),
                        shape = RoundedCornerShape(size = 32.dp),
                    )
                    .width(32.dp)
                    .height(32.dp)
                    .background(
                        color = if (state.isGpsActive) Color(0xFF4CAF50) else Color(0xFFFC3F1D),
                        shape = RoundedCornerShape(size = 32.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.gps),
                    contentDescription = if (state.isGpsActive) stringResource(R.string.gps_active) else stringResource(R.string.gps_inactive),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .width(30.dp)
                        .height(30.dp),
                )
            }

            // Кнопка(и) — поверх карты, прижата к низу
            if (!state.isTracking) {
                // isPaused = пауза (таймер уже шёл), иначе — старт с нуля
                val isPaused = state.elapsedMs > 0
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary),
                ) {
                    Text(
                        text = stringResource(
                            if (isPaused) R.string.workout_resume else R.string.workout_start
                        ),
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 20.sp,
                        color = Color.White,
                    )
                }
            } else {
                // Split-кнопка «Пауза» | «Завершить»
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(50.dp),
                ) {
                    OutlinedButton(
                        onClick = onPauseClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(
                            topStart = 10.dp, bottomStart = 10.dp,
                            topEnd = 0.dp, bottomEnd = 0.dp,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = ColorPrimary,
                        ),
                        border = BorderStroke(1.dp, ColorPrimary),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_pause),
                            fontFamily = geologicaFontFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            color = ColorPrimary,
                        )
                    }
                    Button(
                        onClick = onFinishClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(
                            topStart = 0.dp, bottomStart = 0.dp,
                            topEnd = 10.dp, bottomEnd = 10.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_finish),
                            fontFamily = geologicaFontFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }

    // ── Шторка выбора активности ─────────────────────────────────────────────
    if (showTypeSelector) {
        ModalBottomSheet(
            onDismissRequest = {
                showTypeSelector = false
                onSearchQueryChange("")
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            scrimColor = Color(0x4D0A1928), // rgba(10,25,40,0.30) — как в Figma
        ) {
            Column(modifier = Modifier
                .fillMaxHeight(0.67f)
                .padding(start = 5.dp)
            ) {
            // ── Поиск ───────────────────────────────────────────────────────
            // Кастомное поле: серый фон + скруглённый бордер + иконка лупы слева.
            // BasicTextField вместо OutlinedTextField — чтобы не было встроенного
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier
                        .border(width = 1.dp, color = Color(0xFFD9D9D9), shape = RoundedCornerShape(size = 5.dp))
                        .height(36.dp)
                        .background(color = Color(0xFFD9D9D9), shape = RoundedCornerShape(size = 5.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.search),
                        contentDescription = stringResource(R.string.search_description),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .width(24.dp)
                            .height(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            // Box выравнивает placeholder и курсор в одном слое —
                            // без этого они рендерятся последовательно и курсор смещается вниз
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_hint),
                                        fontFamily = geologicaFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = Color(0xFF888888),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.filteredAndSortedTypes) { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSheetTypeSelected(type)
                                showTypeSelector = false
                                onSearchQueryChange("")
                            }
                            .padding(start = 16.dp, end = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = type.iconFile ?: type.imageUrl ?: iconResForKey(type.iconKey),
                            contentDescription = type.name,
                            colorFilter = ColorFilter.tint(ColorPrimary),
                            placeholder = painterResource(R.drawable.placeholder),
                            error = painterResource(R.drawable.placeholder),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = type.name,
                            fontFamily = geologicaFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = ColorPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        // ── Кнопка избранного ──────────────────────────────
                        val isFav = type.iconKey in state.favoriteIds
                        IconButton(onClick = { onToggleFavorite(type.iconKey) }) {
                            Image(
                                painter = painterResource(if (isFav) R.drawable.star else R.drawable.star_2),
                                contentDescription = if (isFav) "Убрать из избранного" else "В избранное",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }
            } // end Column (фиксирует высоту шторки)
        }
    }
}

// ── Вспомогательные composable-ы ──────────────────────────────────────────────

/**
 * Одна статистика: крупное значение + мелкий лейбл под ним.
 *
 * @param valueMinWidth Минимальная ширина текста значения. Фиксирует ширину блока по
 *   самому длинному ожидаемому значению, чтобы SpaceEvenly не делал рывков при смене
 *   цифр (например, "0.99 км" → "1.00 км" меняет ширину на одну цифру слева).
 */
@Composable
private fun StatItem(value: String, label: String, valueMinWidth: Dp = Dp.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = ColorPrimary,
            textAlign = TextAlign.Center,
            modifier = if (valueMinWidth != Dp.Unspecified)
                Modifier.widthIn(min = valueMinWidth)
            else
                Modifier,
        )
        Text(
            text = label,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Thin,
            fontStyle = FontStyle.Italic,
            fontSize = 10.sp,
            color = Color.Black,
        )
    }
}

/**
 * Иконка типа активности.
 * Контейнер 42dp, иконка 32dp, border ColorPrimary 1dp, corners 5dp.
 * Активная: ColorSecondary фон + белая иконка. Неактивная: белый фон + ColorPrimary иконка.
 *
 * @param iconModel Any? — принимает File (скачанная иконка) или Int (R.drawable.*).
 *   Coil прозрачно обрабатывает оба типа. При ошибке загрузки показывает placeholder.
 */
@Composable
private fun WorkoutTypeIcon(
    iconModel: Any?,
    contentDescription: String,
    isActive: Boolean,
    /** false во время тренировки: alpha 0.38f + клики отключены */
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (isActive) ColorSecondary else Color.White)
            .border(1.dp, ColorPrimary, RoundedCornerShape(5.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = iconModel,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(ColorPrimary),
            placeholder = painterResource(R.drawable.placeholder),
            error = painterResource(R.drawable.placeholder),
            modifier = Modifier.size(36.dp),
        )
    }
}

/**
 * Маппинг type_activ_id → drawable resource id. Один тип — одна иконка.
 * Если для данного ID нет своей иконки — возвращается placeholder.png.
 * ic_activity_other — иконка кнопки меню выбора активностей, здесь не используется.
 * При добавлении новых иконок — добавить соответствующий ID в when.
 *
 * Текущий список API (GET /training/types_activity):
 *  1  — Бег
 *  2  — Северная ходьба
 *  3  — Велосипед
 *  4  — Силовая
 *  5  — Ходьба
 *  6  — Спортивное ориентирование бегом
 *  7  — Спортивное ориентирование на лыжах
 *  8  — Спортивное ориентирование на велосипеде
 *  9  — Свободное катание на лыжах
 *  10 — Классическое катание на лыжах
 *  11 — Свободное катание на роллерах
 *  12 — Классическое катание на роллерах
 *  13 — Бег на беговой дорожке
 */
private fun iconResForKey(key: String): Int = when (key) {
    "1"  -> R.drawable.ic_activity_running  // Бег
    "2"  -> R.drawable.ic_activity_walking  // Северная ходьба
    "3"  -> R.drawable.ic_activity_cycling  // Велосипед
    else -> R.drawable.placeholder          // нет своей иконки
}
