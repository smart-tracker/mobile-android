package com.example.smarttracker.data.local

/**
 * МОБ-6 — Контракт для сохранения выбранных пользователем ролей.
 *
 * Ролевая модель:
 * 1. На Step 2 регистрации пользователь выбирает роли из API (GET /roles)
 * 2. Эти роли сохраняются здесь с timestamp для отслеживания
 * 3. Выбранные роли передаются в payload регистрации на сервер
 * 4. После верификации email — используем сохраненные роли для UI
 * 5. При logout — очищаем сохраненные роли
 *
 * Отличие от TokenStorage.getUserRoles():
 * - TokenStorage.getUserRoles() — роли из API (/role/user_roles)
 * - RoleConfigStorage — выбранные пользователем роли на регистрации
 *
 * Оба источника важны для полноты истории и синхронизации.
 */
interface RoleConfigStorage {

    /**
     * Сохранить выбранные роли пользователем на этапе регистрации.
     * 
     * @param roleIds ID выбранных ролей (например [1, 2] для Athlete+Trainer)
     */
    fun saveSelectedRoles(roleIds: List<Int>)

    /**
     * Получить ранее сохраненные роли или пустой список.
     */
    fun getSelectedRoles(): List<Int>

    /**
     * Удалить сохраненные роли (выход из аккаунта).
     */
    fun clearSelectedRoles()
}
