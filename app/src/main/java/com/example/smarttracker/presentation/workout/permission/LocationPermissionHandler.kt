package com.example.smarttracker.presentation.workout.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Двухшаговый обработчик разрешений геолокации.
 *
 * **Шаг 1 (при первом запуске):** ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION +
 * POST_NOTIFICATIONS (Android 13+).
 *
 * **Шаг 2 (Android Q+ и только после шага 1):** ACCESS_BACKGROUND_LOCATION.
 * Система требует раздельного запроса — объединение с Fine/Coarse в один
 * запрос на Android Q+ завершается SecurityException.
 *
 * @param onLocationGranted  Вызывается когда хотя бы одна геолокация выдана (Fine или Coarse).
 * @param onBackgroundGranted Вызывается когда фоновый доступ выдан (или не требуется, < Q).
 * @param onDenied           Вызывается если пользователь отказал в геолокации.
 */
@Composable
fun LocationPermissionHandler(
    onLocationGranted: () -> Unit = {},
    onBackgroundGranted: () -> Unit = {},
    onDenied: () -> Unit = {},
    // Обратная совместимость: старый колбэк результата (true/false)
    onPermissionsResult: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current

    // ── Шаг 2: фоновый доступ (только Android Q+) ──────────────────────────────
    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onBackgroundGranted()
        // Отказ от фонового доступа не критичен — сервис работает как foreground
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
            }
        } else {
            // Ниже Android Q фоновый доступ не нужен — Fine/Coarse уже покрывает фон
            onBackgroundGranted()
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
                }
            } else {
                onBackgroundGranted()
            }
        } else {
            step1Launcher.launch(step1Permissions)
        }
    }
}
