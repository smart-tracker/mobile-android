package com.example.smarttracker.presentation.workout.map

import android.annotation.SuppressLint
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smarttracker.R
import com.example.smarttracker.data.location.OfflineMapManager
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.presentation.theme.ColorSecondary
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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
 * Слои поверх базовой карты:
 * - "track-layer" — LineLayer с цветом ColorSecondary, рисует GPS-трек тренировки
 * - LocationComponent — встроенный слой MapLibre, показывает живую позицию устройства
 *   напрямую из OS Location API (без Round-trip через Room). Активен до, во время
 *   и после тренировки. Использует кастомный маркер [R.drawable.ic_location_dot]
 *   (синяя точка с белой обводкой), совпадающий по стилю с предыдущим CircleLayer.
 *
 * Поведение камеры:
 * - До тренировки / активная тренировка: [CameraMode.TRACKING] — LocationComponent ведёт
 *   камеру за живой OS-позицией без задержки Room-round-trip.
 * - Пауза во время тренировки: [CameraMode.NONE] — камера замирает, пользователь листает карту.
 * - Первый Room-фикс: однократный zoom 16 через [zoomWhileTracking] без смены позиции.
 *
 * Тонкость: [AndroidView.update] вызывается в тот же кадр что и factory — до загрузки
 * стиля ([MapLibreMap.getStyle] == null). Zoom-логика из update возвращается по null-guard
 * раньше чем срабатывает. Если [isGpsActive] уже true (retained ViewModel, возврат на экран),
 * state больше не меняется и update не получает второго шанса. Решение: дублировать
 * discovery-zoom в [setStyle]-callback factory, читая актуальный isGpsActive через
 * [rememberUpdatedState].
 *
 * Трек: Room-точки + текущая OS-позиция из [LocationComponent.lastKnownLocation] как «голова»
 * линии — трек визуально дотягивается до маркера без ожидания flush-а буфера сервиса.
 *
 * Офлайн: при [mapTilesFailed] == true сразу показывает [OfflineMapFallback].
 *
 * @param currentLocation последняя GPS-точка из Room (null до первого флаша буфера сервиса).
 *   Используется как сигнал «тренировка стартовала» для однократной установки zoom 16.
 * @param lastKnownLocation последняя точка из предыдущих тренировок; используется для
 *   начального центрирования карты пока [currentLocation] == null. Маркер не показывается.
 * @param trackPoints все точки текущей тренировки для рисования трека
 * @param isTracking true пока тренировка запущена (не на паузе); управляет следованием камеры
 * @param isGpsActive true когда GPS-фикс получен; триггерит однократный zoom 16 до старта тренировки.
 *   Если LocationComponent ещё не получил позицию (например, экран открыт повторно), устанавливается
 *   только zoom — позиция подтягивается автоматически через [CameraMode.TRACKING].
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
    isGpsActive: Boolean = false,
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
     * все операции происходят в обратных вызовах getMapAsync.
     */
    val state = remember {
        object {
            var mapView: MapView? = null
            // Флаг первичного центрирования по lastKnownLocation (выполняется один раз)
            var lastKnownCentered: Boolean = false
            // Zoom 16 при первом GPS-фиксе до старта тренировки (discovery-фаза)
            var discoveryZoomed: Boolean = false
            // Zoom 16 при первой Room-точке после старта тренировки
            var cameraMovedToFirstFix: Boolean = false
        }
    }

    // rememberUpdatedState: всегда содержит актуальное значение параметра даже внутри
    // старых замыканий (factory-callback создаётся один раз при первом compose).
    // Нужно для setStyle-callback: isGpsActive может стать true уже после того, как
    // factory зафиксировал начальное значение false.
    val latestIsGpsActive    = rememberUpdatedState(isGpsActive)
    val latestWorkoutStarted = rememberUpdatedState(currentLocation != null)
    val latestLastKnown      = rememberUpdatedState(lastKnownLocation)

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

                        // ── LocationComponent: живая GPS-позиция ───────────────────
                        // Активируется один раз после загрузки стиля.
                        // Работает независимо от Room — показывает точку сразу после
                        // получения GPS-фикса, в том числе до начала тренировки.
                        // CameraMode.NONE: камерой управляем сами в блоке update.
                        // RenderMode.NORMAL: только точка, без стрелки направления.
                        activateLocationComponent(map, style, context)

                        // ── Начальное центрирование при загрузке стиля ────────────
                        // update-блок вызывается до загрузки стиля (map.style == null →
                        // return@getMapAsync) — его логика не успевает сработать.
                        // Если при возврате на экран стейт retained-ViewModel уже
                        // заполнен (lastKnownLocation / isGpsActive), state не меняется
                        // → update не получает второго шанса. Решаем здесь, читая
                        // актуальные значения через rememberUpdatedState.

                        // Fallback: нет GPS — центрируемся на последней точке прошлой тренировки.
                        if (!latestIsGpsActive.value && !latestWorkoutStarted.value && !state.lastKnownCentered) {
                            val lkl = latestLastKnown.value
                            if (lkl != null) {
                                state.lastKnownCentered = true
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lkl.latitude, lkl.longitude), 14.0
                                    ), 800
                                )
                            }
                        }

                        // GPS уже активен — zoom 16 + TRACKING ведёт камеру к маркеру.
                        if (latestIsGpsActive.value && !latestWorkoutStarted.value && !state.discoveryZoomed) {
                            state.discoveryZoomed = true
                            val fix = map.locationComponent.lastKnownLocation
                            if (fix != null) {
                                map.moveCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(fix.latitude, fix.longitude), 16.0
                                    )
                                )
                            } else {
                                // LocationComponent ещё не получил позицию от OS —
                                // только зум; TRACKING подтянет камеру к маркеру сам.
                                map.moveCamera(CameraUpdateFactory.zoomTo(16.0))
                            }
                            // moveCamera может сбросить cameraMode в NONE — восстанавливаем.
                            // В update-блоке TRACKING тоже выставляется, но он не запустится
                            // пока Compose-state не изменится: здесь это единственный шанс.
                            map.locationComponent.cameraMode = CameraMode.TRACKING
                        }
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
                // «Голова» трека всегда совпадает с визуальной позицией маркера:
                // - TRACKING: камера следует за анимацией маркера, поэтому
                //   cameraPosition.target == текущая анимированная позиция маркера.
                //   Использовать lastKnownLocation нельзя — это сырой GPS-фикс, маркер
                //   ещё не успел до него доехать, трек прыгал бы вперёд.
                // - NONE (пауза): маркер статичен, анимации нет — lastKnownLocation
                //   совпадает с визуальной позицией. cameraPosition не используем,
                //   т.к. пользователь мог сдвинуть камеру рукой.
                val coordinates = trackPoints
                    .map { Point.fromLngLat(it.longitude, it.latitude) }
                    .toMutableList()
                val liveHead: Point? =
                    if (map.locationComponent.cameraMode == CameraMode.TRACKING) {
                        map.cameraPosition.target
                            ?.let { Point.fromLngLat(it.longitude, it.latitude) }
                    } else {
                        map.locationComponent.lastKnownLocation
                            ?.let { Point.fromLngLat(it.longitude, it.latitude) }
                    }
                liveHead?.let { coordinates.add(it) }
                val trackFc = if (coordinates.size >= 2) {
                    FeatureCollection.fromFeatures(
                        listOf(Feature.fromGeometry(LineString.fromLngLats(coordinates)))
                    )
                } else {
                    FeatureCollection.fromFeatures(emptyList())
                }
                style.getSourceAs<GeoJsonSource>("track-source")?.setGeoJson(trackFc)

                // ── Управление камерой ─────────────────────────────────────────────
                val workoutStarted = currentLocation != null

                // Разовые операции — выполняются ДО установки cameraMode,
                // чтобы TRACKING не был отменён длительной анимацией.

                // Fallback: центрируем на последней точке прошлой тренировки, пока GPS ещё
                // не получен. Вызывается один раз; animateCamera здесь безопасна —
                // GPS-фикса нет, TRACKING всё равно не активен.
                // !isGpsActive: если GPS уже пришёл и discoveryZoom отработал в setStyle,
                // не перебиваем его центрированием на устаревшей lastKnownLocation.
                if (!workoutStarted && !isGpsActive && !state.lastKnownCentered && lastKnownLocation != null) {
                    state.lastKnownCentered = true
                    val lkLatLng = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(lkLatLng, 14.0), 800)
                }

                // Первый GPS-фикс до тренировки: moveCamera (мгновенно) + затем TRACKING.
                // animateCamera нельзя — она отменяет TRACKING и не восстанавливает его,
                // т.к. discovery не пишет в Room → повторного recompose не будет.
                // moveCamera не создаёт длительной анимации, поэтому TRACKING
                // начинает следить сразу после установки режима ниже.
                //
                // Если lastKnownLocation == null (экран открыт повторно — LocationComponent
                // только что активирован и ещё не получил позицию от OS), устанавливаем
                // только zoom без позиции: CameraMode.TRACKING (ниже) автоматически
                // подтянет камеру к маркеру, как только OS вернёт координату.
                // discoveryZoomed = true ставится в обоих случаях — повторного зума не нужно.
                if (!workoutStarted && isGpsActive && !state.discoveryZoomed) {
                    state.discoveryZoomed = true
                    val fix = map.locationComponent.lastKnownLocation
                    if (fix != null) {
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(fix.latitude, fix.longitude), 16.0
                            )
                        )
                    } else {
                        // Позиция придёт через TRACKING; меняем только zoom
                        map.moveCamera(CameraUpdateFactory.zoomTo(16.0))
                    }
                }

                // Зум 16 на первой Room-точке — один раз.
                // zoomWhileTracking не прерывает TRACKING.
                if (workoutStarted && !state.cameraMovedToFirstFix) {
                    state.cameraMovedToFirstFix = true
                    map.locationComponent.zoomWhileTracking(16.0, 800L)
                }

                // Режим камеры устанавливается ПОСЛЕДНИМ — после всех разовых операций,
                // чтобы TRACKING гарантированно был активен по окончании update-блока.
                // TRACKING: LocationComponent ведёт камеру за живой GPS-позицией.
                // NONE: пауза во время тренировки — пользователь листает карту вручную.
                map.locationComponent.cameraMode =
                    if (!isTracking && workoutStarted) CameraMode.NONE else CameraMode.TRACKING
            }
        },
    )
}

