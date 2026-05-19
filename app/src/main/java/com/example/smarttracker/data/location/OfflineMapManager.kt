package com.example.smarttracker.data.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Источник тайлов карты и (бывший) менеджер офлайн-предзагрузки.
 *
 * История изменений:
 *  - Раньше использовали vector-style OpenFreeMap (`tiles.openfreemap.org/styles/liberty`),
 *    который сам по себе URL → MapLibre OfflineManager умел скачивать pyramid-регионы
 *    вокруг точки старта тренировки.
 *  - С переходом на собственный raster-XYZ сервер `tile.gottland.ru` стиль больше не
 *    хостится как URL — он собирается inline в [STYLE_JSON] и передаётся в
 *    `map.setStyle(Style.Builder().fromJson(...))`. `OfflineTilePyramidRegionDefinition`
 *    требует URL стиля → офлайн-предзагрузка для inline-JSON в MapLibre не работает.
 *
 * Поэтому [downloadRegionIfNeeded] и [reset] оставлены как no-op-стабы:
 *  - DI-инъекции в [LocationTrackingService] и [WorkoutStartViewModel] не ломаются.
 *  - Авто-кэш MapLibre (~100 МБ, LRU) продолжает работать прозрачно — для типичной
 *    тренировки в одном районе тайлы кэшируются и доступны офлайн при повторной сессии.
 *  - Если позже бэк выложит `https://tile.gottland.ru/style.json` — можно вернуть
 *    логику pyramid-загрузки через `OfflineTilePyramidRegionDefinition(STYLE_URL, ...)`.
 *
 * Лицензия: тайлы собираются из OpenStreetMap (CC-BY-SA) — атрибуция
 * `© OpenStreetMap contributors, CC-BY-SA` уже зашита в [STYLE_JSON] и показывается
 * MapLibre автоматически.
 */
@Singleton
class OfflineMapManager @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
) {
    /**
     * No-op после перехода на raster XYZ. Сохранён ради совместимости с callsite в
     * [LocationTrackingService] (первый GPS-fix). См. KDoc класса.
     */
    @Suppress("UNUSED_PARAMETER")
    fun downloadRegionIfNeeded(center: LatLng, isWifiConnected: Boolean) {
        // intentionally empty
    }

    /**
     * No-op. Раньше сбрасывал флаг однократной загрузки за сессию.
     */
    fun reset() {
        // intentionally empty
    }

    companion object {
        /**
         * Inline MapLibre style spec (v8) с одним raster-источником на наш тайл-сервер.
         *
         * Формат запроса: `GET https://tile.gottland.ru/tile/{z}/{x}/{y}.png` → image/png.
         * Подтверждено пробой эндпоинта: 200 OK, `Content-Type: image/png`.
         *
         * `tileSize: 256` — стандарт XYZ. `maxzoom: 18` — заявленный сервером предел
         * (см. index-страницу tile.gottland.ru).
         *
         * **Атрибуция OSM**: данные карты — из OpenStreetMap (лицензия ODbL для данных,
         * рендер сервера наш). Атрибуция **обязательна** по лицензии даже при собственном
         * рендер-сервере. MapLibre Android парсит атрибуции из source.attribution через
         * regex `<a href="...">текст</a>` (формат Mapbox/OSM) — без HTML-ссылки строка
         * игнорируется и попап показывает только дефолтный «MapLibre Android» линк.
         * Поэтому оборачиваем в `<a>` со ссылкой на osm.org/copyright.
         *
         * Layer без `paint`-секции = тайлы рисуются как есть, без дополнительных
         * трансформаций (raster-opacity = 1.0 по умолчанию).
         */
        const val STYLE_JSON = """
        {
          "version": 8,
          "sources": {
            "gottland": {
              "type": "raster",
              "tiles": ["https://tile.gottland.ru/tile/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 18,
              "attribution": "<a href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap contributors</a>"
            }
          },
          "layers": [
            {"id": "gottland", "type": "raster", "source": "gottland"}
          ]
        }
        """
    }
}
