package com.example.smarttracker.domain.model

/**
 * Конфигурация элемента BottomNavigationBar.
 * Каждый элемент привязан к конкретным ролям пользователя.
 *
 * @param id Уникальный идентификатор (используется для отслеживания выбора)
 * @param label Название кнопки (отображается в UI)
 * @param icon Material Design Icon ID (например "home", "people", "fitness_center")
 * @param route Маршрут экрана для навигации
 * @param requiredRoles Роли, для которых этот элемент доступен
 */
data class BottomNavItem(
    val id: String,
    val label: String,
    val icon: String,  // Material Icon ID
    val route: String,
    val requiredRoles: List<Int>
)

/**
 * Конфигурация навигации приложения в зависимости от ролей пользователя.
 * Генерируется на основе набора ролей, полученных после регистрации/авторизации.
 *
 * @param roleIds Список ID ролей пользователя
 * @param bottomNavItems Доступные кнопки в BottomNavigation
 * @param drawerItems Опциональный список элементов для Drawer (если будет реализован)
 */
data class NavigationConfig(
    val roleIds: List<Int>,
    val bottomNavItems: List<BottomNavItem>,
    val drawerItems: List<DrawerItem>? = null
)

/**
 * Элемент Drawer меню (для будущего использования).
 */
data class DrawerItem(
    val id: String,
    val label: String,
    val icon: String,  // Material Icon ID
    val route: String,
    val requiredRoles: List<Int>
)
