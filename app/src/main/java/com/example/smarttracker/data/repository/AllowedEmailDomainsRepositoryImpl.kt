package com.example.smarttracker.data.repository

import com.example.smarttracker.domain.repository.AllowedEmailDomainsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [AllowedEmailDomainsRepository] с захардкоженным списком
 * российских почтовых доменов (требование 149-ФЗ).
 *
 * TODO(backend): заменить на загрузку с бэкенда, когда появится эндпоинт
 *   GET /auth/allowed-email-domains → {"domains": ["yandex.ru", ...]}
 *   План реализации:
 *   1. Запрос при старте приложения (или лениво при первом обращении).
 *   2. Кэш в памяти + DataStore (переживает перезапуск без сети).
 *   3. [HARDCODED_RUSSIAN_DOMAINS] остаётся как fallback при недоступности сети.
 *   Зеркальная проверка на бэкенде в POST /auth/register обязательна —
 *   клиентская валидация обходится прямым HTTP-запросом.
 */
@Singleton
class AllowedEmailDomainsRepositoryImpl @Inject constructor() : AllowedEmailDomainsRepository {

    override suspend fun getAllowedDomains(): Set<String> = HARDCODED_RUSSIAN_DOMAINS

    companion object {
        /**
         * Три крупнейшие российские почтовые группы. Официального перечня
         * «российских почт» в законе нет — сервис определяет список сам.
         * Корпоративные/вузовские домены РФ сюда не входят — это осознанное
         * ограничение до появления серверного списка.
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
