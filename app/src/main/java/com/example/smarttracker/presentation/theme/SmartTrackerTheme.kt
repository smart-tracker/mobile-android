package com.example.smarttracker.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R

// Geologica Font Family
val geologicaFontFamily = FontFamily(
    Font(R.font.geologica_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.geologica_regular, FontWeight.Normal, FontStyle.Normal),
)

// Для italic используем настоящий italic файл
val geologicaFontFamilyItalic = FontFamily(
    Font(R.font.geologica_italic, FontWeight.Light, FontStyle.Italic),
)

// Custom Typography with Geologica
val SmartTrackerTypography = Typography(
    // 32px Italic - Название на странице входа (Login Screen Title)
    titleLarge = TextStyle(
        fontFamily = geologicaFontFamilyItalic,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        fontStyle = FontStyle.Italic,
        letterSpacing = (-0.5).sp
    ),
    
    // 20px Light - Заголовки страниц регистрации (RegisterScreen titles)
    headlineSmall = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        fontStyle = FontStyle.Normal
    ),
    
    // 20px Light - Кнопки "войти", "создать аккаунт", "продолжить", "отправить код повторно"
    labelLarge = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        fontStyle = FontStyle.Normal
    ),
    
    // 16px Light - Плейсхолдеры, цели использования, выбор пола, вторичные надписи
    bodyMedium = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        fontStyle = FontStyle.Normal
    ),
    
    // Дополнительный стиль для 16px Light (для совместимости)
    bodySmall = TextStyle(
        fontFamily = geologicaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        fontStyle = FontStyle.Normal
    ),
)

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()

@Composable
fun SmartTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = SmartTrackerTypography,
        content = content
    )
}
