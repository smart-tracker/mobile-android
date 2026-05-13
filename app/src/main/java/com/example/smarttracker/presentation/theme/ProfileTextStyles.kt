package com.example.smarttracker.presentation.theme

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Текстовые стили для экранов профиля (просмотр и редактирование).
 *
 * Поля профиля используют один и тот же 14sp Italic шрифт с двумя цветами:
 * [fieldLabel] — метка (ColorPrimary), [fieldValue] — значение (ColorSecondary),
 * [fieldEmpty] — пустое значение (ColorPrimary 30% alpha).
 *
 * Используем [SpanStyle] там где нужны annotated strings (метка + значение в одном Text),
 * и [TextStyle] для полей ввода BasicTextField.
 */
object ProfileTextStyles {

    /** Стиль метки поля — «Фамилия: », «Рост (см): » и т.д. */
    val fieldLabel = SpanStyle(
        fontFamily = geologicaFontFamilyItalic,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = ColorPrimary,
    )

    /** Стиль заполненного значения поля — бирюзовый. */
    val fieldValue = SpanStyle(
        fontFamily = geologicaFontFamilyItalic,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = ColorSecondary,
    )

    /** Стиль пустого значения поля — «Не указано», приглушённый. */
    val fieldEmpty = SpanStyle(
        fontFamily = geologicaFontFamilyItalic,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = ColorPrimary.copy(alpha = 0.3f),
    )

    /**
     * Стиль для BasicTextField внутри полей редактирования.
     * Цвет передаётся отдельно через [androidx.compose.ui.text.TextStyle.copy].
     */
    val fieldInput = TextStyle(
        fontFamily = geologicaFontFamilyItalic,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    )
}
