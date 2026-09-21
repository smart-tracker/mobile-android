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
 *  - С переходом на raster-XYZ (сначала дипломный `tile.gottland.ru`, с 21.07.2026 —
 *    публичные тайлы OpenStreetMap) стиль больше не хостится как URL — он собирается
 *    inline в [STYLE_JSON] и передаётся в `map.setStyle(Style.Builder().fromJson(...))`.
 *    `OfflineTilePyramidRegionDefinition` требует URL стиля → офлайн-предзагрузка
 *    для inline-JSON в MapLibre не работает.
 *
 * Поэтому [downloadRegionIfNeeded] и [reset] оставлены как no-op-стабы:
 *  - DI-инъекции в [LocationTrackingService] и [WorkoutStartViewModel] не ломаются.
 *  - Авто-кэш MapLibre (~100 МБ, LRU) продолжает работать прозрачно — для типичной
 *    тренировки в одном районе тайлы кэшируются и доступны офлайн при повторной сессии.
 *  - Если позже появится свой тайл-сервер с хостингом style.json (план тех-долга:
 *    уйти с публичных OSM-тайлов до масштабного релиза) — можно вернуть логику
 *    pyramid-загрузки через `OfflineTilePyramidRegionDefinition(STYLE_URL, ...)`.
 *    С публичными osm.org-тайлами bulk-предзагрузка запрещена policy — стаб
 *    оставаться no-op обязан.
 *
 * Лицензия: данные карты — OpenStreetMap (ODbL) — атрибуция
 * `© OpenStreetMap contributors` зашита в [STYLE_JSON] и показывается
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
         * Inline MapLibre style spec (v8) с одним raster-источником на публичные
         * тайлы OpenStreetMap (дипломный tile.gottland.ru выведен из эксплуатации).
         *
         * Формат запроса: `GET https://tile.openstreetmap.org/{z}/{x}/{y}.png`.
         * `tileSize: 256` — стандарт XYZ. `maxzoom: 19` — предел osm.org.
         * Сабдомены `{a,b,c}.tile.osm.org` не используются — deprecated,
         * основной хост отдаёт всё сам (HTTP/2).
         *
         * ⚠️ **OSMF Tile Usage Policy** (operations.osmfoundation.org/policies/tiles):
         *  - обязателен идентифицирующий User-Agent — выставляется интерцептором
         *    в общем OkHttpClient (AuthModule), MapLibre ходит через него
         *    (`HttpRequestUtil.setOkHttpClient` в SmartTrackerApp);
         *  - массовая предзагрузка запрещена — офлайн-pyramid здесь и так no-op,
         *    работает только прозрачный LRU-кэш MapLibre (~100 МБ), это допустимо;
         *  - приложения с заметным трафиком должны переезжать на свой рендер
         *    или коммерческого провайдера — план в CLAUDE.md TODO (до
         *    масштабного релиза).
         *
         * **Атрибуция OSM** обязательна по ODbL. MapLibre Android парсит
         * source.attribution через regex `<a href="...">текст</a>` (формат
         * Mapbox/OSM) — без HTML-ссылки строка игнорируется и попап показывает
         * только дефолтный «MapLibre Android» линк, поэтому оборачиваем в `<a>`.
         *
         * Layer без `paint`-секции = тайлы рисуются как есть, без дополнительных
         * трансформаций (raster-opacity = 1.0 по умолчанию).
         */
        const val STYLE_JSON = """
        {
          "version": 8,
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 19,
              "attribution": "<a href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap contributors</a>"
            }
          },
          "layers": [
            {"id": "osm", "type": "raster", "source": "osm"}
          ]
        }
        """
    }
}
