package com.example.smarttracker.presentation.menu.sensors

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.geologicaFontFamily

/**
 * Компактный диалог «Датчики» поверх экрана тренировки (тап по HR-бейджу):
 * карточка по центру с затемнённым фоном. Список сохранённых пульсометров
 * (статус + корзина), компактная кнопка поиска; при появлении секции
 * найденных карточка расширяется вниз ([animateContentSize]).
 *
 * НЕ навигация и НЕ window-Dialog намеренно: navigate() с живой карты
 * разрушает MapView, и неотменяемые аниматоры LocationComponent роняют
 * процесс IllegalStateException-ом изнутри MapLibre (нюанс 36). Карточка
 * рисуется в той же композиции ПОВЕРХ экрана — карта живёт под скримом.
 * Тот же паттерн, что SummaryOverlay.
 *
 * ViewModel скоупится на backstack-entry Home — переживает закрытие диалога,
 * поэтому остановка скана при закрытии делается явно (DisposableEffect →
 * [SensorsViewModel.onScanStopRequested]); соединение с датчиком при этом
 * НЕ рвётся (им владеет HrmManager).
 *
 * @param onClose закрытие диалога (тап по скриму и системный Back)
 */
@Composable
fun SensorsDialog(onClose: () -> Unit) {
    val viewModel: SensorsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Системный Back закрывает диалог, а не экран под ним
    BackHandler(onBack = onClose)

    // Скан не должен жить после закрытия диалога: VM не умирает
    // (скоуп — backstack-entry Home), onCleared не сработает
    DisposableEffect(Unit) {
        onDispose { viewModel.onScanStopRequested() }
    }

    // ── Скрим: затемнение + закрытие по тапу ─────────────────────────────
    // clickable без indication — ripple на весь экран выглядит мусорно.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // ── Карточка ─────────────────────────────────────────────────────
        // Глушим тапы (паттерн SummaryDetailsPanel): тап по телу карточки
        // не должен просачиваться в скрим и закрывать диалог.
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .pointerInput(Unit) { detectTapGestures { } }
                .animateContentSize()
                .padding(vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.sensors_title),
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = ColorPrimary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp),
            )
            SensorsScreenContent(
                state = state,
                onPermissionsGranted = viewModel::onPermissionsGranted,
                onPermissionsDenied = viewModel::onPermissionsDenied,
                onScanClick = viewModel::onScanClick,
                onSavedDeviceClick = viewModel::onSavedDeviceClick,
                onRemoveDeviceClick = viewModel::onRemoveDeviceClick,
                onAddDeviceClick = viewModel::onAddDeviceClick,
                onDismissBluetoothPrompt = viewModel::onDismissBluetoothPrompt,
                onBluetoothEnabled = viewModel::onBluetoothEnabled,
                // Высота ограничена — длинные списки скроллятся внутри
                // карточки, а не растягивают её за экран
                modifier = Modifier.heightIn(max = 440.dp),
            )
        }
    }
}