/**
 * Активирует LocationComponent с кастомным маркером [R.drawable.ic_location_dot].
 *
 * Вызывается один раз внутри setStyle-колбэка. Разделена в отдельную функцию,
 * чтобы применить @SuppressLint("MissingPermission") точечно —
 * разрешение ACCESS_FINE_LOCATION уже выдано (LocationTrackingService запущен).
 *
 * Параметры компонента:
 * - [CameraMode.TRACKING] — начальный режим; update-блок переключает на NONE во время паузы
 * - [RenderMode.NORMAL] — точка без стрелки направления движения
 * - accuracyAlpha=0 — круг точности скрыт (отвлекает, не несёт пользы на беговой карте)
 * - pulseEnabled=false — нет пульсации
 * - enableStaleState=false — маркер не серится при устаревании данных
 */
@SuppressLint("MissingPermission")
private fun activateLocationComponent(
    map: MapLibreMap,
    style: Style,
    context: android.content.Context,
) {
    val locationOptions = LocationComponentOptions.builder(context)
        .foregroundDrawable(R.drawable.ic_location_dot)
        // Используем тот же drawable как background — убираем дефолтную тень MapLibre,
        // чтобы итоговый вид совпадал с предыдущим CircleLayer (только синяя точка).
        .backgroundDrawable(R.drawable.ic_location_dot)
        .accuracyAlpha(0f)
        .pulseEnabled(false)
        .enableStaleState(false)
        .build()

    map.locationComponent.apply {
        activateLocationComponent(
            LocationComponentActivationOptions
                .builder(context, style)
                .locationComponentOptions(locationOptions)
                .build()
        )
        isLocationComponentEnabled = true
        // TRACKING: камера следует GPS-позиции до начала тренировки.
        // Переключается на NONE в update-блоке когда появляется первая Room-точка тренировки.
        cameraMode = CameraMode.TRACKING
        renderMode = RenderMode.NORMAL
    }
}
