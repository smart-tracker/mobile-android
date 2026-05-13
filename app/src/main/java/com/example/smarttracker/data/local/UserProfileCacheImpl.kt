package com.example.smarttracker.data.local

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Реализация [UserProfileCache] на базе EncryptedSharedPreferences.
 *
 * Хранится в отдельном файле "user_profile_prefs" (не "secure_prefs") —
 * токены и профиль изолированы, их можно очищать независимо.
 *
 * Nullable поля (lastName, middleName, weight, height) хранятся как строки.
 * Отсутствие ключа = null. Это позволяет отличить «не заполнено» от «не загружено».
 *
 * [LocalDate] сериализуется как ISO-8601 ("YYYY-MM-DD") — поддерживается
 * нативно при minSdk=26 через LocalDate.parse().
 *
 * [Gender] сериализуется как имя enum: "MALE" / "FEMALE".
 */
class UserProfileCacheImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserProfileCache {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "user_profile_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(user: User) {
        prefs.edit()
            .putInt(KEY_ID, user.id)
            .putString(KEY_FIRST_NAME,   user.firstName)
            .putString(KEY_USERNAME,     user.username)
            .putString(KEY_EMAIL,        user.email)
            .putString(KEY_BIRTH_DATE,   user.birthDate.toString())   // ISO-8601
            .putString(KEY_GENDER,       user.gender.name)
            // Nullable: если null — убираем ключ; если не null — сохраняем строкой
            .apply {
                if (user.lastName   != null) putString(KEY_LAST_NAME,   user.lastName)
                else                         remove(KEY_LAST_NAME)

                if (user.middleName != null) putString(KEY_MIDDLE_NAME, user.middleName)
                else                         remove(KEY_MIDDLE_NAME)

                if (user.weight     != null) putString(KEY_WEIGHT, user.weight.toString())
                else                         remove(KEY_WEIGHT)

                if (user.height     != null) putString(KEY_HEIGHT, user.height.toString())
                else                         remove(KEY_HEIGHT)

                if (user.photoUrl   != null) putString(KEY_PHOTO_URL, user.photoUrl)
                else                         remove(KEY_PHOTO_URL)
            }
            .apply()
    }

    override fun get(): User? {
        // Кэш считается пустым, если обязательного поля нет в хранилище
        if (!prefs.contains(KEY_FIRST_NAME)) return null

        return try {
            User(
                id         = prefs.getInt(KEY_ID, 0),
                firstName  = prefs.getString(KEY_FIRST_NAME,  null) ?: return null,
                lastName   = prefs.getString(KEY_LAST_NAME,   null),
                middleName = prefs.getString(KEY_MIDDLE_NAME, null),
                username   = prefs.getString(KEY_USERNAME,    null) ?: return null,
                email      = prefs.getString(KEY_EMAIL,       null) ?: "",
                birthDate  = LocalDate.parse(prefs.getString(KEY_BIRTH_DATE, null) ?: return null),
                gender     = Gender.valueOf(prefs.getString(KEY_GENDER, null) ?: return null),
                weight     = prefs.getString(KEY_WEIGHT,    null)?.toFloatOrNull(),
                height     = prefs.getString(KEY_HEIGHT,    null)?.toFloatOrNull(),
                photoUrl   = prefs.getString(KEY_PHOTO_URL, null),
            )
        } catch (e: Exception) {
            // При любой ошибке парсинга — считаем кэш невалидным
            Log.w(TAG, "Кэш профиля повреждён, будет перезагружен: ${e.message}")
            null
        }
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "UserProfileCache"

        private const val KEY_ID          = "id"
        private const val KEY_FIRST_NAME  = "first_name"
        private const val KEY_LAST_NAME   = "last_name"
        private const val KEY_MIDDLE_NAME = "middle_name"
        private const val KEY_USERNAME    = "username"
        private const val KEY_EMAIL       = "email"
        private const val KEY_BIRTH_DATE  = "birth_date"
        private const val KEY_GENDER      = "gender"
        private const val KEY_WEIGHT      = "weight"
        private const val KEY_HEIGHT      = "height"
        private const val KEY_PHOTO_URL   = "photo_url"
    }
}
