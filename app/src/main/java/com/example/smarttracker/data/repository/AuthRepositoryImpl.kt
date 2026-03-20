package com.example.smarttracker.data.repository

import android.util.Log
import com.example.smarttracker.data.cache.RoleGoalCache
import com.example.smarttracker.data.local.RoleConfigStorage
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.dto.EmailVerificationDto
import com.example.smarttracker.data.remote.dto.GoalResponseDto
import com.example.smarttracker.data.remote.dto.LoginRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckRequestDto
import com.example.smarttracker.data.remote.dto.ResendEmailDto
import com.example.smarttracker.data.remote.dto.RoleResponseDto
import com.example.smarttracker.data.remote.dto.toDomain
import com.example.smarttracker.data.remote.dto.toDto
import com.example.smarttracker.domain.model.AuthResult
import com.example.smarttracker.domain.model.GoalResponse
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.RegisterResult
import com.example.smarttracker.domain.model.ResendResult
import com.example.smarttracker.domain.model.NicknameCheckResponse
import com.example.smarttracker.domain.model.RoleResponse
import com.example.smarttracker.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * МОБ-2.3 — Реализация контракта AuthRepository.
 *
 * Задача этого класса — перевести вызовы use-case'ов в HTTP-запросы через
 * AuthApiService и при успехе сохранить/обновить токены в TokenStorage.
 *
 * Принцип обработки ошибок:
 * Каждый метод оборачивается в runCatching { }. Это даёт Result<T>:
 * - Result.success(value) — запрос прошёл, value — domain-объект
 * - Result.failure(throwable) — любое исключение (IOException, HttpException и т.д.)
 * ViewModel вызывает result.fold(...) и не пишет try/catch.
 *
 * Почему runCatching работает с suspend-вызовами:
 * runCatching — inline-функция, поэтому блок вставляется прямо в тело
 * suspend-метода. Компилятор видит suspend-контекст и позволяет вызывать
 * другие suspend-функции внутри блока.
 */
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val tokenStorage: TokenStorage,
    private val roleGoalCache: RoleGoalCache,
    private val roleConfigStorage: RoleConfigStorage,
) : AuthRepository {

    /**
     * Шаг 1 регистрации. Токены НЕ сохраняются — пользователь ещё не верифицирован,
     * is_active=false на бэкенде.
     */
    override suspend fun register(request: RegisterRequest): Result<RegisterResult> =
        runCatching { api.register(request.toDto()).toDomain() }

    /**
     * Шаг 2 регистрации. Бэкенд устанавливает is_active=true и возвращает токены.
     * Сохраняем токены сразу — пользователь считается авторизованным.
     *
     * МОБ-6 — После верификации используем сохраненные роли из Step 2 (если были выбраны),
     * если таких нет — загружаем роли пользователя с API для инициализации BottomNavigation.
     * 
     * Стратегия:
     * 1. Если при регистрации была выбрана цель → roles сохранены в RoleConfigStorage ✓
     * 2. Если регистрация без выбора (EXPLORING) → RoleConfigStorage пуста → пытаемся загрузить с API
     * 3. Если API ошибается → роли могут остаться пустыми (нормально для EXPLORING пользователей)
     */
    override suspend fun verifyEmail(email: String, code: String): Result<AuthResult> =
        runCatching {
            val result = api.verifyEmail(EmailVerificationDto(email, code)).toDomain()
            
            // МОБ-6 — Проверить сохраненные роли из Step 2 регистрации
            var roleIds = roleConfigStorage.getSelectedRoles()
            
            // Если ролей не было сохранено (переустановка приложения или регистрация без выбора) —
            // пытаемся загрузить с API
            if (roleIds.isEmpty()) {
                val rolesResult = runCatching {
                    api.getUserRoles(email).map { it.roleId }
                }
                
                rolesResult
                    .onSuccess { roles -> roleIds = roles }
                    .onFailure { error ->
                        // Логируем ошибку, но не блокируем верификацию
                        // Может быть, это EXPLORING пользователь или сервер недоступен
                        Log.w(
                            "AuthRepository",
                            "Failed to load user roles for $email during email verification: ${error.message}"
                        )
                    }
            }
            
            // Сохранить токены И роли (даже если roleIds пуста из-за ошибки или EXPLORING регистрации)
            tokenStorage.saveTokens(result.accessToken, result.refreshToken, roleIds)
            
            result
        }

    /**
     * Повторный запрос кода подтверждения. Токены не затрагиваются.
     */
    override suspend fun resendCode(email: String): Result<ResendResult> =
        runCatching { api.resendCode(ResendEmailDto(email)).toDomain() }

    /**
     * Вход для верифицированных пользователей. Перезаписывает токены
     * (старая сессия заменяется новой).
     *
     * МОБ-6 — При входе загружаем роли пользователя заново (могли измениться в админ-панели).
     * 
     * Выход:
     * - Если загрузка ролей успешна → сохраняем новые роли ✓
     * - Если загрузка ролей ошибается → логируем, сохраняем пустые роли (может быть EXPLORING)
     */
    override suspend fun login(email: String, password: String): Result<AuthResult> =
        runCatching {
            val result = api.login(LoginRequestDto(email, password)).toDomain()
            
            // Загрузить роли пользователя (свежие данные с API)
            val rolesResult = runCatching {
                api.getUserRoles(email).map { it.roleId }
            }
            
            val roleIds = rolesResult
                .onFailure { error ->
                    Log.w(
                        "AuthRepository",
                        "Failed to load user roles for $email during login: ${error.message}"
                    )
                }
                .getOrElse { emptyList() }
            
            tokenStorage.saveTokens(result.accessToken, result.refreshToken, roleIds)
            result
        }

    /**
     * Обновление access token по refresh token.
     * Новая пара токенов заменяет старую в хранилище.
     *
     * Refresh token передаётся как @Query (не @Body) — подтверждено
     * сигнатурой FastAPI-роута (нюанс #7 в CONTEXT.md).
     *
     * ⚠️ ВАЖНО: Роли НЕ перезагружаются — остаются из TokenStorage (предыдущего входа).
     * Причина: refreshToken вызывается часто (при экспирации access token),
     * не нужно каждый раз ходить на getUserRoles.
     * 
     * Если нужна актуальная информация о ролях → вызовите login() повторно.
     * Роли обновляются только при явном входе в систему.
     */
    override suspend fun refreshToken(refreshToken: String): Result<AuthResult> =
        runCatching {
            val result = api.refreshToken(refreshToken).toDomain()
            
            // Сохранить токены, роли остаются из TokenStorage (уже актуальные)
            val currentRoles = tokenStorage.getUserRoles()
            tokenStorage.saveTokens(result.accessToken, result.refreshToken, currentRoles)
            result
        }

    /**
     * Проверка доступности nickname.
     * Не требует авторизации — вызывается на этапе регистрации.
     * Результат не сохраняется в TokenStorage.
     */
    override suspend fun checkNickname(nickname: String): Result<NicknameCheckResponse> =
        runCatching {
            api.checkNickname(NicknameCheckRequestDto(nickname)).toDomain()
        }

    /**
     * МОБ-6.3 — Получение доступных ролей для регистрации.
     * 
     * Сначала проверяет in-memory кеш (RoleGoalCache).
     * Если кеш пуст или истек (TTL=1 час), загружает с API и сохраняет в кеш.
     * 
     * Стратегия кеширования:
     * 1. Роли редко меняются → безопасно кешировать на 1 час
     * 2. Первый запрос может быть медленным, но последующие мгновенные
     * 3. При logout кеш можно инвалидировать через roleGoalCache.clearCache()
     */
    override suspend fun getAvailableRoles(): Result<List<RoleResponse>> =
        // DEPRECATED: Теперь используем getGoalsByRole(null) для загрузки всех целей
        // и автоматического определения ролей от целей
        Result.failure(NotImplementedError("getAvailableRoles deprecated - use getGoalsByRole(null)"))

    /**
     * МОБ-6.4 — Получение целей, опционально отфильтрованных по role_id.
     * 
     * Сначала проверяет in-memory кеш (RoleGoalCache).
     * Если кеш пуст или истек (TTL=1 час), загружает с API и сохраняет в кеш.
     * 
     * Стратегия кеширования:
     * 1. Цели кешируются отдельно для каждого roleId (или null для всех целей)
     * 2. Первый запрос может быть медленным, но последующие мгновенные
     * 3. При выборе новой роли кеш обновляется в следующем запросе
     * 
     * @param roleId ID роли для фильтрации (null = все цели)
     */
    override suspend fun getGoalsByRole(roleId: Int?): Result<List<GoalResponse>> =
        runCatching {
            // Проверить in-memory кеш
            val cachedGoals = roleGoalCache.getCachedGoals(roleId)
            if (cachedGoals != null) {
                return@runCatching cachedGoals.map { it.toDomain() }
            }
            
            // Кеш пуст → загрузить с API (всегда загружаем ВСЕ цели)
            val goalsDto = api.getGoals()
            
            // Сохранить в кеш
            roleGoalCache.setCachedGoals(roleId, goalsDto)
            
            // Вернуть domain объекты
            goalsDto.map { it.toDomain() }
        }
}
