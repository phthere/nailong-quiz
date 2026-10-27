package com.nailong.quiz.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nailong.quiz.R
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.ThemeBg

fun ThemeBg.drawableRes(): Int = when (this) {
    ThemeBg.DEEP_SEA -> R.drawable.bg_deep_sea
    ThemeBg.CLOUD -> R.drawable.bg_cloud
    ThemeBg.SKY_MIRROR -> R.drawable.bg_sky_mirror
}

/** 全局奶龙背景：图片 + 可调遮罩 + (API31+) 可调模糊 */
@Composable
fun AppBackground(settings: AppSettings, showBg: Boolean, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val plainBg = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize()) {
        if (showBg) {
            Image(
                painter = painterResource(settings.themeBg.drawableRes()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (settings.bgBlur > 0.02f && Build.VERSION.SDK_INT >= 31)
                            Modifier.blur((settings.bgBlur * 20).dp)
                        else Modifier
                    )
            )
            // 遮罩：深浅可调；深色模式自动加深
            val baseAlpha = if (dark) (settings.scrimAlpha + 0.3f).coerceAtMost(0.85f) else settings.scrimAlpha
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = if (dark) 0f else baseAlpha)))
            if (dark) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = baseAlpha)))
        } else {
            Box(Modifier.fillMaxSize().background(plainBg))
        }
        content()
    }
}

/** 轻量玻璃质感卡片：半透明 + 圆角 + 细边 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val colors = CardDefaults.cardColors(containerColor = bg)
    val shape = RoundedCornerShape(corner)
    val border = CardDefaults.outlinedCardBorder().copy(
        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    )
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(1.dp), content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(1.dp), content = content)
    }
}

/** 学习进度环 */
@Composable
fun ProgressRing(progress: Float, sizeDp: Dp = 120.dp, trackColor: Color = MaterialTheme.colorScheme.surfaceVariant, color: Color = MaterialTheme.colorScheme.secondary, center: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(sizeDp)) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = trackColor,
            strokeWidth = 9.dp,
        )
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 9.dp,
        )
        center()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = modifier.padding(vertical = 8.dp))
}

/** 防重复点击 */
@Composable
fun rememberClickGuard(intervalMs: Long = 600): (() -> Unit) -> Unit {
    val last = androidx.compose.runtime.remember { longArrayOf(0L) }
    return { action ->
        val now = System.currentTimeMillis()
        if (now - last[0] > intervalMs) {
            last[0] = now
            action()
        }
    }
}
