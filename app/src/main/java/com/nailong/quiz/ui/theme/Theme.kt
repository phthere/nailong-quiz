package com.nailong.quiz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NailongYellow = Color(0xFFF5CF63)
val SkyBlue = Color(0xFF7DC5EC)
val DeepBlue = Color(0xFF234D65)
val LightBg = Color(0xFFF3F6FA)
val WhiteCard = Color(0xFFFFFFFF)
val DarkBg = Color(0xFF0E181F)
val DarkCard = Color(0xFF1D2B35)
val CorrectGreen = Color(0xFF46B995)
val WrongRed = Color(0xFFE2767D)
val TextPrimary = Color(0xFF1A2E3D)
val TextSecondary = Color(0xFF647889)

val LightColors = lightColorScheme(
    primary = DeepBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCECF6), onPrimaryContainer = DeepBlue,
    secondary = Color(0xFF8C6A19), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF9EDC8), onSecondaryContainer = Color(0xFF634C16),
    tertiary = Color(0xFF508DAD), background = LightBg, onBackground = TextPrimary,
    surface = WhiteCard, onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE6EDF3), onSurfaceVariant = TextSecondary,
    outline = Color(0xFFC1CED9), outlineVariant = Color(0xFFDCE5EC), error = Color(0xFFB84E5D)
)
val DarkColors = darkColorScheme(
    primary = SkyBlue, onPrimary = Color(0xFF0B2738),
    primaryContainer = DeepBlue, onPrimaryContainer = Color(0xFFE6F3FC),
    secondary = NailongYellow, onSecondary = Color(0xFF3C2E0F),
    secondaryContainer = Color(0xFF4C4128), onSecondaryContainer = Color(0xFFFFE8A7),
    tertiary = SkyBlue, background = DarkBg, onBackground = Color(0xFFECF2F7),
    surface = DarkCard, onSurface = Color(0xFFECF2F7),
    surfaceVariant = Color(0xFF2B3D49), onSurfaceVariant = Color(0xFFB2C0CA),
    outline = Color(0xFF415360), outlineVariant = Color(0xFF30424E), error = WrongRed
)
private val base = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 17.sp)
)
@Composable
fun NailongTheme(darkTheme: Boolean = isSystemInDarkTheme(), fontScale: Float = 1f, content: @Composable () -> Unit) {
    val typography = Typography(
        displaySmall = base.displaySmall.scaled(fontScale), headlineLarge = base.headlineLarge.scaled(fontScale),
        headlineMedium = base.headlineMedium.scaled(fontScale), headlineSmall = base.headlineSmall.scaled(fontScale),
        titleLarge = base.titleLarge.scaled(fontScale), titleMedium = base.titleMedium.scaled(fontScale),
        titleSmall = base.titleSmall.scaled(fontScale), bodyLarge = base.bodyLarge.scaled(fontScale),
        bodyMedium = base.bodyMedium.scaled(fontScale), bodySmall = base.bodySmall.scaled(fontScale),
        labelLarge = base.labelLarge.scaled(fontScale), labelMedium = base.labelMedium.scaled(fontScale),
        labelSmall = base.labelSmall.scaled(fontScale)
    )
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = typography,
        shapes = Shapes(RoundedCornerShape(10.dp), RoundedCornerShape(14.dp), RoundedCornerShape(22.dp), RoundedCornerShape(28.dp), RoundedCornerShape(32.dp)),
        content = content)
}
private fun TextStyle.scaled(scale: Float) = if (scale == 1f) this else copy(fontSize = (fontSize.value * scale).sp, lineHeight = (lineHeight.value * scale).sp)
val QuestionStemStyle @Composable get() = MaterialTheme.typography.titleMedium.copy(lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.18f)
