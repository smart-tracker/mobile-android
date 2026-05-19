package com.example.smarttracker.presentation.workout.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorFieldFill
import com.example.smarttracker.presentation.theme.ColorGpsActive
import com.example.smarttracker.presentation.theme.ColorGpsInactive
import com.example.smarttracker.presentation.workout.map.MapViewComposable
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.workout.activityIconRes
import com.example.smarttracker.presentation.workout.permission.LocationPermissionHandler
import com.example.smarttracker.presentation.workout.summary.ScrubDisplayStats
import com.example.smarttracker.presentation.workout.summary.StatsOverlayCard
import com.example.smarttracker.presentation.workout.summary.SummaryBody
import com.example.smarttracker.presentation.workout.summary.SummaryHeader
import com.example.smarttracker.presentation.workout.summary.SummaryOrigin
import com.example.smarttracker.presentation.workout.summary.TrainingProgressBar
import com.example.smarttracker.presentation.workout.summary.WorkoutSummaryFormatters
import com.example.smarttracker.presentation.workout.summary.WorkoutSummaryUiState
import kotlin.math.roundToInt

/**
 * Экран начала / активной / завершённой тренировки.
 *
 * Три режима, переключаемые состоянием [WorkoutStartViewModel.UiState]:
 * 1. **Стандартный** (`summaryOverlay == null`, `isTracking == false`):
 *    дата + таймер + статистика + ряд активностей + карта + кнопка «Начать».
 * 2. **Активная тренировка** (`isTracking == true`):
 *    то же самое, но кнопка «Начать» меняется на «Пауза» | «Завершить».
 * 3. **Оверлей итогов** (`summaryOverlay != null`):
 *    шапка с датой и стрелкой назад, иконка/название активности, ряд карточек
 *    статистики, та же карта (показывает пройденный трек), снизу — слайдер.
 *    При `isMapFullscreen == true` карта на весь экран (Figma 723:460).
 *
 * Ключевая особенность: оверлей итогов **не навигация**, а смена состояния. MapView
 * остаётся той же composable-инстанцией — это устраняет краши анимаций MapLibre
 * (LocationComponent), которые возникали при переходе через NavCompose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutStartScreen(
    state: WorkoutStartViewModel.UiState,
    padding: PaddingValues,
    onStartClick: () -> Unit,
    onTypeSelected: (WorkoutType) -> Unit,
    onSheetTypeSelected: (WorkoutType) -> Unit,
    onPauseClick: () -> Unit,
    onFinishClick: () -> Unit,
    onMapTilesFailed: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSummary: () -> Unit,
    onToggleFullscreenMap: () -> Unit,
    onDeleteHistoryTraining: () -> Unit,
) {
    // Запрашиваем разрешения при открытии экрана.
    LocationPermissionHandler(onPermissionsResult = { /* обработка в сервисе */ })

    // Локальное состояние шторки выбора активности — чисто UI, не нужно в ViewModel
    var showTypeSelector by remember { mutableStateOf(false) }

    // Счётчик recenter-тапов на GPS-бейдж. Передаётся в MapViewComposable как
    // recenterTrigger — каждое изменение значения (≠ 0) триггерит анимированное
    // центрирование карты на текущей позиции. Тип Int (не Boolean): два тапа
    // подряд должны срабатывать дважды, а Boolean toggle при сбросе TRACKING
    // → user re-tap не отличался бы от предыдущего значения.
    var recenterTick by remember { mutableIntStateOf(0) }

    val summary = state.summaryOverlay
    val overlayVisible = summary != null
    val isFullscreen = state.isMapFullscreen

    // ── Scrubbing трека ──────────────────────────────────────────────────────────
    // Сбрасывается в 1f каждый раз, когда открывается новый оверлей итогов:
    // тренировка только что завершилась — ползунок стоит в конце.
    var scrubProgress by remember(summary) { mutableFloatStateOf(1f) }

    val scrubIndex = if (summary != null && summary.trackPoints.size >= 2) {
        (scrubProgress * (summary.trackPoints.size - 1))
            .roundToInt()
            .coerceIn(0, summary.trackPoints.size - 1)
    } else null

    val scrubStats: ScrubDisplayStats? = if (scrubIndex != null && summary != null) {
        val cd = summary.cumulativeData
        ScrubDisplayStats(
            // speed читается из cumulativeData (вычисляется в buildCumulativeData)
            // вместо trackPoints[i].speed: для истории sensor-speed = null
            // (бэк не отдаёт скорости в gps_track), поэтому считаем сами как
            // Δdistance/Δtime между соседними точками. Для FINISH даёт ту же
            // шкалу, но через расчёт, а не sensor — единая логика для обоих режимов.
            speedDisplay     = WorkoutSummaryFormatters.formatInstantPace(
                                   cd.speedsMs.getOrElse(scrubIndex) { 0f }),
            elapsedDisplay   = WorkoutSummaryFormatters.formatDuration(
                                   cd.elapsedMs.getOrElse(scrubIndex) { 0L }),
            distanceDisplay  = WorkoutSummaryFormatters.formatDistance(
                                   cd.distancesKm.getOrElse(scrubIndex) { 0f }),
            elevationDisplay = WorkoutSummaryFormatters.formatElevation(
                                   cd.elevationsM.getOrElse(scrubIndex) { 0f }),
        )
    } else null

    val scrubPoint = scrubIndex?.let { summary?.trackPoints?.getOrNull(it) }

    // ── Системная кнопка Back ────────────────────────────────────────────────
    // В полноэкранном режиме карты — сворачиваем к обычному оверлею.
    // В обычном оверлее — закрываем оверлей и возвращаем экран в исходное состояние.
    BackHandler(enabled = overlayVisible) {
        if (isFullscreen) onToggleFullscreenMap() else onCloseSummary()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .padding(padding),
    ) {
        // Дополнительный отступ сверху ровно на высоту выреза камеры за
        // вычетом статус-бара (его уже отдал Scaffold). На устройствах без
        // выреза высота равна 0 — лишних пикселей не появится.
        Spacer(
            modifier = Modifier.windowInsetsTopHeight(
                WindowInsets.displayCutout.exclude(WindowInsets.statusBars)
            )
        )

        // ── Шапка с датой ────────────────────────────────────────────────────
        // Видна во всех режимах включая полноэкранную карту — пользователь
        // должен понимать, какая дата у показываемой тренировки.
        AnimatedContent(targetState = overlayVisible, label = "header") { showOverlay ->
            if (showOverlay && summary != null) {
                SummaryHeader(
                    dateDisplay = summary.dateDisplay,
                    // Иконка корзины только для оверлея из истории — FINISH-оверлей
                    // не предлагает удаление (юзер только что закончил тренировку).
                    showDelete = summary.origin == SummaryOrigin.HISTORY,
                    onDeleteClick = onDeleteHistoryTraining,
                )
            } else {
                ActiveHeader(dateDisplay = state.currentDate)
            }
        }
        HorizontalDivider(color = ColorPrimary, thickness = 1.dp)

        // ── Тело: таймер/статистика (active) ↔ активность/карточки (summary) ──
        // В полноэкранном режиме карты схлопывается в ноль, оставляя только шапку.
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            // Наслоение через alpha — оба варианта (active vs summary) всегда
            // участвуют в layout. Box принимает высоту max(active, summary),
            // поэтому область карты под ним не меняет размер при переходе.
            val activeAlpha   by animateFloatAsState(
                targetValue = if (overlayVisible) 0f else 1f,
                label = "active-alpha",
            )
            val summaryAlpha  by animateFloatAsState(
                targetValue = if (overlayVisible) 1f else 0f,
                label = "summary-alpha",
            )
            Box {
                // Active body всегда в дереве. Интерактивные элементы блокируются
                // параметром interactive когда оверлей активен (alpha=0 клики не блочит).
                Box(modifier = Modifier.alpha(activeAlpha)) {
                    ActiveBody(
                        state = state,
                        onTypeSelected = onTypeSelected,
                        onMoreClick = { showTypeSelector = true },
                        isMoreActive = showTypeSelector,
                        interactive = !overlayVisible,
                    )
                }
                // Summary body — placeholder пока оверлея нет (фиксирует высоту).
                Box(modifier = Modifier.alpha(summaryAlpha)) {
                    SummaryBody(state = summary ?: WorkoutSummaryUiState())
                }
            }
        }

        // ── Граница над картой ──────────────────────────────────────────────
        // Тонкая чёрная линия только сверху и снизу карты (без боковых).
        // Реализовано через HorizontalDivider до и после Box карты —
        // Modifier.border не умеет рисовать только две стороны.
        HorizontalDivider(color = ColorPrimary, thickness = 1.dp)

        // ── Карта + наложения ──────────────────────────────────────────────
        // Карта одна на все режимы — никогда не пересоздаётся, поэтому
        // LocationComponent не разбирается, аниматоры не падают.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            MapViewComposable(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!overlayVisible &&
                            !state.isGpsActive &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ) {
                            Modifier.blur(8.dp)
                        } else {
                            Modifier
                        }
                    ),
                currentLocation = state.trackPoints.lastOrNull(),
                lastKnownLocation = state.lastKnownLocation,
                // В режиме оверлея итогов live-список очищен (onFinishClick),
                // трек берётся из снимка чтобы не дублировать ~1800 точек в памяти.
                trackPoints = summary?.trackPoints ?: state.trackPoints,
                isTracking = state.isTracking,
                isGpsActive = state.isGpsActive,
                mapTilesFailed = state.mapTilesFailed,
                onMapTilesFailed = onMapTilesFailed,
                // Триггер для one-shot fit-to-bounds: при появлении снимка итогов
                // карта анимированно подгоняется под весь маршрут. Когда оверлей
                // закрывается (summary становится null), fit не повторяется.
                fitToTrackBoundsKey = summary,
                // Маркер scrubbing: виден только в полноэкранном режиме карты.
                // В обычном оверлее карта маленькая — маркер лишний и отвлекает.
                scrubPoint = if (isFullscreen) scrubPoint else null,
                // Иконка активности для маркера старта трека.
                // null пока оверлей не открыт (summary == null).
                startIconRes = summary?.let { activityIconRes(it.activityIconKey) },
                // В fullscreen-режиме attribution уходит в правый верхний угол —
                // иначе он перекрывает StatsOverlayCard в левом верхнем углу.
                attributionTopEnd = isFullscreen,
                // Recenter по тапу на GPS-бейдж — счётчик инкрементируется
                // в onClick ниже, карта реагирует через LaunchedEffect(recenterTrigger).
                recenterTrigger = recenterTick,
            )

            // Прозрачный слой для перехвата клика в режиме превью оверлея.
            // MapView внутри AndroidView поглощает тапы, поэтому Modifier.clickable
            // на самой карте не работает. Накладываем Box сверху.
            if (overlayVisible && !isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onToggleFullscreenMap)
                )
            }

            // ── Скрим-заглушка для API < 31 (blur недоступен) ───────────────
            // Только в активной фазе — в оверлее карта показывает завершённый трек.
            if (!overlayVisible && !state.isGpsActive && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.60f)),
                )
            }

            // ── GPS-бейдж — только в активной фазе ───────────────────────────
            if (!overlayVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        // Кликаем по бейджу → recenter карты на текущую позицию.
                        // clip обязателен ПЕРЕД clickable: иначе ripple-эффект
                        // выходит за круглую форму бейджа (рисуется на прямоугольнике).
                        .clip(RoundedCornerShape(size = 32.dp))
                        .clickable { recenterTick++ }
                        .border(
                            width = 1.dp,
                            color = ColorPrimary,
                            shape = RoundedCornerShape(size = 32.dp),
                        )
                        .width(32.dp)
                        .height(32.dp)
                        .background(
                            color = if (state.isGpsActive) ColorGpsActive else ColorGpsInactive,
                            shape = RoundedCornerShape(size = 32.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.gps),
                        contentDescription = if (state.isGpsActive) stringResource(R.string.gps_active)
                                             else stringResource(R.string.gps_inactive),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .width(30.dp)
                            .height(30.dp),
                    )
                }
            }

            // ── Карточка мини-статистики поверх карты в полноэкранном режиме оверлея
            // Соответствует Figma 723:460 (FullScreenMap).
            if (overlayVisible && isFullscreen && summary != null) {
                StatsOverlayCard(
                    state = summary,
                    scrubStats = scrubStats,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                )
            }

            // ── Кнопки — низ карты, только в активной фазе ───────────────────
            if (!overlayVisible) {
                if (!state.isTracking) {
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
                            style = WorkoutTextStyles.primaryButtonLabel,
                            color = Color.White,
                        )
                    }
                } else {
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
                                style = WorkoutTextStyles.primaryButtonLabel,
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
                                style = WorkoutTextStyles.primaryButtonLabel,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        // ── Граница под картой ──────────────────────────────────────────────
        HorizontalDivider(color = ColorPrimary, thickness = 1.dp)

        // ── Блок прогресс-бара — только в полноэкранном режиме оверлея
        // (Figma 723:496). Содержит сам бар + нижнюю чёрную границу блока.
        // Тренировка завершена ⇒ progress = 1f. Когда появится «проигрывание»
        // маршрута, сюда передастся state-овое значение.
        AnimatedVisibility(
            visible = overlayVisible && isFullscreen,
            enter = fadeIn(),
            exit  = fadeOut(),
        ) {
            Column {
                TrainingProgressBar(
                    progress = scrubProgress,
                    onProgressChange = { scrubProgress = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                )
                HorizontalDivider(color = ColorPrimary, thickness = 1.dp)
            }
        }
    }

    // ── Шторка выбора активности (только в активной фазе) ────────────────────
    if (showTypeSelector && !overlayVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                showTypeSelector = false
                onSearchQueryChange("")
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            scrimColor = ColorPrimary.copy(alpha = 0.30f),
        ) {
            Column(modifier = Modifier
                .fillMaxHeight(0.67f)
                .padding(start = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier
                            .border(width = 1.dp, color = ColorFieldFill, shape = RoundedCornerShape(size = 5.dp))
                            .height(36.dp)
                            .background(color = ColorFieldFill, shape = RoundedCornerShape(size = 5.dp))
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
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (state.searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_hint),
                                            style = WorkoutTextStyles.activityListItem,
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
                    contentPadding = PaddingValues(bottom = 14.dp),
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
                                model = type.iconFile ?: type.imageUrl ?: activityIconRes(type.iconKey),
                                contentDescription = type.name,
                                colorFilter = ColorFilter.tint(ColorPrimary),
                                placeholder = painterResource(R.drawable.placeholder),
                                error = painterResource(R.drawable.placeholder),
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = type.name,
                                style = WorkoutTextStyles.activityListItem,
                                modifier = Modifier.weight(1f),
                            )
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
            }
        }
    }
}

