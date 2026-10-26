package studio.lumen.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt

/**
 * 液态玻璃表面：有背景层时画真玻璃（模糊 + 折射 + 高光），没有时退化为半透明面板。
 * exportedBackdrop 会把这一层自身记录下来，供内部的滑块、按钮再次折射。
 */
@Composable
fun Modifier.liquidSurface(
    backdrop: LayerBackdrop?,
    shape: Shape = RoundedCornerShape(percent = 50),
    blurRadius: Dp = 14.dp,
    refraction: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    shadowRadius: Dp = 22.dp,
    exportedBackdrop: LayerBackdrop? = null
): Modifier {
    if (backdrop == null) return this.clip(shape).background(tint)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            lens(refraction.toPx(), refraction.toPx(), chromaticAberration = true)
        },
        highlight = { Highlight.Default },
        shadow = { Shadow(radius = shadowRadius) },
        innerShadow = { InnerShadow(radius = 7.dp, alpha = 0.45f) },
        exportedBackdrop = exportedBackdrop,
        onDrawSurface = { drawRect(tint) }
    )
}

/** 圆形玻璃按钮：图标 + 下方标签。 */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    label: String,
    backdrop: LayerBackdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .width(66.dp)
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(50.dp)
                .liquidSurface(
                    backdrop = backdrop,
                    blurRadius = 4.dp,
                    refraction = 26.dp,
                    tint = if (enabled) scheme.primary.copy(alpha = 0.10f) else scheme.outlineVariant.copy(alpha = 0.3f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 液态滑块：胶囊轨道 + 折射气泡滑块头。steps 为中间分段数，0 表示连续。 */
@Composable
fun LiquidSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    steps: Int = 0
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
                .liquidSurface(
                    backdrop = backdrop,
                    blurRadius = 3.dp,
                    refraction = 30.dp,
                    tint = scheme.primary.copy(alpha = 0.14f),
                    shadowRadius = 8.dp
                )
        )

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(valueRange, steps) {
                    detectTapGestures { offset -> commit(offset.x) }
                }
                .pointerInput(valueRange, steps) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> commit(offset.x) },
                        onHorizontalDrag = { change, _ -> commit(change.position.x) }
                    )
                }
        )
    }
}
