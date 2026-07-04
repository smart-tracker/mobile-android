package com.example.smarttracker.data.remote

import com.example.smarttracker.data.local.TokenStorage
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
 * Проверяет РЕАЛЬНЫЙ интерцептор [buildAuthInterceptor] (тот же код,
 * что использует AuthModule — раньше тест держал инлайн-копию логики,
 * которая молча разъехалась бы с продакшеном при правке):
 * - Добавляет "Authorization: Bearer <token>" к запросам на хост API
 * - Не добавляет заголовок при пустом токене
 * - НЕ добавляет заголовок на чужой хост (токен не утекает на внешние
 *   URL картинок, которые грузят Coil/IconCacheManager этим же клиентом)
 * - Токен читается из TokenStorage в момент каждого запроса (не кешируется)
 */
class AuthTokenFlowTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStorage: TokenStorage
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server       = MockWebServer()
        server.start()
        tokenStorage = mock()

        // Реальный интерцептор из data/remote/AuthInterceptor.kt.
        // apiHost = хост MockWebServer — «свои» запросы идут именно туда.
        client = OkHttpClient.Builder()
            .addInterceptor(buildAuthInterceptor(tokenStorage, server.url("/").host))
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

    // ── Host-check: токен не утекает на чужой хост ────────────────────────────

    @Test
    fun `интерцептор не добавляет Authorization на чужой хост`() {
        // Клиент с apiHost, отличным от хоста MockWebServer:
        // запрос на сервер = «внешний CDN» с точки зрения интерцептора.
        val foreignClient = OkHttpClient.Builder()
            .addInterceptor(buildAuthInterceptor(tokenStorage, "api.example.com"))
            .build()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken()).thenReturn("secret-token")

        foreignClient.newCall(Request.Builder().url(server.url("/icon.png")).build()).execute()

        assertNull(
            "Bearer-токен не должен уходить на хост, отличный от API",
            server.takeRequest().getHeader("Authorization")
        )
    }

    @Test
    fun `интерцептор добавляет Authorization только при совпадении хоста`() {
        // Совпадающий хост (обычный client из setUp) — заголовок есть;
        // проверяем что host-check не сломал основной сценарий.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken()).thenReturn("secret-token")

        client.newCall(Request.Builder().url(server.url("/api")).build()).execute()

        assertEquals("Bearer secret-token", server.takeRequest().getHeader("Authorization"))
    }

    // ── Отказоустойчивость: сбой хранилища не роняет запрос ──────────────────

    @Test
    fun `сбой хранилища токенов не роняет запрос — уходит без Authorization`() {
        // EncryptedSharedPreferences может кинуть RuntimeException (AEADBadTagException,
        // KeyStoreException). Не-IOException из интерцептора OkHttp 4 перебрасывает
        // на dispatcher-потоке → краш процесса. Интерцептор обязан деградировать
        // до запроса без заголовка (штатный 401), а не пробрасывать исключение.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        whenever(tokenStorage.getAccessToken())
            .thenThrow(RuntimeException("Keystore corrupted"))

        val response = client.newCall(
            Request.Builder().url(server.url("/api")).build()
        ).execute()

        assertEquals(200, response.code)
        assertNull(
            "При сбое хранилища запрос должен уйти без Authorization",
            server.takeRequest().getHeader("Authorization")
        )
    }
}
