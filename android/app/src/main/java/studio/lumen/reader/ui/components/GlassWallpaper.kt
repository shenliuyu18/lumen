package studio.lumen.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * 玻璃的采样源：一块静止的纯色层，位于所有内容之下。
 * 内容里的玻璃只能采样自己所在图层之外的图层，否则渲染节点会自引用并栈溢出。
 * 页面底色保持主题纯色，不用彩色光斑，避免界面底色随位置变化。
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
    )
}
