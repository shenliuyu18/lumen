/*
 * 改编自 legado-with-MD3（https://github.com/HapeLee/legado-with-MD3，GNU GPLv3）
 * 的 ui/animation/InteractiveHighlight.kt：按压时的光斑与跟随位移。
 */
package studio.lumen.reader.ui.animation

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import studio.lumen.reader.ui.util.inspectDragGestures

/** 玻璃控件的按压缩放与光斑；Android 13 以下没有 AGSL，直接不启用。 */
fun interactiveHighlightSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@SuppressLint("NewApi")
class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    private val pressSpec = spring(0.5f, 300f, 0.001f)
    private val moveSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressAnimation.value
    val offset: Offset get() = positionAnimation.value
    val dragOffset: Offset get() = positionAnimation.value - startPosition

    @Language("AGSL")
    private val shader = RuntimeShader(
        """
        uniform float2 size;
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;

        half4 main(float2 coord) {
            float dist = distance(coord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            return color * intensity;
        }
        """
    )

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressAnimation.value
        if (progress > 0f) {
            drawRect(Color.White.copy(0.06f * progress), blendMode = BlendMode.Plus)
            shader.apply {
                val at = position(size, positionAnimation.value)
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                setFloatUniform("radius", size.minDimension * 1.2f)
                setFloatUniform(
                    "position",
                    at.x.fastCoerceIn(0f, size.width),
                    at.y.fastCoerceIn(0f, size.height)
                )
            }
            drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressAnimation.animateTo(1f, pressSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressAnimation.animateTo(0f, pressSpec) }
                    launch { positionAnimation.animateTo(startPosition, moveSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressAnimation.animateTo(0f, pressSpec) }
                    launch { positionAnimation.animateTo(startPosition, moveSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}
