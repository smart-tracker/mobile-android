package com.example.smarttracker.presentation.workout.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smarttracker.data.system.BatteryOptimizationHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.workout.activityIconRes
import com.example.smarttracker.presentation.workout.permission.LocationPermissionHandler
import androidx.compose.runtime.rememberCoroutineScope
import com.example.smarttracker.presentation.workout.summary.GpxComposer
import com.example.smarttracker.presentation.workout.summary.ScrubDisplayStats
import com.example.smarttracker.presentation.workout.summary.ShareImageComposer
import com.example.smarttracker.presentation.workout.summary.StatsOverlayCard
import com.example.smarttracker.presentation.workout.summary.SummaryBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.smarttracker.presentation.workout.summary.SummaryDetailsPanel
import com.example.smarttracker.presentation.workout.summary.SummaryHeader
import com.example.smarttracker.presentation.workout.summary.summaryHasDetails
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
    onOpenSensors: () -> Unit = {},
    onCoachmarkDismissed: () -> Unit = {},
) {
    // ── Статус Doze whitelist (для баннера) ──────────────────────────────────────
    // batteryOptimized = true → приложение в whitelist → баннер скрыт.
    // false → Android Doze будет throttle'ить GPS-обновления при выключенном экране.
    // Состояние локальное (не в VM): нужно только для баннера на этом экране,
    // нет других потребителей. При наличии других — перенести в WorkoutStartViewModel.UiState.
    // В @Preview системные сервисы недоступны (power_exemption/permission бросают
    // в layoutlib) — гейтим все обращения к ним по LocalInspectionMode с безопасными
    // дефолтами. Рантайм не меняется (inPreview всегда false вне preview-хоста).
    val inPreview = LocalInspectionMode.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var batteryOptimized by remember {
        mutableStateOf(if (inPreview) true else BatteryOptimizationHelper.isIgnoringBatteryOptimizations(ctx))
    }
    // Обновляем статус при возврате из системных настроек (юзер мог нажать "Разрешить"
    // в системном диалоге Doze whitelist). ON_RESUME — стандартный hook для перепроверки
    // системных permissions при возврате на экран.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !inPreview) {
                batteryOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Launcher для запуска системного диалога Doze whitelist из баннера.
    // После закрытия диалога ON_RESUME выше обновит batteryOptimized.
    val bannerBatteryOptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        batteryOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(ctx)
    }

    // ── Статус разрешения геолокации (для карты) ─────────────────────────────
    // MapViewComposable не должен активировать LocationComponent без разрешения:
    // на свежей установке это роняет процесс SecurityException-ом изнутри MapLibre.
    // Начальное значение — фактический статус (при повторных открытиях разрешение
    // обычно уже есть), true приходит из onLocationGranted когда юзер согласится.
    var locationPermissionGranted by remember {
        mutableStateOf(
            inPreview ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Запрашиваем разрешения при открытии экрана (в preview не нужно — launcher'ы
    // и permission-проверки в inspection-контексте только мешают рендеру).
    if (!inPreview) {
        LocationPermissionHandler(
            onLocationGranted   = { locationPermissionGranted = true },
            onPermissionsResult = { /* обработка в сервисе */ },
            onBatteryOptResult  = { granted -> batteryOptimized = granted },
        )
    }

    // Локальное состояние шторки выбора активности — чисто UI, не нужно в ViewModel
    var showTypeSelector by remember { mutableStateOf(false) }

    // ── «Не гасить экран» (Меню → Настройки) ─────────────────────────────────
    // View.keepScreenOn вместо флага окна Activity: снимается автоматически
    // когда composable покидает композицию (уход с вкладки/экрана), не требует
    // доступа к Window. Активен только во время записи тренировки.
    val rootView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(state.isTracking, state.keepScreenOn) {
        rootView.keepScreenOn = state.isTracking && state.keepScreenOn
        onDispose { rootView.keepScreenOn = false }
    }

    // Счётчик recenter-тапов на GPS-бейдж. Передаётся в MapViewComposable как
    // recenterTrigger — каждое изменение значения (≠ 0) триггерит анимированное
    // центрирование карты на текущей позиции. Тип Int (не Boolean): два тапа
    // подряд должны срабатывать дважды, а Boolean toggle при сбросе TRACKING
    // → user re-tap не отличался бы от предыдущего значения.
    var recenterTick by remember { mutableIntStateOf(0) }

    val summary = state.summaryOverlay
    val overlayVisible = summary != null
    val isFullscreen = state.isMapFullscreen
    // Onboarding-coachmark: авто-показ при первом входе в активную фазу (до дисмисса)
    // ИЛИ принудительно по кнопке справки в хедере ([coachmarkForced]).
    var coachmarkForced by remember { mutableStateOf(false) }
    val coachmarkVisible = !overlayVisible && !isFullscreen &&
        (coachmarkForced || (state.isWorkoutStarted && !state.coachmarkShown))

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
            // Пульс в точке scrub — из trackPoints напрямую (в cumulativeData
            // серии пульса нет). Гейт по avgHeartRateDisplay: у тренировки без
            // датчика строка пульса в карточке не показывается вовсе; при
            // локальном пропуске сэмпла (обрыв) — "—".
            heartRateDisplay = if (summary.avgHeartRateDisplay != null) {
                summary.trackPoints.getOrNull(scrubIndex)?.heartRate
                    ?.let { WorkoutSummaryFormatters.formatHeartRate(it) } ?: "—"
            } else null,
        )
    } else null

    val scrubPoint = scrubIndex?.let { summary?.trackPoints?.getOrNull(it) }

    // ── Панель деталей (сплиты/график) ──────────────────────────────────────
    // Разворачивается чевроном на StatsRow и рисуется поверх зоны карты —
    // сама карта остаётся в композиции (пересоздание MapView ломает MapLibre).
    // remember(summary): при открытии другой тренировки панель сворачивается.
    var detailsExpanded by remember(summary) { mutableStateOf(false) }
    val hasDetails = summary != null && summaryHasDetails(summary)

    // ── Шаринг тренировки картинкой ─────────────────────────────────────────
    // «С картой»: инкремент счётчика → MapViewComposable делает snapshot →
    // onSnapshot дорисовывает плашку и открывает share sheet.
    // «Только статистика»: карточка с силуэтом трека собирается сразу.
    // Сборка Bitmap и запись PNG — на IO (блокирующие операции).
    val shareScope = rememberCoroutineScope()
    var snapshotTick by remember { mutableIntStateOf(0) }
    fun shareStatsOf(s: WorkoutSummaryUiState) = ShareImageComposer.ShareStats(
        activityName = s.activityName,
        dateDisplay = s.dateDisplay,
        distanceDisplay = s.distanceDisplay,
        durationDisplay = s.durationDisplay,
        paceDisplay = s.paceDisplay,
    )

    // ── Хинт «Удерживайте 3 сек» на короткий тап по «Завершить» ──────────────
    // Инкремент триггера (из onShortTapFinish) перезапускает таймер авто-скрытия.
    var finishHintTrigger by remember { mutableIntStateOf(0) }
    var finishHintVisible by remember { mutableStateOf(false) }

    // Bounds контролов в координатах окна — для spotlight-выреза в coachmark
    // (шаг 1 — «Завершить», шаг 2 — GPS-бейдж, шаг 3 — HR-бейдж).
    var finishButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var gpsBadgeBounds by remember { mutableStateOf<Rect?>(null) }
    var hrBadgeBounds by remember { mutableStateOf<Rect?>(null) }
    // Текущий шаг многошагового онбординга (0-based).
    var coachmarkStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(finishHintTrigger) {
        if (finishHintTrigger > 0) {
            finishHintVisible = true
            delay(2200)
            finishHintVisible = false
        }
    }

    // ── Системная кнопка Back ────────────────────────────────────────────────
    // В полноэкранном режиме карты — сворачиваем к обычному оверлею.
    // При развёрнутых деталях — сворачиваем панель.
    // В обычном оверлее — закрываем оверлей и возвращаем экран в исходное состояние.
    BackHandler(enabled = overlayVisible) {
        when {
            isFullscreen -> onToggleFullscreenMap()
            detailsExpanded -> detailsExpanded = false
            else -> onCloseSummary()
        }
    }

    // Box-обёртка — чтобы поверх экрана лечь onboarding-coachmark на весь экран.
    Box(modifier = Modifier.fillMaxSize()) {
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
                    onShareWithMap = { snapshotTick++ },
                    onShareStatsOnly = {
                        shareScope.launch(Dispatchers.IO) {
                            val bitmap = ShareImageComposer.composeTrackCard(
                                ctx, summary.trackPoints, shareStatsOf(summary)
                            )
                            ShareImageComposer.shareBitmap(ctx, bitmap)
                        }
                    },
                    // GPX только при непустом треке: тренировка без GPS-точек
                    // (зал/недоступный трек истории) — экспортировать нечего.
                    onShareGpx = if (summary.trackPoints.isNotEmpty()) {
                        {
                            shareScope.launch(Dispatchers.IO) {
                                GpxComposer.shareGpx(ctx, summary)
                            }
                        }
                    } else null,
                )
            } else {
                ActiveHeader(
                    dateDisplay = state.currentDate,
                    onHelpClick = { coachmarkStep = 0; coachmarkForced = true },
                )
            }
        }
        HorizontalDivider(color = ColorPrimary, thickness = 1.dp)

        // ── Баннер: Doze whitelist не выдан ─────────────────────────────────────
        // Показывается только в основном (pre-start) режиме экрана. Скрыт во время
        // активной тренировки (юзер уже в процессе — не дёргать), в оверлее итогов
        // и в fullscreen-карте.
        // Тап на "Настроить" → системный диалог запроса whitelist; статус обновится
        // через ON_RESUME observer и баннер исчезнет.
        if (!batteryOptimized && !overlayVisible && !isFullscreen && !state.isTracking) {
            BatteryOptBanner(
                onConfigureClick = {
                    bannerBatteryOptLauncher.launch(
                        BatteryOptimizationHelper.buildRequestIntent(ctx)
                    )
                }
            )
        }

        // ── Тело: таймер/статистика (active) ↔ активность/карточки (summary) ──
        // Показывается только когда тренировка идёт/на паузе (isWorkoutStarted)
        // или открыт оверлей итогов. В pre-start блок схлопнут — карта занимает
        // всю высоту до даты, а выбор активности живёт внизу над кнопкой старта.
        // В полноэкранном режиме карты тоже схлопывается, оставляя только шапку.
        AnimatedVisibility(
            visible = !isFullscreen && (state.isWorkoutStarted || overlayVisible),
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            // AnimatedContent размерит блок по РЕАЛЬНОМУ контенту (active короче
            // summary — нет селектора) и плавно анимирует высоту при переходе
            // active↔summary. Карта — сосед снизу с weight(1f), заполняет остаток;
            // при смене высоты блока просто ресайзится (MapView не пересоздаётся —
            // безопасно). Раньше был alpha-Box с max(active,summary) высотой —
            // после выноса селектора он оставлял пустоту под статами в активной фазе.
            AnimatedContent(targetState = overlayVisible, label = "workout-body") { showOverlay ->
                if (showOverlay) {
                    SummaryBody(
                        state = summary ?: WorkoutSummaryUiState(),
                        detailsExpanded = detailsExpanded,
                        onToggleDetails = if (hasDetails) {
                            { detailsExpanded = !detailsExpanded }
                        } else {
                            null
                        },
                    )
                } else {
                    ActiveBody(state = state)
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
                // Без разрешения LocationComponent не активируется (краш на свежей
                // установке); после выдачи разрешения активируется на лету.
                locationPermissionGranted = locationPermissionGranted,
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
                // Снимок карты для шаринга: счётчик инкрементируется в диалоге
                // SummaryHeader («С картой»), Bitmap приходит сюда.
                snapshotRequest = snapshotTick,
                onSnapshot = { mapBitmap ->
                    val s = state.summaryOverlay
                    if (s != null) {
                        shareScope.launch(Dispatchers.IO) {
                            val bitmap = ShareImageComposer.composeWithMap(
                                ctx, mapBitmap, shareStatsOf(s)
                            )
                            ShareImageComposer.shareBitmap(ctx, bitmap)
                        }
                    }
                },
            )

            // Прозрачный слой для перехвата клика в режиме превью оверлея.
            // MapView внутри AndroidView поглощает тапы, поэтому Modifier.clickable
            // на самой карте не работает. Накладываем Box сверху.
            // При развёрнутых деталях перехват отключён — тап должен оставаться
            // в панели, а не разворачивать карту под ней.
            if (overlayVisible && !isFullscreen && !detailsExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onToggleFullscreenMap)
                )
            }

            // ── Панель деталей: сплиты + график поверх зоны карты ────────────
            // Карта не убирается из композиции (MapLibre-краши при пересоздании),
            // панель просто накрывает её непрозрачным фоном.
            if (overlayVisible && !isFullscreen && detailsExpanded && summary != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ColorBackground),
                ) {
                    SummaryDetailsPanel(
                        state = summary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
                        .onGloballyPositioned { gpsBadgeBounds = it.boundsInWindow() }
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

            // ── HR-бейдж — под GPS-бейджем, виден всегда (как GPS), гейт — настройка
            // «Бейдж пульса» (дефолт вкл). Статусный: зелёный = датчик подключён,
            // красный = нет связи. Значение пульса здесь НЕ показывается — оно уже
            // есть в ряду статистики (StatItem «Пульс»). Тап открывает ОВЕРЛЕЙ
            // «Датчики» поверх экрана (SensorsOverlay в WorkoutHomeScreen) — быстрый
            // путь к подключению; навигация с живой карты запрещена (нюанс 36).
            if (!overlayVisible && state.showHrBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 8.dp)
                        .onGloballyPositioned { hrBadgeBounds = it.boundsInWindow() }
                        // clip ПЕРЕД clickable — иначе ripple рисуется на прямоугольнике
                        .clip(RoundedCornerShape(size = 32.dp))
                        .clickable { onOpenSensors() }
                        .border(
                            width = 1.dp,
                            color = ColorPrimary,
                            shape = RoundedCornerShape(size = 32.dp),
                        )
                        .width(32.dp)
                        .height(32.dp)
                        .background(
                            color = if (state.hrmConnected) ColorGpsActive else ColorGpsInactive,
                            shape = RoundedCornerShape(size = 32.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = if (state.hrmConnected) {
                            stringResource(R.string.hrm_badge_connected)
                        } else {
                            stringResource(R.string.hrm_badge_disconnected)
                        },
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── Демо-HR-бейдж во время онбординга (шаг 3) ────────────────────
            // Если бейдж выключен в настройках, на экране его нет — показываем
            // копию на штатном месте (под GPS-бейджем), чтобы вырез+стрелка
            // coachmark указывали на контрол (как с GPS-бейджем).
            if (!overlayVisible && coachmarkVisible && coachmarkStep == 2 && !state.showHrBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 8.dp)
                        .onGloballyPositioned { hrBadgeBounds = it.boundsInWindow() }
                        .clip(RoundedCornerShape(size = 32.dp))
                        .border(1.dp, ColorPrimary, RoundedCornerShape(size = 32.dp))
                        .width(32.dp)
                        .height(32.dp)
                        .background(ColorGpsActive, RoundedCornerShape(size = 32.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
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

            // ── Низ карты: выбор активности + кнопки ─────────────────────────
            if (!overlayVisible) {
                if (!state.isWorkoutStarted) {
                    // Pre-start: селектор активности стопкой над кнопкой «Начать»,
                    // с небольшим разрывом. Оба плавают у низа карты-героя.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        ActivityTypeSelector(
                            state = state,
                            onTypeSelected = onTypeSelected,
                            onMoreClick = { showTypeSelector = true },
                            isMoreActive = showTypeSelector,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onStartClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary),
                        ) {
                            Text(
                                text = stringResource(R.string.workout_start),
                                style = WorkoutTextStyles.primaryButtonLabel,
                                color = Color.White,
                            )
                        }
                    }
                } else if (!state.isTracking) {
                    // На паузе — те же две кнопки, что в активной фазе, только
                    // слева «Продолжить» вместо «Пауза». Раньше была одна кнопка
                    // «Продолжить», и завершить тренировку с паузы было нельзя.
                    PausableFinishRow(
                        leftLabel = stringResource(R.string.workout_resume),
                        onLeft = onStartClick,
                        onFinish = onFinishClick,
                        onShortTapFinish = { finishHintTrigger++ },
                        finishConfirmationHold = state.finishConfirmationHold,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onFinishBoundsChanged = { finishButtonBounds = it },
                        demoFill = coachmarkVisible && coachmarkStep == 0,
                    )
                } else {
                    PausableFinishRow(
                        leftLabel = stringResource(R.string.workout_pause),
                        onLeft = onPauseClick,
                        onFinish = onFinishClick,
                        onShortTapFinish = { finishHintTrigger++ },
                        finishConfirmationHold = state.finishConfirmationHold,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onFinishBoundsChanged = { finishButtonBounds = it },
                        demoFill = coachmarkVisible && coachmarkStep == 0,
                    )
                }

                // Хинт «Удерживайте 3 сек» — на короткий тап по «Завершить» в hold-режиме.
                // Выезжает справа над кнопкой, авто-скрывается (см. LaunchedEffect выше).
                FinishHoldHint(
                    visible = finishHintVisible,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 74.dp),
                )
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

        // ── Onboarding-coachmark: одноразовый при первом входе в активную фазу ──
        if (coachmarkVisible) {
            WorkoutCoachmark(
                step = coachmarkStep,
                stepCount = COACHMARK_STEPS,
                finishConfirmationHold = state.finishConfirmationHold,
                finishAvailable = state.isWorkoutStarted,
                finishButtonBounds = finishButtonBounds,
                gpsBadgeBounds = gpsBadgeBounds,
                hrBadgeBounds = hrBadgeBounds,
                onNext = { coachmarkStep++ },
                onBack = { if (coachmarkStep > 0) coachmarkStep-- },
                onDismiss = {
                    coachmarkStep = 0
                    coachmarkForced = false
                    onCoachmarkDismissed()
                },
            )
        }
    } // конец Box-обёртки

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
private fun ActiveHeader(dateDisplay: String, onHelpClick: () -> Unit) {
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
        // Кнопка справки (левый угол хедера) — открывает онбординг в любой момент.
        Image(
            painter = painterResource(id = R.drawable.ic_help),
            contentDescription = "Справка",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onHelpClick),
        )
    }
}

