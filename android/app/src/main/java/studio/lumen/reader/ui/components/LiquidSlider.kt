/*
 * 细玻璃滑条：形态取自阅读 app（legado-with-MD3，GNU GPLv3）阅读界面的亮度条——
 * 6dp 胶囊轨道 + 小圆点滑块头，滑块头本身是一颗会折射背景的玻璃珠。
 */
package studio.lumen.reader.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 细玻璃滑条。steps 为中间分段数，0 表示连续。 */
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
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    // pointerInput 的协程只在 key 变化时重启，直接闭包会永远用第一次组合的回调，
    // 于是「应用字号」之后滑条写进的是已经废弃的 state，看起来像拖不动。
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        val trackHeight = 6.dp
        val thumbSize = 18.dp
        val travel = (maxWidth - thumbSize).coerceAtLeast(0.dp)
        val thumbOffset by animateDpAsState(travel * fraction, label = "thumb")

        fun commit(x: Float, widthPx: Float) {
            if (!enabled || widthPx <= 0f) return
            val ratio = (x / widthPx).coerceIn(0f, 1f)
            var next = valueRange.start + ratio * span
            if (steps > 0) {
                val unit = span / (steps + 1)
                next = valueRange.start + ((next - valueRange.start) / unit).roundToInt() * unit
            }
            latestChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
            latestFinished?.invoke()
        }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.surfaceVariant)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .liquidGlass(
                    shape = CircleShape,
                    blurRadius = 2.dp,
                    lensRadius = 18.dp,
                    surfaceColor = scheme.primary.copy(alpha = 0.35f),
                    shadow = false,
                    innerShadow = false,
                    interactive = enabled
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(valueRange, steps, enabled) {
                    detectTapGestures { offset -> commit(offset.x, size.width.toFloat()) }
                }
                .pointerInput(valueRange, steps, enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> commit(offset.x, size.width.toFloat()) },
                        onHorizontalDrag = { change, _ -> commit(change.position.x, size.width.toFloat()) }
                    )
                }
        )
    }
}
