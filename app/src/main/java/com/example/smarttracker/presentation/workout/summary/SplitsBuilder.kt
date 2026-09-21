package com.example.smarttracker.presentation.workout.summary

import com.example.smarttracker.domain.model.LocationPoint

/**
 * Строка таблицы «Сплиты» в деталях итогов тренировки.
 *
 * @property label         подпись километра: "1", "2", … или "0.4" для неполного хвоста
 * @property paceDisplay   темп круга "M:SS мин/км" (готов к отрисовке)
 * @property relativeSpeed скорость круга относительно самого быстрого, 0..1 —
 *                         длина горизонтального бара в UI
 * @property isPartial     true для последнего неполного километра
 */
data class SplitUi(
    val label: String,
    val paceDisplay: String,
    val relativeSpeed: Float,
    val isPartial: Boolean,
)

/**
 * Разбивка трека на километровые сплиты по предвычисленным накопленным данным
 * [CumulativeTrackData].
 *
 * Чистая логика без Android-зависимостей — покрыта юнит-тестами [SplitsBuilderTest].
 *
 * Паузы уже учтены на этапе построения [CumulativeTrackData]: gap-пары не дают
 * прироста дистанции, а время пауз вычтено из elapsedMs — дополнительной
 * обработки здесь не требуется.
 */
object SplitsBuilder {

    /**
     * Минимальный правдоподобный elapsed последней точки. Для истории без
     * серверных таймстемпов (до BR-5) маппер синтезирует timestampUtc = index,
     * что даёт elapsed порядка миллисекунд — темпы по таким данным бессмысленны.
     * Реальная тренировка длится заведомо дольше 10 секунд.
     */
    const val MIN_PLAUSIBLE_ELAPSED_MS = 10_000L

    /** Хвост короче 50 м не показываем — темп на нём — чистый шум. */
    private const val MIN_PARTIAL_KM = 0.05f

    /** Минимальный разрыв времени между точками, считающийся паузой (история). */
    const val MIN_PAUSE_GAP_MS = 15_000L

    /** Во сколько раз разрыв должен превышать медианный интервал трека. */
    private const val PAUSE_GAP_MEDIAN_FACTOR = 3L

    /** true, если у трека есть настоящие временные метки (гейт сплитов и графика скорости). */
    fun hasRealTiming(data: CumulativeTrackData): Boolean =
        (data.elapsedMs.lastOrNull() ?: 0L) >= MIN_PLAUSIBLE_ELAPSED_MS

    /**
     * Эвристика границ пауз для трека из истории — сервер gap-индексы пауз
     * не хранит, но на паузе сервис не пишет точек, поэтому пауза видна как
     * разрыв времени между соседними точками.
     *
     * Gap — интервал больше max([MIN_PAUSE_GAP_MS], [PAUSE_GAP_MEDIAN_FACTOR] ×
     * медианный интервал трека): медиана устойчива к самим паузам (их единицы
     * против сотен обычных интервалов), нижний порог страхует треки с редкой
     * записью точек. Возвращает индексы ПЕРВОЙ точки после паузы — тот же
     * формат, что pauseGapIndices живой тренировки.
     *
     * Синтетические таймстемпы (timestampUtc = index, история до BR-5) дают
     * интервалы ~1 мс — порог недостижим, список пуст (самогейт, отдельная
     * проверка hasRealTiming не нужна).
     */
    fun detectPauseGapIndices(points: List<LocationPoint>): List<Int> {
        if (points.size < 3) return emptyList()
        val deltas = LongArray(points.size - 1) {
            points[it + 1].timestampUtc - points[it].timestampUtc
        }
        val median = deltas.sorted()[deltas.size / 2]
        val threshold = maxOf(MIN_PAUSE_GAP_MS, median * PAUSE_GAP_MEDIAN_FACTOR)
        return (1 until points.size).filter {
            points[it].timestampUtc - points[it - 1].timestampUtc > threshold
        }
    }

    /**
     * Строит список сплитов. Пустой список, если дистанция < 1 точки пересечения
     * километровой границы и нет значимого хвоста, либо нет настоящего тайминга.
     *
     * Время пересечения границы километра интерполируется линейно между соседними
     * точками — GPS-точка редко ложится ровно на границу.
     */
    fun buildSplits(data: CumulativeTrackData): List<SplitUi> {
        if (!hasRealTiming(data)) return emptyList()
        val dist = data.distancesKm
        val time = data.elapsedMs
        if (dist.size < 2 || dist.size != time.size) return emptyList()

        // (дистанция круга км, длительность круга мс, метка, неполный?)
        data class RawSplit(val km: Float, val durationMs: Long, val label: String, val partial: Boolean)

        val raw = ArrayList<RawSplit>()
        var boundaryKm = 1f
        var prevBoundaryMs = 0L
        for (i in 1 until dist.size) {
            // Одна пара точек может пересечь несколько границ (теоретически, при
            // дырах GPS) — while, не if.
            while (dist[i] >= boundaryKm) {
                val span = dist[i] - dist[i - 1]
                val frac = if (span > 0f) (boundaryKm - dist[i - 1]) / span else 1f
                val boundaryMs = time[i - 1] + ((time[i] - time[i - 1]) * frac).toLong()
                raw.add(
                    RawSplit(
                        km = 1f,
                        durationMs = boundaryMs - prevBoundaryMs,
                        label = boundaryKm.toInt().toString(),
                        partial = false,
                    )
                )
                prevBoundaryMs = boundaryMs
                boundaryKm += 1f
            }
        }
        // Неполный хвост после последней целой границы.
        val tailKm = dist.last() - (boundaryKm - 1f)
        if (tailKm >= MIN_PARTIAL_KM) {
            raw.add(
                RawSplit(
                    km = tailKm,
                    durationMs = time.last() - prevBoundaryMs,
                    label = "%.1f".format(java.util.Locale.US, tailKm),
                    partial = true,
                )
            )
        }
        if (raw.isEmpty()) return emptyList()

        // Относительная скорость: км/мс каждого круга против максимума.
        val speeds = raw.map { if (it.durationMs > 0L) it.km / it.durationMs else 0f }
        val maxSpeed = speeds.max()
        return raw.mapIndexed { idx, s ->
            SplitUi(
                label = s.label,
                paceDisplay = WorkoutSummaryFormatters.formatPace(s.km, s.durationMs),
                relativeSpeed = if (maxSpeed > 0f) (speeds[idx] / maxSpeed).coerceIn(0f, 1f) else 0f,
                isPartial = s.partial,
            )
        }
    }
}
