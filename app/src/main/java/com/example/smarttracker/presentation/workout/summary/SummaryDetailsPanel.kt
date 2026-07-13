package com.example.smarttracker.presentation.workout.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorFieldFill
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Панель «Детали» оверлея итогов: километровые сплиты + график профиля
 * тренировки (скорость/высота по дистанции).
 *
 * Разворачивается чевроном на [SummaryBody] и рисуется ПОВЕРХ зоны карты в
 * [com.example.smarttracker.presentation.workout.start.WorkoutStartScreen] —
 * MapView при этом остаётся в композиции (пересоздание MapLibre-вью ломает
 * анимации LocationComponent, см. заголовок SummaryOverlay.kt).
 *
 * Доступность данных:
 *  - сплиты и график скорости — только при настоящих временных метках трека
 *    ([SplitsBuilder.hasRealTiming]); для истории появятся после BR-5;
 *  - график высоты — при наличии altitude в точках (есть и в истории).
 */

/** Вкладка графика. Одна серия на полотне — переключение вместо двойной оси. */
private enum class ChartTab { SPEED, ELEVATION }

/** Есть ли что показывать в панели деталей (гейт для чеврона в [SummaryBody]). */
fun summaryHasDetails(state: WorkoutSummaryUiState): Boolean =
    state.splits.isNotEmpty() ||
        SplitsBuilder.hasRealTiming(state.cumulativeData) ||
        elevationSeriesAvailable(state) ||
        state.avgHeartRateDisplay != null

/** Высотный профиль доступен: ≥2 точки с ненулевой альтиметрией. */
private fun elevationSeriesAvailable(state: WorkoutSummaryUiState): Boolean {
    var count = 0
    for (p in state.trackPoints) if (p.altitude != null && ++count >= 2) return true
    return false
}

@Composable
fun SummaryDetailsPanel(
    state: WorkoutSummaryUiState,
    modifier: Modifier = Modifier,
) {
    // Серии графика. remember(state): snapshot неизменяемый, пересчёт только
    // при открытии другой тренировки.
    val gapSet = remember(state) { state.pauseGapIndices.toHashSet() }
    val speedSegments = remember(state) {
        if (!SplitsBuilder.hasRealTiming(state.cumulativeData)) {
            emptyList()
        } else {
            val cd = state.cumulativeData
            val xs = ArrayList<Float>(cd.speedsMs.size)
            val ys = ArrayList<Float>(cd.speedsMs.size)
            // i=0 и gap-точки пропускаем: их speed=0 — артефакт расчёта
            // (нет предыдущей точки движения), не реальная остановка.
            // Дистанция на паузе не растёт, поэтому пропуск не рвёт ось X.
            for (i in 1 until cd.speedsMs.size) {
                if (i in gapSet) continue
                xs.add(cd.distancesKm[i])
                ys.add(cd.speedsMs[i] * 3.6f) // м/с → км/ч
            }
            TrackChartData.prepare(xs, ys, emptySet())
        }
    }
    val elevationSegments = remember(state) {
        val cd = state.cumulativeData
        val n = minOf(state.trackPoints.size, cd.distancesKm.size)
        val xs = ArrayList<Float>(n)
        val ys = ArrayList<Float>(n)
        for (i in 0 until n) {
            val alt = state.trackPoints[i].altitude ?: continue
            xs.add(cd.distancesKm[i])
            ys.add(alt.toFloat())
        }
        // Высота непрерывна и через паузы (реальные метры) — сегменты не рвём.
        // Сглаживание шире: GPS-альтиметр шумит на ±5–10 м.
        TrackChartData.prepare(xs, ys, emptySet(), smoothWindow = 9)
    }
    val speedAvailable = speedSegments.isNotEmpty()
    val elevationAvailable = elevationSegments.isNotEmpty()

    var tab by remember(state) {
        mutableStateOf(if (speedAvailable) ChartTab.SPEED else ChartTab.ELEVATION)
    }

    Column(
        modifier = modifier
            // Глушим тапы: под панелью лежит MapView и слой клика
            // «развернуть карту» — просачивание тапа сквозь пустые места
            // панели развернуло бы карту под открытыми деталями.
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // ── Пульс (avg/max с BLE-датчика) ────────────────────────────────────
        // null = в точках тренировки пульса нет (датчик не подключался;
        // история — до BR-16) → секция целиком скрыта.
        if (state.avgHeartRateDisplay != null) {
            Text("Пульс", style = WorkoutTextStyles.screenHeaderDate)
            Spacer(modifier = Modifier.height(6.dp))
            HeartRateRow(
                label = stringResource(R.string.summary_avg_heart_rate),
                value = state.avgHeartRateDisplay,
            )
            state.maxHeartRateDisplay?.let {
                HeartRateRow(
                    label = stringResource(R.string.summary_max_heart_rate),
                    value = it,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Сплиты ───────────────────────────────────────────────────────────
        if (state.splits.isNotEmpty()) {
            Text("Сплиты", style = WorkoutTextStyles.screenHeaderDate)
            Spacer(modifier = Modifier.height(6.dp))
            SplitsHeaderRow()
            state.splits.forEach { SplitRow(it) }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── График ───────────────────────────────────────────────────────────
        if (speedAvailable || elevationAvailable) {
            Text("Профиль тренировки", style = WorkoutTextStyles.screenHeaderDate)
            Spacer(modifier = Modifier.height(8.dp))
            if (speedAvailable && elevationAvailable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartChip("Скорость", tab == ChartTab.SPEED) { tab = ChartTab.SPEED }
                    ChartChip("Высота", tab == ChartTab.ELEVATION) { tab = ChartTab.ELEVATION }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            val showSpeed = tab == ChartTab.SPEED && speedAvailable
            TrackChart(
                segments = if (showSpeed) speedSegments else elevationSegments,
                yLabel = if (showSpeed) {
                    { v: Float -> "%.1f км/ч".format(Locale.US, v) }
                } else {
                    { v: Float -> "%.0f м".format(Locale.US, v) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ── Пульс: строка «лейбл — значение» ─────────────────────────────────────────

@Composable
private fun HeartRateRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = WorkoutTextStyles.timelineInfo,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = WorkoutTextStyles.timelineLabelBold,
        )
    }
}

// ── Сплиты: строки таблицы ────────────────────────────────────────────────────

@Composable
private fun SplitsHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("км", style = WorkoutTextStyles.statCardLabel, modifier = Modifier.width(36.dp))
        Text("темп", style = WorkoutTextStyles.statCardLabel, modifier = Modifier.width(96.dp))
    }
}

@Composable
private fun SplitRow(split: SplitUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = split.label,
            style = WorkoutTextStyles.timelineInfo.copy(
                fontWeight = if (split.isPartial) FontWeight.Light else FontWeight.Bold,
            ),
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = split.paceDisplay,
            style = WorkoutTextStyles.timelineInfo,
            modifier = Modifier.width(96.dp),
        )
        // Бар относительной скорости круга: самый быстрый = полная ширина.
        // Значение несёт текст темпа слева — бар лишь визуальное сравнение.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(ColorFieldFill.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(split.relativeSpeed.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(ColorSecondary),
            )
        }
    }
}

// ── Чипы переключения серии графика ──────────────────────────────────────────

@Composable
private fun ChartChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ColorPrimary else Color.White)
            .border(width = 1.dp, color = ColorPrimary, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = WorkoutTextStyles.statsOverlayValue,
            color = if (selected) Color.White else ColorPrimary,
        )
    }
}
