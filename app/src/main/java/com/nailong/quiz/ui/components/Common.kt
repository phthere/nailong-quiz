package com.nailong.quiz.ui.components

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nailong.quiz.R
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.ThemeBg

fun ThemeBg.drawableRes() = when (this) {
    ThemeBg.DEEP_SEA -> R.drawable.bg_deep_sea
    ThemeBg.CLOUD -> R.drawable.bg_cloud
    ThemeBg.SKY_MIRROR -> R.drawable.bg_sky_mirror
}

@Composable
fun AppBackground(settings: AppSettings, showBg: Boolean, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .3f
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (showBg) {
            Image(painterResource(settings.themeBg.drawableRes()), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (settings.bgBlur > .02f && Build.VERSION.SDK_INT >= 31)
                    Modifier.blur((settings.bgBlur * 20).dp) else Modifier))
            Box(Modifier.fillMaxSize().background((if (dark) Color.Black else Color.White)
                .copy(alpha = if (dark) (.10f + settings.scrimAlpha * .55f).coerceAtMost(.7f) else settings.scrimAlpha * .5f)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,
                colors.background.copy(alpha = .04f), colors.background.copy(alpha = .78f)))))
        }
        content()
    }
}

/** Scale is read in the layer phase so pressing a card does not recompose its content every frame. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, corner: Dp = 26.dp, onClick: (() -> Unit)? = null,
              content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && onClick != null) .975f else 1f,
        spring(dampingRatio = .78f, stiffness = 700f), label = "cardPress")
    val guard = rememberClickGuard(320)
    val colors = MaterialTheme.colorScheme
    val cardColors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = .96f))
    val border = BorderStroke(.8.dp, colors.outline.copy(alpha = .30f))
    val shape = RoundedCornerShape(corner)
    val layer = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
    if (onClick != null) Card(onClick = { guard(onClick) }, modifier = layer, interactionSource = interaction,
        shape = shape, colors = cardColors, border = border, elevation = CardDefaults.cardElevation(2.dp), content = content)
    else Card(modifier = layer, shape = shape, colors = cardColors, border = border,
        elevation = CardDefaults.cardElevation(1.dp), content = content)
}

@Composable
fun ProgressRing(progress: Float, sizeDp: Dp = 120.dp,
                 trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
                 color: Color = MaterialTheme.colorScheme.primary, center: @Composable () -> Unit) {
    val value = animateFloatAsState(progress.coerceIn(0f, 1f), tween(650), label = "studyProgress")
    Box(Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize(), color = trackColor, strokeWidth = 8.dp)
        CircularProgressIndicator(progress = { value.value }, modifier = Modifier.fillMaxSize(), color = color, strokeWidth = 8.dp)
        center()
    }
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), corner = 22.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun rememberClickGuard(intervalMs: Long = 320): (() -> Unit) -> Unit {
    val last = remember { longArrayOf(Long.MIN_VALUE / 2) }
    return { action -> val now = SystemClock.uptimeMillis(); if (now - last[0] > intervalMs) { last[0] = now; action() } }
}
