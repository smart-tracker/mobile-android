import com.google.gson.annotations.SerializedName

/**
 * DTO для отправки на запрос resendResetCode (POST /auth/resend-reset-code).
 * Пользователь нажал "Отправить код повторно" на третьем шаге.
 */
data class ResendResetCodeRequestDto(
    @SerializedName("email")
    val email: String
)
