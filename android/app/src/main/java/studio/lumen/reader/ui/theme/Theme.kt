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

// 取色取自阅读 app（legado-with-MD3 的 GR 配色）：浅色偏米绿纸面，深色为墨绿黑。
private val LightColors = lightColorScheme(
    primary = Color(0xFF4C662B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDA3),
    onPrimaryContainer = Color(0xFF354E16),
    secondary = Color(0xFF586249),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7C8),
    onSecondaryContainer = Color(0xFF404A33),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE7),
    onTertiaryContainer = Color(0xFF1F4E4B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9FAEF),
    onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF),
    onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE1E4D5),
    onSurfaceVariant = Color(0xFF44483D),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8BA),
    inverseSurface = Color(0xFF2F312A),
    inverseOnSurface = Color(0xFFF1F2E6),
    inversePrimary = Color(0xFFB1D18A),
    surfaceDim = Color(0xFFDADBD0),
    surfaceBright = Color(0xFFF9FAEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4E9),
    surfaceContainer = Color(0xFFEEEFE3),
    surfaceContainerHigh = Color(0xFFE8E9DE),
    surfaceContainerHighest = Color(0xFFE2E3D8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB1D18A),
    onPrimary = Color(0xFF1F3701),
    primaryContainer = Color(0xFF354E16),
    onPrimaryContainer = Color(0xFFCDEDA3),
    secondary = Color(0xFFBFCBAD),
    onSecondary = Color(0xFF2A331E),
    secondaryContainer = Color(0xFF404A33),
    onSecondaryContainer = Color(0xFFDCE7C8),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF003735),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBCECE7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140E),
    onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF12140E),
    onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC5C8BA),
    outline = Color(0xFF8F9285),
    outlineVariant = Color(0xFF44483D),
    inverseSurface = Color(0xFFE2E3D8),
    inverseOnSurface = Color(0xFF2F312A),
    inversePrimary = Color(0xFF4C662B),
    surfaceDim = Color(0xFF12140E),
    surfaceBright = Color(0xFF383A32),
    surfaceContainerLowest = Color(0xFF0C0F09),
    surfaceContainerLow = Color(0xFF1A1C16),
    surfaceContainer = Color(0xFF1E201A),
    surfaceContainerHigh = Color(0xFF282B24),
    surfaceContainerHighest = Color(0xFF33362E)
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
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = LumenTypography, content = content)
}
