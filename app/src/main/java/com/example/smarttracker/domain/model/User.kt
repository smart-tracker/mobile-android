package com.example.smarttracker.domain.model

import java.time.LocalDate

data class User(

    val id: Int,                        // user_id в БД

    val firstName: String,              // first_name в БД - заполняется при регистрации
    val lastName: String? = null,       // last_name в БД - заполняется позже в профиле
    val middleName: String? = null,     // middle_name в БД - заполняется позже в профиле

    val username: String,               // nickname в БД

    val email: String,

    val birthDate: LocalDate,           // birth_date в БД

    val gender: Gender,

    // TODO: ждём добавления поля role в таблицу users (Михаил)
    // val role: UserRole,

    // Заполняются позже в профиле - не при регистрации
    val weight: Float? = null,
    val height: Float? = null

    // jwt_session и jwt_reload - не включаем в domain модель
    // Токены идут в AuthResult и сохраняются в EncryptedSharedPreferences
)