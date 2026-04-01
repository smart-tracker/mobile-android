# План реализации GPS-трекинга тренировок

## Архитектурный принцип

Каждый этап — независимая рабочая единица. Бэкенд может быть не готов: этапы 1–4
работают полностью локально. Этап 5 — опциональный сетевой слой поверх готового функционала.

```
Этап 1 (Room) → Этап 2 (GPS) → Этап 3 (Таймер) → Этап 4 (MapLibre)
                                                         ↑
                                                    Этап 5 (Sync)
                                              (независим, добавляется
                                               поверх Этапов 1–4)
```

---

## Этап 1 — Room: локальное хранение GPS-точек

**Цель:** создать фундамент — локальная БД является единственным источником правды для всех GPS-данных.

### Создаваемые файлы

```
app/src/main/java/com/example/smarttracker/
├── domain/
│   ├── model/LocationPoint.kt
│   └── repository/LocationRepository.kt
└── data/
    ├── local/db/
    │   ├── GpsPointEntity.kt
    │   ├── GpsPointDao.kt
    │   └── SmartTrackerDatabase.kt
    └── repository/location/
        └── LocationRepositoryImpl.kt
```

### Изменяемые файлы

```
di/AuthModule.kt                — добавить @Provides SmartTrackerDatabase + @Binds LocationRepository
gradle/libs.versions.toml       — добавить room
app/build.gradle.kts            — добавить room зависимости + kapt room-compiler
```

### Зависимости `libs.versions.toml`

```toml
[versions]
room = "2.6.1"

[libraries]
room-runtime  = { group = "androidx.room", name = "room-runtime",  version.ref = "room" }
room-ktx      = { group = "androidx.room", name = "room-ktx",      version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler",  version.ref = "room" }
```

В `app/build.gradle.kts`:
```kotlin
implementation(libs.room.runtime)
implementation(libs.room.ktx)
kapt(libs.room.compiler)
```

### Модели

**`domain/model/LocationPoint.kt`** — чистый Kotlin, без `android.*`:
```kotlin
data class LocationPoint(
    val id: Long = 0,
    val trainingId: String,          // UUID тренировки
    val timestampUtc: Long,          // epoch millis (из Location.time)
    val elapsedNanos: Long,          // монотонные часы (из Location.elapsedRealtimeNanos)
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,           // null если hasAltitude() == false
    val speed: Float?,               // null если hasSpeed() == false, единицы: м/с
    val accuracy: Float?,            // null если hasAccuracy() == false, единицы: метры
    val batchId: String? = null,     // UUID блока для idempotency (заполняется при Этапе 5)
    val isSent: Boolean = false
)
```

**`domain/repository/LocationRepository.kt`** — без `android.*`:
```kotlin
interface LocationRepository {
    suspend fun savePoint(point: LocationPoint)
    suspend fun getPointsForTraining(trainingId: String): List<LocationPoint>
    suspend fun getUnsentPoints(trainingId: String): List<LocationPoint>
    suspend fun markBatchAsSent(batchId: String)
    fun observePointsForTraining(trainingId: String): Flow<List<LocationPoint>>
}
```

### Готово, когда:

- [ ] `./gradlew assembleDebug` — компилируется без ошибок
- [ ] `./gradlew installDebug` — устанавливается на эмулятор
- [ ] App Inspection (Android Studio → App Inspection → Database) показывает таблицу `gps_points`
- [ ] Ручная вставка тестовой точки через DAO в `@Test` — строка видна в App Inspection

---

## Этап 2 — LocationManager: получение GPS-координат

**Цель:** Foreground Service собирает GPS-координаты и пишет их в Room через `LocationRepository`.

### Создаваемые файлы

```
app/src/main/java/com/example/smarttracker/
├── data/
│   └── location/
│       ├── LocationTrackingService.kt
│       └── LocationConfig.kt
└── presentation/
    └── workout/
        └── permission/
            └── LocationPermissionHandler.kt
```

### Изменяемые файлы

