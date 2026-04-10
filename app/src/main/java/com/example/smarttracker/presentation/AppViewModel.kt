package com.example.smarttracker.presentation

import androidx.lifecycle.ViewModel
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel уровня приложения.
 *
 * Отвечает за два сквозных сценария:
 * 1. Определение стартового маршрута при запуске — если токены уже сохранены,
 *    пользователь сразу попадает на Home без повторного логина.
 * 2. Выход из аккаунта — очищает токены, после чего NavGraph перенаправляет на Login.
 *
 * Почему здесь, а не в LoginViewModel:
 * эти действия не привязаны к конкретному экрану, они нужны до того,
 * как NavHost выбрал первый маршрут (startRoute) или после того,
 * как пользователь покидает Home (logout).
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val tokenStorage: TokenStorage,
) : ViewModel() {

    /**
     * Стартовый маршрут определяется один раз при создании ViewModel —
     * синхронное чтение из EncryptedSharedPreferences, splash-экран не нужен.
     */
    val startRoute: String =
        if (tokenStorage.hasTokens()) Screen.Home.route else Screen.Login.route

    /**
     * Очищает все токены и роли.
     * Навигация на Login выполняется в AppNavGraph после вызова этой функции.
     */
    fun logout() {
        tokenStorage.clearAll()
    }
}
