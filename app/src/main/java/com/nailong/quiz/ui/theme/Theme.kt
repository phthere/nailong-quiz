package com.nailong.quiz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 奶龙色彩系统
val NailongYellow = Color(0xFFF6C94C)
val SkyBlue = Color(0xFF73BCE8)
val DeepBlue = Color(0xFF244B65)
val LightBg = Color(0xFFF7F9FC)
val WhiteCard = Color(0xFFFFFFFF)
val DarkBg = Color(0xFF101820)
val DarkCard = Color(0xFF1D2933)
val CorrectGreen = Color(0xFF22A06B)
val WrongRed = Color(0xFFE25555)
val TextPrimary = Color(0xFF202938)
val TextSecondary = Color(0xFF788596)

val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7F2),
    onPrimaryContainer = DeepBlue,
    secondary = NailongYellow,
    onSecondary = Color(0xFF5A4310),
    secondaryContainer = Color(0xFFFDF0CC),
    onSecondaryContainer = Color(0xFF5A4310),
    tertiary = SkyBlue,
    background = LightBg,
    onBackground = TextPrimary,
    surface = WhiteCard,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE9EEF4),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFC9D4E0),
    error = WrongRed
)

val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF0E2433),
    primaryContainer = DeepBlue,
    onPrimaryContainer = Color(0xFFD8E7F2),
    secondary = NailongYellow,
    onSecondary = Color(0xFF3F2F08),
    secondaryContainer = Color(0xFF6B5518),
    onSecondaryContainer = Color(0xFFFDF0CC),
    tertiary = SkyBlue,
    background = DarkBg,
    onBackground = Color(0xFFE8EEF4),
    surface = DarkCard,
    onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF27343F),
    onSurfaceVariant = Color(0xFF9AA8B6),
    outline = Color(0xFF3A4A58),
    error = Color(0xFFFF7B7B)
)

private val base = Typography()

@Composable
fun NailongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val typo = Typography(
        displaySmall = base.displaySmall.scaled(fontScale),
        headlineLarge = base.headlineLarge.scaled(fontScale),
        headlineMedium = base.headlineMedium.scaled(fontScale),
        headlineSmall = base.headlineSmall.scaled(fontScale),
        titleLarge = base.titleLarge.scaled(fontScale),
        titleMedium = base.titleMedium.scaled(fontScale),
        titleSmall = base.titleSmall.scaled(fontScale),
        bodyLarge = base.bodyLarge.scaled(fontScale),
        bodyMedium = base.bodyMedium.scaled(fontScale),
        bodySmall = base.bodySmall.scaled(fontScale),
        labelLarge = base.labelLarge.scaled(fontScale),
        labelMedium = base.labelMedium.scaled(fontScale),
        labelSmall = base.labelSmall.scaled(fontScale)
    )
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = typo,
        content = content
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale == 1.0f) this else copy(fontSize = (fontSize.value * scale).sp, lineHeight = (lineHeight.value * scale).sp)

val QuestionStemStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.2)
