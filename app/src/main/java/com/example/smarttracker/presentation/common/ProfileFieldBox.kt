package com.example.smarttracker.presentation.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarttracker.presentation.theme.ColorPrimary

/**
 * Контейнер поля профиля: полная ширина, фиксированная высота, рамка ColorPrimary.
 *
 * Используется для полей просмотра ([ProfileScreen]) и редактирования ([ProfileEditScreen]).
 * Вызывающий добавляет `.clickable` и контент через [content].
 */
@Composable
fun ProfileFieldBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(UiTokens.ProfileFieldHeight)
            .border(1.dp, ColorPrimary, RoundedCornerShape(UiTokens.ProfileFieldCornerRadius)),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}
