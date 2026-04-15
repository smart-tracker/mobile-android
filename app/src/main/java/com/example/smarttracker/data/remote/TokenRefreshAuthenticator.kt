package com.example.smarttracker.data.remote

import android.util.Log
import com.example.smarttracker.data.local.TokenStorage
import com.example.smarttracker.data.remote.dto.AuthResponseDto
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OkHttp Authenticator — автоматически обновляет access token при получении HTTP 401.
 *
 * Почему Authenticator, а не Interceptor:
 * OkHttp вызывает Authenticator именно при 401-ответе и ожидает новый Request
 * для повторной попытки. Interceptor мог бы делать то же самое, но Authenticator —
 * семантически правильный способ и имеет встроенную защиту от зацикливания.
 *
 * Почему отдельный OkHttpClient для refresh-запроса:
 * Основной OkHttpClient использует этот Authenticator, который обращается к AuthApiService,
 * который создан через Retrofit с тем же OkHttpClient — циклическая зависимость в Hilt.
 * Решение: refresh-запрос делается напрямую через отдельный простой OkHttpClient
 * (без interceptors, без authenticator), минуя Retrofit полностью.
 *
 * Поток при 401:
 *   1. Проверить, что это не сам refresh-запрос (избежать рекурсии)
 *   2. Проверить счётчик повторов (не более 1)
 *   3. Взять refresh token из TokenStorage
 *   4. POST /auth/refresh?refresh_token=... (синхронно через refreshClient)
 *   5. Успех → сохранить новую пару токенов, вернуть исходный запрос с новым Bearer
 *   6. Ошибка → signalSessionExpired() (принудительный выход + UI-сигнал), вернуть null
 */
@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    @Named("baseUrl") private val baseUrl: String,
) : Authenticator {

    /**
     * Отдельный OkHttpClient без auth-interceptor и без authenticator.
     * Используется только для вызова /auth/refresh, чтобы разорвать циклическую
     * зависимость Hilt: OkHttpClient → Authenticator → OkHttpClient.
     */
    private val refreshClient = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val TAG = "TokenRefreshAuth"
    }

    /**
     * @Synchronized предотвращает гонку при двух одновременных 401-ответах.
     *
     * Без синхронизации: оба потока читают одинаковый refreshToken → первый обновляет,
     * второй отправляет уже невалидный old-refreshToken → сервер возвращает 401 →
     * сессия уничтожается и пользователь принудительно выходит из аккаунта.
     *
     * С @Synchronized: второй поток ждёт завершения первого, затем читает уже
     * обновлённый accessToken из tokenStorage и строит повторный запрос без refresh.
     */
    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Если сам refresh-запрос получил 401 → оба токена недействительны, выход.
        if (response.request.url.encodedPath.contains("auth/refresh")) {
            Log.w(TAG, "Refresh-запрос вернул 401 — сессия полностью истекла, выход")
            tokenStorage.signalSessionExpired()
            return null
        }

        // priorResponse цепочка показывает сколько раз уже пробовали.
        // Если был хотя бы один повтор — прекратить.
        if (responseCount(response) >= 2) {
            Log.w(TAG, "Уже была повторная попытка после refresh — прекращаем")
            return null
        }

        val refreshToken = tokenStorage.getRefreshToken() ?: run {
            Log.w(TAG, "Refresh token отсутствует — пользователь не авторизован")
            return null
        }

        Log.d(TAG, "Access token истёк (401), пробуем обновить через refresh token")

        // Строим URL: baseUrl/auth/refresh?refresh_token=...
        // trimEnd('/') защита от двойного слэша если BASE_URL заканчивается на '/'
        val url = "${baseUrl.trimEnd('/')}/auth/refresh".toHttpUrl()
            .newBuilder()
            .addQueryParameter("refresh_token", refreshToken)
            .build()

        val refreshRequest = Request.Builder()
            .url(url)
            // POST без тела — FastAPI принимает refresh_token как @Query param
            .post("".toRequestBody(null))
            .build()

        // use {} гарантирует закрытие соединения в connection pool при любом исходе.
        // non-local return из authenticate() работает внутри use{} т.к. use — inline-функция.
        val refreshResponse = try {
            refreshClient.newCall(refreshRequest).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Сетевая ошибка при обновлении токена: ${e.message}")
            return null
        }

        val body: String = refreshResponse.use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401 || resp.code == 403) {
                    Log.w(TAG, "Refresh вернул ${resp.code} — refresh token невалиден, завершаем сессию")
                    tokenStorage.signalSessionExpired()
                } else {
                    Log.w(TAG, "Refresh вернул ${resp.code} — токены сохраняем, logout не выполняем")
                }
                return null
            }
            resp.body?.string() ?: run {
                Log.e(TAG, "Пустое тело ответа от refresh-эндпоинта")
                return null
            }
        }

        val authDto = try {
            gson.fromJson(body, AuthResponseDto::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось разобрать ответ refresh: ${e.message}")
            return null
        }

        // Сохраняем обновлённые токены; роли не меняются (обновляются только при login)
        val currentRoles = tokenStorage.getUserRoles()
        tokenStorage.saveTokens(authDto.accessToken, authDto.refreshToken, currentRoles)
        Log.d(TAG, "Токены успешно обновлены, повторяем исходный запрос")

        // Повторяем исходный запрос с новым access token
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${authDto.accessToken}")
            .build()
    }

    /**
     * Считает число уже выполненных повторных попыток через priorResponse-цепочку.
     * OkHttp сохраняет историю: каждый повтор добавляет ещё один priorResponse.
     */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
