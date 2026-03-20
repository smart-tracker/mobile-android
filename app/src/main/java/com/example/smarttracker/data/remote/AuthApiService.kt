package com.example.smarttracker.data.remote

import com.example.smarttracker.data.remote.dto.AuthResponseDto
import com.example.smarttracker.data.remote.dto.EmailVerificationDto
import com.example.smarttracker.data.remote.dto.GoalResponseDto
import com.example.smarttracker.data.remote.dto.LoginRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckResponseDto
import com.example.smarttracker.data.remote.dto.RegisterRequestDto
import com.example.smarttracker.data.remote.dto.RegisterResultDto
import com.example.smarttracker.data.remote.dto.ResendCodeResponseDto
import com.example.smarttracker.data.remote.dto.ResendEmailDto
import com.example.smarttracker.data.remote.dto.RoleDto
import com.example.smarttracker.data.remote.dto.RoleResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit-интерфейс для эндпоинтов авторизации.
 *
 * Retrofit при сборке автоматически сгенерирует реализацию этого интерфейса.
 * Все методы — suspend-функции: вызываются из корутин, не блокируют поток.
 *
 * BASE_URL задаётся при создании экземпляра Retrofit в AuthModule (МОБ-5.1).
 */
interface AuthApiService {

    /**
     * Регистрация нового пользователя.
     * Бэкенд создаёт запись со статусом is_active=false и отправляет код на email.
     */
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): RegisterResultDto

    /**
     * Подтверждение email по 6-значному коду.
     * При успехе бэкенд устанавливает is_active=true и возвращает пару токенов.
     */
    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body request: EmailVerificationDto): AuthResponseDto

    /**
     * Повторная отправка кода подтверждения.
     * Доступно не чаще чем раз в 2 минуты (логика на стороне бэкенда).
     */
    @POST("auth/resend-code")
    suspend fun resendCode(@Body request: ResendEmailDto): ResendCodeResponseDto

    /**
     * Вход для уже подтверждённых пользователей (is_active=true).
     * Возвращает новую пару access/refresh токенов.
     */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    /**
     * Обновление access token по refresh token.
     *
     * Нюанс: FastAPI без явного Body(...) трактует строковый параметр
     * POST-роута как query param, а не тело запроса. Поэтому @Query, не @Body.
     * Подтверждено сигнатурой: `async def refresh_token(refresh_token: str, ...)`.
     */
    @POST("auth/refresh")
    suspend fun refreshToken(@Query("refresh_token") token: String): AuthResponseDto

    /**
     * Проверка доступности nickname.
     * Возвращает объект с полями: is_available (Boolean), message (String).
     */
    @POST("auth/check-nickname")
    suspend fun checkNickname(@Body request: NicknameCheckRequestDto): NicknameCheckResponseDto

    /**
     * МОБ-6.2 — Получение ролей пользователя.
     * API endpoint: GET /role/user_roles?email=...
     * Возвращает список ролей, назначенных пользователю (из таблицы user_and_role).
     * Используется после верификации email для инициализации BottomNavigation.
     *
     * @param email Email пользователя (используется для поиска в БД)
     * @return Список ролей с полями role_id и name
     */
    @GET("role/user_roles")
    suspend fun getUserRoles(@Query("email") email: String): List<RoleDto>

    /**
     * МОБ-6 — Получение целей для регистрации (Step 2).
     * API endpoint: GET /goal/
     * Возвращает ВСЕ доступные цели. Каждая цель автоматически привязана к роли (id_role).
     * Пользователь выбирает цель, и роль определяется на основе id_role.
     * Кешируется сроком на 1 час.
     *
     * @return Список целей с полями goal_id, description, id_role
     */
    @GET("goal/")
    suspend fun getGoals(): List<GoalResponseDto>
}
