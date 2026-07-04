package com.example.smarttracker.data.remote

import com.example.smarttracker.data.local.TokenStorage
import okhttp3.Interceptor

/**
 * Фабрика auth-интерцептора: добавляет `Authorization: Bearer <token>`
 * к запросам, идущим на хост API.
 *
 * Вынесено из AuthModule в отдельную функцию, чтобы AuthTokenFlowTest
 * тестировал реальную логику, а не её копию (раньше тест дублировал
 * интерцептор инлайном — при правке они бы молча разъехались).
 *
 * Проверка хоста обязательна: тот же OkHttpClient используется Coil
 * (фото профиля) и IconCacheManager (иконки активностей), а URL картинок
 * приходят с сервера (`image_path`). Без проверки хоста JWT уходил бы
 * на любой внешний CDN, который бэкенд однажды укажет в image_path, —
 * утечка токена третьей стороне.
 *
 * @param apiHost хост API (например "runtastic.gottland.ru") — только
 *   на него добавляется заголовок; остальные хосты получают запрос как есть.
 */
fun buildAuthInterceptor(tokenStorage: TokenStorage, apiHost: String): Interceptor =
    Interceptor { chain ->
        val token = tokenStorage.getAccessToken()
        val request = if (!token.isNullOrBlank() && chain.request().url.host == apiHost) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
