package com.example.smarttracker.presentation.menu.sensors

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmScanResult
import com.example.smarttracker.presentation.common.AppTab
import com.example.smarttracker.presentation.common.PrimaryButton
import com.example.smarttracker.presentation.common.SmartTrackerBottomBar
import com.example.smarttracker.presentation.theme.ColorGpsActive
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.geologicaFontFamily

/**
 * Экран «Датчики» (Меню → Настройки → Датчики): подключение BLE-пульсометра.
 *
 * Скелет — как [SettingsScreen]: Scaffold + CenterAlignedTopAppBar + нижний
 * бар с вкладкой «Меню». Содержимое: карточка сохранённого датчика
 * (статус, живой пульс, «Подключить»/«Забыть»), кнопка поиска и список
 * найденных устройств (сортировка по силе сигнала).
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
    onDeviceClick: (HrmScanResult) -> Unit,
    onConnectSavedClick: () -> Unit,
    onForgetClick: () -> Unit,
) {
    BluetoothPermissionHandler(
        onGranted = onPermissionsGranted,
        onDenied = onPermissionsDenied,
    )

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.sensors_section_hrm))

            if (state.savedDeviceAddress != null) {
                SavedDeviceCard(
                    state = state,
                    onConnectClick = onConnectSavedClick,
                    onForgetClick = onForgetClick,
                )
            } else {
                HintText(stringResource(R.string.sensors_no_saved_device))
            }

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = if (state.isScanning) {
                    stringResource(R.string.sensors_scanning)
                } else {
                    stringResource(R.string.sensors_scan)
                },
                onClick = onScanClick,
                isEnabled = state.permissionsGranted && !state.isScanning,
                isLoading = state.isScanning,
            )

            if (!state.permissionsGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                HintText(stringResource(R.string.sensors_permission_denied))
            }

            Spacer(modifier = Modifier.height(12.dp))

            state.scanResults.forEach { device ->
                ScanResultRow(device = device, onClick = { onDeviceClick(device) })
            }
            if (state.hasScanned && !state.isScanning && state.scanResults.isEmpty()) {
                HintText(stringResource(R.string.sensors_empty_hint))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Карточка сохранённого датчика: имя, статус соединения (живой пульс при
 * CONNECTED) и действия. «Подключить» видна только когда соединения нет
 * и оно не устанавливается прямо сейчас.
 */
@Composable
private fun SavedDeviceCard(
    state: SensorsUiState,
    onConnectClick: () -> Unit,
    onForgetClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.sensors_saved_device),
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 12.sp,
            color = ColorPrimary.copy(alpha = 0.65f),
        )
        Text(
            text = state.savedDeviceName ?: state.savedDeviceAddress.orEmpty(),
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            color = ColorPrimary,
        )
        Text(
            text = connectionStatusText(state),
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = if (state.connectionState == HrmConnectionState.CONNECTED) {
                ColorGpsActive
            } else {
                ColorPrimary.copy(alpha = 0.65f)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val showConnect = state.connectionState == HrmConnectionState.DISCONNECTED ||
                state.connectionState == HrmConnectionState.BLUETOOTH_OFF
            if (showConnect) {
                TextButton(onClick = onConnectClick) {
                    Text(
                        text = stringResource(R.string.sensors_connect),
                        fontFamily = geologicaFontFamily,
                        color = ColorPrimary,
                    )
                }
            }
            TextButton(onClick = onForgetClick) {
                Text(
                    text = stringResource(R.string.sensors_forget),
                    fontFamily = geologicaFontFamily,
                    color = ColorPrimary.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/** Текст статуса соединения; при CONNECTED — вместе с живым пульсом. */
@Composable
private fun connectionStatusText(state: SensorsUiState): String = when (state.connectionState) {
    HrmConnectionState.CONNECTED -> {
        val status = stringResource(R.string.sensors_status_connected)
        val bpm = state.currentBpm
        if (bpm != null) "$status · " + stringResource(R.string.sensors_bpm_format, bpm) else status
    }
    HrmConnectionState.CONNECTING -> stringResource(R.string.sensors_status_connecting)
    HrmConnectionState.RECONNECTING -> stringResource(R.string.sensors_status_reconnecting)
    HrmConnectionState.BLUETOOTH_OFF -> stringResource(R.string.sensors_bt_off)
    HrmConnectionState.DISCONNECTED -> stringResource(R.string.sensors_status_disconnected)
}

/** Строка найденного устройства: имя + сила сигнала. */
@Composable
private fun ScanResultRow(
    device: HrmScanResult,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: stringResource(R.string.sensors_unknown_device),
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = ColorPrimary,
            )
            Text(
                text = device.address,
                fontFamily = geologicaFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                color = ColorPrimary.copy(alpha = 0.65f),
            )
        }
        Text(
            text = "${device.rssi} dBm",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 12.sp,
            color = ColorPrimary.copy(alpha = 0.65f),
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
