package com.example.smarttracker.data.remote

import com.example.smarttracker.data.local.TokenStorage
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit-тесты TokenRefreshAuthenticator — отказоустойчивость при сбое хранилища.
 *
 * authenticate() выполняется на OkHttp-потоке: необработанное исключение из
 * TokenStorage (повреждённые EncryptedSharedPreferences, глюк Keystore) роняет
 * процесс. Authenticator обязан деградировать до `return null` (запрос завершится
 * штатным 401), а при сбое записи новых токенов — всё равно вернуть повторный
 * запрос с новым access-токеном.
 *
 * Refresh-эндпоинт мокается MockWebServer'ом: authenticator создаёт собственный
 * OkHttpClient и ходит по baseUrl из конструктора.
 */
class TokenRefreshAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStorage: TokenStorage
    private lateinit var authenticator: TokenRefreshAuthenticator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStorage  = mock()
        authenticator = TokenRefreshAuthenticator(tokenStorage, server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Собирает фиктивный 401-ответ на запрос к произвольному эндпоинту API. */
    private fun build401Response(path: String = "/training/history"): Response {
        val request = Request.Builder().url(server.url(path)).build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }

    // ── Сбой хранилища при чтении refresh-токена ─────────────────────────────

    @Test
    fun `сбой чтения refresh-токена возвращает null без исключения`() {
        whenever(tokenStorage.getRefreshToken())
            .thenThrow(RuntimeException("Keystore corrupted"))

        val result = authenticator.authenticate(null, build401Response())

        assertNull("authenticate обязан вернуть null, а не пробросить исключение", result)
    }

    // ── Сбой хранилища при сохранении новых токенов ──────────────────────────

    @Test
    fun `сбой сохранения новых токенов не мешает повторному запросу`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"new-access","refresh_token":"new-refresh","token_type":"bearer"}"""
            )
        )
        whenever(tokenStorage.getRefreshToken()).thenReturn("old-refresh")
        whenever(tokenStorage.getUserRoles())
            .thenThrow(RuntimeException("Keystore corrupted"))

        val result = authenticator.authenticate(null, build401Response())

        // Новый access-токен рабочий: повторный запрос должен состояться,
        // даже если refresh-токен не удалось сохранить (цена — logout при
        // следующем истечении access).
        assertNotNull("Повторный запрос должен быть построен несмотря на сбой записи", result)
        assertEquals("Bearer new-access", result?.header("Authorization"))
    }

    // ── Штатный отказ: refresh вернул 401 → сессия завершается ───────────────

    @Test
    fun `401 на refresh завершает сессию без повторного запроса`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        whenever(tokenStorage.getRefreshToken()).thenReturn("expired-refresh")

        val result = authenticator.authenticate(null, build401Response())

        assertNull(result)
        verify(tokenStorage).signalSessionExpired()
        verify(tokenStorage, never()).saveTokens(any(), any(), anyOrNull())
    }

    // ── Штатный отказ: 5xx на refresh → без logout ───────────────────────────

    @Test
    fun `5xx на refresh не завершает сессию`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        whenever(tokenStorage.getRefreshToken()).thenReturn("valid-refresh")

        val result = authenticator.authenticate(null, build401Response())

        assertNull(result)
        verify(tokenStorage, never()).signalSessionExpired()
    }
}
