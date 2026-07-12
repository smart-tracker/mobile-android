package com.example.smarttracker.presentation.menu.settings

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smarttracker.data.local.AppSettings
import kotlin.math.roundToInt
import com.example.smarttracker.presentation.common.AppTab
import com.example.smarttracker.presentation.common.SmartTrackerBottomBar
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.geologicaFontFamily

/**
 * Экран «Настройки» (Меню → Настройки).
 *
 * Секция «Тренировка»: автопауза, голосовые подсказки + их частота,
 * «не гасить экран». Значения приходят из DataStore ([SettingsViewModel.state]),
 * изменения применяются сразу — кнопки «Сохранить» нет.
 *
 * Паттерн экрана — как ProfileScreen: Scaffold + CenterAlignedTopAppBar,
 * нижний бар с подсвеченной вкладкой «Меню», возврат — тапом по другой вкладке
 * или системной кнопкой Back (обрабатывается NavController-ом).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onAutopauseChanged: (Boolean) -> Unit,
    onVoiceCuesChanged: (Boolean) -> Unit,
    onVoiceCueIntervalChanged: (Int) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
) {
    // Пробный отыгрыш фразы при выборе громкости. Живёт в composition:
    // TTS-движок освобождается при уходе с экрана.
    val context = LocalContext.current
    val samplePlayer = remember { VoiceCueSamplePlayer(context) }
    DisposableEffect(Unit) {
        onDispose { samplePlayer.release() }
    }

    // ── Громкость подсказок = системная громкость медиа (STREAM_MUSIC) ──────
    // Слайдер крутит её напрямую: одна ручка с кнопками громкости устройства,
    // отдельной настройки в DataStore нет — иначе две независимые громкости
    // расходились бы (см. KDoc AppSettings).
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxMediaVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }
    fun readMediaVolumeFraction(): Float =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxMediaVolume

    var mediaVolumeFraction by remember { mutableFloatStateOf(readMediaVolumeFraction()) }

    // Кнопки громкости меняют STREAM_MUSIC мимо слайдера — пересчитываем при
    // возврате на экран (ON_RESUME); живого колбэка без ContentObserver у Android нет.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaVolumeFraction = readMediaVolumeFraction()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        bottomBar = {
            SmartTrackerBottomBar(
                selectedIndex = AppTab.MENU,
                onTabSelected = { index -> if (index != AppTab.MENU) onBack() },
            )
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = ColorPrimary,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
        containerColor = Color.White,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Тренировка")

            SwitchRow(
                title = "Автопауза",
                subtitle = "Пауза при остановке, продолжение при движении",
                checked = settings.autopauseEnabled,
                onCheckedChange = onAutopauseChanged,
            )
            SwitchRow(
                title = "Голосовые подсказки",
                subtitle = "Километраж и темп круга во время тренировки",
                checked = settings.voiceCuesEnabled,
                onCheckedChange = onVoiceCuesChanged,
            )
            // Частота и громкость подсказок — только при включённых подсказках.
            if (settings.voiceCuesEnabled) {
                IntervalSelectorRow(
                    selectedKm = settings.voiceCueIntervalKm,
                    onSelected = onVoiceCueIntervalChanged,
                )
                VolumeSliderRow(
                    volume = mediaVolumeFraction,
                    onVolumeSelected = { fraction ->
                        val step = (fraction * maxMediaVolume).roundToInt()
                            .coerceIn(0, maxMediaVolume)
                        // runCatching: setStreamVolume может бросить SecurityException
                        // в редких режимах DND — слайдер не должен ронять экран.
                        runCatching {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
                        }
                        // Фактическое значение после округления до системного шага.
                        mediaVolumeFraction = readMediaVolumeFraction()
                        // Короткий шаблон подсказки на только что выставленной
                        // громкости — пользователь сразу слышит, что выбрал.
                        samplePlayer.play()
                    },
                )
            }
            SwitchRow(
                title = "Не гасить экран",
                subtitle = "Экран остаётся включённым во время тренировки",
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOnChanged,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = ColorPrimary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = ColorPrimary,
            )
            Text(
                text = subtitle,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = ColorPrimary.copy(alpha = 0.65f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = ColorSecondary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color.White,
                uncheckedThumbColor = ColorPrimary,
                uncheckedBorderColor = ColorPrimary,
            ),
        )
    }
}

/**
 * Ряд «Громкость»: слайдер системной громкости медиа (0–100%) + значение процентом.
 *
 * Локальный стейт на время перетаскивания: наружу (setStreamVolume + пробная
 * фраза) значение уходит только по отпусканию (onValueChangeFinished), иначе
 * система получала бы запись на каждый пиксель, а TTS — очередь фраз.
 * remember(volume): внешнее изменение (кнопки громкости, округление до
 * системного шага) пересинхронизирует слайдер.
 */
@Composable
private fun VolumeSliderRow(
    volume: Float,
    onVolumeSelected: (Float) -> Unit,
) {
    var sliderValue by remember(volume) { mutableFloatStateOf(volume) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Громкость",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 14.sp,
            color = ColorPrimary,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onVolumeSelected(sliderValue) },
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = ColorPrimary,
                activeTrackColor = ColorSecondary,
                inactiveTrackColor = ColorPrimary.copy(alpha = 0.15f),
            ),
        )
        Text(
            text = "${(sliderValue * 100).roundToInt()}%",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = ColorPrimary,
        )
    }
}

/** Ряд «Частота подсказок»: сегменты «1 км / 2 км / 5 км». */
@Composable
private fun IntervalSelectorRow(
    selectedKm: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Частота",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 14.sp,
            color = ColorPrimary,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettings.ALLOWED_VOICE_INTERVALS.forEach { km ->
                val selected = km == selectedKm
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) ColorPrimary else Color.White)
                        .border(
                            width = 1.dp,
                            color = ColorPrimary,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { onSelected(km) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "$km км",
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = if (selected) Color.White else ColorPrimary,
                    )
                }
            }
        }
    }
}
