package com.example.smarttracker.presentation.workout.map

import android.animation.ValueAnimator
import android.view.Gravity
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smarttracker.data.location.OfflineMapManager
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.presentation.theme.ColorSecondary
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Composable-обёртка над MapLibre MapView.
 *
 * Жизненный цикл MapView привязан к Compose lifecycle через [DisposableEffect] +
 * [LocalLifecycleOwner]. Карта загружает OpenFreeMap-стиль (без API-ключа).
 *
 * Три слоя поверх базовой карты:
 * - "track-layer" — LineLayer с цветом ColorSecondary, рисует GPS-трек тренировки
 * - "location-layer" — CircleLayer (синяя точка), показывает текущее положение
 *
 * Поведение камеры (Tweak 2):
 * - [isTracking] == true: камера плавно следует за маркером (animateCamera, 800 мс)
 * - [isTracking] == false (пауза/стоп): камера не двигается — пользователь может листать карту
 *
 * Плавное движение маркера (Tweak 3):
 * - Переход между GPS-точками анимируется через ValueAnimator (800 мс, LinearInterpolator)
 * - Предыдущий аниматор отменяется при появлении новой точки
 *
 * Офлайн: при [mapTilesFailed] == true сразу показывает [OfflineMapFallback].
 *
 * @param currentLocation последняя GPS-точка текущей тренировки (null до первого фикса)
 * @param lastKnownLocation последняя точка из предыдущих тренировок; используется для
 *   начального центрирования карты пока [currentLocation] == null. Маркер не показывается.
 * @param trackPoints все точки текущей тренировки для рисования трека
 * @param isTracking true пока тренировка запущена (не на паузе); управляет следованием камеры
 * @param mapTilesFailed true когда MapLibre не смог загрузить тайлы (нет сети + нет кэша)
 * @param onMapTilesFailed колбэк при onDidFailLoadingMap
 */
