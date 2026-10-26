package studio.lumen.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Sage = Color(0xFF60866A)
private val SageDark = Color(0xFFAFCDB5)
private val SageContainer = Color(0xFFDDEBDD)
private val SageContainerDark = Color(0xFF34513D)
private val Ink = Color(0xFF1C2620)
private val Paper = Color(0xFFF7F9F4)
private val Clay = Color(0xFF9A6F4E)

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = Ink,
    secondary = Clay,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFCFDF9),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7ECE4),
    onSurfaceVariant = Color(0xFF4B554D),
    outline = Color(0xFF7A857C)
)

private val DarkColors = darkColorScheme(
    primary = SageDark,
    onPrimary = Color(0xFF1B3824),
    primaryContainer = SageContainerDark,
    onPrimaryContainer = Color(0xFFD7E8DA),
    secondary = Color(0xFFE0BE9F),
    onSecondary = Color(0xFF422B16),
    background = Color(0xFF111713),
    onBackground = Color(0xFFE1E8E1),
    surface = Color(0xFF161D18),
    onSurface = Color(0xFFE1E8E1),
    surfaceVariant = Color(0xFF3F4941),
    onSurfaceVariant = Color(0xFFBFC9C0),
    outline = Color(0xFF89948B)
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
    MaterialTheme(colorScheme = colors, content = content)
}
