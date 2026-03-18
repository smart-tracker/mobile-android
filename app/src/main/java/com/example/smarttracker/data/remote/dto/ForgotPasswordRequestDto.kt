import com.google.gson.annotations.SerializedName

/**
 * DTO для отправки на запрос initiateForgotPassword (POST /auth/forgot-password).
 * Пользователь вводит email на первом шаге password recovery.
 */
data class ForgotPasswordRequestDto(
    @SerializedName("email")
    val email: String
)
