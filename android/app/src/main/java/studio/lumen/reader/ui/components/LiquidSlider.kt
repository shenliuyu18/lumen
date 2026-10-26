/*
 * 玻璃滑块：折射胶囊滑块头。按压形变沿用 legado-with-MD3（GNU GPLv3）
 * 的 InteractiveHighlight 配方。
 */
package studio.lumen.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 液态滑块：轨道是胶囊，滑块头是一颗会折射背景的气泡。steps 为中间分段数。 */
@Composable
fun LiquidSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbWidth = 56.dp
        val travel = (maxWidth - thumbWidth).coerceAtLeast(0.dp)

        fun commit(x: Float) {
            if (!enabled) return
            val ratio = (x / trackWidthPx).coerceIn(0f, 1f)
            var next = valueRange.start + ratio * span
            if (steps > 0) {
                val unit = span / (steps + 1)
                next = valueRange.start + ((next - valueRange.start) / unit).roundToInt() * unit
            }
            onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
        }

        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(scheme.primary.copy(alpha = 0.25f))
        )

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = travel * fraction)
                .width(thumbWidth)
                .fillMaxHeight()
                .liquidGlass(
                    blurRadius = 3.dp,
                    lensRadius = 30.dp,
                    surfaceColor = scheme.primary.copy(alpha = 0.14f),
                    shadow = false,
                    innerShadow = false,
                    interactive = enabled
                )
        )

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(valueRange, steps, enabled) {
                    detectTapGestures { offset -> commit(offset.x) }
                }
                .pointerInput(valueRange, steps, enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> commit(offset.x) },
                        onHorizontalDrag = { change, _ -> commit(change.position.x) }
                    )
                }
        )
    }
}
