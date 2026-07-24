package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.WorkoutTextStyles
import com.example.smarttracker.presentation.theme.geologicaFontFamily

// ── Fling с повышенным трением для timeline-списков ─────────────────────────

/**
 * `FlingBehavior` на `exponentialDecay` с увеличенным [frictionMultiplier]
 * (дефолт платформы = 1). Fling короче доезжает после отпускания, но само
 * движение остаётся естественным (не «вязким», как при урезании velocity).
 * Применяется к LazyColumn всех трёх timeline-view — на длинной ленте периодов
 * нативный fling пролетал слишком далеко.
 */
@Composable
internal fun rememberSnappyFling(frictionMultiplier: Float = 2.2f): FlingBehavior {
    val decay = remember(frictionMultiplier) {
        exponentialDecay<Float>(frictionMultiplier = frictionMultiplier)
    }
    return remember(decay) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (kotlin.math.abs(initialVelocity) <= 1f) return initialVelocity
                var lastValue = 0f
                var leftoverVelocity = initialVelocity
                AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
                    .animateDecay(decay) {
                        val delta = value - lastValue
                        val consumed = scrollBy(delta)
                        lastValue = value
                        leftoverVelocity = velocity
                        // Дошли до края списка — прекращаем анимацию.
                        if (kotlin.math.abs(delta - consumed) > 0.5f) cancelAnimation()
                    }
                return leftoverVelocity
            }
        }
    }
}

// ── Modifier-расширения таймлайна ────────────────────────────────────────────

/**
 * Стандартная «поверхность» карточки таймлайна:
 * рамка [TimelineDims.BorderThickness] [TrunkColor] + clip + фон.
 *
 * Используется и для стрипов (любой [TimelineStripShape]), и для инфо-блоков
 * ([TimelineInfoShape]), и для одиночной полоски тренировки в Day view.
 */
internal fun Modifier.timelineCardSurface(
    shape: Shape,
    background: Color = Color.White,
): Modifier = this
    .border(TimelineDims.BorderThickness, TrunkColor, shape)
    .clip(shape)
    .background(background)

/**
 * Рисует вертикальный ствол ([TimelineDims.TrunkWidth] [TrunkColor]) по горизонтальному центру.
 * Используется как фон контейнера в [TrainingHistoryScreen].
 */
internal fun Modifier.drawTrunk(): Modifier = this.drawBehind {
    val trunkWidthPx = TimelineDims.TrunkWidth.toPx()
    drawRect(
        color = TrunkColor,
        topLeft = Offset(size.width / 2f - trunkWidthPx / 2f, 0f),
        size = Size(trunkWidthPx, size.height),
    )
}

// ── Нод дерева ────────────────────────────────────────────────────────────────

/**
 * Нод таймлайна:
 *  - ic_active_node (ColorSecondary) — текущий период в Week/Month view
 *  - ic_common_node (ColorPrimary)   — обычный нод
 */
@Composable
internal fun TrunkNode(isCurrent: Boolean) {
    val res = if (isCurrent) R.drawable.ic_active_node else R.drawable.ic_common_node
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.size(TimelineDims.NodeColumnWidth),
    )
}

// ── Базовая строка таймлайна ──────────────────────────────────────────────────

/**
 * Строка таймлайна: левая половина / нод (32dp) / правая половина.
 * Обе половины прижимают контент к стволу:
 *  - левая: [CenterEnd]  → карточка/метка у правого края (рядом со стволом)
 *  - правая: [CenterStart] → карточка/метка у левого края (рядом со стволом)
 * Ствол НЕ рисуется здесь — он рисуется [Modifier.drawTrunk] в [TrainingHistoryScreen].
 */
@Composable
internal fun TimelineRow(
    isCardRight: Boolean,
    isCurrent: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    card: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!isCardRight && card != null) card()
            else if (isCardRight) PeriodLabel(label, isCurrent)
        }

        Box(
            modifier = Modifier.width(TimelineDims.NodeColumnWidth).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            TrunkNode(isCurrent)
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isCardRight && card != null) card()
            else if (!isCardRight) PeriodLabel(label, isCurrent)
        }
    }
}