```
app/src/main/AndroidManifest.xml                         — permissions + service declaration
presentation/workout/start/WorkoutStartViewModel.kt      — UiState.GpsStatus + запуск Service
presentation/workout/start/WorkoutStartScreen.kt         — индикатор "Поиск GPS..."
```

### Permissions `AndroidManifest.xml`

```xml
<!-- GPS -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Foreground Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- Notification (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Service declaration -->
<service
    android:name=".data.location.LocationTrackingService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

### `LocationConfig.kt`

```kotlin
object LocationConfig {
    const val INTERVAL_MS_RUNNING  = 3000L   // бег, ходьба
    const val INTERVAL_MS_CYCLING  = 2000L   // велосипед
    const val MIN_DISTANCE_M       = 0f      // фильтрация по расстоянию отключена
    const val MAX_ACCURACY_RUNNING = 20f     // метры — плохие точки отбрасываются
    const val MAX_ACCURACY_CYCLING = 30f
    const val GPS_FIX_TIMEOUT_MS   = 30_000L // через 30 сек без fix — предупреждение
    const val NOTIFICATION_ID      = 1001
    const val CHANNEL_ID           = "workout_tracking"
}
```

### `LocationTrackingService.kt` — ключевые моменты

- Наследует `Service`, инжектируется через `@AndroidEntryPoint`
- `onStartCommand`: принимает `trainingId: String` и `intervalMs: Long` через `Intent.extras`
- `LocationManager.requestLocationUpdates(GPS_PROVIDER, intervalMs, 0f, listener)`
- `onLocationChanged`:
  1. Проверить `location.accuracy` — если превышает порог, отбросить
  2. Создать `LocationPoint` из `Location`
  3. `scope.launch { locationRepository.savePoint(point) }`
- Foreground notification: текст "Тренировка идёт", non-dismissible
- `onDestroy`: `locationManager.removeUpdates(listener)`, отменить scope

### UX-изменения в `WorkoutStartScreen`

Добавить `gpsStatus: GpsStatus` в `UiState`:
```kotlin
enum class GpsStatus { SEARCHING, ACQUIRED, UNAVAILABLE }
```

Оверлей поверх карты (рендерится внутри Box, когда `gpsStatus == SEARCHING`):
```
┌────────────────────────────────┐
│  ⟳  Поиск GPS-сигнала...       │  ← полупрозрачный тёмный оверлей
│     Не закрывайте приложение   │
└────────────────────────────────┘
```

Если `gpsStatus == UNAVAILABLE` (нет fix > 30 сек):
```
"GPS-сигнал слабый. Выйдите на открытое место."
```

**НЕ менять:** кнопки "Старт"/"Пауза"/"Завершить" — их логика в Этапе 3.

### Готово, когда:

- [ ] `./gradlew installDebug` — без ошибок
- [ ] При открытии экрана тренировки — система запрашивает разрешение на геолокацию
- [ ] Emulator → Extended Controls → Location → задать координаты → точки появляются в App Inspection
- [ ] Заблокировать экран (Power button) → через 60 сек GPS-точки продолжают появляться в Room
- [ ] В шторке уведомлений видна notification "Тренировка идёт"
- [ ] Оверлей "Поиск GPS-сигнала..." виден до получения первой точки с accuracy < 20м

---

## Этап 3 — Таймер и статистика тренировки

**Цель:** заменить хардкод "00:00:00" реальным таймером; рассчитывать дистанцию, скорость, калории из GPS-точек Room.

### Создаваемые файлы

```
app/src/main/java/com/example/smarttracker/
└── domain/
    └── usecase/
        └── CalculateTrainingStatsUseCase.kt
```

### Изменяемые файлы

```
presentation/workout/start/WorkoutStartViewModel.kt  — таймер + подписка на Flow<LocationPoint>
presentation/workout/start/WorkoutStartScreen.kt     — заменить "00:00:00" на state.timerDisplay
```

### `CalculateTrainingStatsUseCase.kt` — без `android.*`

```kotlin
data class TrainingStats(
    val distanceMeters: Double,
    val avgSpeedMps: Double,        // среднее: distanceMeters / durationSec
    val kilocalories: Float,
    val durationSeconds: Long
)

