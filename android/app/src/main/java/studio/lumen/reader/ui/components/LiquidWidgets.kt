/*
 * 玻璃配方与交互形变改编自 legado-with-MD3（https://github.com/HapeLee/legado-with-MD3，
 * GNU GPLv3）的 topbar/TopBarLiquidGlass.kt、reader/ReaderMenuGlass.kt 与
 * ui/animation/InteractiveHighlight.kt，参数（模糊 12dp、透镜 24dp、表面透明度 0.5、
 * 按压缩放 4dp）沿用其取值；接口按 Lumen 的主题与导航结构重写。
 */
package studio.lumen.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
import studio.lumen.reader.ui.animation.InteractiveHighlight
import studio.lumen.reader.ui.animation.interactiveHighlightSupported
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** 全局共享的背景层：所有玻璃表面都从这里采样页面内容。 */
val LocalGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** 玻璃是否可用：需要背景层，且系统支持 AGSL 折射（Android 13+）。 */
@Composable
fun glassAvailable(): Boolean = LocalGlassBackdrop.current != null && interactiveHighlightSupported()

/**
 * 液态玻璃表面。没有背景层或系统不支持时退化为半透明纯色，保证低版本仍可用。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(percent = 50),
    blurRadius: Dp = 12.dp,
    lensRadius: Dp = 24.dp,
    surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    shadow: Boolean = true,
    innerShadow: Boolean = true,
    interactive: Boolean = true,
    exportedBackdrop: LayerBackdrop? = null,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current
): Modifier {
    if (backdrop == null || !interactiveHighlightSupported()) {
        // 没有背景层（玻璃关闭或系统不支持折射）时铺不透明底色，保证深色模式下的文字对比度。
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(surfaceColor)
    }
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { if (interactive) InteractiveHighlight(scope) else null }
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.coerceAtLeast(0.dp).toPx())
                if (lensRadius > 0.dp) {
                    lens(lensRadius.toPx(), lensRadius.toPx(), chromaticAberration = true)
                }
            },
            highlight = { Highlight.Default },
            shadow = { if (shadow) Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.06f)) else null },
            innerShadow = { if (innerShadow) InnerShadow(radius = 7.dp, alpha = 0.45f) else null },
            exportedBackdrop = exportedBackdrop,
            layerBlock = highlight?.let { outer ->
                {
                    val width = size.width
                    val height = size.height
                    if (width > 0f && height > 0f) {
                        val progress = outer.pressProgress
                        val scale = 1f + 4.dp.toPx() / height * progress
                        val maxOffset = size.minDimension
                        val drag = outer.dragOffset
                        translationX = maxOffset * tanh(0.05f * drag.x / maxOffset) * progress
                        translationY = maxOffset * tanh(0.05f * drag.y / maxOffset) * progress
                        val maxDragScale = 4.dp.toPx() / height
                        val angle = atan2(drag.y, drag.x)
                        scaleX = scale + maxDragScale *
                            abs(cos(angle) * drag.x / size.maxDimension) *
                            (width / height).coerceAtMost(1f) * progress
                        scaleY = scale + maxDragScale *
                            abs(sin(angle) * drag.y / size.maxDimension) *
                            (height / width).coerceAtMost(1f) * progress
                    }
                }
            },
            onDrawSurface = { drawRect(surfaceColor) }
        )
        .then(highlight?.modifier ?: Modifier)
        .then(highlight?.gestureModifier ?: Modifier)
}

/** 大块内容容器：圆角 24dp 的玻璃卡。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .liquidGlass(shape = shape, blurRadius = 10.dp, lensRadius = 18.dp, interactive = onClick != null)
            .padding(contentPadding),
        content = content
    )
}

/** 胶囊玻璃按钮。 */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .then(if (enabled) Modifier.liquidGlass(interactive = true) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant
        )
    }
}

/** 玻璃页签/标签胶囊。 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .liquidGlass(
                blurRadius = if (selected) 8.dp else 5.dp,
                lensRadius = if (selected) 20.dp else 14.dp,
                surfaceColor = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surface.copy(alpha = 0.4f),
                shadow = selected
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (dense) 12.dp else 16.dp, vertical = if (dense) 6.dp else 9.dp)
    ) {
        Text(
            label,
            style = if (dense) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant
        )
    }
}

/** 圆形玻璃图标按钮（顶栏与阅读模式工具条共用）。 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showLabel: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.width(if (showLabel) 66.dp else 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(if (enabled) Modifier.liquidGlass(lensRadius = 20.dp) else Modifier)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) tint else scheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
        }
        if (showLabel) {
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
}
