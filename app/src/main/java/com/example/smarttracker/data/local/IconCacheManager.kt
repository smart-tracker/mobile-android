package com.example.smarttracker.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер локального кэша иконок активностей.
 *
 * Иконки сохраняются в filesDir/activity_icons/ — в отличие от cacheDir,
 * эта директория не очищается Android при нехватке памяти.
 * Файлы удаляются только при "Очистить данные" или деинсталляции приложения.
 *
 * Именование файлов: {typeId}.png — по ID типа, а не по имени,
 * чтобы избежать коллизий при многоязычных названиях.
 *
 * URL-трекинг: [urlPrefs] хранит URL, по которому был скачан текущий файл.
 * Это позволяет обнаружить смену imagePath на бэкенде и перезагрузить иконку.
 */
@Singleton
class IconCacheManager @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val iconsDir = File(context.filesDir, "activity_icons").also { it.mkdirs() }

    /** Хранит маппинг typeId → URL последней успешной загрузки. */
    private val urlPrefs: SharedPreferences =
        context.getSharedPreferences("icon_url_cache", Context.MODE_PRIVATE)

    /**
     * Возвращает закэшированный файл если иконка уже скачана, иначе null.
     * Вызов не блокирует поток — только проверяет существование файла.
     */
    fun getCached(typeId: Int): File? =
        File(iconsDir, "$typeId.png").takeIf { it.exists() }

    /**
     * Возвращает URL, по которому была скачана текущая иконка.
     * Используется для обнаружения смены imagePath на бэкенде.
     */
    fun getDownloadedUrl(typeId: Int): String? = urlPrefs.getString("url_$typeId", null)

    /**
     * Скачивает иконку по URL и сохраняет в filesDir.
     * После успешной записи запоминает URL в [urlPrefs].
     *
     * Suspend-функция — вызывать из корутины.
     * При ошибке сети или записи возвращает null (не бросает исключение),
     * чтобы не прерывать отображение остальных типов.
     *
     * @param typeId числовой ID типа активности — используется как имя файла
     * @param url    полный URL иконки (предоставляется бэкендом в поле image_url)
     * @return       File при успехе, null при любой ошибке
     */
    suspend fun download(typeId: Int, url: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val response = okHttpClient
                .newCall(Request.Builder().url(url).build())
                .execute()
            val bytes = response.body?.bytes()
                ?: return@runCatching null
            val file = File(iconsDir, "$typeId.png")
            file.writeBytes(bytes)
            urlPrefs.edit().putString("url_$typeId", url).apply()
            file
        }.getOrNull()
    }
}
