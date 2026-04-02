package com.example.smarttracker.presentation.workout.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Composable-обработчик разрешений геолокации.
 *
 * Запрашивает ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION и POST_NOTIFICATIONS
 * (только Android 13+) при первой компоновке экрана. Не рендерит никаких элементов UI —
 * только управляет системным диалогом.
 *
 * @param onPermissionsResult Вызывается после ответа пользователя: true если геолокация
 *   разрешена (ACCESS_FINE_LOCATION или ACCESS_COARSE_LOCATION), false иначе.
 */
@Composable
fun LocationPermissionHandler(
    onPermissionsResult: (Boolean) -> Unit,
) {
    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            // POST_NOTIFICATIONS требует явного запроса только начиная с Android 13 (API 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Считаем разрешение выданным, если пользователь разрешил хотя бы одну из
        // геолокаций. FINE даёт и точный, и приблизительный доступ, COARSE — только приблизительный.
        val locationGranted =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onPermissionsResult(locationGranted)
    }

    // Запускаем запрос один раз при появлении экрана.
    // Unit-ключ гарантирует однократный вызов за жизнь Composable.
    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }
}
