package com.example.smarttracker.data.remote.dto

import android.util.Log
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.User
import com.google.gson.annotations.SerializedName
import java.time.LocalDate

/**
 * DTO ответа GET /user_info/user/.
 *
 * Возвращает профиль текущего авторизованного пользователя на основе Bearer-токена.
 * Поля [weight] и [height] nullable — пользователь мог не заполнить профиль.
 *
 * @param firstName имя
 * @param lastName фамилия, nullable
 * @param middleName отчество, nullable
 * @param birthDate дата рождения в формате ISO 8601 ("YYYY-MM-DD")
 * @param weight масса тела в кг, nullable
 * @param height рост в см, nullable
 * @param gender пол: "male" или "female"
 * @param nickname никнейм пользователя (поле username в domain)
 */
data class UserInfoResponseDto(
    @SerializedName("first_name")  val firstName: String,
    @SerializedName("last_name")   val lastName: String?,
    @SerializedName("middle_name") val middleName: String?,
    @SerializedName("birth_date")  val birthDate: String,
    val weight: Float?,
    val height: Float?,
    val gender: String,
    val nickname: String,
)

/**
 * Маппинг DTO → domain-модель [User].
 *
 * [birthDate] парсится через [LocalDate.parse] — ISO 8601 ("YYYY-MM-DD")
 * поддерживается нативно при minSdk=26 без desugaring.
 *
 * [gender] — строка "male"/"female" → [Gender]; неизвестные значения
 * считаются MALE во избежание краша (лучше неточный расчёт, чем сбой).
 *
 * [birthDate] оборачивается в runCatching: при неожиданном формате logcat выдаст
 * диагностику и тренировка продолжит работу с EPOCH-датой (возраст ~55 лет).
 */
fun UserInfoResponseDto.toDomain(): User = User(
    id         = 0,                          // ID не возвращается этим эндпоинтом
    firstName  = firstName,
    lastName   = lastName,
    middleName = middleName,
    username   = nickname,
    email      = "",                         // не возвращается этим эндпоинтом
    birthDate  = runCatching { LocalDate.parse(birthDate) }.getOrElse { e ->
        Log.e("UserInfoDto", "Не удалось распарсить birth_date='$birthDate': ${e.message}")
        LocalDate.EPOCH   // fallback: возраст ~55 лет, calories не null
    },
    gender     = when (gender.lowercase()) {
        "female" -> Gender.FEMALE
        else     -> Gender.MALE
    },
    weight = weight,
    height = height,
)
