import com.google.gson.annotations.SerializedName

/**
 * DTO для отправки на запрос resetPassword (POST /auth/reset-password).
 * Пользователь вводит код верификации и новый пароль на третьем шаге.
 */
data class ResetPasswordRequestDto(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("code")
    val code: String,
    
    @SerializedName("new_password")
    val newPassword: String,
    
    @SerializedName("confirm_password")
    val confirmPassword: String
)
