package studio.lumen.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

enum class GlassMode {
    Off,
    Liquid;

    companion object {
        // frosted 已下线，旧设置里的取值按液态玻璃处理。
        fun from(value: String): GlassMode = if (value == "liquid" || value == "frosted") Liquid else Off
    }
}

val LocalFloatingNavPadding = compositionLocalOf { 0.dp }

fun glassNavShape(floating: Boolean, mode: GlassMode): Shape = when {
    floating -> RoundedCornerShape(percent = 50)
    mode != GlassMode.Off -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    else -> RoundedCornerShape(0.dp)
}

@Composable
fun GlassSurface(
    mode: GlassMode,
    shape: Shape,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    floating: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var surface = modifier
    when {
        mode == GlassMode.Off -> surface = surface.background(scheme.surface)
        backdrop != null -> surface = surface.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                // blur 需要 Android 12+，lens 折射需要 Android 13+，更低版本自动跳过。
                blur(14f.dp.toPx())
                lens(26f.dp.toPx(), 26f.dp.toPx(), chromaticAberration = true)
            },
            highlight = { Highlight.Default.copy(alpha = 0.6f) },
            shadow = { if (floating) Shadow(radius = 30.dp) else Shadow(radius = 14.dp) },
            innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.5f) },
            onDrawSurface = { drawRect(scheme.surface.copy(alpha = if (floating) 0.42f else 0.6f)) }
        )
        // 底栏下方没有可采样的内容时，退化为带色调的半透明面板。
        else -> surface = surface
            .shadow(if (floating) 22.dp else 10.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(scheme.primary.copy(alpha = 0.22f), scheme.tertiary.copy(alpha = 0.16f))
                ),
                shape
            )
            .background(scheme.surface.copy(alpha = 0.72f))
    }
    Box(modifier = surface) {
        content()
    }
}
