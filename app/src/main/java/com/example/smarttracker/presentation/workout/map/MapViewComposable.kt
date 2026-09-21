package com.example.smarttracker.presentation.workout.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smarttracker.R
import com.example.smarttracker.data.location.OfflineMapManager
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Composable-обёртка над MapLibre MapView.
 *
 * Жизненный цикл MapView привязан к Compose lifecycle через [DisposableEffect] +
 * [LocalLifecycleOwner]. Карта загружает raster-стиль с публичных тайлов
 * OpenStreetMap (см. OfflineMapManager.STYLE_JSON) — никакого внешнего API-ключа.
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
 * @param enableLocationDot true (по умолчанию) — активировать LocationComponent (живая точка
 *   GPS-позиции и связанные с ней аниматоры). false — карта статична, LocationComponent не
 *   активируется. Используем false на экранах просмотра завершённой тренировки, где синяя
 *   точка не нужна и её аниматоры приводили к крашам при навигации.
 * @param locationPermissionGranted true — разрешение геолокации (Fine или Coarse) выдано.
 *   Пока false, LocationComponent НЕ активируется даже при [enableLocationDot] = true:
 *   активированный без разрешения компонент роняет процесс SecurityException-ом изнутри
 *   MapLibre (MapView.onStart → LocationComponent.onStart → getLastKnownLocation), и снаружи
 *   этот путь не перехватывается. Смена false → true (юзер дал разрешение в диалоге)
 *   активирует компонент поздно — в update-блоке, без пересоздания карты.
 * @param fitToTrackBoundsKey one-shot триггер «подогнать камеру под bounds трека». Каждый
 *   раз когда значение ключа меняется на не-null, камера однократно анимируется на
 *   `LatLngBounds` всех текущих `trackPoints` с padding. Применяется при открытии оверлея
 *   итогов, чтобы пользователь сразу видел весь маршрут целиком, а не последний кадр.
 *   Когда ключ становится null — fit не выполняется (нечего показывать или оверлей закрыт).
 * @param scrubPoint GPS-точка, на которой должен стоять маркер scrubbing. null — маркер скрыт.
 *   Маркер отрисовывается отдельным CircleLayer поверх трека; камера при scrub не двигается.
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
    enableLocationDot: Boolean = true,
    locationPermissionGranted: Boolean = true,
    // true → немедленно погасить LocationComponent (камера NONE + disable +
    // cancelTransitions). Используется перед НАВИГАЦИЕЙ с живой карты:
    // teardown MapView при уходе с экрана гонится с аниматорами локации
    // (accuracy radius/bearing) — тик после инвалидации Style роняет процесс
    // IllegalStateException("Calling getSourceAs when a newer style is loading").
    // Экран сначала выставляет флаг, ждёт пару кадров, потом навигирует.
    suppressLocationDot: Boolean = false,
    fitToTrackBoundsKey: Any? = null,
    scrubPoint: com.example.smarttracker.domain.model.LocationPoint? = null,
    // Drawable-ресурс иконки активности для маркера старта; null — маркер не показывается.
    // Тинтится цветом ColorPrimary (как в SummaryOverlay).
    startIconRes: Int? = null,
    // true → значок attribution переезжает в правый верхний угол (полноэкранный режим).
    attributionTopEnd: Boolean = false,
    // Счётчик-триггер для recenter на текущую GPS-позицию. Инкрементируется
    // в WorkoutStartScreen по тапу на GPS-бейдж. При каждом изменении ключа
    // [LaunchedEffect] анимирует камеру к актуальной позиции и восстанавливает
    // CameraMode.TRACKING. Начальное значение 0 = пропустить первый compose.
    recenterTrigger: Int = 0,
    // Счётчик-триггер снимка карты (шаринг тренировки): тот же паттерн, что
    // recenterTrigger. Инкремент → MapLibreMap.snapshot() → Bitmap в onSnapshot.
    // Снимок содержит только MapView (трек, маркеры) — Compose-оверлеи поверх
    // в кадр не попадают.
    snapshotRequest: Int = 0,
    onSnapshot: ((android.graphics.Bitmap) -> Unit)? = null,
) {
    // В @Preview (Android Studio) MapLibre не инициализирован и MapView во
    // AndroidView не рендерится — без этой заглушки любой экран с картой не
    // открылся бы в превью. Рантайм не затрагивается (нюансы 34/36): ветка
    // только для LocalInspectionMode. Переиспользуем чистый OfflineMapFallback.
    if (LocalInspectionMode.current) {
        OfflineMapFallback(currentLocation = currentLocation, modifier = modifier)
        return
    }

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
            // Сохранённая ссылка на MapLibreMap — нужна в onDispose, чтобы синхронно
            // (без getMapAsync) погасить LocationComponent и отменить анимации до onDestroy.
            // getMapAsync в onDispose ставит callback в очередь, который выполнится уже
            // после разрушения карты — слишком поздно.
            var mapLibreMap: MapLibreMap? = null
            // Флаг первичного центрирования по lastKnownLocation (выполняется один раз)
            var lastKnownCentered: Boolean = false
            // Zoom 16 при первом GPS-фиксе до старта тренировки (discovery-фаза)
            var discoveryZoomed: Boolean = false
            // Zoom 16 при первой Room-точке после старта тренировки
            var cameraMovedToFirstFix: Boolean = false
            // Последний применённый fitToTrackBoundsKey — для one-shot fit-to-bounds.
            // Любое != сравнение с null корректно: при первом запуске оверлея новый key
            // (snapshot WorkoutSummaryUiState) != null → fit срабатывает один раз.
            var lastFittedKey: Any? = null
            // Ресурс, который уже загружен в стиль как "start-icon". Если startIconRes
            // изменится — изображение перезагружается.
            var lastLoadedStartIconRes: Int? = null
        }
    }

    // rememberUpdatedState: всегда содержит актуальное значение параметра даже внутри
    // старых замыканий (factory-callback создаётся один раз при первом compose).
    // Нужно для setStyle-callback: isGpsActive может стать true уже после того, как
    // factory зафиксировал начальное значение false.
    val latestIsGpsActive          = rememberUpdatedState(isGpsActive)
    val latestWorkoutStarted       = rememberUpdatedState(currentLocation != null)
    val latestLastKnown            = rememberUpdatedState(lastKnownLocation)
    val latestStartIconRes         = rememberUpdatedState(startIconRes)
    // Нужен в ON_START чтобы не включать GPS-точку когда карта в summary-режиме.
    // DisposableEffect(lifecycleOwner) создаёт замыкание один раз — без rememberUpdatedState
    // он бы зафиксировал начальный null и не видел последующих обновлений.
    val latestFitToTrackBoundsKey  = rememberUpdatedState(fitToTrackBoundsKey)
    val latestTrackPoints          = rememberUpdatedState(trackPoints)
    // Разрешение может быть выдано ПОСЛЕ загрузки style (свежая установка:
    // диалог разрешения и загрузка карты идут параллельно) — замыкания должны
    // видеть актуальное значение.
    val latestLocationPermission   = rememberUpdatedState(locationPermissionGranted)
    // Гашение перед навигацией должно быть видно и в ON_START-замыкании —
    // иначе рестарт lifecycle мид-навигации перевключит компонент.
    val latestSuppressLocationDot  = rememberUpdatedState(suppressLocationDot)

    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle observer: пробрасывает события Activity в MapView.
    //
    // Дополнительно гасит/восстанавливает LocationComponent на ON_STOP/ON_START.
    // Это критично при навигации Compose: переход вперёд переводит предыдущий
    // backstack-entry в ON_STOP, но НЕ уничтожает его (DisposableEffect.onDispose
    // не срабатывает). Если LocationComponent остаётся активным — он продолжает
    // получать GPS-fix-ы от LocationTrackingService и запускать аниматоры
    // (bearing, accuracy radius), которые тикают после того, как Style уже невалиден,
    // что приводит к IllegalStateException("Calling getSourceAs when a newer style ...").
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = state.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START   -> {
                    mapView.onStart()
                    // Восстанавливаем LocationComponent если он был активирован.
                    // runCatching: до setStyle locationComponent.activate ещё не вызывался —
                    // обращение к нему бросает IllegalStateException.
                    // Проверяем latestFitToTrackBoundsKey: если != null — карта в summary-
                    // режиме, GPS-точку не показываем (иначе синяя точка появляется поверх
                    // замороженного трека при возврате из фона).
                    // latestLocationPermission: без разрешения компонент не активирован —
                    // enable бросил бы IllegalStateException (runCatching скроет, но
                    // и делать нечего).
                    if (enableLocationDot && latestLocationPermission.value) {
                        runCatching {
                            state.mapLibreMap?.locationComponent?.isLocationComponentEnabled =
                                (latestFitToTrackBoundsKey.value == null &&
                                    !latestSuppressLocationDot.value)
                        }
                    }
                }
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> {
                    // Сначала глушим LocationComponent (остановит все аниматоры),
                    // потом отдаём mapView.onStop. Если порядок инверсный — успевает
                    // прилететь GPS-callback на уже остановленную карту.
                    runCatching {
                        state.mapLibreMap?.locationComponent?.let { lc ->
                            lc.cameraMode = CameraMode.NONE
                            lc.isLocationComponentEnabled = false
                        }
                    }
                    runCatching { state.mapLibreMap?.cancelTransitions() }
                    mapView.onStop()
                }
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // ── Гасим LocationComponent ДО onDestroy ──────────────────────────
            // Иначе bearing-/camera-анимации, поставленные в очередь Choreographer-а
            // OS-обновлением локации, продолжают тикать после уничтожения карты:
            // SymbolLocationLayerRenderer.refreshSource обращается к Style → бросает
            // IllegalStateException("Calling getSourceAs when a newer style is loading").
            // Воспроизводилось при навигации с WorkoutHomeScreen на WorkoutSummary —
            // одновременно жили две MapView (старая разрушалась, новая создавалась).
            //
            // Порядок обязателен:
            //  1) NONE  — отвязываем камеру от локации, прекращаем bearing-анимации
            //  2) disable LocationComponent — снимаем подписку на OS Location API
            //  3) cancelTransitions — обрываем уже запущенные камер-анимации
            //  4) onPause/onStop/onDestroy
            state.mapLibreMap?.let { map ->
                if (enableLocationDot) {
                    runCatching {
                        map.locationComponent.cameraMode = CameraMode.NONE
                        map.locationComponent.isLocationComponentEnabled = false
                    }
                }
                runCatching { map.cancelTransitions() }
            }
            state.mapView?.let { mapView ->
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
            state.mapLibreMap = null
            state.mapView = null
        }
    }

    // ── Гашение LocationComponent перед навигацией с живой карты ────────────
    // Порядок и семантика — как в ON_STOP выше: NONE → disable → cancelTransitions.
    // Отдельные runCatching на каждый шаг: сбой одного не пропускает остальные
    // (в ON_STOP оба вызова под одним runCatching — там компонент гарантированно
    // активирован; здесь флаг может прийти до setStyle).
    LaunchedEffect(suppressLocationDot) {
        if (!suppressLocationDot) return@LaunchedEffect
        state.mapLibreMap?.let { map ->
            runCatching { map.locationComponent.cameraMode = CameraMode.NONE }
            runCatching { map.locationComponent.isLocationComponentEnabled = false }
            runCatching { map.cancelTransitions() }
        }
    }

    // ── Recenter по тапу на GPS-бейдж ───────────────────────────────────────
    // Key — счётчик из WorkoutStartScreen. При первом compose key = 0 → skip.
    // На каждом инкременте: вычисляем актуальную позицию (приоритет:
    // LocationComponent.lastKnownLocation → currentLocation prop → lastKnownLocation prop),
    // анимируем камеру на zoom 16, после анимации восстанавливаем TRACKING.
    //
    // Почему не moveCamera+TRACKING сразу: TRACKING прерывает animateCamera,
    // поэтому ставим mode в onFinish-колбэке. Если location не доступен —
    // тап no-op (GPS ещё не получен ни одного fix-а за всю сессию).
    // ── Снимок карты для шаринга ─────────────────────────────────────────────
    // snapshot() требует отрендеренной карты и вызывается на main thread;
    // если карта ещё не готова (map == null) — запрос молча игнорируется,
    // вызывающая сторона делает fallback на вариант «без карты».
    LaunchedEffect(snapshotRequest) {
        if (snapshotRequest == 0 || onSnapshot == null) return@LaunchedEffect
        state.mapLibreMap?.snapshot { bitmap -> onSnapshot(bitmap) }
    }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger == 0) return@LaunchedEffect
        val map = state.mapLibreMap ?: return@LaunchedEffect

        // runCatching: до setStyle locationComponent.activate ещё не вызывался —
        // обращение к нему бросает IllegalStateException.
        val lc = runCatching { map.locationComponent }.getOrNull()
            ?.takeIf {
                runCatching { it.isLocationComponentActivated && it.isLocationComponentEnabled }
                    .getOrDefault(false)
            }

        val fixLatLng = lc?.lastKnownLocation?.let { LatLng(it.latitude, it.longitude) }
        val target = fixLatLng
            ?: currentLocation?.let { LatLng(it.latitude, it.longitude) }
            ?: lastKnownLocation?.let { LatLng(it.latitude, it.longitude) }
            ?: return@LaunchedEffect

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(target, 16.0),
            500,
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() {}
                override fun onFinish() {
                    runCatching {
                        if (lc != null) lc.cameraMode = CameraMode.TRACKING
                    }
                }
            }
        )
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
                    // Сохраняем ссылку синхронно, чтобы onDispose мог дотянуться до неё
                    // без повторного getMapAsync (тот ставит в очередь callback, выполняемый
                    // уже после onDestroy — поздно для отключения LocationComponent).
                    state.mapLibreMap = map

                    mapView.addOnDidFailLoadingMapListener {
                        onMapTilesFailed()
                    }

                    // ── UI-элементы карты ──────────────────────────────────────────────
                    // Логотип MapLibre: BSD-лицензия не обязывает его показывать — скрываем.
                    // Attribution (© OpenStreetMap contributors): ODbL требует сохранить —
                    // перемещаем в верхний левый угол, чтобы не перекрывал кнопки внизу.
                    val density = context.resources.displayMetrics.density
                    val margin8px = (8 * density).toInt()
                    // Компас отображается при повороте карты. Дефолтная позиция MapLibre —
                    // top-end, ровно под нашим GPS-бейджем (Alignment.TopEnd, 8dp padding,
                    // 32dp размер ⇒ верхние ~48dp правого края заняты). Опускаем компас
                    // ниже бейджа: 8 (padding) + 32 (badge) + 8 (gap) = 48dp сверху,
                    // 8dp справа — выравнивание с бейджем по правой границе.
                    val compassTopPx   = (48 * density).toInt()
                    val compassRight8px = margin8px

                    map.uiSettings.apply {
                        isLogoEnabled = false
                        attributionGravity = Gravity.TOP or Gravity.START
                        setAttributionMargins(margin8px, margin8px, 0, 0)
                        // Компас оставляем включённым (помогает ориентироваться при
                        // повороте карты двумя пальцами), только переносим под бейдж.
                        compassGravity = Gravity.TOP or Gravity.END
                        setCompassMargins(0, compassTopPx, compassRight8px, 0)
                    }

                    // Стиль собирается inline из raster-XYZ источника OpenStreetMap
                    // (см. OfflineMapManager.STYLE_JSON): публичные osm.org-тайлы не
                    // хостят style.json, поэтому JSON конструируется в коде и
                    // передаётся через Style.Builder.
                    map.setStyle(Style.Builder().fromJson(OfflineMapManager.STYLE_JSON)) { style ->
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

                        // ── Маркер scrubbing: белый круг с тёмной обводкой ────────
                        // Показывается только когда scrubPoint != null (fullscreen + scrub).
                        // Визуально совпадает с бегунком TrainingProgressBar.
                        val density = android.content.res.Resources.getSystem().displayMetrics.density
                        val scrubRadiusPx = 3f * density
                        val scrubStrokeWidthPx = 0.5f * density
                        style.addSource(
                            GeoJsonSource("scrub-source",
                                FeatureCollection.fromFeatures(emptyList()))
                        )
                        style.addLayer(
                            CircleLayer("scrub-layer", "scrub-source").withProperties(
                                PropertyFactory.circleRadius(scrubRadiusPx),
                                PropertyFactory.circleColor(android.graphics.Color.WHITE),
                                PropertyFactory.circleStrokeWidth(scrubStrokeWidthPx),
                                PropertyFactory.circleStrokeColor(
                                    android.graphics.Color.rgb(
                                        (ColorPrimary.red   * 255).toInt(),
                                        (ColorPrimary.green * 255).toInt(),
                                        (ColorPrimary.blue  * 255).toInt(),
                                    )
                                ),
                            )
                        )

                        // ── Маркеры старта и финиша трека ────────────────────────
                        // Источники пустые при старте — данные приходят из update-блока.
                        // Изображения загружаются лениво в update (startIconRes может
                        // прийти позже чем setStyle — summary ещё не готов при первом запуске).
                        style.addSource(
                            GeoJsonSource("start-source",
                                FeatureCollection.fromFeatures(emptyList()))
                        )
                        style.addSource(
                            GeoJsonSource("finish-source",
                                FeatureCollection.fromFeatures(emptyList()))
                        )
                        style.addLayer(
                            SymbolLayer("start-layer", "start-source").withProperties(
                                PropertyFactory.iconImage("start-icon"),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                                PropertyFactory.iconAnchor("center"),
                            )
                        )
                        style.addLayer(
                            SymbolLayer("finish-layer", "finish-source").withProperties(
                                PropertyFactory.iconImage("finish-icon"),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                                // "center": флаг оборачивается в круг — центр круга на точке
                                PropertyFactory.iconAnchor("center"),
                            )
                        )

                        // ── LocationComponent: живая GPS-позиция ───────────────────
                        // Активируется один раз после загрузки стиля.
                        // Работает независимо от Room — показывает точку сразу после
                        // получения GPS-фикса, в том числе до начала тренировки.
                        // CameraMode.NONE: камерой управляем сами в блоке update.
                        // RenderMode.NORMAL: только точка, без стрелки направления.
                        // На экранах статичного просмотра (Summary/Map) пропускаем —
                        // живая точка не нужна, а её аниматоры (bearing/accuracy)
                        // продолжают тикать после ON_STOP и крашат карту.
                        // Без разрешения активацию пропускаем (иначе краш изнутри
                        // MapLibre, см. KDoc locationPermissionGranted). Если юзер
                        // выдаст разрешение позже — поздняя активация в update-блоке.
                        if (enableLocationDot && latestLocationPermission.value) {
                            activateLocationComponent(map, style, context)
                            // В summary-режиме (fitToTrackBoundsKey уже не null) скрываем
                            // живую точку сразу — update ещё не успел это сделать, пока
                            // style грузился. Компонент активирован и будет доступен
                            // когда пользователь закроет оверлей (update re-enable-ит его).
                            if (latestFitToTrackBoundsKey.value != null) {
                                runCatching { map.locationComponent.isLocationComponentEnabled = false }
                            }
                        }

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
                        // На статичном экране (enableLocationDot=false) пропускаем —
                        // locationComponent не активирован, обращение упадёт.
                        if (enableLocationDot && latestIsGpsActive.value && !latestWorkoutStarted.value && !state.discoveryZoomed) {
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

                        // ── Трек + fit-to-bounds при запоздалой загрузке style ────
                        // Сценарий: пользователь переключился с другой вкладки →
                        // MapView пересоздаётся, style загружается по сети. GPS-трек
                        // приходит из history раньше чем style готов → update делает
                        // early return (map.style == null), track-source остаётся
                        // пустым, fit не запускается. Здесь style точно готов —
                        // отрисовываем трек и позиционируем камеру.
                        val pendingFitKey = latestFitToTrackBoundsKey.value
                        val pendingPts    = latestTrackPoints.value
                        if (pendingPts.size >= 2) {
                            // Обновляем трек (track-source создан выше в этом же callback)
                            val coords = pendingPts.map { Point.fromLngLat(it.longitude, it.latitude) }
                            val trackFc = FeatureCollection.fromFeatures(
                                listOf(Feature.fromGeometry(LineString.fromLngLats(coords)))
                            )
                            style.getSourceAs<GeoJsonSource>("track-source")?.setGeoJson(trackFc)

                            // Маркеры старта и финиша (только в режиме summary)
                            if (pendingFitKey != null) {
                                val primaryArgb = android.graphics.Color.rgb(
                                    (ColorPrimary.red   * 255).toInt(),
                                    (ColorPrimary.green * 255).toInt(),
                                    (ColorPrimary.blue  * 255).toInt(),
                                )
                                val iconRes = latestStartIconRes.value
                                if (iconRes != null) {
                                    state.lastLoadedStartIconRes = iconRes
                                    BitmapFactory.decodeResource(context.resources, iconRes)?.let { raw ->
                                        style.addImage("start-icon",
                                            makeMarkerBitmap(raw, context.resources.displayMetrics.density, primaryArgb, tintIcon = true))
                                        raw.recycle()
                                    }
                                }
                                if (style.getImage("finish-icon") == null) {
                                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_finish_flag)?.let { raw ->
                                        style.addImage("finish-icon",
                                            makeMarkerBitmap(raw, context.resources.displayMetrics.density, primaryArgb, tintIcon = false))
                                        raw.recycle()
                                    }
                                }
                                val startPt = pendingPts.first()
                                val endPt   = pendingPts.last()
                                style.getSourceAs<GeoJsonSource>("start-source")?.setGeoJson(
                                    FeatureCollection.fromFeatures(listOf(
                                        Feature.fromGeometry(Point.fromLngLat(startPt.longitude, startPt.latitude))
                                    ))
                                )
                                style.getSourceAs<GeoJsonSource>("finish-source")?.setGeoJson(
                                    FeatureCollection.fromFeatures(listOf(
                                        Feature.fromGeometry(Point.fromLngLat(endPt.longitude, endPt.latitude))
                                    ))
                                )
                            }
                        }
                        if (pendingFitKey != null && pendingPts.size >= 2 && pendingFitKey != state.lastFittedKey) {
                            state.lastFittedKey = pendingFitKey
                            val builder = LatLngBounds.Builder()
                            pendingPts.forEach { p -> builder.include(LatLng(p.latitude, p.longitude)) }
                            val bounds = builder.build()
                            if (enableLocationDot) {
                                runCatching { map.locationComponent.cameraMode = CameraMode.NONE }
                            }
                            val paddingPx = (40 * context.resources.displayMetrics.density).toInt()
                            mapView.post {
                                if (map.style != null) {
                                    map.cancelTransitions()
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngBounds(bounds, paddingPx), 800
                                    )
                                }
                            }
                        }
                    }
                }

                state.mapView = mapView
            }
        },
        update = { mapView ->
            // getMapAsync выполняет колбэк сразу если карта готова, иначе ставит в очередь.
            state.mapView?.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync

                // ── Attribution: позиция зависит от режима экрана ─────────────────
                // В fullscreen-режиме карта занимает весь экран — перемещаем в правый
                // верхний угол, чтобы не перекрывать StatsOverlayCard слева.
                val attrDensity = mapView.resources.displayMetrics.density
                val attrMargin  = (8 * attrDensity).toInt()
                if (attributionTopEnd) {
                    map.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
                    map.uiSettings.setAttributionMargins(0, attrMargin, attrMargin, 0)
                } else {
                    map.uiSettings.attributionGravity = Gravity.TOP or Gravity.START
                    map.uiSettings.setAttributionMargins(attrMargin, attrMargin, 0, 0)
                }

                // ── Поздняя активация LocationComponent ───────────────────────────
                // Свежая установка: style загрузился ДО выдачи разрешения → активация
                // в setStyle-callback была пропущена. Юзер дал разрешение в диалоге →
                // recompose с locationPermissionGranted = true → активируем здесь,
                // не пересоздавая карту.
                if (enableLocationDot && locationPermissionGranted &&
                    runCatching { !map.locationComponent.isLocationComponentActivated }
                        .getOrDefault(false)
                ) {
                    activateLocationComponent(map, style, mapView.context)
                }

                // ── Синяя GPS-точка: скрываем в режиме просмотра итогов ───────────
                // В summary-mode (fitToTrackBoundsKey != null) LocationComponent
                // уже активирован (запускается один раз в setStyle), но отображение
                // точки излишне — пользователь смотрит завершённый трек, а не
                // текущую позицию.
                // !suppressLocationDot ОБЯЗАТЕЛЕН: update-блок выполняется на каждой
                // рекомпозиции — без учёта флага он перевключал бы компонент сразу
                // после гашения перед навигацией, и аниматоры доживали до разрушения
                // карты (краш из нюанса 36).
                if (enableLocationDot && locationPermissionGranted) {
                    runCatching {
                        map.locationComponent.isLocationComponentEnabled =
                            (fitToTrackBoundsKey == null && !suppressLocationDot)
                    }
                }

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
                // На статичных экранах LocationComponent не активирован — голову трека
                // не добавляем, иначе обращение к locationComponent.* падает с
                // IllegalStateException ("not activated").
                // Без разрешения геолокации компонент тоже не активирован (см.
                // locationPermissionGranted) — та же причина пропустить.
                // В режиме оверлея итогов (fitToTrackBoundsKey != null) голову не добавляем:
                // трек заморожен, а liveHead не входит в bounds → линия выходит за край camera.
                val liveHead: Point? = if (!enableLocationDot || !locationPermissionGranted ||
                    fitToTrackBoundsKey != null) {
                    null
                } else if (map.locationComponent.cameraMode == CameraMode.TRACKING) {
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

                // ── Scrub-маркер: точка на треке при перемотке ────────────────────
                val scrubFc = if (scrubPoint != null) {
                    FeatureCollection.fromFeatures(
                        listOf(
                            Feature.fromGeometry(
                                Point.fromLngLat(scrubPoint.longitude, scrubPoint.latitude)
                            )
                        )
                    )
                } else {
                    FeatureCollection.fromFeatures(emptyList())
                }
                style.getSourceAs<GeoJsonSource>("scrub-source")?.setGeoJson(scrubFc)

                // ── Старт/финиш маркеры: только в режиме просмотра итогов ────────
                // fitToTrackBoundsKey != null означает «оверлей summary открыт».
                val primaryArgb = android.graphics.Color.rgb(
                    (ColorPrimary.red   * 255).toInt(),
                    (ColorPrimary.green * 255).toInt(),
                    (ColorPrimary.blue  * 255).toInt(),
                )
                val density = mapView.resources.displayMetrics.density
                if (fitToTrackBoundsKey != null && trackPoints.size >= 2) {
                    // start-icon: иконка активности в белом круге, тинт ColorPrimary.
                    // Перезагружаем только если ресурс изменился (смена типа активности).
                    val iconRes = latestStartIconRes.value
                    if (iconRes != null && iconRes != state.lastLoadedStartIconRes) {
                        state.lastLoadedStartIconRes = iconRes
                        BitmapFactory.decodeResource(mapView.resources, iconRes)?.let { raw ->
                            style.addImage("start-icon",
                                makeMarkerBitmap(raw, density, primaryArgb, tintIcon = true))
                            raw.recycle()
                        }
                    }
                    // finish-icon: флаг финиша в белом круге, без тинта (собственные цвета).
                    if (style.getImage("finish-icon") == null) {
                        BitmapFactory.decodeResource(mapView.resources, R.drawable.ic_finish_flag)
                            ?.let { raw ->
                                style.addImage("finish-icon",
                                    makeMarkerBitmap(raw, density, primaryArgb, tintIcon = false))
                                raw.recycle()
                            }
                    }
                    val startPt = trackPoints.first()
                    val endPt   = trackPoints.last()
                    style.getSourceAs<GeoJsonSource>("start-source")?.setGeoJson(
                        FeatureCollection.fromFeatures(listOf(
                            Feature.fromGeometry(Point.fromLngLat(startPt.longitude, startPt.latitude))
                        ))
                    )
                    style.getSourceAs<GeoJsonSource>("finish-source")?.setGeoJson(
                        FeatureCollection.fromFeatures(listOf(
                            Feature.fromGeometry(Point.fromLngLat(endPt.longitude, endPt.latitude))
                        ))
                    )
                } else {
                    // Вне режима summary — скрываем маркеры
                    val empty = FeatureCollection.fromFeatures(emptyList())
                    style.getSourceAs<GeoJsonSource>("start-source")?.setGeoJson(empty)
                    style.getSourceAs<GeoJsonSource>("finish-source")?.setGeoJson(empty)
                }

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

                // ── На статичных экранах вся camera/location-логика ниже не нужна ───
                // Трек уже нарисован, трекинга нет, начальная камера выставится из
                // bounds трека (ниже добавим one-shot fit-to-bounds). Возвращаемся.
                if (!enableLocationDot) {
                    // One-shot центрирование на bounds трека: чтобы при открытии экрана
                    // итогов сразу было видно весь маршрут, а не дефолтная позиция карты.
                    if (!state.lastKnownCentered && trackPoints.size >= 2) {
                        state.lastKnownCentered = true
                        val first = trackPoints.first()
                        val last  = trackPoints.last()
                        // Простое центрирование на середине отрезка с дефолтным зумом.
                        // Для точного fit-to-bounds потребовался бы расчёт LatLngBounds,
                        // здесь же достаточно показать общий район.
                        val midLat = (first.latitude + last.latitude) / 2.0
                        val midLng = (first.longitude + last.longitude) / 2.0
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(midLat, midLng), 14.0)
                        )
                    }
                    return@getMapAsync
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

                // ── One-shot fit-to-bounds по треку ────────────────────────────────
                // Когда ключ меняется на новый не-null — камера однократно
                // подгоняется под все trackPoints. Используется для оверлея итогов:
                // как только тренировка завершена, пользователь видит весь маршрут.
                // Важно: ставим cameraMode=NONE до animateCamera, иначе TRACKING
                // отменит анимацию при следующем GPS-фиксе.
                val fitKey = fitToTrackBoundsKey
                if (fitKey != null && fitKey != state.lastFittedKey && trackPoints.size >= 2) {
                    state.lastFittedKey = fitKey
                    val builder = LatLngBounds.Builder()
                    trackPoints.forEach { p ->
                        builder.include(LatLng(p.latitude, p.longitude))
                    }
                    val bounds = builder.build()
                    // Останавливаем TRACKING ДО post {} — чтобы LocationComponent не двигал
                    // камеру за GPS пока ждём следующего frame.
                    if (enableLocationDot) {
                        map.locationComponent.cameraMode = CameraMode.NONE
                    }
                    // 40dp в пикселях — корректный density-independent отступ.
                    // Прежнее значение 80px = ~27dp на xxhdpi (3×), что почти незаметно.
                    val paddingPx = (40 * mapView.resources.displayMetrics.density).toInt()
                    // post {} гарантирует, что animateCamera вызывается после завершения
                    // layout-прохода и MapView имеет финальные размеры — newLatLngBounds
                    // использует width/height для расчёта zoom.
                    // cancelTransitions() внутри post {}: отменяет любую анимацию камеры,
                    // которая могла остаться от TRACKING-режима.
                    mapView.post {
                        if (map.style != null) {
                            map.cancelTransitions()
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(bounds, paddingPx),
                                800,
                            )
                        }
                    }
                    // Дальше cameraMode не трогаем — TRACKING после оверлея вернётся
                    // когда пользователь закроет оверлей и stat сбросится.
                    return@getMapAsync
                }

                // Режим камеры устанавливается ПОСЛЕДНИМ — после всех разовых операций,
                // чтобы TRACKING гарантированно был активен по окончании update-блока.
                // TRACKING: LocationComponent ведёт камеру за живой GPS-позицией.
                // NONE: пауза во время тренировки — пользователь листает карту вручную.
                //       Также NONE применяется когда виден оверлей итогов (fitKey != null) —
                //       не возвращаем TRACKING пока пользователь смотрит маршрут.
                // runCatching: в summary-mode LocationComponent отключён (isEnabled=false),
                // обращение к cameraMode на disabled компоненте может выбросить ISE.
                if (enableLocationDot) {
                    runCatching {
                        map.locationComponent.cameraMode = when {
                            fitKey != null                -> CameraMode.NONE
                            !isTracking && workoutStarted -> CameraMode.NONE
                            else                          -> CameraMode.TRACKING
                        }
                    }
                }
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
    // Страховка последнего рубежа: активация без разрешения геолокации роняет
    // процесс SecurityException-ом изнутри MapLibre (MapView.onStart →
    // LocationComponent.onStart → LocationManager.getLastKnownLocation) —
    // вызов идёт из внутреннего Handler-а карты, снаружи не перехватывается.
    // Вызывающие стороны гейтятся по locationPermissionGranted, но проверка
    // здесь защищает и любые будущие пути вызова.
    val permissionGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    if (!permissionGranted) return

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

