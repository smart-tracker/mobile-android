package com.example.smarttracker.data.remote.dto

/**
 * DTO ответа GET /auth/allowed-email-domains (BR-4, 149-ФЗ).
 *
 * Формат: {"domains": ["autorambler.ru", "bk.ru", ..., "yandex.ru"]}
 * Сервер отдаёт тот же список, которым сам проверяет домен при регистрации
 * (единый источник — рассинхрон проверки и выдачи невозможен, см. BACK_REQ BR-2/BR-4).
 *
 * [domains] nullable защитно: неожиданное тело не должно ронять клиент —
 * репозиторий трактует null/пустой список как сбой и падает на fallback.
 */
data class AllowedEmailDomainsResponseDto(
    val domains: List<String>?,
)
