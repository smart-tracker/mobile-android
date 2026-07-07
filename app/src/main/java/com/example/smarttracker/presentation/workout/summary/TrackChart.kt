package com.example.smarttracker.presentation.workout.summary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.presentation.theme.ColorChartLine
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.WorkoutTextStyles

/**
 * График «профиля тренировки» (скорость или высота по дистанции) для панели
 * деталей SummaryOverlay.
 *
 * Рисуется вручную через Compose Canvas — первый график в проекте; сторонние
 * chart-библиотеки не берём (одна кривая с заливкой не оправдывает зависимость).
 *
 * Дизайн-решения (см. skill dataviz):
 *  - одна серия на графике, переключение «Скорость/Высота» чипами — вместо
 *    двух Y-осей на одном полотне (анти-паттерн dual axis);
 *  - линия — [ColorChartLine] (контраст 4.0:1 на белом; сам ColorSecondary
 *    даёт лишь 2.7:1 — ниже WCAG 3:1 для нетекстовой графики);
 *  - подписи значений — цветом текста темы, не цветом серии;
 *  - сетка рецессивная: пунктир с прозрачностью, только min/mid/max.
 *
 * Подготовка данных вынесена в [TrackChartData] (чистый Kotlin, покрыт юнит-тестами):
 * разбиение на сегменты по паузам, сглаживание скользящим средним, даунсемпл
 * до ~[TrackChartData.MAX_POINTS] точек.
 */

/** Точка графика: x — дистанция от старта (км), y — значение (км/ч или м). */
data class ChartPoint(val x: Float, val y: Float)

object TrackChartData {

    /** Больше точек рисовать нет смысла — ширина графика ~360dp. */
    const val MAX_POINTS = 200

    /** Окно скользящего среднего для сглаживания шумной пер-сегментной скорости. */
    const val SMOOTH_WINDOW = 5

    /**
     * Разбивает параллельные массивы x/y на сегменты, разрывая линию на gap-парах
     * пауз: `i ∈ gapIndices` означает, что между точками i−1 и i пользователь
     * стоял на паузе (телепорт) — соединять их отрезком нельзя.
     *
     * Сегменты из одной точки отбрасываются — линию из них не построить.
     */
    fun buildSegments(
        xs: List<Float>,
        ys: List<Float>,
        gapIndices: Set<Int>,
    ): List<List<ChartPoint>> {
        if (xs.size != ys.size || xs.size < 2) return emptyList()
        val segments = ArrayList<List<ChartPoint>>()
        var current = ArrayList<ChartPoint>()
        for (i in xs.indices) {
            if (i in gapIndices && current.isNotEmpty()) {
                if (current.size >= 2) segments.add(current)
                current = ArrayList()
            }
            current.add(ChartPoint(xs[i], ys[i]))
        }
        if (current.size >= 2) segments.add(current)
        return segments
    }

    /**
     * Скользящее среднее по y с окном [window] (центрированное; на краях окно
     * усечено до доступных соседей). X-координаты не меняются.
     */
    fun smooth(points: List<ChartPoint>, window: Int = SMOOTH_WINDOW): List<ChartPoint> {
        if (points.size < 3 || window <= 1) return points
        val half = window / 2
        return points.mapIndexed { i, p ->
            val from = maxOf(0, i - half)
            val to = minOf(points.lastIndex, i + half)
            var sum = 0f
            for (j in from..to) sum += points[j].y
            ChartPoint(p.x, sum / (to - from + 1))
        }
    }

    /**
     * Даунсемпл до ≤ [maxPoints]: точки группируются в бакеты по порядку,
     * каждый бакет заменяется средней точкой (среднее и по x, и по y).
     */
    fun downsample(points: List<ChartPoint>, maxPoints: Int = MAX_POINTS): List<ChartPoint> {
        if (points.size <= maxPoints) return points
        val bucketSize = points.size.toFloat() / maxPoints
        val result = ArrayList<ChartPoint>(maxPoints)
        var start = 0
        for (b in 0 until maxPoints) {
            val end = minOf(points.size, ((b + 1) * bucketSize).toInt().coerceAtLeast(start + 1))
            var sx = 0f; var sy = 0f
            for (j in start until end) { sx += points[j].x; sy += points[j].y }
            val cnt = end - start
            result.add(ChartPoint(sx / cnt, sy / cnt))
            start = end
            if (start >= points.size) break
        }
        return result
    }

