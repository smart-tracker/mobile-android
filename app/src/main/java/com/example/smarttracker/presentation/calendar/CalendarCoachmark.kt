package com.example.smarttracker.presentation.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.ColorSecondary
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.workout.activityIconRes
import kotlinx.coroutines.delay

/** Число шагов онбординга: 1 — пинч/навигация, 2 — День, 3 — Неделя, 4 — Месяц. */
internal const val COACHMARK_STEPS = 4

/** Светлый фон объединённого «фото + текст» блока внутри callout. */
private val DemoBlockBg = Color(0xFFF3F5F7)

/**
 * Многошаговый onboarding-coachmark экрана истории тренировок (календаря).
 *
 * По образцу онбординга экрана тренировки (`WorkoutStartScreen.WorkoutCoachmark`),
 * но без spotlight-выреза по реальным контролам: у нового пользователя истории
 * ещё нет и экран показывает лишь один режим за раз, поэтому виды рисуются
 * демо-строками таймлайна прямо в оверлее (те же internal-компоненты пакета —
 * ствол, нод, метка и карточка визуально идентичны реальным).
 *
 * Шаги:
 *  - 0 «Как переключать виды» — анимация пинча (два пальца) над мини-«деревом»
 *    таймлайна, которое переключается между обзорным и детальным видом.
 *  - 1–3 «День»/«Неделя»/«Месяц» — полная строка (ствол + нод + метка + карточка)
 *    и краткая расшифровка полей в едином блоке.
 *
 * «Далее»/«Назад» листают шаги, «Понятно»/тап по затемнению → [onDismiss] (персист).
 */
@Composable
internal fun CalendarCoachmark(
    step: Int,
    stepCount: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLast = step >= stepCount - 1
    val title = when (step) {
        0 -> "Как переключать виды"
        1 -> "Вид «День»"
        2 -> "Вид «Неделя»"
        else -> "Вид «Месяц»"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Затемнение + тап-дисмисс по фону.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onDismiss),
        )

        // 2. Callout-карточка по центру. Кнопки перехватывают тап; тап по пустой
        // области карточки проваливается на фон-дисмисс — как в онбординге тренировки.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Text(
                text = "${step + 1}/$stepCount  $title",
                color = ColorPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            // Контент шага — скроллится, если не влезает (крупная Month-строка
            // + расшифровка на невысоких экранах).
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (step) {
                    0 -> PinchStep()
                    1 -> DayStep()
                    2 -> WeekStep()
                    else -> MonthStep()
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // «Назад» — серая, со второго шага и далее.
                if (step > 0) {
                    TextButton(onClick = onBack) {
                        Text(text = "Назад", color = ColorPrimary.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = if (isLast) onDismiss else onNext) {
                    Text(
                        text = if (isLast) "Понятно" else "Далее",
                        color = ColorPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Шаг 0: пинч над мини-«деревом» ───────────────────────────────────────────

@Composable
private fun ColumnScope.PinchStep() {
    TreePinchDemo()
    Spacer(Modifier.height(12.dp))
    CoachmarkTip("Разведите пальцы — глубже: Месяц → Неделя → День.")
    CoachmarkTip("Сведите пальцы — обзорнее: День → Неделя → Месяц.")
    CoachmarkTip("Тап по карточке открывает период подробнее.")
    Spacer(Modifier.height(10.dp))
    CoachmarkTip("Или переключайте режим табами внизу экрана:")
    Spacer(Modifier.height(6.dp))
    MiniModeTabs()
}

/** Мини-образец сегмент-контрола режимов (для онбординга). */
@Composable
private fun MiniModeTabs() {
    val shape = RoundedCornerShape(6.dp)
    val labels = listOf("День", "Неделя", "Месяц")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(shape)
            .border(1.dp, ColorPrimary, shape),
    ) {
        labels.forEachIndexed { i, label ->
            if (i > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(ColorPrimary.copy(alpha = 0.4f)))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Для примера подсвечен «День».
                    .background(if (i == 0) ColorSecondary else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (i == 0) Color.White else ColorPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Мини-«дерево» таймлайна с анимацией пинча: два кружка-«пальца» циклично
 * сходятся↔расходятся, а само дерево (ствол + ноды + карточки) переключается
 * между обзорным (много кратких периодов) и детальным (крупные карточки) видом.
 */
@Composable
private fun TreePinchDemo() {
    // p: 0 — пальцы сведены (обзорно), 1 — разведены (детально). Плавный ход
    // туда-обратно с паузами на крайних режимах (дать разглядеть каждый вид).
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            p.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
            delay(1100)
            p.animateTo(0f, tween(1300, easing = FastOutSlowInEasing))
            delay(1100)
        }
    }
    val t = p.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DemoBlockBg)
            // Ствол дерева — тонкая вертикальная полоса по центру.
            .drawBehind {
                val w = 6.dp.toPx()
                drawRect(
                    color = ColorPrimary,
                    topLeft = Offset(size.width / 2f - w / 2f, 0f),
                    size = Size(w, size.height),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Обзорное дерево при сведённых пальцах, детальное — при разведённых.
        Box(modifier = Modifier.alpha(1f - t)) {
            MiniTree(rows = listOf(1, 1, 1), currentIndex = 1)
        }
        Box(modifier = Modifier.alpha(t)) {
            MiniTree(rows = listOf(3, 3), currentIndex = -1)
        }

        // Два «пальца» расходятся по диагонали (↙ ↗): смещение по X и Y растёт с t.
        val dx = lerp(10.dp, 58.dp, t)
        val dy = lerp(6.dp, 34.dp, t)
        FingerDot(Modifier.offset(x = -dx, y = dy))
        FingerDot(Modifier.offset(x = dx, y = -dy))
    }
}

/**
 * Мини-дерево: [rows] задаёт число строк и «детальность» каждой (кол-во полосок
 * в карточке). Карточки чередуются слева/справа от ствола; [currentIndex] — строка
 * с активным (акцентным) нодом (−1 = нет).
 */
@Composable
private fun MiniTree(rows: List<Int>, currentIndex: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        rows.forEachIndexed { i, lines ->
            MiniTreeRow(cardOnLeft = i % 2 == 0, isCurrent = i == currentIndex, cardLines = lines)
        }
    }
}

@Composable
private fun MiniTreeRow(cardOnLeft: Boolean, isCurrent: Boolean, cardLines: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (cardOnLeft) MiniCard(cardLines)
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isCurrent) TealAccent else ColorPrimary)
                .border(2.dp, Color.White, RoundedCornerShape(50)),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (!cardOnLeft) MiniCard(cardLines)
        }
    }
}

/** Мини-карточка дерева: цветная полоска слева + [lines] строк-полосок. */
@Composable
private fun MiniCard(lines: Int) {
    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .width(96.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, ColorPrimary, RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height((14 + lines * 8).dp)
                .background(ColorSecondary),
        )
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(lines) {
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(56.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ColorPrimary.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

/** Кружок-«палец»: полупрозрачный акцентный круг с белой обводкой. */
@Composable
private fun FingerDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(50))
            .background(ColorSecondary.copy(alpha = 0.85f))
            .border(2.dp, Color.White, RoundedCornerShape(50)),
    )
}

// ── Шаг 1: День ──────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.DayStep() {
    DemoBlock(rowHeight = 96.dp, label = "08:00 - 08:35", isCurrent = false, card = { DemoDayCard() }) {
        FieldAnnotation(activityIconRes("1"), "Вид (цвет полоски = тип)")
        FieldAnnotation(R.drawable.ic_samples, "Название")
        FieldAnnotation(R.drawable.ic_time, "Длительность")
        FieldAnnotation(R.drawable.ic_distance, "Дистанция, если был GPS; иначе — калории")
        Spacer(Modifier.height(4.dp))
        CoachmarkTip("Тап по карточке → подробная информация о тренировке.")
    }
}

