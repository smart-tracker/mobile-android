package com.example.smarttracker.data.cache

import com.example.smarttracker.data.remote.dto.GoalResponseDto
import com.example.smarttracker.data.remote.dto.RoleResponseDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * МОБ-6.5 — In-memory кеш для ролей и целей.
 *
 * Стратегия кеширования:
 * 1. Первый запрос → загрузить с API, сохранить в памяти с timestamp
 * 2. Последующие запросы → вернуть из памяти, если кеш еще валидный (TTL=1 час)
 * 3. После истечения TTL → загрузить заново с API
 *
 * ВАЖНО: Это in-memory кеш. При выходе приложения (kill process) кеш теряется.
 * Для сохранения между сеансами используется SharedPreferences (реализуется отдельно).
 *
 * Синхронизация: использует synchronized блоки для потокобезопасности
 * (хотя в большинстве случаев UI работает в одном потоке).
 */
@Singleton
class RoleGoalCache @Inject constructor() {

    // ── Кеш для доступных ролей (GET /roles) ────────────────────────────────
    private var cachedRoles: List<RoleResponseDto>? = null
    private var rolesTimestamp: Long = 0L

    // ── Кеш для целей (GET /goals) ───────────────────────────────────────────
    // Ключ: roleId (или null для всех целей), Значение: GoalResponseDto
    private val cachedGoals = mutableMapOf<Int?, List<GoalResponseDto>>()
    private val goalsTimestamp = mutableMapOf<Int?, Long>()

    // ── Константы ────────────────────────────────────────────────────────────
    companion object {
        // Время жизни кеша: 1 час (3600 секунд)
        private const val CACHE_TTL_MS = 3600 * 1000L
    }

    // ── Кеш ролей ────────────────────────────────────────────────────────────

    /**
     * Получить кешированные роли, если они еще валидны.
     * Возвращает null, если кеш истек или пуст.
     */
    fun getCachedRoles(): List<RoleResponseDto>? = synchronized(this) {
        if (isRolesCacheValid()) {
            cachedRoles
        } else {
            null
        }
    }

    /**
     * Сохранить роли в кеш с текущим timestamp.
     */
    fun setCachedRoles(roles: List<RoleResponseDto>) = synchronized(this) {
        cachedRoles = roles
        rolesTimestamp = System.currentTimeMillis()
    }

    /**
     * Проверить, валидна ли роль кеш.
     */
    private fun isRolesCacheValid(): Boolean {
        return cachedRoles != null && 
               (System.currentTimeMillis() - rolesTimestamp) < CACHE_TTL_MS
    }

    // ── Кеш целей ────────────────────────────────────────────────────────────

    /**
     * Получить кешированные цели для roleId, если они еще валидны.
     * 
     * @param roleId ID роли (null означает "все цели без фильтра")
     * @return Список целей или null, если кеш истек
     */
    fun getCachedGoals(roleId: Int?): List<GoalResponseDto>? = synchronized(this) {
        if (isGoalsCacheValid(roleId)) {
            cachedGoals[roleId]
        } else {
            null
        }
    }

    /**
     * Сохранить цели в кеш для roleId.
     */
    fun setCachedGoals(roleId: Int?, goals: List<GoalResponseDto>) = synchronized(this) {
        cachedGoals[roleId] = goals
        goalsTimestamp[roleId] = System.currentTimeMillis()
    }

    /**
     * Проверить, валидна ли цель кеш для roleId.
     */
    private fun isGoalsCacheValid(roleId: Int?): Boolean {
        val goals = cachedGoals[roleId]
        val timestamp = goalsTimestamp[roleId] ?: return false
        
        return goals != null && 
               (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS
    }

    // ── Инвалидация кеша ───────────────────────────────────────────────────────

    /**
     * Очистить весь кеш (используется при logout или других критических событиях).
     */
    fun clearCache() = synchronized(this) {
        cachedRoles = null
        rolesTimestamp = 0L
        cachedGoals.clear()
        goalsTimestamp.clear()
    }

    /**
     * Инвалидировать только кеш целей (для синхронизации, если роли изменились).
     */
    fun invalidateGoalsCache() = synchronized(this) {
        cachedGoals.clear()
        goalsTimestamp.clear()
    }
}
