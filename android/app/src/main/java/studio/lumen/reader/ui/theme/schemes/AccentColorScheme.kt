/*
 * 桌面端主题只给一个主色（ui/styles.css 的 --accent 与 --bg），安卓端按同一主色派生
 * light / dark 两套 Material3 配色，保证两端「同名主题」看到的是同一种颜色。
 */
package studio.lumen.reader.ui.theme.schemes

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** 按 weight 比例把 a 混向 b；weight=0 得到 a，weight=1 得到 b。 */
private fun mix(a: Color, b: Color, weight: Float): Color = Color(
    a.red + (b.red - a.red) * weight,
    a.green + (b.green - a.green) * weight,
    a.blue + (b.blue - a.blue) * weight
)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)

class AccentColorScheme(
    val accent: Color,
    val paper: Color
) : BaseColorScheme() {

    private val ink = mix(paper, Black, 0.86f)
    private val muted = mix(accent, paper, 0.45f)
    private val night = mix(paper, Black, 0.93f)
    private val nightInk = mix(paper, White, 0.55f)
    private val nightMuted = mix(accent, paper, 0.75f)
    private val bright = mix(accent, White, 0.45f)

    override val lightScheme = lightColorScheme(
        primary = accent,
        onPrimary = White,
        primaryContainer = mix(accent, White, 0.82f),
        onPrimaryContainer = mix(accent, Black, 0.3f),
        secondary = mix(accent, paper, 0.4f),
        onSecondary = White,
        secondaryContainer = mix(accent, paper, 0.84f),
        onSecondaryContainer = mix(accent, Black, 0.2f),
        tertiary = mix(accent, Black, 0.12f),
        onTertiary = White,
        tertiaryContainer = mix(accent, White, 0.88f),
        onTertiaryContainer = mix(accent, Black, 0.35f),
        error = ErrorLight,
        onError = White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = mix(accent, paper, 0.86f),
        onSurfaceVariant = muted,
        outline = mix(accent, paper, 0.55f),
        outlineVariant = mix(accent, paper, 0.8f),
        surfaceDim = mix(paper, Black, 0.06f),
        surfaceBright = mix(paper, White, 0.6f),
        surfaceContainerLowest = White,
        surfaceContainerLow = mix(paper, accent, 0.04f),
        surfaceContainer = mix(paper, accent, 0.08f),
        surfaceContainerHigh = mix(paper, accent, 0.13f),
        surfaceContainerHighest = mix(paper, accent, 0.18f),
        inverseSurface = mix(paper, Black, 0.85f),
        inverseOnSurface = mix(paper, White, 0.9f),
        inversePrimary = bright
    )

    override val darkScheme = darkColorScheme(
        primary = bright,
        onPrimary = mix(accent, Black, 0.55f),
        primaryContainer = mix(accent, Black, 0.25f),
        onPrimaryContainer = mix(accent, White, 0.85f),
        secondary = nightMuted,
        onSecondary = mix(accent, Black, 0.5f),
        secondaryContainer = mix(accent, Black, 0.1f),
        onSecondaryContainer = mix(accent, White, 0.8f),
        tertiary = mix(accent, White, 0.62f),
        onTertiary = mix(accent, Black, 0.6f),
        tertiaryContainer = mix(accent, Black, 0.2f),
        onTertiaryContainer = mix(accent, White, 0.85f),
        error = ErrorDark,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = night,
        onBackground = nightInk,
        surface = night,
        onSurface = nightInk,
        surfaceVariant = mix(accent, Black, 0.55f),
        onSurfaceVariant = nightMuted,
        outline = mix(accent, paper, 0.35f),
        outlineVariant = mix(accent, Black, 0.45f),
        surfaceDim = night,
        surfaceBright = mix(paper, Black, 0.72f),
        surfaceContainerLowest = mix(paper, Black, 0.98f),
        surfaceContainerLow = mix(paper, Black, 0.9f),
        surfaceContainer = mix(paper, Black, 0.87f),
        surfaceContainerHigh = mix(paper, Black, 0.84f),
        surfaceContainerHighest = mix(paper, Black, 0.8f),
        inverseSurface = nightInk,
        inverseOnSurface = night,
        inversePrimary = accent
    )
}