class CalculateTrainingStatsUseCase @Inject constructor() {
    fun execute(points: List<LocationPoint>): TrainingStats {
        // Дистанция: сумма haversine между последовательными точками
        // Haversine: стандартная формула по lat/lng
        // Калории: distanceKm * 70 (усреднённый вес; уточнение — в отдельной задаче)
    }
    private fun haversineMeters(p1: LocationPoint, p2: LocationPoint): Double
}
```

### Таймер в `WorkoutStartViewModel`

```kotlin
private var timerJob: Job? = null
private var startTimeMs: Long = 0L
private var pausedElapsedMs: Long = 0L

fun onStartWorkoutClick() {
    startTimeMs = System.currentTimeMillis() - pausedElapsedMs
    timerJob = viewModelScope.launch {
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTimeMs
            _state.update { it.copy(
                isTracking = true,
                elapsedMs = elapsed,
                timerDisplay = formatDuration(elapsed)
            ) }
            delay(1000)
        }
    }
    // Запустить LocationTrackingService
}

fun onPauseClick() {
    pausedElapsedMs = _state.value.elapsedMs
    timerJob?.cancel()
    // Остановить LocationTrackingService
}

fun onFinishClick() {
    timerJob?.cancel()
    pausedElapsedMs = 0L
    // Остановить LocationTrackingService
    // Сохранить итог тренировки (Этап 5)
}
```

### Подписка на GPS-точки → пересчёт статистики

```kotlin
init {
    viewModelScope.launch {
        locationRepository
            .observePointsForTraining(currentTrainingId)
            .collect { points ->
                val stats = calculateTrainingStatsUseCase.execute(points)
                _state.update { it.copy(
                    distanceDisplay = "%.2f км".format(stats.distanceMeters / 1000),
                    avgSpeedDisplay = formatPace(stats.avgSpeedMps),
                    caloriesDisplay = "${stats.kilocalories.toInt()} кКал"
                ) }
            }
    }
}
```

### Готово, когда:

- [ ] `./gradlew installDebug` — без ошибок
- [ ] Нажать "Старт" → таймер идёт: 00:00:01, 00:00:02...
- [ ] Emulator → задать несколько GPS-точек → дистанция и скорость обновляются
- [ ] "Пауза" → таймер замер на текущем значении
- [ ] Повторный "Старт" → таймер продолжает с места паузы
- [ ] "Завершить" → таймер и статистика сбрасываются

---

## Этап 4 — MapLibre: живая карта + офлайн-стратегия

**Цель:** заменить серый прямоугольник-заглушку реальной картой с GPS-треком. Карта работает
офлайн за счёт авто-кэша и тихого предзагрузки регионов.

### Офлайн-стратегия (три уровня)

| Уровень | Механизм | Что даёт |
|---------|----------|----------|
| 1 — Авто-кэш | MapLibre встроенный, до 100 МБ | Тайлы, которые пользователь уже видел, доступны офлайн автоматически. **Код не нужен.** |
| 2 — OfflineManager | `OfflineManager.createOfflineRegion()`, zooms 12–16 | При первом GPS-fix + Wi-Fi тихо скачивает bbox 5×5 км (~700 тайлов). Лимит MapLibre = 6000 тайлов (не 750 как у Mapbox). FIFO-удаление: максимум 3 региона. |
| 3 — OfflineMapFallback | Composable без карты | Показывается **только** когда нет сети И кэш не покрывает текущий вид. Координаты + статистика, запись трека продолжается. |

### Создаваемые файлы

```
app/src/main/java/com/example/smarttracker/
├── data/
│   └── location/
│       └── OfflineMapManager.kt               — тихая предзагрузка региона карты
└── presentation/
    └── workout/
        └── map/
            ├── MapViewComposable.kt
            └── OfflineMapFallback.kt
