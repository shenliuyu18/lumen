/*
 * 悬浮玻璃胶囊底栏。布局与动效参照 legado-with-MD3
 * （https://github.com/HapeLee/legado-with-MD3，GNU GPLv3）的
 * ui/widget/components/FloatingBottomBar.kt：选中项展开成「图标 + 文字」的宽胶囊，
 * 其余项收缩为纯图标；胶囊本身是折射背后的玻璃表面。按 Lumen 的五页签精简重写。
 */
package studio.lumen.reader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop

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
 * 阅读 app 式的玻璃悬浮底栏：整条是悬浮胶囊，选中项展开成带文字的宽胶囊，
 * 其余项只留图标；胶囊半透明并折射下方的页面内容。
 */
@Composable
fun AppBottomBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = true,
    backdrop: LayerBackdrop? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val barModifier = if (glass) {
        modifier.liquidGlass(
            shape = shape,
            blurRadius = 20.dp,
            lensRadius = 26.dp,
            surfaceColor = scheme.surfaceContainer.copy(alpha = 0.55f)
        )
    } else {
        modifier.clip(shape).background(scheme.surfaceContainer)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = barModifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEachIndexed { index, item ->
                BottomBarItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    item: LiquidNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 124.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "pill-width"
    )
    val pillAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "pill")
    val labelAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "label")
    val tint by animateColorAsState(
        if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        label = "tint"
    )
    Box(
        modifier = Modifier
            .height(46.dp)
            .width(pillWidth)
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.secondaryContainer.copy(alpha = pillAlpha * if (pressed) 1f else 0.92f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            if (labelAlpha > 0.01f) {
                Spacer(Modifier.width(8.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = tint.copy(alpha = labelAlpha),
                    maxLines = 1
                )
            }
        }
    }
}

/** 兼容旧调用的入口：按是否提供背景层决定玻璃开关。 */
@Composable
fun LiquidBottomBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current
) {
    AppBottomBar(
        items = items,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        modifier = modifier,
        glass = backdrop != null,
        backdrop = backdrop
    )
}
