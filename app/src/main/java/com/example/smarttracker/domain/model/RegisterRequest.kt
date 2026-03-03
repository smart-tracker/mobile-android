package com.example.smarttracker.domain.model

import java.time.LocalDate

data class RegisterRequest(

    // Шаг 1/4 - Личные данные
    val firstName: String,
    val username: String,
    val birthDate: LocalDate,
    val gender: Gender,

    // Шаг 2/4 - Цель использования
    // TODO: уточнить у Артёма - сохранять ли purpose в БД или только для определения роли?
    val purpose: UserPurpose,

    // Шаг 3/4 - Безопасность и доступ
    val email: String,
    val password: String,

    // Только для клиентской валидации - на сервер НЕ отправляется
    val confirmPassword: String,

    // TODO: ждём добавления поля role в таблицу users (Михаил)
    // val role: UserRole = purpose.toUserRole()
)