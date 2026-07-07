package com.example.smarttracker.presentation.workout.summary

import com.example.smarttracker.domain.model.LocationPoint
import java.io.File

/**
 * Предвычисленные накопленные значения трека для O(1) scrub-lookups.
 *
 * Индекс i соответствует i-й GPS-точке в [WorkoutSummaryUiState.trackPoints].
 * Вычисляется один раз в ViewModel при построении снимка итогов.
 *
 * @property distancesKm накопленная дистанция от старта до точки i, км
 * @property elevationsM накопленный набор высоты от старта до точки i, м
 * @property elapsedMs   прошедшее время от старта до точки i, мс
 * @property speedsMs    мгновенная скорость на отрезке (i-1 → i), м/с.
 *                       Для i=0 равна 0. Для истории, где сервер не отдаёт
 *                       `speed` в gps_track, вычисляется на клиенте как
 *                       Δdistance / Δtime между соседними точками.
 *                       Для онлайн-завершения это даёт ту же шкалу, что
 *                       sensor-speed (haversine + GPS-timestamps).
 */
data class CumulativeTrackData(
    val distancesKm: List<Float> = emptyList(),
    val elevationsM: List<Float> = emptyList(),
    val elapsedMs:   List<Long>  = emptyList(),
    val speedsMs:    List<Float> = emptyList(),
)

/**
 * Источник оверлея итогов: определяет поведение [onCloseSummaryOverlay].
 *
 * - [FINISH]  — оверлей открыт после завершения активной тренировки.
 *               При закрытии нужно сбрасывать live-поля (trackPoints/elapsedMs/...)
 *               и рестартовать discovery-GPS.
 * - [HISTORY] — оверлей открыт как превью из истории во время DAY view.
 *               При закрытии нельзя трогать live-состояние: если параллельно
 *               идёт активная тренировка, её данные будут затёрты, а GPS-сервис
 *               переключён на discovery UUID → молчаливая потеря точек.
 */
enum class SummaryOrigin { FINISH, HISTORY }

/**
 * Снимок итогов завершённой тренировки. Используется как поле
 * [WorkoutStartViewModel.UiState.summaryOverlay] и передаётся в UI-слой
 * для отрисовки оверлея поверх [WorkoutStartScreen] (без навигации).
 *
 * Все строковые поля готовы к отрисовке — форматирование делается
 * в момент построения снимка через [WorkoutSummaryFormatters].
 *
 * @property origin           источник оверлея: завершение тренировки или превью истории
 * @property trainingId       серверный UUID тренировки. Заполняется только для
 *                            [SummaryOrigin.HISTORY] — нужен для DELETE /training/{id}/delete_completed
 *                            при тапе на иконку корзины. Для FINISH остаётся null
 *                            (после завершения id уже передан в saveTraining, а оверлей
 *                            закрывается без операций удаления).
 * @property dateDisplay      дата старта тренировки "dd.MM.yyyy (День)" в русской локали
 * @property activityName     название типа активности ("Бег", "Ходьба" и т.д.)
 * @property activityIconFile скачанный файл иконки из IconCacheManager, null если не загружен
 * @property activityIconUrl  URL иконки с сервера (image_path), используется как fallback
 * @property activityIconKey  строковый ключ type_activ_id для drawable-fallback
 * @property paceDisplay      средний темп "5:30 мин/км" или "—" если дистанция == 0
 * @property distanceDisplay  дистанция "1.23 км"
 * @property durationDisplay  длительность "HH:MM:SS"
 * @property elevationDisplay набор высоты "12.3 м"
 * @property trackPoints      GPS-точки тренировки для отрисовки трека на карте
 * @property cumulativeData   предвычисленные накопленные значения для scrubbing
 * @property splits           километровые сплиты для секции «Детали» (пусто, если
 *                            нет настоящих временных меток — история до BR-5)
 * @property pauseGapIndices  индексы первых точек после каждого resume — график
 *                            рвёт линию на этих парах (телепорт, не движение).
 *                            Для истории всегда пуст (сервер пауз не отдаёт).
 * @property isLoading        true пока загружаются данные (для будущего экрана истории)
 */
data class WorkoutSummaryUiState(
    val origin: SummaryOrigin = SummaryOrigin.FINISH,
    val trainingId: String? = null,
    val dateDisplay: String = "",
    val activityName: String = "",
    val activityIconFile: File? = null,
    val activityIconUrl: String? = null,
    val activityIconKey: String = "",
    val paceDisplay: String = "—",
    val distanceDisplay: String = "0.00 км",
    val durationDisplay: String = "00:00:00",
    val elevationDisplay: String = "0.0 м",
    val trackPoints: List<LocationPoint> = emptyList(),
    val cumulativeData: CumulativeTrackData = CumulativeTrackData(),
    val splits: List<SplitUi> = emptyList(),
    val pauseGapIndices: List<Int> = emptyList(),
    val isLoading: Boolean = false,
)