/**
 * Создаёт bitmap маркера: белый круг с обводкой [primaryArgb] и иконкой внутри.
 *
 * Размер итогового маркера ~32dp — чуть крупнее scrub-маркера (circleRadius 10dp).
 * Иконка занимает ~65% диаметра круга.
 *
 * @param icon       исходный PNG-bitmap иконки (не recycled)
 * @param density    DisplayMetrics.density для перевода dp → px
 * @param primaryArgb цвет обводки и опционального тинта иконки
 * @param tintIcon   true — иконка перекрашивается в [primaryArgb] (активность);
 *                   false — иконка сохраняет оригинальные цвета (флаг финиша)
 */
private fun makeMarkerBitmap(
    icon: Bitmap,
    density: Float,
    primaryArgb: Int,
    tintIcon: Boolean,
): Bitmap {
    val totalPx  = (32 * density).toInt()
    val iconPx   = (20 * density).toInt()
    val strokePx = (1.5f * density)
    val cx = totalPx / 2f
    val r  = cx - strokePx / 2f

    val result = Bitmap.createBitmap(totalPx, totalPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    // Белая заливка
    canvas.drawCircle(cx, cx, r + strokePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    })
    // Обводка ColorPrimary
    canvas.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryArgb
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    })
    // Иконка по центру
    val scaled = Bitmap.createScaledBitmap(icon, iconPx, iconPx, true)
    val iconPaint = if (tintIcon) {
        Paint().apply { colorFilter = PorterDuffColorFilter(primaryArgb, PorterDuff.Mode.SRC_IN) }
    } else {
        Paint()
    }
    val offset = (totalPx - iconPx) / 2f
    canvas.drawBitmap(scaled, offset, offset, iconPaint)
    scaled.recycle()

    return result
}
