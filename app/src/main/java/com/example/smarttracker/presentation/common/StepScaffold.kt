package com.example.smarttracker.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorPlaceholder
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorWhite

/**
 * Универсальный Scaffold для пошаговых auth-экранов (регистрация, восстановление пароля).
 *
 * Объединяет дублировавшиеся RegisterScaffold и GenericStepScaffold.
 * Содержит: TopAppBar, нижнюю кнопку действия с индикатором загрузки,
 * скроллируемый контент с заголовком шага.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepScaffold(
    title: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    isLoading: Boolean,
    isNextEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler { onBack() }
    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorBackground,
                ),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = UiTokens.BottomActionHorizontalPadding,
                        vertical = UiTokens.BottomActionVerticalPadding,
                    ),
            ) {
                Button(
                    onClick = onNext,
                    enabled = !isLoading && isNextEnabled,
                    modifier = Modifier
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
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = nextLabel,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = UiTokens.ScreenHorizontalPadding,
                    vertical = UiTokens.ContentVerticalPadding,
                ),
        ) {
            Spacer(Modifier.height(UiTokens.StepTopSpacer))
            StepTitle(title)
            Spacer(Modifier.height(UiTokens.SectionSpacing))
            content()
        }
    }
}

/**
 * Заголовок шага: ── Текст ──
 * Линии по бокам, текст по центру.
 */
@Composable
fun StepTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = ColorPrimary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = ColorPrimary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = ColorPrimary,
        )
    }
}
