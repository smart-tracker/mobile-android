package com.example.smarttracker.presentation.common

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smarttracker.R
import com.example.smarttracker.presentation.theme.ColorPrimary

/**
 * Круглый аватар профиля с загрузкой через Coil.
 *
 * Cache-key включает [photoKey] — гарантирует перезагрузку фото когда сервер
 * заменяет файл по тому же URL (после upload/delete).
 *
 * Вызывающий управляет размером и кликабельностью через [modifier]:
 * `Modifier.size(96.dp).clickable { ... }`
 */
@Composable
fun ProfileAvatarImage(
    photoUrl: String?,
    photoKey: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(photoUrl)
            .memoryCacheKey("$photoUrl/$photoKey")
            .diskCacheKey("$photoUrl/$photoKey")
            .crossfade(true)
            .build(),
        contentDescription = "Фото профиля",
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.ic_profile_2),
        error = painterResource(R.drawable.ic_profile_2),
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, ColorPrimary, CircleShape),
    )
}
