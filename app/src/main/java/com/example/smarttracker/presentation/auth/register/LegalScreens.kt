/**
 * Юридические экраны регистрации: Условия использования и Политика конфиденциальности.
 *
 * ⚠️ ДО РЕЛИЗА:
 *  1. Заполнить плейсхолдеры «[…— заполнить до релиза]»: реквизиты оператора ПДн
 *     и контактный email (без них политика не соответствует 152-ФЗ).
 *  2. Показать тексты юристу — это черновик, структурированный по 152-ФЗ,
 *     а не проверенный юридический документ.
 *  3. Каноническая версия политики должна быть опубликована по публичному URL
 *     (BACK_REQ.md BR-15) — RuStore требует ссылку в карточке приложения;
 *     текст здесь и на сервере должен совпадать.
 *  4. Вопрос юристу: валидация регистрации допускает возраст от 6 лет —
 *     согласие на обработку ПДн несовершеннолетних дают законные представители.
 *     Как оформлять (поднять мин. возраст / чекбокс представителя / текст в
 *     политике) — решить до релиза.
 */
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

Приложение SmartTracker предназначено для записи и анализа спортивных тренировок с использованием GPS.

Используя приложение, Вы соглашаетесь с настоящими Условиями использования. Пожалуйста, внимательно прочитайте их перед началом работы.

1. Использование приложения

Приложение предоставляется «как есть». Показатели тренировок (дистанция, темп, калории) рассчитываются алгоритмически и могут отличаться от фактических значений; они носят справочный характер и не являются медицинскими показаниями. Перед началом занятий спортом при наличии ограничений по здоровью проконсультируйтесь с врачом.

2. Учётная запись

Для использования приложения требуется регистрация. Вы отвечаете за сохранность своих учётных данных и за действия, совершённые под Вашей учётной записью. Регистрация возможна только с адресами электронной почты российских почтовых сервисов (требование Федерального закона № 149-ФЗ).

3. Персональные данные

Персональные данные, включая данные геолокации, обрабатываются в соответствии с Политикой конфиденциальности, которая является неотъемлемой частью настоящих Условий.

4. Интеллектуальная собственность

Содержимое приложения (тексты, графика, логотипы, изображения) защищено авторским правом и не может использоваться без разрешения правообладателя. Картографические данные предоставляются на условиях лицензии ODbL (© участники OpenStreetMap).

5. Ограничение ответственности

Оператор не несёт ответственности за технические сбои, недоступность сервера, потерю данных, а также за последствия использования справочных показателей тренировок.

6. Изменение условий

Условия могут быть изменены. Продолжение использования приложения после публикации изменений означает согласие с новой редакцией.

Дата последнего обновления: 7 июля 2026 г.
Контакт: [email поддержки — заполнить до релиза]
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
                text = """Настоящая Политика конфиденциальности разработана в соответствии с Федеральным законом от 27.07.2006 № 152-ФЗ «О персональных данных» и определяет порядок обработки и защиты персональных данных пользователей мобильного приложения SmartTracker.

1. Оператор персональных данных

Оператор: [ФИО / наименование оператора — заполнить до релиза]
Контакт по вопросам персональных данных: [email — заполнить до релиза]

2. Состав обрабатываемых персональных данных

2.1. Данные учётной записи: имя (фамилия и отчество — по желанию), никнейм, адрес электронной почты, дата рождения, пол.

2.2. Данные профиля (указываются по желанию): рост, вес, фотография.

2.3. Данные геолокации: GPS-треки тренировок (координаты, высота, скорость, отметки времени). Записываются ТОЛЬКО во время активной тренировки, запущенной Вами вручную, и прекращают записываться при её завершении.

2.4. Производные данные тренировок: дистанция, длительность, темп, расчётные калории.

2.5. Технические данные: модель устройства, версия операционной системы, идентификаторы установки приложения, отчёты о сбоях и обезличенная статистика использования.

3. Цели обработки

- регистрация и аутентификация пользователя;
- запись, хранение и отображение Ваших тренировок, включая маршрут на карте;
- расчёт статистики тренировок (дистанция, темп, калории — для расчёта используются вес, рост, возраст и пол);
- восстановление доступа к учётной записи;
- обеспечение работоспособности и безопасности сервиса.

4. Правовые основания

Обработка осуществляется на основании Вашего согласия, выражаемого при регистрации (отметка о принятии условий), а также в целях исполнения соглашения об использовании приложения (ст. 6 Закона № 152-ФЗ).

5. Хранение и защита

5.1. Данные хранятся на серверах, расположенных на территории Российской Федерации (требование ст. 18 Закона № 152-ФЗ о локализации).

5.2. Передача данных между приложением и сервером осуществляется по защищённому соединению (HTTPS). Токены доступа хранятся на устройстве в зашифрованном хранилище.

5.3. Данные обрабатываются до момента удаления учётной записи.

6. Передача третьим лицам и поручение обработки

Персональные данные не продаются и не передаются третьим лицам, за исключением:
- технических данных (п. 2.5), обработка которых поручена оператором сервису аналитики AppMetrica (ООО «ЯНДЕКС», Российская Федерация) в соответствии с ч. 3 ст. 6 Закона № 152-ФЗ — для диагностики сбоев и улучшения приложения; данные геолокации и GPS-треки в аналитику НЕ передаются;
- случаев, предусмотренных законодательством Российской Федерации (по требованию уполномоченных государственных органов).

7. Права субъекта персональных данных

Вы вправе:
- получать сведения об обработке Ваших данных;
- уточнять (исправлять) данные — через экран редактирования профиля;
- отозвать согласие и потребовать удаления данных — функция «Удалить аккаунт» в редактировании профиля удаляет учётную запись и связанные с ней данные, включая GPS-треки;
- обжаловать действия оператора в Роскомнадзоре или в суде.

8. Изменение политики

Актуальная редакция публикуется в приложении. Продолжение использования после публикации изменений означает согласие с новой редакцией.

Дата последнего обновления: 7 июля 2026 г.
""",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ColorPrimary,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}
