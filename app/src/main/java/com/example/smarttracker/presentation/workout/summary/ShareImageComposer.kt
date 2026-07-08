package com.example.smarttracker.presentation.workout.summary

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.LocationPoint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos

/**
 * Сборка шаринг-картинки тренировки и отправка через системный share sheet.
 *
 * Два варианта (пользователь выбирает в диалоге — приватность геоданных):
 *  - **с картой**: снимок MapLibre (fitToTrackBounds уже показал весь маршрут)
 *    + плашка статистики снизу;
 *  - **только статистика**: квадратная карточка на фирменном фоне с силуэтом
 *    маршрута (форма без геопривязки — район пробежек не раскрывается).
 *
 * Всё рисование — android.graphics.Canvas: composable здесь не нужен,
 * картинка живёт вне UI-дерева. Размеры шрифтов — доли ширины картинки,
 * чтобы не зависеть от плотности экрана.
 */
object ShareImageComposer {

    /** Готовые к отрисовке строки статистики (те же, что в SummaryOverlay). */
    data class ShareStats(
        val activityName: String,
        val dateDisplay: String,
        val distanceDisplay: String,
        val durationDisplay: String,
        val paceDisplay: String,
    )

    // Цвета темы в android.graphics (Compose Color сюда не тянем).
    private const val COLOR_PRIMARY = 0xFF0A1928.toInt()
    private const val COLOR_SECONDARY = 0xFF4DACA7.toInt()
    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()

    private const val TRACK_CARD_SIZE = 1080
    private const val APP_LABEL = "SmartTracker"

    /**
     * Нормализация трека в единичный квадрат с сохранением пропорций:
     * долгота корректируется на cos(широты) — иначе маршрут сплющивается
     * тем сильнее, чем дальше от экватора. Возвращает (x, y) в [0..1],
     * y растёт вниз (экранные координаты). Трек < 2 точек → пустой список.
     *
     * Чистая функция — покрыта [ShareImageComposerTest].
     */
    fun normalizeTrack(points: List<LocationPoint>): List<Pair<Float, Float>> {
        if (points.size < 2) return emptyList()
        val midLatRad = Math.toRadians(points.map { it.latitude }.average())
        val lonScale = cos(midLatRad)
        val xs = points.map { (it.longitude * lonScale) }
        val ys = points.map { it.latitude }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val spanX = maxX - minX
        val spanY = maxY - minY
        val span = maxOf(spanX, spanY)
        if (span <= 0.0) return emptyList()
        // Центрируем меньшую сторону внутри квадрата.
        val offsetX = (span - spanX) / 2.0
        val offsetY = (span - spanY) / 2.0
        return points.indices.map { i ->
            val x = ((xs[i] - minX + offsetX) / span).toFloat()
            // Инверсия: широта растёт на север, экранный y — вниз.
            val y = (1.0 - (ys[i] - minY + offsetY) / span).toFloat()
            x to y
        }
    }

    /** Снимок карты + плашка статистики снизу. Исходный bitmap не мутируется. */
    fun composeWithMap(context: Context, mapSnapshot: Bitmap, stats: ShareStats): Bitmap {
        val result = mapSnapshot.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        val pad = w * 0.04f
        val panelHeight = w * 0.30f
        val panel = RectF(pad, h - panelHeight - pad, w - pad, h - pad)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY
            alpha = 235
        }
        canvas.drawRoundRect(panel, w * 0.03f, w * 0.03f, panelPaint)

        drawStatsBlock(
            context, canvas, stats,
            left = panel.left + pad,
            right = panel.right - pad,
            top = panel.top + pad * 0.9f,
            bottom = panel.bottom - pad * 0.7f,
            width = w,
        )
        return result
    }

    /**
     * Карточка 1080×1080 без карты: фирменный фон, силуэт маршрута,
     * статистика снизу. Пустой/короткий трек → карточка без силуэта.
     */
    fun composeTrackCard(context: Context, trackPoints: List<LocationPoint>, stats: ShareStats): Bitmap {
        val size = TRACK_CARD_SIZE
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val w = size.toFloat()
        canvas.drawColor(COLOR_PRIMARY)

        // Силуэт трека: центральная зона квадрата, под шапкой и над статистикой.
        val normalized = normalizeTrack(trackPoints)
        if (normalized.isNotEmpty()) {
            val inset = w * 0.16f
            val top = w * 0.18f
            val bottom = w * 0.70f
            val area = RectF(inset, top, w - inset, bottom)
            val side = minOf(area.width(), area.height())
            val originX = area.centerX() - side / 2f
            val originY = area.centerY() - side / 2f
            val path = Path()
            normalized.forEachIndexed { i, (nx, ny) ->
                val px = originX + nx * side
                val py = originY + ny * side
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_SECONDARY
                style = Paint.Style.STROKE
                strokeWidth = w * 0.008f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(path, trackPaint)
        }

        val pad = w * 0.06f
        drawStatsBlock(
            context, canvas, stats,
            left = pad,
            right = w - pad,
            top = w * 0.74f,
            bottom = w - pad,
            width = w,
        )
        return result
    }

    /**
     * Общий блок текста: сверху «Активность — дата», по центру крупная строка
     * «дистанция • время • темп», в правом нижнем углу — имя приложения.
     * Текст всегда белый — рисуется по тёмной плашке/фону.
     */
    private fun drawStatsBlock(
        context: Context,
        canvas: Canvas,
        stats: ShareStats,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        width: Float,
    ) {
        val regular = ResourcesCompat.getFont(context, R.font.geologica_regular)
            ?: Typeface.DEFAULT
        val light = ResourcesCompat.getFont(context, R.font.geologica_light)
            ?: Typeface.DEFAULT

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_WHITE
            typeface = light
            textSize = width * 0.035f
        }
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_WHITE
            typeface = regular
            textSize = width * 0.048f
        }
        val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SECONDARY
            typeface = light
            textSize = width * 0.030f
            textAlign = Paint.Align.RIGHT
        }

        val title = listOf(stats.activityName, stats.dateDisplay)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
        canvas.drawText(title, left, top + titlePaint.textSize, titlePaint)

        val statsLine = "${stats.distanceDisplay}  •  ${stats.durationDisplay}  •  ${stats.paceDisplay}"
        // Крупная строка обязана влезть: ужимаем размер под ширину при необходимости.
        while (statsPaint.measureText(statsLine) > right - left && statsPaint.textSize > width * 0.02f) {
            statsPaint.textSize *= 0.94f
        }
        val statsY = top + titlePaint.textSize + statsPaint.textSize * 1.6f
        canvas.drawText(statsLine, left, statsY, statsPaint)

        canvas.drawText(APP_LABEL, right, bottom, appPaint)
    }

    /**
     * Пишет PNG в cacheDir/share/ и открывает системный share sheet.
     * Файл перезаписывается (одно имя) — старые шаринги не копятся в кэше.
     * Вызывать с IO-диспетчера: запись файла блокирующая.
     */
    fun shareBitmap(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "workout_share.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        // authority = "<applicationId>.provider" — как у FileProvider камеры
        // (манифест использует ${applicationId}); packageName и есть applicationId.
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Поделиться тренировкой"))
    }
}
