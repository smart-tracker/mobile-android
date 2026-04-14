package com.example.smarttracker.data.remote

import com.example.smarttracker.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Тесты Auth Token Flow с MockWebServer.
 *
 * Проверяет поведение OkHttp-интерцептора из AuthModule:
 * - Добавляет "Authorization: Bearer <token>" к каждому запросу
 * - Не добавляет заголовок при пустом токене
 * - Токен читается из TokenStorage в момент каждого запроса (не кешируется)
 *
 * Конструируем тот же интерцептор, что и в di/AuthModule.kt,
 * чтобы тестировать реальную логику без Hilt.
 */
class AuthTokenFlowTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStorage: TokenStorage
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server       = MockWebServer()
        tokenStorage = mock()

        // Дублируем логику интерцептора из AuthModule.kt
        val authInterceptor = Interceptor { chain ->
            val token = tokenStorage.getAccessToken()
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Добавление токена ─────────────────────────────────────────────────────

    @Test
    fun `интерцептор добавляет Authorization Bearer если токен есть`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken()).thenReturn("abc123")

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals("Bearer abc123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `интерцептор не добавляет Authorization если токен null`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken()).thenReturn(null)

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(
            "Authorization не должен быть добавлен при null токене",
            recorded.getHeader("Authorization")
        )
    }

    @Test
    fun `интерцептор не добавляет Authorization если токен пустая строка`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken()).thenReturn("")

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(
            "Authorization не должен быть добавлен при пустом токене",
            recorded.getHeader("Authorization")
        )
    }

    @Test
    fun `интерцептор не добавляет Authorization если токен пробельная строка`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken()).thenReturn("   ")

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(
            "Authorization не должен быть добавлен для blank токена",
            recorded.getHeader("Authorization")
        )
    }

    // ── Токен читается в момент каждого запроса ───────────────────────────────

    @Test
    fun `интерцептор читает токен из TokenStorage при каждом запросе`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        // Первый запрос — токен A
        whenever(tokenStorage.getAccessToken()).thenReturn("token-A", "token-B")

        val url = server.url("/test")
        client.newCall(Request.Builder().url(url).build()).execute()
        client.newCall(Request.Builder().url(url).build()).execute()

        val first  = server.takeRequest().getHeader("Authorization")
        val second = server.takeRequest().getHeader("Authorization")

        assertEquals("Bearer token-A", first)
        assertEquals("Bearer token-B", second)
    }

    // ── Формат заголовка ──────────────────────────────────────────────────────

    @Test
    fun `заголовок имеет формат Bearer пробел токен`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken()).thenReturn("eyJhbGciOiJIUzI1NiJ9.test")

        client.newCall(Request.Builder().url(server.url("/api")).build()).execute()

        val header = server.takeRequest().getHeader("Authorization")
        assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.test", header)
    }
}
