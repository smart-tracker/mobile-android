package com.example.smarttracker.presentation.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.smarttracker.presentation.theme.SmartTrackerTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorPrimary
import com.example.smarttracker.presentation.theme.geologicaFontFamily

/** Цвет фона иконок достижений — жёлтый акцент #FFFC00. */
private val ColorAchievementIcon1 = Color(0xFFFFFC00)
/** Цвет фона иконок достижений — серебристый #C0C0C0. */
private val ColorAchievementIcon2 = Color(0xFFC0C0C0)
/** Цвет фона иконок достижений — бронзовый #CD7F32. */
private val ColorAchievementIcon3 = Color(0xFFCD7F32)

/**
 * Вкладка «Меню» — навигационная сетка разделов и лента достижений.
 * Для MVP рабочая кнопка — только «Профиль», остальное — заглушки.
 */
@Composable
fun MenuScreen(
    padding: PaddingValues,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.White)
            .verticalScroll(rememberScrollState()),
    ) {
        MenuHeader()
        Spacer(modifier = Modifier.height(8.dp))
        MenuGrid(
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToSettings = onNavigateToSettings,
        )
        Spacer(modifier = Modifier.height(24.dp))
        AchievementsSection()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Шапка ─────────────────────────────────────────────────────────────────────

@Composable
private fun MenuHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_profile_2),
            contentDescription = null,
            tint = ColorPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Меню",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = ColorPrimary,
        )
    }
}

// ── Навигационная сетка ────────────────────────────────────────────────────────

private data class GridItemData(
    val painter: @Composable () -> Painter,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun MenuGrid(
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val noOp: () -> Unit = {}

    val items = listOf(
        GridItemData({ painterResource(R.drawable.ic_clubs) },            "Клубы",       noOp),
        GridItemData({ painterResource(R.drawable.ic_achivements) },      "Достижения",  noOp),
        GridItemData({ painterResource(R.drawable.ic_samples) },          "Шаблоны",     noOp),
        GridItemData({ painterResource(R.drawable.ic_profile) },          "Профиль",     onNavigateToProfile),
        GridItemData({ painterResource(R.drawable.ic_settings) },  "Настройки",   onNavigateToSettings),
    )

    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        // Первый ряд: 4 элемента
        Row(modifier = Modifier.fillMaxWidth()) {
            items.take(4).forEach { item ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GridCell(
                        painter = item.painter(),
                        label = item.label,
                        onClick = item.onClick,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Второй ряд: 1 элемент, выровнен влево
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                GridCell(
                    painter = items[4].painter(),
                    label = items[4].label,
                    onClick = items[4].onClick,
                )
            }
            Spacer(modifier = Modifier.weight(3f))
        }
    }
}

@Composable
private fun GridCell(
    painter: Painter,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .border(
                    width = 2.dp,
                    color = ColorPrimary,
                    shape = RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = label,
                tint = ColorPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 12.sp,
            lineHeight = 32.sp,
            color = ColorPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Секция достижений ──────────────────────────────────────────────────────────

private data class AchievementData(
    val iconRes: Int,
    val title: String,
    val description: String,
    val timestamp: String,
    val iconBackgroundColor: Color,
)

@Composable
private fun AchievementsSection() {
    // Достижения — демо-заглушки для MVP
    val achievements = listOf(
        AchievementData(
            iconRes = R.drawable.ic_activity_running,
            title = "Прощай, лень!",
            description = "Сделать трек первой тренировки",
            timestamp = "10:50 24.03.2026",
            iconBackgroundColor = ColorAchievementIcon1,
        ),
        AchievementData(
            iconRes = R.drawable.ic_bot,
            title = "Человек, а не бот",
            description = "Заполните полностью данные профиля",
            timestamp = "10:41 24.03.2026",
            iconBackgroundColor = ColorAchievementIcon2,
        ),
        AchievementData(
            iconRes = R.drawable.ic_house,
            title = "Прописка оформлена",
            description = "Зарегистрируйтесь в приложении",
            timestamp = "10:33 24.03.2026",
            iconBackgroundColor = ColorAchievementIcon3,
        ),
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Достижения",
            fontFamily = geologicaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 32.sp,
            color = Color.Black,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        achievements.forEach { achievement ->
            AchievementCard(achievement = achievement)
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Иконка с цветным фоном
        Box(
            modifier = Modifier
                .size(55.dp)
                .background(achievement.iconBackgroundColor, RoundedCornerShape(5.dp))
                .border(3.dp, ColorPrimary, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(achievement.iconRes),
                contentDescription = null,
                tint = ColorPrimary,
                modifier = Modifier.size(50.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, ColorPrimary, RoundedCornerShape(5.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = achievement.title,
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = achievement.description,
                        fontFamily = geologicaFontFamily,
                        fontWeight = FontWeight.Thin,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = achievement.timestamp,
                    fontFamily = geologicaFontFamily,
                    fontWeight = FontWeight.Thin,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    color = Color.Black,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Меню")
@Composable
private fun MenuScreenPreview() {
    SmartTrackerTheme {
        MenuScreen(
            padding = PaddingValues(0.dp),
            onNavigateToProfile = {},
            onNavigateToSettings = {},
        )
    }
}
