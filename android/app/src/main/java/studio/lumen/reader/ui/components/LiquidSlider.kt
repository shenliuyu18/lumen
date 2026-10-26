/*
 * 细滑条：与阅读 app（legado-with-MD3，GNU GPLv3）的亮度/字号调节一致，
 * 直接使用 Material 3 Slider 的形态——细轨道 + 小圆点滑块头，不再使用玻璃胶囊。
 */
package studio.lumen.reader.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 细轨道滑条。steps 为中间分段数。 */
@Composable
fun LiquidSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = scheme.primary,
            activeTrackColor = scheme.primary,
            inactiveTrackColor = scheme.surfaceVariant,
            activeTickColor = scheme.onPrimary.copy(alpha = 0.6f),
            inactiveTickColor = scheme.outline.copy(alpha = 0.5f)
        )
    )
}