// ── Активная фаза: таймер + статистика + ряд активностей ─────────────────────

@Composable
private fun ActiveBody(
    state: WorkoutStartViewModel.UiState,
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
            // С настроенным пульсометром элементов четыре — ширины ужаты,
            // чтобы ряд помещался на экранах 360dp.
            if (state.hrmConfigured) {
                StatItem(value = state.distanceDisplay, label = stringResource(R.string.workout_distance), valueMinWidth = 75.dp)
                StatItem(value = state.avgSpeedDisplay, label = stringResource(R.string.workout_avg_speed), valueMinWidth = 115.dp)
                StatItem(value = state.caloriesDisplay, label = stringResource(R.string.workout_calories), valueMinWidth = 80.dp)
                StatItem(value = state.heartRateDisplay, label = stringResource(R.string.workout_heart_rate), valueMinWidth = 45.dp)
            } else {
                StatItem(value = state.distanceDisplay, label = stringResource(R.string.workout_distance), valueMinWidth = 100.dp)
                StatItem(value = state.avgSpeedDisplay, label = stringResource(R.string.workout_avg_speed), valueMinWidth = 140.dp)
                StatItem(value = state.caloriesDisplay, label = stringResource(R.string.workout_calories), valueMinWidth = 110.dp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Переключатель типа активности (pre-start).
 *
 * Живёт внизу карты-героя над кнопкой «Начать» (перенесён из [ActiveBody]).
 * Плавает поверх карты, поэтому у контейнера сплошной белый фон — иначе тайлы
 * просвечивали бы между иконками. Показывается только до старта тренировки:
 * тип нельзя менять в ходе записи, поэтому в активной фазе селектора нет.
 */
@Composable
private fun ActivityTypeSelector(
    state: WorkoutStartViewModel.UiState,
    onTypeSelected: (WorkoutType) -> Unit,
    onMoreClick: () -> Unit,
    isMoreActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, ColorPrimary, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        state.pinnedTypes.forEach { type ->
            WorkoutTypeIcon(
                iconModel = type.iconFile ?: type.imageUrl ?: activityIconRes(type.iconKey),
                contentDescription = type.name,
                isActive = !isMoreActive && type.id == state.selectedType?.id,
                onClick = { onTypeSelected(type) },
            )
        }
        WorkoutTypeIcon(
            iconModel = R.drawable.ic_activity_other,
            contentDescription = stringResource(R.string.workout_more),
            isActive = isMoreActive,
            onClick = onMoreClick,
        )
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

/** Число шагов онбординга: 1 — управление, 2 — GPS и карта, 3 — датчик пульса. */
private const val COACHMARK_STEPS = 3

/**
 * Многошаговый onboarding-coachmark при первом входе в активную тренировку.
 *
 * Скрим на весь экран с вырезом-«дыркой» над контролом текущего шага (шаг 1 —
 * «Завершить», шаг 2 — GPS-бейдж, шаг 3 — HR-бейдж): контрол светится сквозь
 * затемнение, обведён и со стрелкой; callout-карточка рядом (над контролом снизу /
 * под контролом сверху). Если контрол не на экране (напр. HR-бейдж без
 * подключённого датчика) — карточка у низа без выреза. «Далее»/«Назад» листают
 * шаги, «Понятно»/тап по скриму → [onDismiss] (персист).
 */
@Composable
private fun WorkoutCoachmark(
    step: Int,
    stepCount: Int,
    finishConfirmationHold: Boolean,
    finishAvailable: Boolean,
    finishButtonBounds: Rect?,
    gpsBadgeBounds: Rect?,
    hrBadgeBounds: Rect?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    val isLast = step >= stepCount - 1
    val title = when (step) {
        0 -> "Управление тренировкой"
        1 -> "GPS и карта"
        else -> "Датчик пульса"
    }
    // Контрол текущего шага + скругление дырки (Завершить — слева квадратная).
    // Шаг 1 (Завершить) без активной фазы (справка из pre-start) не подсвечиваем —
    // кнопки нет на экране, bounds протухшие.
    val targetBounds = when (step) {
        0 -> finishButtonBounds.takeIf { finishAvailable }
        1 -> gpsBadgeBounds
        else -> hrBadgeBounds
    }
    val squareLeft = step == 0
    val cornerDp = if (step == 0) 12f else 18f

    val holePadPx = with(density) { 4.dp.toPx() }
    val hole: Rect? = targetBounds?.let {
        Rect(
            it.left - origin.x - holePadPx, it.top - origin.y - holePadPx,
            it.right - origin.x + holePadPx, it.bottom - origin.y + holePadPx,
        )
    }
    // Контрол в верхней половине (GPS-бейдж) → карточка снизу, стрелка вверх; иначе наоборот.
    val targetAtTop = hole != null && hole.center.y < overlaySize.height / 2f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow(); overlaySize = it.size },
    ) {
        // 1. Скрим с вырезом-«дыркой» (BlendMode.Clear нужен offscreen-слой).
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            hole?.let { h ->
                val cr = CornerRadius(cornerDp.dp.toPx())
                val z = CornerRadius.Zero
                val holePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = h,
                            topLeft = if (squareLeft) z else cr,
                            topRight = cr,
                            bottomRight = cr,
                            bottomLeft = if (squareLeft) z else cr,
                        )
                    )
                }
                drawPath(holePath, Color.Transparent, blendMode = BlendMode.Clear)
                drawPath(
                    path = holePath,
                    color = ColorSecondary,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
                // Стрелка к контролу: вниз (контрол снизу) или вверх (контрол сверху).
                val cx = h.center.x
                val gap = 10.dp.toPx()
                val aw = 18.dp.toPx()
                val ah = 12.dp.toPx()
                val arrow = Path().apply {
                    if (targetAtTop) {
                        val tipY = h.bottom + gap
                        moveTo(cx - aw / 2, tipY + ah); lineTo(cx + aw / 2, tipY + ah); lineTo(cx, tipY)
                    } else {
                        val tipY = h.top - gap
                        moveTo(cx - aw / 2, tipY - ah); lineTo(cx + aw / 2, tipY - ah); lineTo(cx, tipY)
                    }
                    close()
                }
                drawPath(arrow, ColorSecondary)
            }
        }

        // 2. Ловушка тапа для дисмисса.
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss))

        // 3. Карточка: над контролом (снизу экрана) или под контролом (сверху экрана).
        // Если подсвечивать нечего (напр. справка из pre-start — кнопки «Завершить»
        // на экране нет) — центрируем как обычную модалку, а не жмём к низу.
        val arrowZonePx = with(density) { 30.dp.toPx() }
        val cardModifier = when {
            hole == null -> Modifier.align(Alignment.Center)
            targetAtTop -> Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, (hole.bottom + arrowZonePx).toInt()) }
            else -> Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(0, (hole.top - arrowZonePx - overlaySize.height).toInt()) }
        }
        Column(
            modifier = cardModifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Text(
                text = "${step + 1}/$stepCount  $title",
                color = ColorPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            when (step) {
                0 -> {
                    if (finishConfirmationHold) {
                        CoachmarkTip("Кнопку «Завершить» удерживайте 3 секунды — защита от случайного нажатия.")
                    }
                    CoachmarkTip("На паузе доступны кнопки «Продолжить» и «Завершить».")
                    CoachmarkTip("Автопаузу (авто-стоп при остановке) можно включить в Настройках.")
                }
                1 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GpsBadgeSample(active = true, label = "Сигнал есть")
                        GpsBadgeSample(active = false, label = "Поиск сигнала")
                    }
                    Spacer(Modifier.height(10.dp))
                    CoachmarkTip("Нажмите на бейдж — карта центрируется на вашей позиции.")
                    CoachmarkTip("На паузе карту можно свободно двигать.")
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HrBadgeSample(connected = true, label = "128 уд/мин")
                        HrBadgeSample(connected = false, label = "Нет связи")
                    }
                    Spacer(Modifier.height(10.dp))
                    CoachmarkTip("Нажмите на бейдж — откроется список датчиков.")
                    CoachmarkTip("Бейдж можно скрыть в Настройках.")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // «Назад» — серая полупрозрачная, со второго шага и далее.
                if (step > 0) {
                    TextButton(onClick = onBack) {
                        Text(text = "Назад", color = ColorPrimary.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = if (isLast) onDismiss else onNext) {
                    Text(
                        text = if (isLast) "Понятно" else "Далее",
                        color = ColorPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Мини-образец GPS-бейджа (как на карте) + подпись — для шага «GPS и карта». */
@Composable
private fun GpsBadgeSample(active: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (active) ColorGpsActive else ColorGpsInactive)
                .border(1.dp, ColorPrimary, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.gps),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = ColorPrimary, fontSize = 13.sp)
    }
}

/** Мини-образец HR-бейджа (как на карте) + подпись — для шага «Датчик пульса». */
@Composable
private fun HrBadgeSample(connected: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (connected) ColorGpsActive else ColorGpsInactive)
                .border(1.dp, ColorPrimary, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = ColorPrimary, fontSize = 13.sp)
    }
}

