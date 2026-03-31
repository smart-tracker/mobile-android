package com.example.smarttracker.data.local

import android.content.Context
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
 */
@Singleton
class IconCacheManager @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val iconsDir = File(context.filesDir, "activity_icons").also { it.mkdirs() }

    /**
     * Возвращает закэшированный файл если иконка уже скачана, иначе null.
     * Вызов не блокирует поток — только проверяет существование файла.
     */
    fun getCached(typeId: Int): File? =
        File(iconsDir, "$typeId.png").takeIf { it.exists() }

    /**
     * Скачивает иконку по URL и сохраняет в filesDir.
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
            file
        }.getOrNull()
    }
}
