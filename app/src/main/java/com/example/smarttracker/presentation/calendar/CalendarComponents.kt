package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
