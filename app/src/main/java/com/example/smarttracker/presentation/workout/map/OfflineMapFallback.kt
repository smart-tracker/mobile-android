package com.example.smarttracker.presentation.workout.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.geologicaFontFamily

/**
 * Заглушка-замена карты, когда тайлы недоступны (нет сети И авто-кэш не покрывает область).
 *
 * Показывается только при [mapTilesFailed == true] — пока тайлы грузятся из кэша,
 * даже в авиарежиме, карта отображается штатно.
 * Трек продолжает записываться — данные не теряются.
 *
 * @param currentLocation последняя известная позиция для отображения координат
 */
@Composable
fun OfflineMapFallback(
    currentLocation: LocationPoint?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(ColorBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Нет карты",
                tint = ColorSecondary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (currentLocation != null) {
                Text(
                    text = "%.4f° с.ш.  %.4f° в.д.".format(
                        currentLocation.latitude, currentLocation.longitude
                    ),
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = ColorPrimary,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = "Нет карты офлайн.",
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = ColorPrimary,
            )
            Text(
                text = "Трек записывается.",
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
                color = ColorPrimary,
            )
        }
    }
}