@Composable
private fun DemoDayCard() {
    Row(modifier = Modifier.height(86.dp)) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .timelineCardSurface(TimelineStripShape, activityColorFor(1)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(activityIconRes("1")),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
        }
        TimelineInfoColumn(
            modifier = Modifier.width(TimelineDims.InfoCardWidth).fillMaxHeight(),
        ) {
            InfoRow(R.drawable.ic_samples, "Бег")
            InfoRow(R.drawable.ic_time, "00:35:00")
            InfoRow(R.drawable.ic_distance, "5,20 км")
        }
    }
}

// ── Шаг 2: Неделя ────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.WeekStep() {
    DemoBlock(rowHeight = 110.dp, label = "22.07.26", isCurrent = true, card = { DemoWeekCard() }) {
        FieldAnnotation(activityIconRes("1"), "Виды за день (до 3)")
        FieldAnnotation(R.drawable.ic_time, "Длительность за день")
        FieldAnnotation(R.drawable.ic_distance, "Дистанция")
        FieldAnnotation(R.drawable.ic_kcal, "Калории")
        FieldAnnotation(R.drawable.ic_samples, "Кол-во тренировок")
        Spacer(Modifier.height(4.dp))
        CoachmarkTip("Тап → вид «День».")
    }
}

@Composable
private fun DemoWeekCard() {
    Row(modifier = Modifier.height(110.dp)) {
        Column(
            modifier = Modifier
                .width(36.dp)
                .height(110.dp)
                .timelineCardSurface(TimelineStripShape)
                .padding(vertical = 14.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            listOf(1, 3, 5).forEach { id ->
                TimelineIconBox(
                    iconRes = activityIconRes(id.toString()),
                    bgColor = Color.White,
                    boxSize = 26.dp,
                )
            }
        }
        TimelineInfoColumn(
            modifier = Modifier.width(TimelineDims.InfoCardWidth).height(110.dp),
        ) {
            InfoRow(R.drawable.ic_time, "01:55:00")
            InfoRow(R.drawable.ic_distance, "18,20 км")
            InfoRow(R.drawable.ic_kcal, "980 кКал")
            InfoRow(R.drawable.ic_samples, formatTrainingCount(3))
        }
    }
}

// ── Шаг 3: Месяц ─────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MonthStep() {
    DemoBlock(rowHeight = 160.dp, label = "20.07 - 26.07", isCurrent = true, card = { DemoMonthCard() }) {
        FieldAnnotation(activityIconRes("1"), "Бег 62% — преобладающий вид недели")
        FieldAnnotation(R.drawable.ic_time, "Время за неделю")
        FieldAnnotation(R.drawable.ic_distance, "Дистанция")
        FieldAnnotation(R.drawable.ic_elevation, "Набор высоты")
        FieldAnnotation(R.drawable.ic_kcal, "Калории")
        Spacer(Modifier.height(4.dp))
        CoachmarkTip("В левой полосе — 7 дней недели: значок вида или отдых (сон).")
        CoachmarkTip("Строка сверху — всего тренировок за неделю.")
        CoachmarkTip("Тап → вид «Неделя».")
    }
}

