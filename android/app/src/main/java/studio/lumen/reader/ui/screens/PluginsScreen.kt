@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package studio.lumen.reader.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.PluginInfo
import studio.lumen.reader.ui.components.CircleIconButton
import studio.lumen.reader.ui.components.GlassButton
import studio.lumen.reader.ui.components.GlassCard
import studio.lumen.reader.ui.components.PageHeader

@Composable
fun PluginsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val plugins by viewModel.plugins.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<PluginInfo?>(null) }
    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { viewModel.installPlugin(uri) }
                busy = false
                result.onSuccess {
                    Toast.makeText(context, "已安装 ${it.name}", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "安装失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val enabledCount = plugins.count { it.enabled }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            PageHeader(
                title = "插件中心",
                subtitle = "来源与去广告规则由插件提供",
                actions = {
                    CircleIconButton(Icons.Outlined.ArrowBack, "返回", onClick = onBack)
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(contentPadding = 20.dp) {
                    Text("来源与阅读体验，都由插件提供", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$enabledCount / ${plugins.size} 个插件已启用。Lumen 不内置来源或广告规则；导入官方 ZIP 或声明式 JS 后，来源、各站前三与去广告规则才会生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            label = if (busy) "安装中…" else "导入 ZIP / JS",
                            onClick = { if (!busy) installLauncher.launch(arrayOf("*/*")) },
                            icon = Icons.Outlined.FileOpen,
                            enabled = !busy
                        )
                        GlassButton(label = "复制插件目录", onClick = {
                            clipboard.setText(AnnotatedString(viewModel.pluginDirectory().absolutePath))
                            Toast.makeText(context, "插件目录已复制", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
            items(plugins, key = { it.id }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    onToggle = { enabled -> viewModel.togglePlugin(plugin.id, enabled) },
                    onRemove = { pendingRemove = plugin }
                )
            }
            if (plugins.isEmpty()) {
                item {
                    Text("还没有安装插件。导入官方爬虫 ZIP 后，媒体目录与各站前三会恢复。", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Text(
                    "插件格式：ZIP 根目录或一级目录内包含 plugin.json；JS 使用 module.exports = {...}、export default {...} 或 window.__LUMEN_PLUGIN__ = {...} 导出同一个 JSON 对象。插件只包含声明式来源与规则，应用不会执行插件脚本。去广告规则只处理页面元素，不修改正文，也不绕过登录或付费墙。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    pendingRemove?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除 ${plugin.name}？") },
            text = { Text("移除后，它提供的来源和规则会立即失效。可随时重新导入。") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { viewModel.removePlugin(plugin.id) }
                        .onSuccess { Toast.makeText(context, "插件已移除", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    pendingRemove = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun PluginCard(plugin: PluginInfo, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    GlassCard(contentPadding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (plugin.type == "adblock") Icons.Outlined.Block else Icons.Outlined.TravelExplore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(plugin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (plugin.type == "adblock") "去广告" else "爬虫"} · v${plugin.version} · ${if (plugin.author == "Lumen") "官方插件" else "用户插件"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = plugin.enabled, onCheckedChange = onToggle)
            }
            if (plugin.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(plugin.description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (plugin.type == "crawler") "${plugin.sourceCount} 个来源" else "${plugin.ruleCount} 条规则",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (plugin.author.isNotBlank()) {
                    Text(" · ${plugin.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "移除插件", tint = MaterialTheme.colorScheme.error)
                }
            }
    }
}
