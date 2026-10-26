/*
 * 悬浮液态玻璃底栏。选中气泡的按压/拖拽形变与折射参数改编自
 * legado-with-MD3（https://github.com/HapeLee/legado-with-MD3，GNU GPLv3）的
 * ui/widget/components/FloatingBottomBar.kt 与 topbar/TopBarLiquidGlass.kt。
 */
package studio.lumen.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.abs

data class LiquidNavItem(val label: String, val icon: ImageVector)

/** 底栏图标集中声明，避免各页面重复引用。 */
object NavIcons {
    val home: ImageVector = Icons.Outlined.Home
    val list: ImageVector = Icons.Outlined.ViewList
    val globe: ImageVector = Icons.Outlined.Public
    val book: ImageVector = Icons.Outlined.Book
    val settings: ImageVector = Icons.Outlined.Settings
}

/**
 * 悬浮玻璃底栏：选中项由一颗气泡标出，切换时按动画速度横向拉伸、纵向压扁，
 * 气泡经过的背景内容被真实折射；只有选中项显示文字标签。
 */
@Composable
fun LiquidBottomBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 74.dp,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val cellWidthPx = with(density) { maxWidth.toPx() } / items.size.coerceAtLeast(1)
        val blobWidth = with(density) { (cellWidthPx - 12.dp.toPx()).coerceAtLeast(44.dp.toPx()).toDp() }
        val travel = remember { Animatable(selectedIndex.toFloat()) }
        val position by remember { derivedStateOf { travel.value } }
        val stretch by remember {
            derivedStateOf { (travel.velocity / (cellWidthPx * 5f)).coerceIn(-0.36f, 0.36f) }
        }

        LaunchedEffect(selectedIndex) {
            if (selectedIndex in items.indices && travel.value != selectedIndex.toFloat()) {
                travel.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.5f, stiffness = 300f))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .liquidGlass(
                    shape = shape,
                    blurRadius = 16.dp,
                    lensRadius = 22.dp,
                    surfaceColor = scheme.surface.copy(alpha = 0.5f),
                    interactive = false,
                    backdrop = backdrop
                )
        )

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(density) { (position * cellWidthPx + 6.dp.toPx()).toDp() })
                .width(blobWidth)
                .height(height - 16.dp)
                .graphicsLayer {
                    val amount = abs(stretch)
                    scaleX = 1f + amount
                    scaleY = 1f - amount * 0.5f
                    transformOrigin = TransformOrigin.Center
                }
                .liquidGlass(
                    shape = shape,
                    blurRadius = 5.dp,
                    lensRadius = 34.dp,
                    surfaceColor = scheme.primary.copy(alpha = 0.12f),
                    shadow = false,
                    interactive = false,
                    backdrop = backdrop
                )
        )

        Row(Modifier.height(height)) {
            items.forEachIndexed { index, item ->
                val active = index == selectedIndex
                val labelAlpha by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "label")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(height)
                        .clickable { onSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                        maxLines = 1,
                        modifier = Modifier.alpha(labelAlpha)
                    )
                }
            }
        }
    }
}
