package com.example.smarttracker.presentation.workout.summary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.workout.activityIconRes

/**
 * Composable-ы оверлея итогов завершённой тренировки.
 *
 * Используются из [com.example.smarttracker.presentation.workout.start.WorkoutStartScreen]
 * — оверлей рендерится поверх активного экрана без навигации, чтобы сохранить
 * ту же инстанцию `MapView` (избегаем крашей анимаций MapLibre LocationComponent).
 *
 * Публичные точки входа:
 *  - [SummaryHeader]   — шапка со статической датой по центру (без кнопки «назад»);
 *  - [SummaryBody]     — иконка активности + название + темп + три карточки статистики;
 *  - [StatsOverlayCard] — компактная карточка статистики (4 строки; 5-я — пульс,
 *    когда у тренировки есть данные пульса) поверх карты в полноэкранном
 *    режиме (Figma 723:460).
 */

// ── Шапка ─────────────────────────────────────────────────────────────────────

/**
 * Шапка SummaryOverlay: дата по центру, справа — иконка «поделиться» и
 * опциональная иконка корзины.
 *
 * Иконка корзины показывается только при [showDelete] = true (передаётся вызывающим
 * для оверлея, открытого из истории — [SummaryOrigin.HISTORY]).
 * Оба диалога (подтверждение удаления и выбор варианта шаринга) держатся внутри
 * этого composable — вызывающему достаточно передать колбэки.
 *
 * Шаринг: диалог «С картой / Только статистика» — вариант без карты не
 * раскрывает район тренировок (приватность геоданных). [onShareWithMap] = null →
 * иконка шаринга скрыта (нет данных для картинки).
 *
 * Стрелка «назад» убрана — закрытие оверлея делается системной кнопкой Back
 * или переключением вкладки в нижнем баре [WorkoutHomeScreen].
 */
@Composable
fun SummaryHeader(
    dateDisplay: String,
    showDelete: Boolean = false,
    onDeleteClick: () -> Unit = {},
    onShareWithMap: (() -> Unit)? = null,
    onShareStatsOnly: (() -> Unit)? = null,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val shareAvailable = onShareWithMap != null && onShareStatsOnly != null

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
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shareAvailable) {
                Image(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = "Поделиться тренировкой",
                    colorFilter = ColorFilter.tint(ColorPrimary),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showShareDialog = true },
                )
            }
            if (showDelete) {
                Image(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "Удалить тренировку",
                    colorFilter = ColorFilter.tint(ColorPrimary),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showConfirmDialog = true },
                )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Удалить тренировку?") },
            text = { Text("Действие нельзя отменить. Запись будет удалена с сервера.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onDeleteClick()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Отмена") }
            },
        )
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Поделиться тренировкой") },
            text = { Text("Вариант «Только статистика» показывает форму маршрута без карты — место тренировки не раскрывается.") },
            confirmButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    onShareWithMap?.invoke()
                }) { Text("С картой") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    onShareStatsOnly?.invoke()
                }) { Text("Только статистика") }
            },
        )
    }
}

// ── Основной блок: активность + ряд карточек ────────────────────────────────

/**
 * @param detailsExpanded развёрнута ли панель деталей (сплиты/график) — чеврон
 *   на ряду карточек поворачивается на 90°.
 * @param onToggleDetails тап по чеврону; null → чеврон декоративный (данных для
 *   деталей нет — история без временных меток и без альтиметрии).
 */
