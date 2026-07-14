package com.example.smarttracker.presentation.menu.sensors

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.data.local.SavedHrmDevice
import com.example.smarttracker.presentation.common.AppTab
import com.example.smarttracker.presentation.common.SmartTrackerBottomBar
import com.example.smarttracker.presentation.theme.ColorGpsActive
import com.example.smarttracker.presentation.theme.ColorGpsInactive
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.geologicaFontFamily

/**
 * Экран «Датчики» (Меню → Настройки → Датчики): список BLE-пульсометров.
 *
 * Скелет — как SettingsScreen: Scaffold + CenterAlignedTopAppBar + нижний
 * бар с вкладкой «Меню». Содержимое вынесено в [SensorsScreenContent] —
 * оно же используется компактным диалогом [SensorsDialog] с экрана
 * тренировки (тап по HR-бейджу; навигация оттуда запрещена — нюанс 36).
 *
 * Разрешения Bluetooth запрашиваются при входе ([BluetoothPermissionHandler]);
 * при отказе поиск недоступен, показывается подсказка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(
    state: SensorsUiState,
    onBack: () -> Unit,
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit,
    onScanClick: () -> Unit,
    onSavedDeviceClick: (SavedHrmDevice) -> Unit,
    onRemoveDeviceClick: (SavedHrmDevice) -> Unit,
    onAddDeviceClick: (HrmScanResult) -> Unit,
) {
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
                        text = stringResource(R.string.sensors_title),
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
        SensorsScreenContent(
            state = state,
            onPermissionsGranted = onPermissionsGranted,
            onPermissionsDenied = onPermissionsDenied,
            onScanClick = onScanClick,
            onSavedDeviceClick = onSavedDeviceClick,
            onRemoveDeviceClick = onRemoveDeviceClick,
            onAddDeviceClick = onAddDeviceClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * Содержимое «Датчиков» без обвязки — минималистичный список:
 *  - сохранённые датчики: точка статуса + имя + BPM у подключённого +
 *    корзина (удаление); тап по строке — переключение активного;
 *  - компактная кнопка «Поиск» по центру;
 *  - после скана — секция найденных НОВЫХ датчиков с «+» для добавления.
 *
 * Хосты: [SensorsScreen] (полный экран из Настроек) и [SensorsDialog]
 * (компактный диалог с экрана тренировки). Размер задаёт хост через
 * [modifier]; скролл — внутри.
 */
@Composable
fun SensorsScreenContent(
    state: SensorsUiState,
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit,
    onScanClick: () -> Unit,
    onSavedDeviceClick: (SavedHrmDevice) -> Unit,
    onRemoveDeviceClick: (SavedHrmDevice) -> Unit,
    onAddDeviceClick: (HrmScanResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    BluetoothPermissionHandler(
        onGranted = onPermissionsGranted,
        onDenied = onPermissionsDenied,
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── Сохранённые датчики ──────────────────────────────────────────
        if (state.savedDevices.isEmpty()) {
            HintText(stringResource(R.string.sensors_no_saved_device))
        } else {
            state.savedDevices.forEach { device ->
                SavedDeviceRow(
                    device = device,
                    state = state,
                    onClick = { onSavedDeviceClick(device) },
                    onRemoveClick = { onRemoveDeviceClick(device) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Компактная кнопка поиска ─────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            ScanPillButton(
                isScanning = state.isScanning,
                enabled = state.permissionsGranted,
                onClick = onScanClick,
            )
        }
        if (!state.permissionsGranted) {
            Spacer(modifier = Modifier.height(6.dp))
            HintText(stringResource(R.string.sensors_permission_denied))
        }

        // ── Найденные новые датчики (окно расширяется вниз) ──────────────
        if (state.hasScanned) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.sensors_found),
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = ColorPrimary.copy(alpha = 0.65f),
            )
            state.foundDevices.forEach { device ->
                FoundDeviceRow(device = device, onAddClick = { onAddDeviceClick(device) })
            }
            if (!state.isScanning && state.foundDevices.isEmpty()) {
                HintText(stringResource(R.string.sensors_empty_hint))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Строка сохранённого датчика: точка статуса + имя + BPM (у подключённого
 * активного) + корзина. Тап по строке — сделать активным и подключиться.
 */
@Composable
private fun SavedDeviceRow(
    device: SavedHrmDevice,
    state: SensorsUiState,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val isActive = device.address == state.activeAddress
    val dotColor = when {
        isActive && state.connectionState == HrmConnectionState.CONNECTED -> ColorGpsActive
        isActive && (state.connectionState == HrmConnectionState.CONNECTING ||
            state.connectionState == HrmConnectionState.RECONNECTING) -> ColorAmber
        isActive -> ColorGpsInactive
        else -> ColorPrimary.copy(alpha = 0.25f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = device.name ?: device.address,
            fontFamily = geologicaFontFamily,
            fontWeight = if (isActive) FontWeight.Normal else FontWeight.Light,
            fontSize = 15.sp,
            color = ColorPrimary,
            modifier = Modifier.weight(1f),
        )
        if (isActive && state.connectionState == HrmConnectionState.CONNECTED &&
            state.currentBpm != null
        ) {
            Text(
                text = stringResource(R.string.sensors_bpm_format, state.currentBpm),
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = ColorPrimary.copy(alpha = 0.65f),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        IconButton(onClick = onRemoveClick, modifier = Modifier.size(32.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.sensors_remove),
                colorFilter = ColorFilter.tint(ColorPrimary.copy(alpha = 0.65f)),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Строка найденного нового датчика: имя + rssi + «+» для добавления. */
@Composable
private fun FoundDeviceRow(
    device: HrmScanResult,
    onAddClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = device.name ?: stringResource(R.string.sensors_unknown_device),
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 15.sp,
            color = ColorPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${device.rssi} dBm",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 11.sp,
            color = ColorPrimary.copy(alpha = 0.5f),
        )
        IconButton(onClick = onAddClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.sensors_add),
                tint = ColorPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Компактная pill-кнопка поиска (стиль сегментов IntervalSelectorRow):
 * «Поиск» → при скане спиннер + «Поиск…», клики игнорируются.
 */
@Composable
private fun ScanPillButton(
    isScanning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = ColorPrimary, shape = RoundedCornerShape(16.dp))
            .clickable(enabled = enabled && !isScanning, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = ColorPrimary,
                strokeWidth = 1.5.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (isScanning) {
                stringResource(R.string.sensors_scanning)
            } else {
                stringResource(R.string.sensors_scan)
            },
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = ColorPrimary,
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 13.sp,
        color = ColorPrimary.copy(alpha = 0.65f),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

/** Янтарный статус «подключается» — промежуточный между зелёным и красным. */
private val ColorAmber = Color(0xFFFFB300)
