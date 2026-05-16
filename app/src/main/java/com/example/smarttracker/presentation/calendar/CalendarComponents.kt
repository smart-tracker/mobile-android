package com.example.smarttracker.presentation.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.geologicaFontFamily
import com.example.smarttracker.presentation.workout.activityIconRes

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
        modifier = Modifier.size(32.dp),
    )
}

// ── Базовая строка таймлайна ──────────────────────────────────────────────────

/**
 * Строка таймлайна: левая половина / нод (32dp) / правая половина.
 * Обе половины прижимают контент к стволу:
 *  - левая: [CenterEnd]  → карточка/метка у правого края (рядом со стволом)
 *  - правая: [CenterStart] → карточка/метка у левого края (рядом со стволом)
 * Ствол НЕ рисуется здесь — он рисуется drawBehind в [TrainingHistoryScreen].
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
            modifier = Modifier.width(32.dp).fillMaxHeight(),
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

// ── Строка инфо (иконка + текст) ─────────────────────────────────────────────

/**
 * Строка информации внутри карточки.
 * [fontSize] применяется и к тексту, и к иконке (квадрат fontSize×fontSize).
 * [tint = Color.Unspecified] — иконки показываются в оригинальных цветах drawable.
 */
@Composable
internal fun InfoRow(
    iconRes: Int,
    value: String,
    fontSize: TextUnit = 12.sp,
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
            fontSize = fontSize,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Normal,
            color = TrunkColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Полоска активности ────────────────────────────────────────────────────────

/** Одноцветная полоска с иконкой активности (Week/Month view). */
@Composable
internal fun ActivityStrip(typeActivId: Int, height: Int = 90) {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(height.dp)
            .background(activityColorFor(typeActivId)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(activityIconRes(typeActivId.toString())),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.Unspecified,
        )
    }
}

/** Полоска с несколькими активностями (Week/Month view, несколько тренировок за период). */
@Composable
internal fun MultiActivityStrip(typeIds: List<Int>, height: Int = 90) {
    val distinct = typeIds.distinct().take(4)
    Column(
        modifier = Modifier
            .width(24.dp)
            .height(height.dp)
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        distinct.forEach { id ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(activityColorFor(id).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(activityIconRes(id.toString())),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = activityColorFor(id),
                )
            }
        }
        if (distinct.size < 4) {
            Box(
                modifier = Modifier
                    .weight((4 - distinct.size).toFloat())
                    .fillMaxWidth()
                    .background(Color.White),
            )
        }
    }
}
