package com.example.smarttracker.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarttracker.presentation.theme.ColorPlaceholder
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorWhite

/**
 * Основная кнопка действия: заливка ColorPrimary, текст белый, индикатор загрузки внутри.
 *
 * Используется в [StepScaffold] и на экранах где нет [StepScaffold] (напр. [LoginScreen]).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading && isEnabled,
        modifier = modifier
            .fillMaxWidth()
            .height(UiTokens.ButtonHeight),
        shape = RoundedCornerShape(UiTokens.CornerRadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = ColorPrimary,
            contentColor = ColorWhite,
            disabledContainerColor = ColorPlaceholder,
            disabledContentColor = ColorWhite,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(UiTokens.ButtonLoadingIndicatorSize),
                color = ColorWhite,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
