package com.example.smarttracker.presentation.workout.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.smarttracker.R
import com.example.smarttracker.data.system.BatteryOptimizationHelper

/**
 * Трёхшаговый обработчик разрешений для трекинга тренировок.
 *
 * **Шаг 1 (при первом запуске):** ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION +
 * POST_NOTIFICATIONS (Android 13+).
 *
 * **Шаг 2 (Android Q+ и только после шага 1):** ACCESS_BACKGROUND_LOCATION.
 * Система требует раздельного запроса — объединение с Fine/Coarse в один
 * запрос на Android Q+ завершается SecurityException.
 *
 * **Шаг 3 (после шага 2, однократно):** Doze whitelist. Без него Android Doze
 * через ~5-10 мин после выключения экрана throttle'ит GPS-callback'и даже
 * для foreground-сервиса. Показываем объясняющий AlertDialog → системный
 * диалог `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
 *
 * Запрос Doze whitelist делается **один раз за сессию открытия экрана**.
 * Если юзер отказался — повторно его попросит persistent-баннер на стартовом экране.
 *
 * @param onLocationGranted   Вызывается когда хотя бы одна геолокация выдана (Fine или Coarse).
 * @param onBackgroundGranted Вызывается когда фоновый доступ выдан (или не требуется, < Q).
 * @param onDenied            Вызывается если пользователь отказал в геолокации.
 * @param onBatteryOptResult  Вызывается после закрытия системного диалога Doze whitelist
 *                            с актуальным статусом (true = в whitelist).
 *                            Может вызываться несколько раз: при изначальной проверке
 *                            и после возврата из настроек.
 * @param onPermissionsResult Обратная совместимость: старый колбэк (true/false).
 */
@Composable
fun LocationPermissionHandler(
    onLocationGranted: () -> Unit = {},
    onBackgroundGranted: () -> Unit = {},
    onDenied: () -> Unit = {},
    onBatteryOptResult: (granted: Boolean) -> Unit = {},
    onPermissionsResult: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current

    // ── Шаг 3: Doze whitelist (системный диалог + объясняющий AlertDialog) ──────
    var showBatteryOptExplain by remember { mutableStateOf(false) }
    // Защита от повторного показа в рамках одной сессии экрана:
    // если юзер уже видел диалог (даже отказался) — больше не дёргаем.
    // Persistent-баннер на WorkoutStartScreen возьмёт на себя дальнейшее напоминание.
    var batteryOptRequestedThisSession by remember { mutableStateOf(false) }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Системный диалог не возвращает явного результата — проверяем статус повторно.
        val granted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        onBatteryOptResult(granted)
    }

    /**
     * Триггер шага 3. Вызывается из ветки шага 2 (background granted или skipped).
     * Делает повторную проверку whitelist'а — на случай если юзер уже в нём
     * (из предыдущей сессии или установил через настройки до запуска app).
     */
    fun maybeRequestBatteryOpt() {
        if (batteryOptRequestedThisSession) return
        batteryOptRequestedThisSession = true

        val alreadyIgnoring = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        if (alreadyIgnoring) {
            onBatteryOptResult(true)
            return
        }
        // Показываем объясняющий диалог перед системным — UX-best-practice,
        // чтобы юзер понимал зачем приложение просит отключить оптимизацию.
        showBatteryOptExplain = true
    }

    // ── Шаг 2: фоновый доступ (только Android Q+) ──────────────────────────────
    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onBackgroundGranted()
        // Отказ от фонового доступа не критичен — сервис работает как foreground.
        // Шаг 3 запускаем вне зависимости от результата: Doze whitelist важен и
        // когда юзер отказал в background (foreground-режим тоже throttle'ится в Doze).
        maybeRequestBatteryOpt()
    }

    // ── Шаг 1: Fine / Coarse / Notifications ────────────────────────────────────
    val step1Permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val step1Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val fineGranted   = result[Manifest.permission.ACCESS_FINE_LOCATION]  == true
        val coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val locationGranted = fineGranted || coarseGranted

        onPermissionsResult(locationGranted)

        if (!locationGranted) {
            onDenied()
            return@rememberLauncherForActivityResult
        }

        onLocationGranted()

        // Шаг 2: фоновый доступ запрашиваем только если выдан Fine (Coarse недостаточно)
        // и только на Android Q+.
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!bgGranted) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                onBackgroundGranted()
                maybeRequestBatteryOpt()
            }
        } else {
            // Ниже Android Q фоновый доступ не нужен — Fine/Coarse уже покрывает фон
            onBackgroundGranted()
            maybeRequestBatteryOpt()
        }
    }

    LaunchedEffect(Unit) {
        // Если разрешения уже есть — не показываем диалог повторно
        val fineAlready   = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseAlready = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineAlready || coarseAlready) {
            onPermissionsResult(true)
            onLocationGranted()

            if (fineAlready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bgGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!bgGranted) {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    onBackgroundGranted()
                    maybeRequestBatteryOpt()
                }
            } else {
                onBackgroundGranted()
                maybeRequestBatteryOpt()
            }
        } else {
            step1Launcher.launch(step1Permissions)
        }
    }

    // ── Объясняющий диалог перед системным запросом Doze whitelist ──────────────
    if (showBatteryOptExplain) {
        AlertDialog(
            onDismissRequest = {
                showBatteryOptExplain = false
                onBatteryOptResult(false)
            },
            title   = { Text(stringResource(R.string.battery_opt_dialog_title)) },
            text    = { Text(stringResource(R.string.battery_opt_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryOptExplain = false
                    batteryOptLauncher.launch(
                        BatteryOptimizationHelper.buildRequestIntent(context)
                    )
                }) {
                    Text(stringResource(R.string.battery_opt_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBatteryOptExplain = false
                    onBatteryOptResult(false)
                }) {
                    Text(stringResource(R.string.battery_opt_dialog_dismiss))
                }
            },
        )
    }
}