/** Один пункт-совет в coachmark: маркер «•» + текст. */
@Composable
private fun CoachmarkTip(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "•  ", color = ColorPrimary, fontSize = 14.sp)
        Text(text = text, color = ColorPrimary, fontSize = 14.sp)
    }
}

/**
 * Хинт «Удерживайте 3 сек» — выезжает справа с fade, авто-скрывается.
 * Отдельный composable (не inline): вне вложенных Column/Box-скоупов
 * `AnimatedVisibility` разрешается однозначно (плоская перегрузка).
 */
@Composable
private fun FinishHoldHint(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ColorPrimary)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.workout_hold_hint),
                color = Color.White,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * Ряд действий тренировки: левая вторичная кнопка (Пауза/Продолжить) + правая
 * «Завершить». Используется и в активной фазе (лево = «Пауза»), и на паузе
 * (лево = «Продолжить») — низ карты, `BottomCenter`.
 *
 * @param leftLabel текст левой кнопки, [onLeft] — её клик.
 * @param finishConfirmationHold правая кнопка требует удержания 3 сек (см.
 *   [FinishActionButton]).
 */
@Composable
private fun PausableFinishRow(
    leftLabel: String,
    onLeft: () -> Unit,
    onFinish: () -> Unit,
    onShortTapFinish: () -> Unit,
    finishConfirmationHold: Boolean,
    modifier: Modifier = Modifier,
    onFinishBoundsChanged: (Rect) -> Unit = {},
    demoFill: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(50.dp),
    ) {
        OutlinedButton(
            onClick = onLeft,
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
                text = leftLabel,
                style = WorkoutTextStyles.primaryButtonLabel,
                color = ColorPrimary,
            )
        }
        FinishActionButton(
            finishConfirmationHold = finishConfirmationHold,
            onFinish = onFinish,
            onShortTap = onShortTapFinish,
            shape = RoundedCornerShape(
                topStart = 0.dp, bottomStart = 0.dp,
                topEnd = 10.dp, bottomEnd = 10.dp,
            ),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .onGloballyPositioned { onFinishBoundsChanged(it.boundsInWindow()) },
            demoFill = demoFill,
        )
    }
}

