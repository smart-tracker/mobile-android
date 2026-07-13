package com.example.smarttracker.presentation.menu.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Запрос Bluetooth-разрешений для экрана «Датчики»
 * (по образцу LocationPermissionHandler, но одношаговый).
 *
 * - **API 31+:** runtime-пара BLUETOOTH_SCAN + BLUETOOTH_CONNECT.
 *   SCAN объявлен с neverForLocation — геолокация для скана не нужна.
 * - **API 26–30:** BLUETOOTH/BLUETOOTH_ADMIN выдаются при установке, но
 *   результаты BLE-скана приходят только при выданной геолокации
 *   (ACCESS_FINE_LOCATION) И включённых службах геопозиции. Разрешение
 *   обычно уже выдано для тренировок — если нет, запрашиваем.
 *
 * @param onGranted все нужные разрешения выданы — можно сканировать
 * @param onDenied  пользователь отказал — экран показывает подсказку
 */
@Composable
fun BluetoothPermissionHandler(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) onGranted() else onDenied()
    }

    LaunchedEffect(Unit) {
        val required = requiredBluetoothPermissions()
        if (required.all { context.isGranted(it) }) {
            onGranted()
        } else {
            launcher.launch(required)
        }
    }
}

/** Список runtime-разрешений для работы с BLE-датчиком на текущем API. */
private fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        // До API 31 скан требует геолокацию (результаты скана считаются
        // источником местоположения). Обычно уже выдана для тренировок.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