```

### Изменяемые файлы

```
gradle/libs.versions.toml                           — добавить maplibre
app/build.gradle.kts                                — добавить maplibre зависимость
presentation/workout/start/WorkoutStartScreen.kt    — заменить серый Box на MapViewComposable
presentation/workout/start/WorkoutStartViewModel.kt — добавить trackPoints + mapTilesFailed в UiState
data/location/LocationTrackingService.kt            — вызвать OfflineMapManager при первом GPS-fix
di/AuthModule.kt                                    — добавить @Inject OfflineMapManager
```

### Зависимости `libs.versions.toml`

```toml
[versions]
maplibre = "11.8.2"

[libraries]
maplibre-android = { group = "org.maplibre.gl", name = "android-sdk", version.ref = "maplibre" }
```

### `OfflineMapManager.kt`

```kotlin
// data/location/OfflineMapManager.kt — android.* разрешены (data-слой)
@Singleton
class OfflineMapManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val offlineManager = OfflineManager.getInstance(context)
    private var downloadStarted = false   // однократно за сессию тренировки

    /**
     * Вызывается при первом GPS-fix + наличии Wi-Fi.
     * Тихо скачивает bbox 5×5 км вокруг [center], zooms 12–16.
     * Если уже скачан регион этой сессии — ничего не делает.
     */
    fun downloadRegionIfNeeded(center: LatLng, isWifiConnected: Boolean) {
        if (!isWifiConnected || downloadStarted) return
        downloadStarted = true

        offlineManager.listOfflineRegions { error, regions ->
            if (error != null) return@listOfflineRegions
            // FIFO: удалить самый старый если регионов уже MAX_REGIONS
            if ((regions?.size ?: 0) >= MAX_REGIONS) {
                regions?.firstOrNull()?.delete(null)
            }
            val bounds = toBbox(center, radiusKm = 2.5)
            val definition = OfflineTilePyramidRegionDefinition(
                styleUrl = STYLE_URL,
                bounds = LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                minZoom = 12.0,
                maxZoom = 16.0,
                pixelRatio = context.resources.displayMetrics.density,
            )
            offlineManager.createOfflineRegion(definition, byteArrayOf()) { regionError, region ->
                if (regionError != null) return@createOfflineRegion
                region?.setDownloadState(OfflineRegion.STATE_ACTIVE)
                // Тихо. Нет UI-индикации. Ошибки загрузки не показываются.
            }
        }
    }

    /** Сбросить флаг при завершении тренировки */
    fun reset() { downloadStarted = false }

    companion object {
        const val MAX_REGIONS = 3
        const val STYLE_URL   = "https://tiles.openfreemap.org/styles/liberty"
        private fun toBbox(center: LatLng, radiusKm: Double): BboxDegrees {
            // 1° широты ≈ 111 км; 1° долготы ≈ 111 * cos(lat) км
            val dLat = radiusKm / 111.0
            val dLng = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude)))
            return BboxDegrees(
                north = center.latitude  + dLat,
                south = center.latitude  - dLat,
                east  = center.longitude + dLng,
                west  = center.longitude - dLng,
            )
        }
        data class BboxDegrees(val north: Double, val south: Double, val east: Double, val west: Double)
    }
}
```

### `MapViewComposable.kt`

```kotlin
/**
 * @param mapTilesFailed true когда MapLibre не смог загрузить тайлы (нет сети + нет кэша).
 *   Устанавливается через callback onDidFailLoadingMap в MapView.
 */
