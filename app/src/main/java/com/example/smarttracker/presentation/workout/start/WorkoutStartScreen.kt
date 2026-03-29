package com.example.smarttracker.presentation.workout.start

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.geologicaFontFamily

/**
 * Экран начала / активной тренировки.
 *
 * Два режима, определяются [WorkoutStartViewModel.UiState.isTracking]:
 * - isTracking = false → кнопка «Начать тренировку» поверх карты
 * - isTracking = true  → кнопки «Пауза» и «Завершить» поверх карты
 *
 * Тип активности выбирается кликом напрямую по иконке в строке активностей.
 */
@Composable
fun WorkoutStartScreen(
    state: WorkoutStartViewModel.UiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onStartClick: () -> Unit,
    onTypeSelected: (WorkoutType) -> Unit,
    onPauseClick: () -> Unit,
    onFinishClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .padding(padding),
    ) {
        // ── Шапка: дата по центру ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.currentDate,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = ColorPrimary,
            )
        }
        HorizontalDivider(color = ColorPrimary, thickness = 1.dp)

        // ── Таймер ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "00:00:00",
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = ColorPrimary,
            )
            Text(
                text = "Продолжительность",
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Thin,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = Color.Black,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Статистика ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(value = "0.00 км",      label = "Дистанция")
            StatItem(value = "00:00 мин/км", label = "Средняя скорость")
            StatItem(value = "0 кКал",       label = "Калории")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Переключатель типа активности ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, ColorPrimary, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            state.workoutTypes.take(4).forEach { type ->
                WorkoutTypeIcon(
                    iconRes = iconResForKey(type.iconKey),
                    contentDescription = type.name,
                    isActive = type.id == state.selectedType?.id,
                    onClick = { onTypeSelected(type) },
                )
            }
            if (state.workoutTypes.size < 4) {
                WorkoutTypeIcon(
                    iconRes = R.drawable.ic_activity_other,
                    contentDescription = "Ещё",
                    isActive = false,
                    onClick = {},
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Карта + кнопка поверх неё ─────────────────────────────────────────
        // Карта занимает всё оставшееся место, кнопка рендерится поверх неё снизу
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Карта (заглушка) — занимает весь Box включая область под кнопкой
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEEEEEE))
                    .border(width = 1.dp, color = ColorPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Локация",
                    tint = ColorSecondary,
                    modifier = Modifier.size(48.dp),
                )
            }

            // Кнопка(и) — поверх карты, прижата к низу
            if (!state.isTracking) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary),
                ) {
                    Text(
                        text = "Начать тренировку",
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 20.sp,
                        color = Color.White,
                    )
                }
            } else {
                // Split-кнопка «Пауза» | «Завершить»
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(50.dp),
                ) {
                    OutlinedButton(
                        onClick = onPauseClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(
                            topStart = 10.dp, bottomStart = 10.dp,
                            topEnd = 0.dp, bottomEnd = 0.dp,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = ColorPrimary,
                        ),
                        border = BorderStroke(1.dp, ColorPrimary),
                    ) {
                        Text(
                            text = "Пауза",
                            fontFamily = geologicaFontFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            color = ColorPrimary,
                        )
                    }
                    Button(
                        onClick = onFinishClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(
                            topStart = 0.dp, bottomStart = 0.dp,
                            topEnd = 10.dp, bottomEnd = 10.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary),
                    ) {
                        Text(
                            text = "Завершить",
                            fontFamily = geologicaFontFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ── Вспомогательные composable-ы ──────────────────────────────────────────────

/** Одна статистика: крупное значение + мелкий лейбл под ним */
@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = ColorPrimary,
        )
        Text(
            text = label,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Thin,
            fontStyle = FontStyle.Italic,
            fontSize = 10.sp,
            color = Color.Black,
        )
    }
}

/**
 * Иконка типа активности.
 * Контейнер 42dp, иконка 32dp, border ColorPrimary 1dp, corners 5dp.
 * Активная: ColorSecondary фон + белая иконка. Неактивная: белый фон + тёмная иконка.
 */
@Composable
private fun WorkoutTypeIcon(
    iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (isActive) ColorSecondary else Color.White)
            .border(1.dp, ColorPrimary, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = ColorPrimary,
            modifier = Modifier.size(36.dp),
        )
    }
}

/** Маппинг iconKey → drawable resource id */
private fun iconResForKey(key: String): Int = when (key) {
    "running" -> R.drawable.ic_activity_running
    "walking" -> R.drawable.ic_activity_walking
    "cycling" -> R.drawable.ic_activity_cycling
    else      -> R.drawable.ic_activity_other
}
