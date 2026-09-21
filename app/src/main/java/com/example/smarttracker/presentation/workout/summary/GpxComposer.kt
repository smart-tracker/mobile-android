package com.example.smarttracker.presentation.workout.summary

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.smarttracker.domain.model.LocationPoint
import java.io.File
import java.time.Instant

/**
 * Сборка и шаринг GPX-файла тренировки (третий вариант диалога «Поделиться
 * тренировкой», рядом с картинкой «С картой / Только статистика»).
 *
 * GPX 1.1 + Garmin TrackPointExtension для пульса (`gpxtpx:hr`) — формат,
 * который понимают Strava/Garmin Connect/Komoot. Особенности:
 *  - Паузы → отдельные `<trkseg>`: [WorkoutSummaryUiState.pauseGapIndices]
 *    хранит индексы первых точек после resume, на каждом таком индексе
 *    начинается новый сегмент (приёмники не рисуют «телепорт» через паузу).
 *  - `<time>` пишется только при includeTime=true: история до BR-5 имеет
 *    синтетические timestampUtc = index — время в файле было бы мусором
 *    (гейт тот же, что у сплитов: [SplitsBuilder.hasRealTiming]).
 *  - `<ele>`/`gpxtpx:hr` — только у точек, где значение есть (nullable).
 *
 * Сборка — чистая конкатенация строк без XML-библиотек: структура плоская,
 * зато buildGpx тестируется на JVM без Android. Числа форматируются через
 * Double/Int.toString() — они локаленезависимы (точка как разделитель),
 * String.format с дефолтной локалью здесь был бы багом (запятая в ru).
 */
object GpxComposer {

    private const val CREATOR = "SmartTracker"

    /**
     * Собирает содержимое GPX-файла.
     *
     * @param trackName имя трека (`<trk><name>`): название активности + дата.
     * @param points GPS-точки трека (пустой список → валидный GPX без trkseg).
     * @param pauseGapIndices индексы первых точек после пауз — границы сегментов.
     * @param includeTime писать ли `<time>` (false при синтетических таймстемпах).
     */
    fun buildGpx(
        trackName: String,
        points: List<LocationPoint>,
        pauseGapIndices: List<Int>,
        includeTime: Boolean,
    ): String {
        val gaps = pauseGapIndices.toSet()
        val sb = StringBuilder(points.size * 96 + 512)

        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<gpx version="1.1" creator="$CREATOR" """ +
                """xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">"""
        ).append('\n')

        if (includeTime && points.isNotEmpty()) {
            sb.append("  <metadata><time>")
                .append(Instant.ofEpochMilli(points.first().timestampUtc))
                .append("</time></metadata>\n")
        }

        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(trackName)).append("</name>\n")

        var segmentOpen = false
        points.forEachIndexed { index, p ->
            // Новый сегмент: первая точка вообще или первая точка после паузы.
            if (!segmentOpen || index in gaps) {
                if (segmentOpen) sb.append("    </trkseg>\n")
                sb.append("    <trkseg>\n")
                segmentOpen = true
            }
            sb.append("      <trkpt lat=\"").append(p.latitude).append("\" lon=\"")
                .append(p.longitude).append("\">\n")
            p.altitude?.let { sb.append("        <ele>").append(it).append("</ele>\n") }
            if (includeTime) {
                sb.append("        <time>")
                    .append(Instant.ofEpochMilli(p.timestampUtc))
                    .append("</time>\n")
            }
            p.heartRate?.let {
                sb.append("        <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>")
                    .append(it)
                    .append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>\n")
            }
            sb.append("      </trkpt>\n")
        }
        if (segmentOpen) sb.append("    </trkseg>\n")

        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /**
     * Пишет GPX в cacheDir/share/ и открывает системный share sheet.
     * Одно имя файла — перезапись, старые экспорты не копятся (паттерн
     * [ShareImageComposer.shareBitmap]). Вызывать с IO-диспетчера.
     */
    fun shareGpx(context: Context, state: WorkoutSummaryUiState) {
        if (state.trackPoints.isEmpty()) return

        val trackName = listOf(state.activityName, state.dateDisplay)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
            .ifBlank { CREATOR }
        val gpx = buildGpx(
            trackName       = trackName,
            points          = state.trackPoints,
            pauseGapIndices = state.pauseGapIndices,
            includeTime     = SplitsBuilder.hasRealTiming(state.cumulativeData),
        )

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "workout_track.gpx")
        file.writeText(gpx)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Поделиться тренировкой"))
    }

    /** Экранирование спецсимволов XML в текстовых узлах (название активности с сервера). */
    private fun escapeXml(raw: String): String = buildString(raw.length) {
        for (c in raw) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