@Composable
private fun DemoMonthCard() {
    Row(modifier = Modifier.height(160.dp)) {
        // Стрип 7 иконок: 5 тренировочных дней + 2 дня отдыха (ic_sleep).
        Column(
            modifier = Modifier
                .width(24.dp)
                .height(160.dp)
                .timelineCardSurface(TimelineStripShape)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start,
        ) {
            val demoDays = listOf(1, null, 3, 1, null, 3, 1)
            demoDays.forEach { typeId ->
                TimelineIconBox(
                    iconRes = if (typeId != null) activityIconRes(typeId.toString()) else R.drawable.ic_sleep,
                    bgColor = if (typeId != null) TealAccent else Color.White,
                    boxSize = 16.dp,
                )
            }
        }
        TimelineInfoColumn(
            modifier = Modifier.width(140.dp).height(160.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Демо-карточка в онбординге живёт в узком callout (уже реальной
            // Month-карточки), полное «N тренировок» тут не помещается —
            // показываем сокращение. В самом календаре (140dp) — полное слово.
            Text(
                text = formatTrainingCount(5),
                style = WorkoutTextStyles.timelineLabelBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 1.dp, horizontal = 5.dp),
            )
            InfoRow(activityIconRes("1"), "- 62.0%")
            InfoRow(R.drawable.ic_time, "04:10:00")
            InfoRow(R.drawable.ic_distance, "38,40 км")
            InfoRow(R.drawable.ic_elevation, "420 м")
            InfoRow(R.drawable.ic_kcal, "2100 кКал")
        }
    }
}

// ── Общие элементы ───────────────────────────────────────────────────────────

/**
 * Единый блок «фото + текст»: полная строка таймлайна (ствол + нод + метка +
 * карточка) сверху и краткая расшифровка полей снизу, на общем светлом фоне.
 */
@Composable
private fun DemoBlock(
    rowHeight: Dp,
    label: String,
    isCurrent: Boolean,
    card: @Composable () -> Unit,
    annotations: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DemoBlockBg)
            .padding(12.dp),
    ) {
        // Фото: настоящая строка таймлайна со стволом, нодом и меткой.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .drawTrunk(),
        ) {
            TimelineRow(
                isCardRight = false,
                isCurrent = isCurrent,
                label = label,
                modifier = Modifier.height(rowHeight),
                card = { TimelineCardWrapper(isCardRight = false) { card() } },
            )
        }
        Spacer(Modifier.height(10.dp))
        annotations()
    }
}

/**
 * Строка расшифровки поля: иконка-якорь (та же, что на карточке) + стрелка-
 * указатель + короткое пояснение. Совпадение иконки связывает подпись с полем.
 */
@Composable
private fun FieldAnnotation(iconRes: Int, text: String, iconSize: Dp = 18.dp) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "→", color = ColorSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = ColorPrimary, fontSize = 14.sp)
    }
}

/** Пункт-совет: маркер «•» + текст (как в онбординге тренировки). */
@Composable
private fun CoachmarkTip(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "•  ", color = ColorPrimary, fontSize = 14.sp)
        Text(text = text, color = ColorPrimary, fontSize = 14.sp)
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Онбординг — пинч")
@Composable
private fun CalendarCoachmarkPinchPreview() {
    SmartTrackerTheme {
        CalendarCoachmark(step = 0, stepCount = COACHMARK_STEPS, onNext = {}, onBack = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, name = "Онбординг — День")
@Composable
private fun CalendarCoachmarkDayPreview() {
    SmartTrackerTheme {
        CalendarCoachmark(step = 1, stepCount = COACHMARK_STEPS, onNext = {}, onBack = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, name = "Онбординг — Неделя")
@Composable
private fun CalendarCoachmarkWeekPreview() {
    SmartTrackerTheme {
        CalendarCoachmark(step = 2, stepCount = COACHMARK_STEPS, onNext = {}, onBack = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, name = "Онбординг — Месяц")
@Composable
private fun CalendarCoachmarkMonthPreview() {
    SmartTrackerTheme {
        CalendarCoachmark(step = 3, stepCount = COACHMARK_STEPS, onNext = {}, onBack = {}, onDismiss = {})
    }
}
