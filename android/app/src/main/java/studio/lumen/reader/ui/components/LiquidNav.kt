/*
 * 底栏风格沿用 legado-with-MD3（https://github.com/HapeLee/legado-with-MD3，GNU GPLv3）
 * 的 AppNavigationBar：图标居中、常显文字标签，选中项在图标下方铺一枚圆角胶囊。
 * 这里的实现按 Lumen 的主题与玻璃设置重写。
 */
package studio.lumen.reader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
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
 * 阅读 app 风格的底栏：五项等宽，图标在上、名称在下且始终显示；
 * 选中项在图标位置铺一枚 secondaryContainer 胶囊。玻璃开启时整条底栏半透明折射内容。
 */
@Composable
fun AppBottomBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    backdrop: LayerBackdrop? = null
) {
    val scheme = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(percent = 50)
    val content = @Composable { rowModifier: Modifier ->
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEachIndexed { index, item ->
                BottomBarItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    if (glass) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            content(
                Modifier.liquidGlass(
                    shape = pill,
                    blurRadius = 20.dp,
                    lensRadius = 24.dp,
                    surfaceColor = scheme.surface.copy(alpha = 0.52f)
                )
            )
        }
    } else {
        Column(modifier.fillMaxWidth().navigationBarsPadding().background(scheme.surface)) {
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.7f))
            content(Modifier)
        }
    }
}

@Composable
private fun BottomBarItem(
    item: LiquidNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pillAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "pill")
    val iconTint by animateColorAsState(
        if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        label = "icon"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.secondaryContainer.copy(alpha = pillAlpha * (if (pressed) 1f else 0.9f))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1
        )
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