@Composable
fun MapViewComposable(
    modifier: Modifier = Modifier,
    currentLocation: LocationPoint?,
    trackPoints: List<LocationPoint>,
    mapTilesFailed: Boolean,
) {
    // Fallback только когда тайлы недоступны (нет сети И нет кэша)
    if (mapTilesFailed) {
        OfflineMapFallback(currentLocation, modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                getMapAsync { map ->
                    map.setStyle(STYLE_URL) { style ->
                        // LocationIndicatorLayer — синяя точка текущей позиции
                        // GeoJsonSource("track") + LineLayer — маршрут
                        // Цвет линии: ColorSecondary, ширина: 4dp
                    }
                }
            }
        },
        update = { mapView ->
            mapView.getMapAsync { map ->
                // Обновить GeoJsonSource при новых trackPoints
                // Анимировать камеру к currentLocation
            }
        }
    )
}
```

> **Примечание:** `mapTilesFailed` устанавливается в `WorkoutStartViewModel` при получении
> callback-а `onDidFailLoadingMap` из `MapView`. Пока тайлы загружаются из кэша успешно —
> карта отображается даже в Airplane mode.

### `OfflineMapFallback.kt`

```kotlin
@Composable
fun OfflineMapFallback(currentLocation: LocationPoint?, modifier: Modifier) {
    Box(modifier.background(ColorBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.LocationOn, tint = ColorSecondary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            if (currentLocation != null) {
                Text("%.4f° с.ш.  %.4f° в.д.".format(
                    currentLocation.latitude, currentLocation.longitude), ...)
                Spacer(Modifier.height(4.dp))
            }
            Text("Нет карты офлайн.", ...)
            Text("Трек записывается.", ...)
        }
    }
}
```

### Tile-сервер

- URL: `https://tiles.openfreemap.org/styles/liberty`
- Бесплатно, без API-ключа, без лимитов, векторные тайлы
- Работает на Huawei/Honor (не зависит от GMS)

### Готово, когда:

- [ ] `./gradlew installDebug` — без ошибок
- [ ] При наличии сети — карта отображается (OpenFreeMap, Россия, города подписаны)
- [ ] Emulator → Extended Controls → задать координаты → синяя точка появляется на карте
- [ ] Подать несколько GPS-точек → трек рисуется линией
- [ ] Просмотреть район → Airplane mode → снова открыть: **карта отображается из авто-кэша** (не fallback)
- [ ] Airplane mode без кэша → показывается `OfflineMapFallback` с координатами, трек продолжает записываться
- [ ] Первый GPS-fix при наличии Wi-Fi → в Logcat `D/OfflineMapManager: region download started`

---

## Этап 5 — Batch-отправка на бэкенд

**Цель:** тихая фоновая синхронизация GPS-данных. Этапы 1–4 продолжают работать без изменений.

> ⚠️ Этот этап требует готового бэкенда. До готовности бэкенда — пропустить.

### Создаваемые файлы

```
app/src/main/java/com/example/smarttracker/
├── data/
│   ├── sync/
│   │   ├── SyncManager.kt
│   │   └── WorkoutSyncWorker.kt
│   └── remote/
│       ├── WorkoutTrackingApiService.kt
│       └── dto/
│           ├── GpsPointBatchRequestDto.kt
│           └── GpsPointDto.kt
```

### Изменяемые файлы

```
gradle/libs.versions.toml       — добавить work-runtime-ktx
app/build.gradle.kts            — добавить workmanager зависимость
di/AuthModule.kt                — добавить WorkoutTrackingApiService, SyncManager
presentation/workout/start/WorkoutStartViewModel.kt — вызов SyncManager при завершении
```

### Зависимости `libs.versions.toml`

```toml
[versions]
workmanager = "2.9.1"

[libraries]
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
```

### `SyncManager.kt` — ключевая логика

```kotlin
class SyncManager @Inject constructor(
    private val locationRepository: LocationRepository,
    private val workoutTrackingApiService: WorkoutTrackingApiService,
) {
    // Вызывать после каждого сохранения GPS-точки
    suspend fun trySyncBatch(trainingId: String, maxAccuracy: Float) {
        val unsentPoints = locationRepository.getUnsentPoints(trainingId)
        if (unsentPoints.size < BATCH_SIZE) return
        val batch = unsentPoints.take(BATCH_SIZE)
        val batchId = UUID.randomUUID().toString()
        // Присвоить batchId точкам в Room
        try {
            workoutTrackingApiService.postGpsPoints(trainingId, GpsPointBatchRequestDto(batchId, batch.map { it.toDto() }))
            locationRepository.markBatchAsSent(batchId)
        } catch (e: Exception) {
            // Тихо. Точки остаются в Room, WorkManager подберёт позже.
        }
    }

    companion object { const val BATCH_SIZE = 30 }
}
```

