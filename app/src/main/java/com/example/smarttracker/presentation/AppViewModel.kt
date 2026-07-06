package com.example.smarttracker.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.local.UserProfileCache
import com.example.smarttracker.data.work.OfflineFinishScheduler
import com.example.smarttracker.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel уровня приложения.
 *
 * Отвечает за три сквозных сценария:
 * 1. Определение стартового маршрута при запуске — если токены уже сохранены,
 *    пользователь сразу попадает на Home без повторного логина.
 * 2. Выход из аккаунта — очищает токены, после чего NavGraph перенаправляет на Login.
 * 3. Реконсиляция offline-finish цепочек — перепланирует доставку тренировок,
 *    завершённых без сети, чьи WorkManager-цепочки могли «умереть».
 *
 * Почему здесь, а не в LoginViewModel:
 * эти действия не привязаны к конкретному экрану, они нужны до того,
 * как NavHost выбрал первый маршрут (startRoute) или после того,
 * как пользователь покидает Home (logout).
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val userProfileCache: UserProfileCache,
    private val offlineFinishScheduler: OfflineFinishScheduler,
) : ViewModel() {

    /**
     * Стартовый маршрут определяется один раз при создании ViewModel —
     * синхронное чтение из EncryptedSharedPreferences, splash-экран не нужен.
     */
    val startRoute: String =
        if (tokenStorage.hasTokens()) Screen.Home.route else Screen.Login.route

    /**
     * Глобальный сигнал истечения сессии (оба токена невалидны, refresh вернул 4xx).
     * Наблюдается в AppNavGraph — принудительный переход на Login работает с ЛЮБОГО
     * экрана (раньше подписка жила только в WorkoutHomeScreen: 401 на ProfileEdit
     * не разлогинивал, пока пользователь не вернётся на Home).
     */
    val sessionExpired: kotlinx.coroutines.flow.StateFlow<Boolean> =
        tokenStorage.sessionExpiredFlow

    init {
        // Перепланируем доставку offline-завершённых тренировок: цепочка могла
        // «умереть» пока слот активной тренировки на сервере был занят. KEEP внутри
        // enqueue не дублирует ещё живые цепочки.
        //
        // runCatching: реконсиляция читает Room и дёргает WorkManager. Ошибка любого
        // из них (миграция БД, инициализация) не должна ронять старт приложения —
        // глотаем и логируем, старт от реконсиляции не зависит.
        viewModelScope.launch {
            runCatching { offlineFinishScheduler.reconcilePending() }
                .onFailure { Log.w(TAG, "Offline-finish reconcile failed", it) }
        }
    }

    /**
     * Очищает токены, роли и кэш профиля.
     * Навигация на Login выполняется в AppNavGraph после вызова этой функции.
     *
     * Очищаем оба хранилища атомарно: если токены удалены, профиль
     * другого пользователя не должен показаться при следующем входе.
     */
    fun logout() {
        tokenStorage.clearAll()
        userProfileCache.clear()
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