@Composable
fun SummaryBody(
    state: WorkoutSummaryUiState,
    detailsExpanded: Boolean = false,
    onToggleDetails: (() -> Unit)? = null,
) {
    Column {
        Spacer(modifier = Modifier.height(18.dp))
        ActivityHeader(state = state)
        Spacer(modifier = Modifier.height(20.dp))
        StatsRow(
            state = state,
            detailsExpanded = detailsExpanded,
            onToggleDetails = onToggleDetails,
        )
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun ActivityHeader(state: WorkoutSummaryUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .border(width = 1.dp, color = ColorPrimary, shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = state.activityIconFile
                    ?: state.activityIconUrl
                    ?: activityIconRes(state.activityIconKey),
                contentDescription = state.activityName,
                colorFilter = ColorFilter.tint(ColorPrimary),
                placeholder = painterResource(R.drawable.placeholder),
                error = painterResource(R.drawable.placeholder),
                modifier = Modifier.size(70.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.activityName.ifBlank { "—" },
                style = WorkoutTextStyles.activityName,
            )
            HorizontalDivider(
                color = ColorPrimary,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Text(
                text = state.paceDisplay,
                style = WorkoutTextStyles.activityPace,
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun StatsRow(
    state: WorkoutSummaryUiState,
    detailsExpanded: Boolean = false,
    onToggleDetails: (() -> Unit)? = null,
) {
    // Внешний Box — чтобы наложить стрелку поверх правой границы третьей карточки.
    // Все горизонтальные отступы (внешние и между карточками) одинаковы — 14dp.
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCard(
                iconRes = R.drawable.ic_distance,
                value = state.distanceDisplay,
                label = "Дистанция",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                iconRes = R.drawable.ic_time,
                value = state.durationDisplay,
                label = "Продолжительность",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                iconRes = R.drawable.ic_elevation,
                value = state.elevationDisplay,
                label = "Набор высоты",
                modifier = Modifier.weight(1f),
            )
        }
        // Стрелка-чеврон 24dp на правой границе третьей карточки.
        // align(CenterEnd) ставит правый край Box на parent_width;
        // offset(x = -3) сдвигает на (14 - 24/2 - 1) = ~3dp влево, чтобы центр круга
        // лёг на линию границы карточки (parent_width - 14).
        // Тап разворачивает панель деталей (сплиты/график) поверх зоны карты;
        // при null-колбэке (деталей нет) чеврон остаётся декоративным.
        val arrowAngle by animateFloatAsState(
            targetValue = if (detailsExpanded) 90f else 0f,
            label = "details-arrow",
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-3).dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .border(width = 1.dp, color = ColorPrimary, shape = CircleShape)
                .then(
                    if (onToggleDetails != null) {
                        Modifier.clickable(onClick = onToggleDetails)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow),
                contentDescription = if (onToggleDetails != null) "Детали тренировки" else null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowAngle),
            )
        }
    }
}

@Composable
private fun StatCard(
    iconRes: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(120.dp)
            .border(width = 1.dp, color = ColorPrimary, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            colorFilter = ColorFilter.tint(ColorPrimary),
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = value,
            style = WorkoutTextStyles.statCardValue,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = WorkoutTextStyles.statCardLabel,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Карточка мини-статистики поверх карты в полноэкранном режиме оверлея ────

/**
 * Данные для отображения в [StatsOverlayCard] при scrubbing трека.
 * Когда non-null, заменяет суммарные значения из [WorkoutSummaryUiState].
 *
 * @property speedDisplay    мгновенный темп "M:SS мин/км" или "—" в точке scrub
 * @property elapsedDisplay  прошедшее время "HH:MM:SS" с начала тренировки до scrub
 * @property distanceDisplay дистанция "1.23 км" от старта до scrub
 * @property elevationDisplay набор высоты "12.3 м" от старта до scrub
 * @property heartRateDisplay пульс "148 уд/мин" в точке scrub; "—" если в этой
 *   точке сэмпла нет (обрыв датчика); null = у тренировки пульса нет вовсе
 *   (датчик не подключался, история до BR-16) — строка в карточке скрыта
 */
data class ScrubDisplayStats(
    val speedDisplay:     String,
    val elapsedDisplay:   String,
    val distanceDisplay:  String,
    val elevationDisplay: String,
    val heartRateDisplay: String? = null,
)

@Composable
fun StatsOverlayCard(
    state: WorkoutSummaryUiState,
    modifier: Modifier = Modifier,
    scrubStats: ScrubDisplayStats? = null,
) {
    val dur  = scrubStats?.elapsedDisplay   ?: state.durationDisplay
    val spd  = scrubStats?.speedDisplay     ?: state.paceDisplay
    val dist = scrubStats?.distanceDisplay  ?: state.distanceDisplay
    val elev = scrubStats?.elevationDisplay ?: state.elevationDisplay
    // Пульс: при scrubbing — значение в точке трека, без скраба — средний
    // за тренировку. null (у тренировки нет данных пульса) → строка не
    // рисуется, карточка остаётся четырёхстрочной.
    val hr   = if (scrubStats != null) scrubStats.heartRateDisplay else state.avgHeartRateDisplay
    Column(
        modifier = modifier
            .width(133.dp)
            .height(if (hr != null) 122.dp else 100.dp)
            .background(Color.White, shape = RoundedCornerShape(10.dp))
            .border(width = 1.dp, color = ColorPrimary, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StatsOverlayRow(iconRes = R.drawable.ic_time,            value = dur)
        StatsOverlayRow(iconRes = R.drawable.ic_speed,           value = spd)
        StatsOverlayRow(iconRes = R.drawable.ic_passed_distance, value = dist)
        StatsOverlayRow(iconRes = R.drawable.ic_elevation,       value = elev)
        if (hr != null) StatsOverlayHeartRow(value = hr)
    }
}

/**
 * Строка пульса в [StatsOverlayCard]: у проекта нет drawable-иконки сердца,
 * используется векторная Icons.Filled.Favorite (как в HR-бейдже экрана тренировки).
 */
@Composable
private fun StatsOverlayHeartRow(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = ColorPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = WorkoutTextStyles.statsOverlayValue,
        )
    }
}

@Composable
private fun StatsOverlayRow(iconRes: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorPrimary),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = WorkoutTextStyles.statsOverlayValue,
        )
    }
}

