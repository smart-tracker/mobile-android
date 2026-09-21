package com.example.smarttracker.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.domain.repository.AllowedEmailDomainsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * DataStore-файл кэша серверного списка доменов. Делегат на Context гарантирует
 * один экземпляр на процесс (паттерн SettingsStorageImpl). Инжектится в
 * репозиторий через @Named-провайдер в AuthModule — так тесты подставляют
 * DataStore на временном файле без Robolectric.
 */
internal val Context.allowedEmailDomainsDataStore by preferencesDataStore(name = "allowed_email_domains")

/**
 * Реализация [AllowedEmailDomainsRepository]: серверный список почтовых доменов
 * для регистрации (GET /auth/allowed-email-domains, BR-4, 149-ФЗ) с кэшем.
 *
 * Порядок источников:
 * 1. In-memory кэш успешного сетевого ответа — живёт до конца процесса
 *    (вызовов мало: init RegisterViewModel + сабмит регистрации, TTL не нужен);
 * 2. Сеть — список нормализуется (trim + lowercase) и сохраняется в DataStore;
 * 3. DataStore — последний успешный серверный ответ (переживает перезапуск:
 *    офлайн-старт видит актуальный список, даже если APK-хардкод устарел);
 * 4. [HARDCODED_RUSSIAN_DOMAINS] — зашитый fallback на случай первого запуска
 *    без сети.
 *
 * Неудачный результат (сбой сети, пустой/кривой ответ) в память НЕ мемоизируется —
 * следующий вызов снова попробует сеть. Контракт интерфейса «не бросает и
 * возвращает непустое множество» соблюдается на всех ветках.
 */
@Singleton
class AllowedEmailDomainsRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    @Named("allowedEmailDomains") private val dataStore: DataStore<Preferences>,
) : AllowedEmailDomainsRepository {

    private val mutex = Mutex()

    /** Успешно загруженный с сервера список; только он мемоизируется. */
    @Volatile
    private var memoryCache: Set<String>? = null

    override suspend fun getAllowedDomains(): Set<String> {
        memoryCache?.let { return it }
        return mutex.withLock {
            // Повторная проверка: параллельный вызов мог уже загрузить список,
            // пока мы ждали замок.
            memoryCache?.let { return@withLock it }

            val fresh = fetchFromServer()
            if (fresh != null) {
                memoryCache = fresh
                persist(fresh)
                return@withLock fresh
            }
            readPersisted() ?: HARDCODED_RUSSIAN_DOMAINS
        }
    }

    /**
     * Запрос списка с сервера. null при любом сбое (сеть, HTTP-ошибка, пустой
     * или отсутствующий массив — пустой список заблокировал бы ВСЕ регистрации,
     * трактуем его как некорректный ответ, а не как «доменов нет»).
     */
    private suspend fun fetchFromServer(): Set<String>? = try {
        api.getAllowedEmailDomains().domains
            ?.mapNotNull { raw -> raw.trim().lowercase().takeIf { it.isNotEmpty() } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e // отмену корутины не глотаем
    } catch (e: Exception) {
        Log.w(TAG, "Список доменов с сервера недоступен, используем кэш/fallback: " +
            "${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /** Сохранение в DataStore; сбой записи не критичен (останется старый кэш). */
    private suspend fun persist(domains: Set<String>) {
        runCatching { dataStore.edit { it[KEY_DOMAINS] = domains } }
            .onFailure { Log.w(TAG, "Не удалось сохранить кэш доменов: ${it.message}") }
    }

    /** Последний сохранённый серверный список; null если кэша нет или он пуст/битый. */
    private suspend fun readPersisted(): Set<String>? =
        runCatching { dataStore.data.first()[KEY_DOMAINS]?.takeIf { it.isNotEmpty() } }
            .onFailure { Log.w(TAG, "Не удалось прочитать кэш доменов: ${it.message}") }
            .getOrNull()

    companion object {
        private const val TAG = "AllowedEmailDomains"

        private val KEY_DOMAINS = stringSetPreferencesKey("domains")

        /**
         * Зашитый fallback: три крупнейшие российские почтовые группы.
         * Официального перечня «российских почт» в законе нет — сервис
         * определяет список сам. Канонический источник теперь на сервере
         * (app/core/email_domains.py бэкенда); этот список должен ему
         * зеркально соответствовать на момент сборки APK.
         */
        val HARDCODED_RUSSIAN_DOMAINS: Set<String> = setOf(
            // Яндекс
            "yandex.ru", "ya.ru", "yandex.com", "narod.ru",
            // VK / Mail.ru Group
            "mail.ru", "bk.ru", "list.ru", "inbox.ru", "internet.ru", "vk.com",
            // Rambler
            "rambler.ru", "lenta.ru", "autorambler.ru", "myrambler.ru", "ro.ru",
        )
    }
}