    /** Полный конвейер подготовки: сегменты → сглаживание → даунсемпл. */
    fun prepare(
        xs: List<Float>,
        ys: List<Float>,
        gapIndices: Set<Int>,
        smoothWindow: Int = SMOOTH_WINDOW,
    ): List<List<ChartPoint>> =
        buildSegments(xs, ys, gapIndices).map { downsample(smooth(it, smoothWindow)) }
}

/**
 * Canvas-график: кривая с градиентной заливкой под ней, пунктирная сетка
 * (min/mid/max по Y), подписи значений по Y и дистанции по X.
 *
 * @param segments сегменты линии из [TrackChartData.prepare] (разрывы = паузы)
 * @param yLabel   форматирование подписи значения Y (например, "12 км/ч")
 */
@Composable
fun TrackChart(
    segments: List<List<ChartPoint>>,
    yLabel: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        WorkoutTextStyles.statCardLabel.copy(fontSize = 9.sp)
    }

    val allPoints = segments.flatten()
    if (allPoints.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Недостаточно данных", style = WorkoutTextStyles.statCardLabel)
        }
        return
    }

    // Диапазоны. Плоскую линию (yMin == yMax) растягиваем на ±1, чтобы она
    // рисовалась по центру, а не делила на ноль при нормировке.
    var yMin = allPoints.minOf { it.y }
    var yMax = allPoints.maxOf { it.y }
    if (yMax - yMin < 1e-3f) { yMin -= 1f; yMax += 1f }
    val xMax = allPoints.maxOf { it.x }.coerceAtLeast(1e-3f)

    Canvas(modifier = modifier.fillMaxSize()) {
        // Отступы под подписи: слева — значения Y, снизу — дистанция.
        val padLeft = 34.dp.toPx()
        val padBottom = 14.dp.toPx()
        val padTop = 6.dp.toPx()
        val chartW = size.width - padLeft
        val chartH = size.height - padBottom - padTop

        fun px(p: ChartPoint): Offset = Offset(
            x = padLeft + (p.x / xMax) * chartW,
            y = padTop + (1f - (p.y - yMin) / (yMax - yMin)) * chartH,
        )

        // Сетка: три горизонтальные пунктирные линии (max / середина / min).
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        val gridYs = listOf(yMax, (yMin + yMax) / 2f, yMin)
        gridYs.forEach { gy ->
            val y = padTop + (1f - (gy - yMin) / (yMax - yMin)) * chartH
            drawLine(
                color = ColorPrimary.copy(alpha = 0.25f),
                start = Offset(padLeft, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = dash,
            )
            val measured = textMeasurer.measure(yLabel(gy), labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(0f, (y - measured.size.height / 2f).coerceAtLeast(0f)),
            )
        }

        // Подписи X: старт и полная дистанция.
        val xEndLabel = textMeasurer.measure(
            "%.1f км".format(java.util.Locale.US, xMax), labelStyle
        )
        drawText(
            textLayoutResult = xEndLabel,
            topLeft = Offset(size.width - xEndLabel.size.width, size.height - xEndLabel.size.height),
        )
        val xStartLabel = textMeasurer.measure("0", labelStyle)
        drawText(
            textLayoutResult = xStartLabel,
            topLeft = Offset(padLeft, size.height - xStartLabel.size.height),
        )

        // Кривая + заливка по сегментам.
        segments.forEach { seg ->
            if (seg.size < 2) return@forEach
            val line = Path()
            seg.forEachIndexed { i, p ->
                val o = px(p)
                if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
            }
            // Заливка: копия линии, замкнутая на нижнюю границу графика.
            val fill = Path().apply {
                addPath(line)
                lineTo(px(seg.last()).x, padTop + chartH)
                lineTo(px(seg.first()).x, padTop + chartH)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ColorSecondary.copy(alpha = 0.35f),
                        ColorSecondary.copy(alpha = 0.02f),
                    ),
                    startY = padTop,
                    endY = padTop + chartH,
                ),
            )
            drawPath(
                path = line,
                color = ColorChartLine,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
