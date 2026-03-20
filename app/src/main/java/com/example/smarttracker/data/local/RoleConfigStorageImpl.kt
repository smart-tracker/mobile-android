package com.example.smarttracker.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * МОБ-6 — Реализация RoleConfigStorage на базе SharedPreferences.
 *
 * Использует обычный SharedPreferences (не EncryptedSharedPreferences),
 * потому что roleIds — это просто ID (не чувствительные данные).
 * Для чувствительных данных (токены) используется TokenStorage с шифрованием.
 */
class RoleConfigStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RoleConfigStorage {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun saveSelectedRoles(roleIds: List<Int>) {
        prefs.edit().putString(KEY_SELECTED_ROLE_IDS, roleIds.joinToString(",")).apply()
    }

    override fun getSelectedRoles(): List<Int> {
        val rolesString = prefs.getString(KEY_SELECTED_ROLE_IDS, null)
                ?: return emptyList()

        return if (rolesString.isEmpty()) {
            emptyList()
        } else {
            rolesString
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
        }
    }

    override fun clearSelectedRoles() {
        prefs.edit().remove(KEY_SELECTED_ROLE_IDS).apply()
    }

    companion object {
        private const val PREFS_NAME = "role_config_prefs"
        private const val KEY_SELECTED_ROLE_IDS = "selected_role_ids"
    }
}
