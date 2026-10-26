package studio.lumen.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * 玻璃的采样源：一块静止的壁纸层，位于所有内容之下。
 * 内容里的玻璃只能采样自己所在图层之外的图层，否则渲染节点会自引用并栈溢出。
 */
@Composable
fun GlassWallpaper(
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
            .background(scheme.background)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.width * 0.58f
            val stops = listOf(
                Offset(0.16f, 0.12f) to scheme.primary.copy(alpha = 0.34f),
                Offset(0.88f, 0.26f) to scheme.tertiary.copy(alpha = 0.30f),
                Offset(0.28f, 0.70f) to scheme.secondary.copy(alpha = 0.24f),
                Offset(0.78f, 0.94f) to scheme.primary.copy(alpha = 0.20f)
            )
            stops.forEach { (point, color) ->
                val center = Offset(size.width * point.x, size.height * point.y)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
    }
}