### `WorkoutSyncWorker.kt`

- `Constraints`: `requiresNetworkType = NetworkType.CONNECTED`
- Запускается при завершении тренировки (`onFinishClick`)
- Отправляет все неотправленные блоки FIFO по `timestampUtc`
- Затем POST `/training/{id}/complete` с агрегатами

### API эндпоинты

```
POST /training/{active_training_id}/points
  Body: { batch_id, points: [...] }
  → 200 OK (идемпотентно по batch_id)

POST /training/{active_training_id}/complete
  Body: { time_end, total_distance_meters, total_kilocalories }
  → 200 OK
```

### Поведение без бэкенда

- `SyncManager.trySyncBatch()` → `IOException` → `catch` → тихо
- Logcat: только `W/SyncManager: Sync skipped: network error` (не UI)
- WorkManager не запускается (нет сети) или retries — пользователь ничего не видит
- Все данные сохранены в Room, не теряются

### Готово, когда:

- [ ] `./gradlew installDebug` — без ошибок
- [ ] Без сети: тренировка работает, в Logcat только `W/SyncManager: ...`, UI без изменений
- [ ] С сетью + рабочий бэкенд: в Logcat `POST /training/{id}/points → 200 OK`
- [ ] Отключить сеть в середине тренировки → включить → WorkManager отправляет накопленное

---

## Что НЕ входит в план

| Фича | Статус |
|------|--------|
| Экран итогов тренировки (summary screen) | Отдельная задача |
| История тренировок (вкладка "Тренировки") | Отдельная задача |
| Офлайн-скачивание регионов на выбор пользователя | Отдельная задача (у нас — автоматическое) |
| Профиль пользователя | Отдельная задача |
| Ролевая навигация (тренер/атлет/клуб) | Отдельная задача |
| Калории по реальному весу пользователя | После реализации профиля |

---

## Диаграмма новых файлов

```
com.example.smarttracker/
├── domain/
│   ├── model/
│   │   └── LocationPoint.kt                    ← новый (Этап 1)
│   ├── repository/
│   │   └── LocationRepository.kt               ← новый (Этап 1)
│   └── usecase/
│       └── CalculateTrainingStatsUseCase.kt     ← новый (Этап 3)
├── data/
│   ├── local/db/
│   │   ├── GpsPointEntity.kt                   ← новый (Этап 1)
│   │   ├── GpsPointDao.kt                      ← новый (Этап 1)
│   │   └── SmartTrackerDatabase.kt             ← новый (Этап 1)
│   ├── location/
│   │   ├── LocationConfig.kt                   ← новый (Этап 2)
│   │   ├── LocationTrackingService.kt          ← новый (Этап 2)
│   │   └── OfflineMapManager.kt                ← новый (Этап 4)
│   ├── repository/location/
│   │   └── LocationRepositoryImpl.kt           ← новый (Этап 1)
│   ├── sync/
│   │   ├── SyncManager.kt                      ← новый (Этап 5)
│   │   └── WorkoutSyncWorker.kt                ← новый (Этап 5)
│   └── remote/
│       ├── WorkoutTrackingApiService.kt         ← новый (Этап 5)
│       └── dto/
│           ├── GpsPointBatchRequestDto.kt       ← новый (Этап 5)
│           └── GpsPointDto.kt                  ← новый (Этап 5)
└── presentation/
    └── workout/
        ├── map/
        │   ├── MapViewComposable.kt             ← новый (Этап 4)
        │   └── OfflineMapFallback.kt            ← новый (Этап 4)
        ├── permission/
        │   └── LocationPermissionHandler.kt     ← новый (Этап 2)
        └── start/
            ├── WorkoutStartScreen.kt            ← изменения (Этапы 2, 3, 4)
            └── WorkoutStartViewModel.kt         ← изменения (Этапы 2, 3, 5)
```