// ── Шапки ─────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveHeader(dateDisplay: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dateDisplay,
            style = WorkoutTextStyles.screenHeaderDate,
        )
    }
}

// ── Активная фаза: таймер + статистика + ряд активностей ─────────────────────

@Composable
private fun ActiveBody(
    state: WorkoutStartViewModel.UiState,
    onTypeSelected: (WorkoutType) -> Unit,
    onMoreClick: () -> Unit,
    isMoreActive: Boolean,
    /** false когда поверх отрендерен оверлей итогов — отключает клики (alpha=0 их не блокирует) */
    interactive: Boolean = true,
) {
    Column {
        // ── Таймер ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.timerDisplay,
                style = WorkoutTextStyles.timer,
            )
            Text(
                text = stringResource(R.string.workout_duration),
                style = WorkoutTextStyles.timerLabel,
                color = Color.Black,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Статистика ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // valueMinWidth фиксирует ширину блока по самому длинному ожидаемому значению,
            // чтобы SpaceEvenly не прыгал при смене "0.99" → "1.00", "9" → "10" и т.д.
            // Набор высоты не показываем — отображается только на экране итогов.
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
            state.pinnedTypes.forEach { type ->
                WorkoutTypeIcon(
                    iconModel = type.iconFile ?: type.imageUrl ?: activityIconRes(type.iconKey),
                    contentDescription = type.name,
                    isActive = !isMoreActive && type.id == state.selectedType?.id,
                    enabled = interactive && !state.isWorkoutStarted,
                    onClick = { onTypeSelected(type) },
                )
            }
            WorkoutTypeIcon(
                iconModel = R.drawable.ic_activity_other,
                contentDescription = stringResource(R.string.workout_more),
                isActive = isMoreActive,
                enabled = interactive && !state.isWorkoutStarted,
                onClick = onMoreClick,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Вспомогательные composable-ы ──────────────────────────────────────────────

/**
 * Одна статистика: крупное значение + мелкий лейбл под ним.
 */
@Composable
private fun StatItem(value: String, label: String, valueMinWidth: Dp = Dp.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = WorkoutTextStyles.statValue,
            textAlign = TextAlign.Center,
            modifier = if (valueMinWidth != Dp.Unspecified)
                Modifier.widthIn(min = valueMinWidth)
            else
                Modifier,
        )
        Text(
            text = label,
            style = WorkoutTextStyles.statLabel,
            color = Color.Black,
        )
    }
}

/**
 * Иконка типа активности.
 */
@Composable
private fun WorkoutTypeIcon(
    iconModel: Any?,
    contentDescription: String,
    isActive: Boolean,
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

