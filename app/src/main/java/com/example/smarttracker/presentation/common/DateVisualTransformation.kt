package com.example.smarttracker.presentation.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Трансформация поля ввода даты: 8-цифровой ввод "DDMMYYYY" → отображение "ДД.ММ.ГГГГ".
 * Точки добавляются визуально, в state хранятся только цифры.
 */
internal class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = buildString {
            for (i in digits.indices) {
                if (i == 2 || i == 4) append('.')
                append(digits[i])
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset == 3 -> 2
                offset <= 5 -> offset - 1
                offset == 6 -> 4
                else -> offset - 2
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
