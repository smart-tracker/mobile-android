package com.example.smarttracker.presentation.workout.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Два слоя поверх базовой карты:
 * - "track-layer" — LineLayer с цветом ColorSecondary, рисует GPS-трек тренировки
 * - "location-layer" — CircleLayer (синяя точка), показывает текущее положение
 *
 * Офлайн: при [mapTilesFailed] == true сразу показывает [OfflineMapFallback].
 * Пока тайлы грузятся из авто-кэша (даже в авиарежиме) — карта показывается штатно.
 *
 * @param currentLocation последняя GPS-точка; при первом получении камера анимируется к ней
 * @param trackPoints все точки текущей тренировки для рисования трека
 * @param mapTilesFailed true когда MapLibre отрапортовал об ошибке загрузки тайлов
 * @param onMapTilesFailed колбэк при onDidFailLoadingMap; устанавливает mapTilesFailed в ViewModel
 */
@Composable
fun MapViewComposable(
    modifier: Modifier = Modifier,
    currentLocation: LocationPoint?,
    trackPoints: List<LocationPoint>,
    mapTilesFailed: Boolean,
    onMapTilesFailed: () -> Unit,
) {
    // Когда тайлы недоступны — показываем текстовый fallback, карту не создаём
    if (mapTilesFailed) {
        OfflineMapFallback(currentLocation = currentLocation, modifier = modifier)
        return
    }

    // Держим ссылку на MapView чтобы lifecycle observer мог вызывать его методы
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    // Флаг первой анимации камеры: одноразово центрируем на первом GPS-fix
    var cameraMovedToFirstFix by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle observer: пробрасывает события Activity в MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = mapViewRef ?: return@LifecycleEventObserver
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
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { mapView ->
                // onCreate нужен в Compose — нет Activity.onCreate для MapView
                mapView.onCreate(null)

                mapView.getMapAsync { map ->
                    // Подписка на ошибку загрузки тайлов — только когда нет сети И кэш пуст
                    mapView.addOnDidFailLoadingMapListener {
                        onMapTilesFailed()
                    }

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
                                    android.graphics.Color.rgb(59, 130, 246) // #3B82F6 — синий
                                ),
                                PropertyFactory.circleRadius(8f),
                                PropertyFactory.circleStrokeColor(
                                    android.graphics.Color.WHITE
                                ),
                                PropertyFactory.circleStrokeWidth(2f),
                            )
                        )
                    }
                }

                mapViewRef = mapView
            }
        },
        update = { _ ->
            // getMapAsync выполняет колбэк сразу если карта готова, иначе ставит в очередь.
            // Безопасно вызывать при каждом recompose (при смене trackPoints/currentLocation).
            mapViewRef?.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync

                // Обновляем трек-линию
                val coordinates = trackPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                val trackFc = if (coordinates.size >= 2) {
                    FeatureCollection.fromFeatures(
                        listOf(Feature.fromGeometry(LineString.fromLngLats(coordinates)))
                    )
                } else {
                    FeatureCollection.fromFeatures(emptyList())
                }
                style.getSourceAs<GeoJsonSource>("track-source")?.setGeoJson(trackFc)

                // Обновляем точку текущей позиции
                if (currentLocation != null) {
                    val locFc = FeatureCollection.fromFeatures(
                        listOf(Feature.fromGeometry(
                            Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
                        ))
                    )
                    style.getSourceAs<GeoJsonSource>("location-source")?.setGeoJson(locFc)

                    // Первый GPS-fix: анимируем камеру к позиции с zoom 16
                    if (!cameraMovedToFirstFix) {
                        cameraMovedToFirstFix = true
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(currentLocation.latitude, currentLocation.longitude),
                                16.0,
                            )
                        )
                    }
                }
            }
        },
    )
}
