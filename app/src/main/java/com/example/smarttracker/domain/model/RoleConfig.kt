package com.example.smarttracker.domain.model

/**
 * МОБ-6.1 — Конфигурация ролей и динамической навигации.
 *
 * Маппинг между ролями пользователя и доступными экранами в BottomNavigation.
 * При регистрации пользователь может выбрать несколько целей,
 * каждая цель привязана к роли (role_id). Роли определяют:
 * - Какие кнопки видны в BottomNavigation
 * - Какой интерфейс показывается (HomeScreen для ATHLETE отличается от HomeScreen для TRAINER)
 *
 * Примеры:
 * - Пользователь выбрал "Я спортсмен" → role_id = 1 (ATHLETE)
 *   BottomNav: [Главная] [Тренировки] [Профиль]
 *
 * - Пользователь выбрал "Я спортсмен" + "Я тренер" → role_ids = [1, 2]
 *   BottomNav: [Главная] [Тренировки] [Спортсмены] [Профиль]
 *
 * Иконки используют Material Design Icons (com.google.android.material.icons).
 * Отображение иконок происходит в BottomNavigation компоненте Compose.
 */
object RoleConfig {
    // ── Константы ролей (соответствуют таблице БД roles) ────────────────────
    const val ROLE_ATHLETE = 1         // "sportsman" в БД
    const val ROLE_TRAINER = 2         // "trainer" в БД
    const val ROLE_CLUB_OWNER = 3      // "club_organizer" в БД

    /**
     * Генерирует конфигурацию BottomNavigation на основе ролей пользователя.
     * Если у пользователя несколько ролей, объединяются все доступные экраны.
     *
     * Порядок кнопок в BottomNav:
     * 1. Главная (для всех)
     * 2. Специфичные для ролей (в порядке: ATHLETE → TRAINER → CLUB_OWNER)
     * 3. Профиль (для всех)
     *
     * @param roleIds Список ID ролей пользователя (например [1, 2])
     * @return NavigationConfig с доступными BottomNav элементами
     */
    fun getNavigationConfig(roleIds: List<Int>): NavigationConfig {
        val bottomNavItems = mutableListOf<BottomNavItem>()

        // ── Главная (всегда доступна) ──────────────────────────────────────
        bottomNavItems.add(
            BottomNavItem(
                id = "home",
                label = "Главная",
                icon = "home",  // Material Icon ID (отображается через Icon Compose)
                route = "home",
                requiredRoles = listOf(ROLE_ATHLETE, ROLE_TRAINER, ROLE_CLUB_OWNER)
            )
        )

        // ── Роль: ATHLETE (Спортсмен) ──────────────────────────────────────
        if (ROLE_ATHLETE in roleIds) {
            bottomNavItems.add(
                BottomNavItem(
                    id = "my_workouts",
                    label = "Тренировки",
                    icon = "fitness_center",  // Material Icon ID
                    route = "workouts",
                    requiredRoles = listOf(ROLE_ATHLETE)
                )
            )
        }

        // ── Роль: TRAINER (Тренер) ──────────────────────────────────────────
        if (ROLE_TRAINER in roleIds) {
            bottomNavItems.add(
                BottomNavItem(
                    id = "my_athletes",
                    label = "Спортсмены",
                    icon = "people",  // Material Icon ID
                    route = "athletes",
                    requiredRoles = listOf(ROLE_TRAINER)
                )
            )
        }

        // ── Роль: CLUB_OWNER (Владелец клуба) ──────────────────────────────
        if (ROLE_CLUB_OWNER in roleIds) {
            bottomNavItems.add(
                BottomNavItem(
                    id = "my_club",
                    label = "Клуб",
                    icon = "groups",  // Material Icon ID
                    route = "club",
                    requiredRoles = listOf(ROLE_CLUB_OWNER)
                )
            )
        }

        // ── Профиль (всегда доступен) ──────────────────────────────────────
        bottomNavItems.add(
            BottomNavItem(
                id = "profile",
                label = "Профиль",
                icon = "account_circle",  // Material Icon ID
                route = "profile",
                requiredRoles = listOf(ROLE_ATHLETE, ROLE_TRAINER, ROLE_CLUB_OWNER)
            )
        )

        return NavigationConfig(
            roleIds = roleIds,
            bottomNavItems = bottomNavItems
        )
    }

    /**
     * Преобразует role_id в читаемое имя роли (для отладки и логирования).
     */
    fun getRoleName(roleId: Int): String = when (roleId) {
        ROLE_ATHLETE -> "Спортсмен"
        ROLE_TRAINER -> "Тренер"
        ROLE_CLUB_OWNER -> "Владелец клуба"
        else -> "Неизвестная роль ($roleId)"
    }

    /**
     * Проверить, имеет ли пользователь конкретную роль.
     */
    fun hasRole(roleIds: List<Int>, roleId: Int): Boolean = roleId in roleIds

    /**
     * Получить все доступные роли (для dropdown в админ-панели, если понадобится).
     */
    fun getAllRoles(): List<Pair<Int, String>> = listOf(
        ROLE_ATHLETE to getRoleName(ROLE_ATHLETE),
        ROLE_TRAINER to getRoleName(ROLE_TRAINER),
        ROLE_CLUB_OWNER to getRoleName(ROLE_CLUB_OWNER)
    )
}