/**
 * Правая кнопка «Завершить»: по [finishConfirmationHold] — либо
 * [HoldToFinishButton] (удержание 3 сек), либо обычная `Button` (мгновенный тап).
 */
@Composable
private fun FinishActionButton(
    finishConfirmationHold: Boolean,
    onFinish: () -> Unit,
    onShortTap: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    demoFill: Boolean = false,
) {
    if (finishConfirmationHold) {
        HoldToFinishButton(onFinish = onFinish, onShortTap = onShortTap, shape = shape, modifier = modifier, demoFill = demoFill)
    } else {
        Button(
            onClick = onFinish,
            modifier = modifier,
            shape = shape,
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

/**
 * Кнопка «Завершить» с подтверждением по удержанию (3 сек).
 *
 * Пока палец держит кнопку — заполнение [ColorSecondary] растёт слева направо;
 * дойдя до конца (3 сек), вызывает [onFinish]. Отпускание раньше — заполнение
 * плавно откатывается к нулю, тренировка НЕ завершается (защита от случайного
 * нажатия: кнопка в нижней зоне под большим пальцем, телефон в кармане).
 *
 * Обычный тап не срабатывает намеренно — нужно именно удержание.
 * Режим включается настройкой `finishConfirmationHold` (Меню → Настройки);
 * при выключенной настройке рисуется обычная `Button` с мгновенным тапом.
 */
@Composable
private fun HoldToFinishButton(
    onFinish: () -> Unit,
    onShortTap: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    demoFill: Boolean = false,
) {
    val holdDurationMs = 3000
    // Animatable.value — читаемый State: кадр анимации перерисовывает заполнение.
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Демо в coachmark: кнопка сама заполняется (3с) → пауза (2с) → снова,
    // наглядно показывая механику удержания. Кнопка под скримом — жест не мешает.
    LaunchedEffect(demoFill) {
        if (demoFill) {
            // Стартовая пауза 1.5с: не заполнять сразу — дать прочитать карточку.
            delay(1500)
            while (isActive) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(holdDurationMs, easing = LinearEasing))
                delay(2000)
            }
        } else {
            progress.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(ColorPrimary)
            .pointerInput(onFinish, onShortTap) {
                detectTapGestures(
                    onPress = {
                        var completed = false
                        val fill = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(holdDurationMs, easing = LinearEasing),
                            )
                            // Дошло до конца, палец ещё держит — завершаем сразу.
                            completed = true
                            onFinish()
                        }
                        // Ждём отпускания/отмены жеста.
                        tryAwaitRelease()
                        if (!completed) {
                            fill.cancel()
                            // Быстрый тап (отпущено за ~350 мс) — пользователь ждал
                            // мгновенного завершения; подсказываем, что нужно удержание.
                            if (progress.value < 0.12f) onShortTap()
                            scope.launch { progress.animateTo(0f, tween(250)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Заполнение слева направо: ширина = доля progress от ширины кнопки.
        if (progress.value > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(ColorSecondary),
            )
        }
        Text(
            text = stringResource(R.string.workout_finish),
            style = WorkoutTextStyles.primaryButtonLabel,
            color = Color.White,
        )
    }
}

/**
 * Баннер-предупреждение: приложение не в Doze whitelist.
 *
 * Показывается под шапкой стартового экрана когда юзер отказался от системного
 * запроса (или установлен сторонним способом). Без whitelist Android Doze
 * throttle'ит GPS-обновления через ~5-10 мин после выключения экрана —
 * точки могут не записаться при длительной тренировке с заблокированным экраном.
 *
 * Цвет фона — мягкий амбер для warning'а (не критическая ошибка, а инфо-сообщение).
 * Заметный, но не агрессивный — юзер может продолжать без настройки.
 */
@Composable
private fun BatteryOptBanner(onConfigureClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF4D6))   // soft amber
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.battery_opt_banner_text),
            style = WorkoutTextStyles.statLabel,
            color = ColorPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onConfigureClick,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, ColorPrimary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor   = ColorPrimary,
            ),
            modifier = Modifier.height(36.dp),
        ) {
            Text(
                text  = stringResource(R.string.battery_opt_banner_action),
                color = ColorPrimary,
            )
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────
// Карта в @Preview рисуется заглушкой (MapViewComposable гейтит LocalInspectionMode).

private val previewWorkoutTypes = listOf(
    WorkoutType(id = 1, name = "Бег", iconKey = "1"),
    WorkoutType(id = 3, name = "Велосипед", iconKey = "3"),
    WorkoutType(id = 5, name = "Ходьба", iconKey = "5"),
)

@Composable
private fun WorkoutStartScreenPreviewHost(state: WorkoutStartViewModel.UiState) {
    SmartTrackerTheme {
        WorkoutStartScreen(
            state = state,
            padding = PaddingValues(0.dp),
            onStartClick = {}, onTypeSelected = {}, onSheetTypeSelected = {},
            onPauseClick = {}, onFinishClick = {}, onMapTilesFailed = {},
            onToggleFavorite = {}, onSearchQueryChange = {}, onCloseSummary = {},
            onToggleFullscreenMap = {}, onDeleteHistoryTraining = {},
        )
    }
}

@Preview(showBackground = true, name = "Тренировка — до старта")
@Composable
private fun WorkoutStartPreStartPreview() = WorkoutStartScreenPreviewHost(
    WorkoutStartViewModel.UiState(
        currentDate = "22.07.2026 (Среда)",
        pinnedTypes = previewWorkoutTypes,
        selectedType = previewWorkoutTypes.first(),
    )
)

@Preview(showBackground = true, name = "Тренировка — активная")
@Composable
private fun WorkoutStartActivePreview() = WorkoutStartScreenPreviewHost(
    WorkoutStartViewModel.UiState(
        currentDate = "22.07.2026 (Среда)",
        pinnedTypes = previewWorkoutTypes,
        selectedType = previewWorkoutTypes.first(),
        isWorkoutStarted = true,
        isTracking = true,
        timerDisplay = "00:12:34",
        distanceDisplay = "2.45 км",
        avgSpeedDisplay = "5:06 мин/км",
        caloriesDisplay = "180 кКал",
    )
)

