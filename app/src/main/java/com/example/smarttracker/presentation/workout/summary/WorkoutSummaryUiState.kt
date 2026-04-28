package com.example.smarttracker.presentation.workout.summary

import com.example.smarttracker.domain.model.LocationPoint
import java.io.File

/**
 * Снимок итогов завершённой тренировки. Используется как поле
 * [WorkoutStartViewModel.UiState.summaryOverlay] и передаётся в UI-слой
 * для отрисовки оверлея поверх [WorkoutStartScreen] (без навигации).
 *
 * Все строковые поля готовы к отрисовке — форматирование делается
 * в момент построения снимка через [WorkoutSummaryFormatters].
 *
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
 * @property isLoading        true пока загружаются данные (для будущего экрана истории)
 */
data class WorkoutSummaryUiState(
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
    val isLoading: Boolean = false,
)
