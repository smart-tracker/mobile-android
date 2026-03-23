package com.example.smarttracker.presentation.auth.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarttracker.presentation.theme.ColorBackground
import com.example.smarttracker.presentation.theme.ColorPrimary

/**
 * Экран "Условия использования".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Условия использования",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = ColorPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = ColorPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorBackground,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Условия использования SmartTracker",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = """Добро пожаловать в SmartTracker!

Наше мобильное приложение предоставляет услуги для отслеживания Вашей физической активности и здоровья. 

Используя приложение, Вы согласны с данными Условиями использования. Пожалуйста, внимательно прочитайте все условия перед использованием приложения.

1. Использование приложения

Вы используете приложение SmartTracker на собственный риск. Мы не несем ответственность за любые прямые или косвенные убытки, вызванные использованием приложения.

2. Конфиденциальность данных

Ваши личные данные обрабатываются в соответствии с нашей Политикой конфиденциальности. Мы не передаем Ваши данные третьим лицам без Вашего согласия.

3. Интеллектуальная собственность

Все содержимое приложения, включая текст, графику, логотипы и изображения, защищено авторским правом и не может быть использовано без нашего разрешения.

4. Ограничение ответственности

SmartTracker не несет ответственность за любые технические сбои, потерю данных или прерывание услуги.

5. Изменение условий

Мы оставляем право изменять данные условия в любое время. Ваше продолжение использования приложения после изменения условий означает Ваше согласие с ними.

Дата последнего обновления: 18 марта 2026 г.
Вопросы? Свяжитесь с нами: support@smarttracker.com
""",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

/**
 * Экран "Политика конфиденциальности".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        containerColor = ColorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Политика конфиденциальности",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = ColorPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = ColorPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorBackground,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Политика конфиденциальности SmartTracker",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = """SmartTracker уважает Вашу приватность и обеспечивает защиту Ваших личных данных.

1. Сбор информации

Мы собираем следующую информацию:
- Персональные данные (имя, адрес электронной почты, дата рождения)
- Данные о физической активности
- Целевые показатели здоровья
- Технические данные устройства (тип устройства, операционная система)

2. Использование информации

Ваша информация используется для:
- Предоставления и улучшения услуг приложения
- Анализа статистики использования
- Отправки обновлений и уведомлений
- Поддержания безопасности и предотвращения мошенничества

3. Защита данных

Мы используем современные методы шифрования для защиты Ваших данных. Ваша информация хранится на защищенных серверах.

4. Передача данных третьим лицам

Мы НЕ продаем и НЕ передаем Вашу личную информацию третьим лицам, кроме случаев, когда это требуется законом или с Вашего прямого согласия.

5. Cookies и аналитика

Приложение может использовать технологии отслеживания для анализа использования и улучшения пользовательского опыта.

6. Контакт с нами

Если у Вас есть вопросы о данной политике или о том, как мы обрабатываем Ваши данные, пожалуйста, свяжитесь с нами:

Email: privacy@smarttracker.com

7. Изменение политики

Мы оставляем право изменять данную политику. Изменения вступают в силу после опубликования на приложении.

Дата последнего обновления: 18 марта 2026 г.
""",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}