/**
 * Box-обёртка вокруг карточки: добавляет [TimelineDims.TrunkGap] со стороны ствола
 * (зазор от ствола) и опционально делает карточку кликабельной целиком.
 *
 * isCardRight=false → padding end (карточка слева, ствол справа).
 * isCardRight=true  → padding start (карточка справа, ствол слева).
 */
@Composable
internal fun TimelineCardWrapper(
    isCardRight: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(
                end   = if (!isCardRight) TimelineDims.TrunkGap else 0.dp,
                start = if (isCardRight)  TimelineDims.TrunkGap else 0.dp,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

// ── Метка периода ─────────────────────────────────────────────────────────────

@Composable
internal fun PeriodLabel(text: String, isCurrent: Boolean) {
    Text(
        text = text,
        color = if (isCurrent) TealAccent else TrunkColor,
        fontSize = 14.sp,
        fontFamily = geologicaFontFamily,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.padding(horizontal = 14.dp),
    )
}

// ── Заголовок-разделитель периода (бесконечный скролл) ──────────────────────

/**
 * Плашка-заголовок между периодами: центральная капсула на стволе (белый фон
 * перекрывает ствол, рамка `TrunkColor`; текущий период — акцент `TealAccent`).
 * Используется в Day/Week/Month timeline-view как разделитель периодов.
 */
@Composable
internal fun PeriodHeader(label: String, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val accent = if (isCurrent) TealAccent else TrunkColor
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White)
                .border(1.dp, accent, RoundedCornerShape(percent = 50))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                color = accent,
                fontSize = 14.sp,
                fontFamily = geologicaFontFamily,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/**
 * Компактная пометка «нет тренировок» под заголовком пустого дня (только DAY-режим:
 * там нет под-строк-нодов, в отличие от Week/Month). Белый фон перекрывает ствол.
 */
@Composable
internal fun PeriodEmptyNote(text: String = "Нет тренировок", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = text,
                color = TrunkColor.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontFamily = geologicaFontFamily,
            )
        }
    }
}

// ── Инфо-колонка карточки ────────────────────────────────────────────────────

/**
 * Готовая инфо-колонка карточки таймлайна:
 * белый фон + рамка + скругление справа ([TimelineInfoShape]) + стандартный паддинг.
 *
 * Геометрия (ширина/высота) задаётся через [modifier]: например
 * `Modifier.width(120.dp).height(110.dp)` или `Modifier.width(120.dp).fillMaxHeight()`.
 */
@Composable
internal fun TimelineInfoColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .timelineCardSurface(TimelineInfoShape)
            .padding(
                horizontal = TimelineDims.InfoPaddingHorizontal,
                vertical = TimelineDims.InfoPaddingVertical,
            ),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

// ── Строка инфо (иконка + текст) ─────────────────────────────────────────────

/**
 * Строка информации внутри карточки.
 * По умолчанию использует [WorkoutTextStyles.timelineInfo] (14sp Normal ColorPrimary).
 */
@Composable
internal fun InfoRow(
    iconRes: Int,
    value: String,
    textStyle: TextStyle = WorkoutTextStyles.timelineInfo,
    iconSize: Dp = 20.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = value,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Квадратная иконка с фоном (для стрипов и Day-полоски) ───────────────────

/**
 * Квадратная иконка с фоном и скруглением [TimelineDims.IconBoxCornerRadius].
 * Используется в Week-стрипе, Month-стрипе и Day-полоске.
 */
@Composable
internal fun TimelineIconBox(
    iconRes: Int,
    bgColor: Color,
    boxSize: Dp,
    iconSize: Dp = boxSize,
    cornerRadius: Dp = TimelineDims.IconBoxCornerRadius,
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.Unspecified,
        )
    }
}
