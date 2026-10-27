/*
 * 适配层：让从 legado-with-MD3（GNU GPLv3）原样搬来的 FloatingBottomBar / DampedDragAnimation
 * 能在 Lumen 的主题与配置结构下编译。组件源码本身保持原样，语义差异都收敛在这里。
 */
package studio.lumen.reader.ui.components.legado

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

data class LegadoCustomColors(val primary: Int = 0, val secondary: Int = 0)

data class LegadoThemeSettings(
    val bottomBarBlurRadius: Int = 20,
    val bottomBarLensRadius: Int = 26,
    val enableBlur: Boolean = true,
    val bottomBarBlurAlpha: Float = 62f,
    val bottomBarOpacity: Int = 100,
    val appTheme: String = "0",
    val enableDeepPersonalization: Boolean = false
)

data class LegadoUiConfiguration(val theme: LegadoThemeSettings = LegadoThemeSettings())

val LocalAppUiConfiguration = compositionLocalOf { LegadoUiConfiguration() }

object LegadoTheme {
    val isDark: Boolean
        @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val colorScheme: androidx.compose.material3.ColorScheme
        @Composable get() = MaterialTheme.colorScheme

    val typography: androidx.compose.material3.Typography
        @Composable get() = MaterialTheme.typography
}

fun LegadoThemeSettings.customColors(dark: Boolean): LegadoCustomColors = LegadoCustomColors()

val legadoHomeLabel: String = "首页"
