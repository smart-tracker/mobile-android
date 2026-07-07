package com.example.smarttracker.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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

// App Colors (reusable across screens)
val ColorPrimary = androidx.compose.ui.graphics.Color(0xFF0A1928)  // Dark navy
val ColorSecondary = androidx.compose.ui.graphics.Color(0xFF4DACA7) // Mint/teal accent
// Тёмный шаг того же мятного оттенка — для тонких линий графиков на белом фоне.
// ColorSecondary на белом даёт контраст 2.7:1 (< 3:1 WCAG для нетекстовой графики),
// этот шаг — 4.0:1. Крупные заливки и бары остаются на ColorSecondary.
val ColorChartLine = androidx.compose.ui.graphics.Color(0xFF2E8C86)
val ColorPlaceholder = androidx.compose.ui.graphics.Color(0xFF525760)
val ColorLink = androidx.compose.ui.graphics.Color(0xFF0066CC)
val ColorBackground = androidx.compose.ui.graphics.Color.White
val ColorWhite = androidx.compose.ui.graphics.Color.White

/** Красный для деструктивных действий (удалить фото, удалить аккаунт) и ошибок GPS. */
val ColorDestructive = androidx.compose.ui.graphics.Color(0xFFFC3F1D)

// ── Workout-specific colors ───────────────────────────────────────────────────
/** Зелёный фон GPS-бейджа когда сигнал получен. */
val ColorGpsActive = androidx.compose.ui.graphics.Color(0xFF4CAF50)
/** Красный фон GPS-бейджа когда сигнал потерян / разрешения нет. */
val ColorGpsInactive = ColorDestructive
/** Серый фон/обводка полей поиска и других «второстепенных» полей ввода. */
val ColorFieldFill = androidx.compose.ui.graphics.Color(0xFFD9D9D9)

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

/** Настройка темы Material3 для всего приложения. */
private val LightColorScheme = lightColorScheme()

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