@Composable
fun MapViewComposable(
    modifier: Modifier = Modifier,
    currentLocation: LocationPoint?,
    lastKnownLocation: LocationPoint? = null,
    trackPoints: List<LocationPoint>,
    isTracking: Boolean,
    mapTilesFailed: Boolean,
    onMapTilesFailed: () -> Unit,
) {
    // Когда тайлы недоступны — показываем текстовый fallback, карту не создаём
    if (mapTilesFailed) {
        OfflineMapFallback(currentLocation = currentLocation, modifier = modifier)
        return
    }

    /**
     * Весь мутируемый внутренний стейт карты в одном holder-е.
     * Не нужен mutableStateOf — изменения не должны триггерить recompose:
     * все операции происходят в обратных вызовах getMapAsync / ValueAnimator.
     */
    val state = remember {
        object {
            var mapView: MapView? = null
            // Tweak 3: предыдущая позиция маркера для интерполяции
            var prevMarkerLatLng: LatLng? = null
            // Tweak 3: текущий аниматор маркера (отменяется при новом fix)
            var markerAnimator: ValueAnimator? = null
            // Tweak 2: флаг первого fix — нужен чтобы первый раз задать zoom 16
            var cameraMovedToFirstFix: Boolean = false
            // Флаг первичного центрирования по lastKnownLocation (выполняется один раз)
            var lastKnownCentered: Boolean = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle observer: пробрасывает события Activity в MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = state.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.mapView?.let { mapView ->
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
            state.mapView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            // textureMode(true) переключает рендеринг с SurfaceView на TextureView —
            // GL-контент встраивается в View-иерархию и становится доступен для Modifier.blur
            val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
            MapView(context, options).also { mapView ->
                // onCreate нужен в Compose — нет Activity.onCreate для MapView
                mapView.onCreate(null)

                mapView.getMapAsync { map ->
                    mapView.addOnDidFailLoadingMapListener {
                        onMapTilesFailed()
                    }

                    // ── Tweak 1: логотип и attribution — верхний левый угол ───────────
                    // MapLibre требует attribution по лицензии OpenStreetMap; мы лишь
                    // перемещаем элементы, чтобы они не перекрывали кнопки внизу.
                    val density = context.resources.displayMetrics.density
                    val margin8px  = (8  * density).toInt()
                    val margin92px = (92 * density).toInt()

                    map.uiSettings.apply {
                        logoGravity = Gravity.TOP or Gravity.START
                        setLogoMargins(margin8px, margin8px, 0, 0)
                        attributionGravity = Gravity.TOP or Gravity.START
                        setAttributionMargins(margin92px, margin8px, 0, 0)
                    }
                    // Уменьшение размера логотипа: MapLibre UiSettings не предоставляет
                    // публичного API для изменения размера (нет logoSize / setLogoSize).
                    // Доступ через mapView.logoView отсутствует в публичном интерфейсе 11.8.2.
                    // Обход через reflection или org.maplibre.android.R.id.logoView является
                    // внутренним API и потому пропущен согласно требованию задачи.

                    map.setStyle(OfflineMapManager.STYLE_URL) { style ->
                        // ── Источник и слой трека ──────────────────────────────────
                        style.addSource(
                            GeoJsonSource("track-source",
                                FeatureCollection.fromFeatures(emptyList()))
                        )
                        style.addLayer(
                            LineLayer("track-layer", "track-source").withProperties(
                                PropertyFactory.lineColor(
                                    android.graphics.Color.rgb(
                                        (ColorSecondary.red   * 255).toInt(),
                                        (ColorSecondary.green * 255).toInt(),
                                        (ColorSecondary.blue  * 255).toInt(),
                                    )
                                ),
                                PropertyFactory.lineWidth(4f),
                                PropertyFactory.lineCap("round"),
                                PropertyFactory.lineJoin("round"),
                            )
                        )

                        // ── Источник и слой текущей позиции (синяя точка) ──────────
                        style.addSource(
                            GeoJsonSource("location-source",
                                FeatureCollection.fromFeatures(emptyList()))
                        )
                        style.addLayer(
                            CircleLayer("location-layer", "location-source").withProperties(
                                PropertyFactory.circleColor(
                                    android.graphics.Color.rgb(59, 130, 246) // #3B82F6
                                ),
                                PropertyFactory.circleRadius(8f),
                                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                                PropertyFactory.circleStrokeWidth(2f),
                            )
                        )
                    }
                }

                state.mapView = mapView
            }
        },
        update = { _ ->
            // getMapAsync выполняет колбэк сразу если карта готова, иначе ставит в очередь.
            state.mapView?.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync

                // ── Обновляем трек-линию ───────────────────────────────────────────
                val coordinates = trackPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                val trackFc = if (coordinates.size >= 2) {
                    FeatureCollection.fromFeatures(
                        listOf(Feature.fromGeometry(LineString.fromLngLats(coordinates)))
                    )
                } else {
                    FeatureCollection.fromFeatures(emptyList())
                }
                style.getSourceAs<GeoJsonSource>("track-source")?.setGeoJson(trackFc)

                // ── Маркер текущей позиции + камера ───────────────────────────────
                if (currentLocation == null) {
                    // Нет GPS-фикса текущей тренировки. Если есть точка из прошлой тренировки
                    // — центрируем карту на ней один раз (без маркера пользователя).
                    if (!state.lastKnownCentered && lastKnownLocation != null) {
                        state.lastKnownCentered = true
                        val lkLatLng = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(lkLatLng, 14.0), 800)
                    }
                    return@getMapAsync
                }

                val newLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                val prev = state.prevMarkerLatLng

                val positionChanged = prev == null
                    || prev.latitude  != newLatLng.latitude
                    || prev.longitude != newLatLng.longitude

                if (positionChanged) {
                    // Tweak 3: плавная анимация маркера между GPS-точками
                    state.markerAnimator?.cancel()

                    if (prev == null) {
                        // Первая точка — сразу ставим маркер без анимации
                        val locFc = FeatureCollection.fromFeatures(listOf(
                            Feature.fromGeometry(Point.fromLngLat(newLatLng.longitude, newLatLng.latitude))
                        ))
                        style.getSourceAs<GeoJsonSource>("location-source")?.setGeoJson(locFc)
                    } else {
                        // Интерполируем позицию от prev до newLatLng за 800 мс
                        val startLat = prev.latitude
                        val startLng = prev.longitude
                        val endLat   = newLatLng.latitude
                        val endLng   = newLatLng.longitude

                        state.markerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = 800
                            interpolator = LinearInterpolator()
                            addUpdateListener { va ->
                                val f = va.animatedValue as Float
                                val lat = startLat + (endLat - startLat) * f
                                val lng = startLng + (endLng - startLng) * f
                                // Используем map.style вместо захваченного style —
                                // на случай если стиль был перезагружен во время анимации
                                val locFc = FeatureCollection.fromFeatures(listOf(
                                    Feature.fromGeometry(Point.fromLngLat(lng, lat))
                                ))
                                map.style?.getSourceAs<GeoJsonSource>("location-source")
                                    ?.setGeoJson(locFc)
                            }
                            start()
                        }
                    }

                    state.prevMarkerLatLng = newLatLng
                }

                // Tweak 2: камера следует маркеру только во время активной тренировки.
                // На паузе пользователь может свободно листать карту.
                if (isTracking) {
                    if (!state.cameraMovedToFirstFix) {
                        // Первый fix: устанавливаем zoom 16 + позицию
                        state.cameraMovedToFirstFix = true
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(newLatLng, 16.0), 800
                        )
                    } else {
                        // Последующие fix: только следуем, zoom не трогаем
                        map.animateCamera(
                            CameraUpdateFactory.newLatLng(newLatLng), 800
                        )
                    }
                }
                // isTracking == false → камера не двигается, пользователь листает карту
            }
        },
    )
}
