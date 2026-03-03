package com.example.smarttracker.domain.model

data class AuthResult(

    // Токены - обязательная часть ответа
    val accessToken: String,            // jwt_session в БД
    val refreshToken: String,           // jwt_reload в БД

    // TODO: уточнить у Романа - возвращает ли сервер данные пользователя
    // сразу в ответе на регистрацию или только токены?
    // val user: User?
)