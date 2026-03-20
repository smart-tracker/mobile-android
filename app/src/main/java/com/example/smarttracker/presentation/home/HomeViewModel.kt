package com.example.smarttracker.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.domain.model.NavigationConfig
import com.example.smarttracker.domain.model.RoleConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * МОБ-6 — ViewModel для главного экрана приложения.
 *
 * Задача:
 * 1. Получить роли пользователя из TokenStorage (сохранены при верификации email)
 * 2. Генерировать конфигурацию BottomNavigation на основе ролей
 * 3. Предоставить эту конфигурацию UI
 *
 * Data flow:
 * - После входа/регистрации: TokenStorage содержит roleIds
 * - При навигации на Home: HomeViewModel загружает roleIds
 * - RoleConfig.getNavigationConfig(roleIds) генерирует BottomNav элементы
 * - HomeScreen отображает BottomNavigation с доступными экранами
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tokenStorage: TokenStorage,
) : ViewModel() {

    private val _navigationConfig = MutableStateFlow<NavigationConfig>(NavigationConfig(emptyList(), emptyList()))
    val navigationConfig: StateFlow<NavigationConfig> = _navigationConfig.asStateFlow()

    private val _userRoles = MutableStateFlow<List<Int>>(emptyList())
    val userRoles: StateFlow<List<Int>> = _userRoles.asStateFlow()

    init {
        initializeNavigation()
    }

    /**
     * МОБ-6 — Инициализировать навигацию на основе сохраненных ролей.
     * Вызывается при создании ViewModel (при первом открытии HomeScreen).
     */
    private fun initializeNavigation() {
        viewModelScope.launch {
            // Получить роли пользователя из TokenStorage
            val userRoles = tokenStorage.getUserRoles()
            _userRoles.value = userRoles

            // Генерировать конфигурацию BottomNavigation на основе ролей
            val navConfig = RoleConfig.getNavigationConfig(userRoles)
            _navigationConfig.update { navConfig }
        }
    }

    /**
     * Получить наименование роли для отладки/логирования.
     */
    fun getRoleName(roleId: Int): String = RoleConfig.getRoleName(roleId)

    /**
     * Проверить, имеет ли пользователь конкретную роль.
     */
    fun hasRole(roleId: Int): Boolean = RoleConfig.hasRole(_userRoles.value, roleId)
}
