package com.example.smarttracker.data.remote

import com.example.smarttracker.data.remote.dto.AuthResponseDto
import com.example.smarttracker.data.remote.dto.EmailVerificationDto
import com.example.smarttracker.data.remote.dto.ForgotPasswordRequestDto
import com.example.smarttracker.data.remote.dto.ForgotPasswordResponseDto
import com.example.smarttracker.data.remote.dto.ActivityTypeDto
import com.example.smarttracker.data.remote.dto.GoalResponseDto
import com.example.smarttracker.data.remote.dto.LoginRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckRequestDto
import com.example.smarttracker.data.remote.dto.NicknameCheckResponseDto
import com.example.smarttracker.data.remote.dto.RegisterRequestDto
import com.example.smarttracker.data.remote.dto.RegisterResultDto
import com.example.smarttracker.data.remote.dto.ResendCodeResponseDto
import com.example.smarttracker.data.remote.dto.ResendEmailDto
import com.example.smarttracker.data.remote.dto.ResendResetCodeRequestDto
import com.example.smarttracker.data.remote.dto.ResendResetCodeResponseDto
import com.example.smarttracker.data.remote.dto.ResetPasswordRequestDto
import com.example.smarttracker.data.remote.dto.ResetPasswordResponseDto
import com.example.smarttracker.data.remote.dto.RoleDto
import com.example.smarttracker.data.remote.dto.RoleResponseDto
import com.example.smarttracker.data.remote.dto.UserInfoResponseDto
import com.example.smarttracker.data.remote.dto.VerifyResetCodeResponseDto
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
     * Инициация восстановления пароля.
     * Отправляет код подтверждения на email пользователя.
     */
    @POST("password-reset/request")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): ForgotPasswordResponseDto

    /**
     * Проверка кода подтверждения для сброса пароля.
     */
    @POST("password-reset/verify-code")
    suspend fun verifyResetCode(@Body request: EmailVerificationDto): VerifyResetCodeResponseDto

    /**
     * Повторная отправка кода для восстановления пароля.
     */
    @POST("password-reset/resend-verify-code")
    suspend fun resendResetCode(@Body request: ResendResetCodeRequestDto): ResendResetCodeResponseDto

    /**
     * Завершение восстановления пароля.
     * Проверяет код и устанавливает новый пароль.
     */
    @POST("password-reset/confirm")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto): ResetPasswordResponseDto

    /**
     * МОБ-6.3 — Получение всех доступных ролей.
     */
    @GET("role/")
    suspend fun getRoles(): List<RoleResponseDto>

    /**
     * МОБ-6.2 — Получение ролей пользователя.
     * API endpoint: GET /role/user_roles
     * Авторизация через Bearer токен (OkHttp-интерцептор добавляет заголовок автоматически).
     * Email-параметр убран — бэкенд определяет пользователя по токену.
     *
     * @return Список ролей с полями role_id и name
     */
    @GET("role/user_roles")
    suspend fun getUserRoles(): List<RoleDto>

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

    /**
     * Получение доступных типов активности.
     * API endpoint: GET /training/types_activity
     * Возвращает список типов с id, name и опциональным image_url.
     * image_url будет добавлен бэкендом позже — пока всегда null.
     */
    @GET("training/types_activity")
    suspend fun getActivityTypes(): List<ActivityTypeDto>

    /**
     * Получение профиля текущего пользователя по Bearer-токену.
     *
     * Возвращает weight/height для расчёта калорий методом MET.
     * Поля nullable — пользователь мог не заполнить профиль.
     *
     * Путь подтверждён через openapi.json: GET /user/
     * (предыдущий вариант user_info/user/ возвращал 404)
     */
    @GET("user/")
    suspend fun getUserInfo(): UserInfoResponseDto
}
