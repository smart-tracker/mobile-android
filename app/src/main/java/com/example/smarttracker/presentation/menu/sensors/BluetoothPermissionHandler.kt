package com.example.smarttracker.presentation.menu.sensors

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.smarttracker.R

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

/**
 * Окно «Bluetooth выключен» с предложением включить.
 *
 * «Включить» запускает системный `ACTION_REQUEST_ENABLE` (на API 33+ он
 * показывает свой подтверждающий диалог; ниже — включает сразу). Результат
 * OK → [onEnabled] (экран продолжает поиск). При недоступности intent-а
 * (ActivityNotFound / SecurityException без BLUETOOTH_CONNECT) — fallback
 * на экран настроек Bluetooth.
 *
 * @param onEnabled Bluetooth включён пользователем через системный диалог
 * @param onDismiss окно закрыто без включения (кнопка «Отмена» / тап мимо)
 */
@Composable
fun BluetoothEnablePrompt(
    onEnabled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val enableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        // RESULT_OK — пользователь подтвердил включение; иначе окно просто
        // закрываем (адаптер остался выключен).
        if (result.resultCode == Activity.RESULT_OK) onEnabled() else onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sensors_bt_off)) },
        text = { Text(stringResource(R.string.sensors_bt_enable_message)) },
        confirmButton = {
            TextButton(onClick = {
                try {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (e: Exception) {
                    // Нет BLUETOOTH_CONNECT / OEM без ACTION_REQUEST_ENABLE —
                    // отправляем в настройки Bluetooth, окно закрываем
                    Log.w("BtEnablePrompt", "ACTION_REQUEST_ENABLE недоступен", e)
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.sensors_bt_enable_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sensors_bt_enable_cancel))
            }
        },
    )
}