// ── Прогресс-бар «проигрывания» маршрута (Figma 723:496) ────────────────────

/**
 * Полоса прогресса под полноэкранной картой.
 *
 * Дизайн (Figma 723:496):
 *  - Pill-форма (CircleShape) с тонкой обводкой [ColorPrimary] по всей длине;
 *  - левая часть до бегунка заполнена мятным [ColorSecondary];
 *  - правая часть — тёмным [ColorPrimary];
 *  - круглый белый бегунок с обводкой [ColorPrimary] на границе двух сегментов.
 *
 * @param progress 0f..1f — позиция бегунка и граница мятной заливки.
 * @param onProgressChange колбэк при перетаскивании/тапе (0f..1f). Если null — компонент
 *   не реагирует на касания (чисто визуальный режим).
 */
@Composable
fun TrainingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onProgressChange: ((Float) -> Unit)? = null,
) {
    // Размеры заметно увеличены — прогресс-бар крупнее, читается в полноэкранном режиме.
    val trackHeight = 10.dp
    val thumbSize = 28.dp
    val clamped = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize)
            // Тап → прыжок на позицию; горизонтальный drag → плавное перемещение.
            // pointerInput(onProgressChange): при смене колбэка жест переинициализируется.
            .pointerInput(onProgressChange) {
                if (onProgressChange == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onProgressChange((down.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    horizontalDrag(down.id) { change ->
                        onProgressChange(
                            (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        )
                        change.consume()
                    }
                }
            },
    ) {
        // Дорожка: pill, тёмный фон + чёрная обводка. Активная заливка
        // вкладывается внутрь и обрезается общим CircleShape — стык получается
        // прямой на правом крае мятного сегмента и скруглённый на левом крае
        // (за счёт parent-clip).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(ColorPrimary)
                .border(width = 1.dp, color = ColorPrimary, shape = CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .background(ColorSecondary),
            )
        }
        // Бегунок: круг white + 1dp ColorPrimary border, центр на границе сегментов.
        // Используем BoxWithConstraints чтобы знать ширину дорожки в Dp.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val barWidth = maxWidth
            val thumbX = (barWidth - thumbSize) * clamped
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbX)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(width = 1.dp, color = ColorPrimary, shape = CircleShape),
            )
        }
    }
}
