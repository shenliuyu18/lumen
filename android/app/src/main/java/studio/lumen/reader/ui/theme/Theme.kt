package studio.lumen.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// 深色优先的取色：近黑底 + 墨绿卡片描边 + 柔蓝强调色。
private val NightBackground = Color(0xFF0A0A0C)
private val NightSurface = Color(0xFF121215)
private val NightCard = Color(0xFF17171B)
private val NightVariant = Color(0xFF222229)
private val NightInk = Color(0xFFECECEF)
private val NightMuted = Color(0xFF9C9EA9)
private val NightAccent = Color(0xFF9DBEFF)
private val NightAccentInk = Color(0xFF10203C)
private val NightClay = Color(0xFFE0BE9F)

private val DayBackground = Color(0xFFF5F5F8)
private val DaySurface = Color(0xFFFFFFFF)
private val DayCard = Color(0xFFFFFFFF)
private val DayVariant = Color(0xFFE9EAF0)
private val DayInk = Color(0xFF15161B)
private val DayMuted = Color(0xFF5B5E6B)
private val DayAccent = Color(0xFF3D5C9C)
private val DayAccentInk = Color(0xFFFFFFFF)
private val DayClay = Color(0xFF8A6242)

private val NightColors = darkColorScheme(
    primary = NightAccent,
    onPrimary = NightAccentInk,
    primaryContainer = NightVariant,
    onPrimaryContainer = NightInk,
    secondary = NightClay,
    onSecondary = NightAccentInk,
    background = NightBackground,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightCard,
    onSurfaceVariant = NightMuted,
    outline = Color(0xFF3A3B44),
    outlineVariant = Color(0xFF26272E)
)

private val DayColors = lightColorScheme(
    primary = DayAccent,
    onPrimary = DayAccentInk,
    primaryContainer = DayVariant,
    onPrimaryContainer = DayInk,
    secondary = DayClay,
    onSecondary = Color.White,
    background = DayBackground,
    onBackground = DayInk,
    surface = DaySurface,
    onSurface = DayInk,
    surfaceVariant = DayCard,
    onSurfaceVariant = DayMuted,
    outline = Color(0xFFB9BBC6),
    outlineVariant = Color(0xFFDFE0E7)
)

/** 标题与卡片名使用衬线，正文与标签保持无衬线，贴近参考应用的排版。 */
private val Serif = FontFamily.Serif
private val SerifHeadline = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.SemiBold,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

private val LumenTypography = Typography(
    displaySmall = SerifHeadline.copy(fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = SerifHeadline.copy(fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = SerifHeadline.copy(fontSize = 25.sp, lineHeight = 33.sp),
    headlineSmall = SerifHeadline.copy(fontSize = 21.sp, lineHeight = 28.sp),
    titleLarge = SerifHeadline.copy(fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = SerifHeadline.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = SerifHeadline.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun LumenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NightColors
        else -> DayColors
    }
    MaterialTheme(colorScheme = colors, typography = LumenTypography, content = content)
}
