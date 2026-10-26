@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package studio.lumen.reader.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.Settings
import studio.lumen.reader.data.Weights
import studio.lumen.reader.ui.components.GlassMode
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.PageHeader
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: MainViewModel, onOpenPlugins: () -> Unit) {
    val store by viewModel.store.collectAsState()
    val plugins by viewModel.plugins.collectAsState()
    val settings = store.settings
    val context = LocalContext.current
    val secrets = remember { viewModel.secrets() }
    var weights by remember(settings.weights) { mutableStateOf(settings.weights) }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf<String?>(null) }
    val keyStatus = secrets.status()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                viewModel.importBackup(text).getOrThrow()
            }.onSuccess { count ->
                Toast.makeText(context, "已合并 $count 条学习记录", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(viewModel.backupJson()) }
                    ?: error("无法写入文件")
            }.onSuccess { Toast.makeText(context, "学习备份已导出", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write("\uFEFF")
                    it.write(viewModel.vocabularyCsv())
                } ?: error("无法写入文件")
            }.onSuccess { Toast.makeText(context, "词句 CSV 已导出", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            PageHeader(
                title = "偏好设置",
                subtitle = "配色、翻译与学习数据都在本机"
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp + LocalFloatingNavPadding.current),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingSection("外观") {
                    Text("主题", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                            FilterChip(
                                selected = settings.themeMode == value,
                                onClick = { viewModel.updateSettings(settings.copy(themeMode = value)) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch("Material You 动态取色", settings.dynamicColor, "Android 12+ 可用") {
                        viewModel.updateSettings(settings.copy(dynamicColor = it))
                    }
                    var fontSize by remember(settings.fontSize) { mutableStateOf(settings.fontSize) }
                    Text("正文字号：$fontSize", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { fontSize = it.toInt() },
                        valueRange = 14f..28f,
                        steps = 13,
                        onValueChangeFinished = { viewModel.updateSettings(settings.copy(fontSize = fontSize)) }
                    )
                }
            }
            item {
                SettingSection("界面质感") {
                    Text("玻璃质感", style = MaterialTheme.typography.labelLarge)
                    val liquid = GlassMode.from(settings.glassEffect) == GlassMode.Liquid
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to "关闭", true to "液态玻璃").forEach { (value, label) ->
                            FilterChip(
                                selected = liquid == value,
                                onClick = { viewModel.updateSettings(settings.copy(glassEffect = if (value) "liquid" else "none")) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch("悬浮底栏", settings.floatingNav, "底栏浮在内容之上；当前版本默认开启") {
                        viewModel.updateSettings(settings.copy(floatingNav = it))
                    }
                    Text(
                        "液态玻璃会实时折射底栏下方的内容。Android 13+ 有完整折射效果，Android 12 只有背景模糊，更低版本回退为半透明面板；关闭这两项可回到贴底的普通底栏。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SettingSection("推荐权重") {
                    WeightSlider("时效", weights.freshness) { weights = weights.copy(freshness = it) }
                    WeightSlider("影响力", weights.reach) { weights = weights.copy(reach = it) }
                    WeightSlider("同题覆盖", weights.buzz) { weights = weights.copy(buzz = it) }
                    WeightSlider("前沿性", weights.frontier) { weights = weights.copy(frontier = it) }
                    WeightSlider("学习信号", weights.learning) { weights = weights.copy(learning = it) }
                    OutlinedButton(onClick = {
                        viewModel.updateSettings(settings.copy(weights = weights))
                        Toast.makeText(context, "权重已保存", Toast.LENGTH_SHORT).show()
                    }) { Text("保存权重") }
                    Spacer(Modifier.height(6.dp))
                    Text("兴趣", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("新闻", "科技", "学术").forEach { category ->
                            val selected = category in settings.interests
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) settings.interests - category else settings.interests + category
                                    viewModel.updateSettings(settings.copy(interests = next))
                                },
                                label = { Text(category) }
                            )
                        }
                    }
                }
            }
            item {
                SettingSection("翻译") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "microsoft-web" to "微软网页",
                            "microsoft" to "微软 API",
                            "deepl" to "DeepL",
                            "mymemory" to "MyMemory"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = settings.translation.provider == value,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(translation = settings.translation.copy(provider = value)))
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (settings.translation.provider != "mymemory" && settings.translation.provider != "microsoft-web") {
                        val provider = settings.translation.provider
                        val saved = keyStatus[provider] == true
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (saved) "API 密钥（已保存，可更新）" else "API 密钥") },
                            placeholder = { Text("sk-...") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (keyInput.isNotBlank()) {
                                    secrets.set(provider, keyInput.trim())
                                    keyInput = ""
                                    Toast.makeText(context, "密钥已保存", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("保存密钥") }
                            if (saved) {
                                OutlinedButton(onClick = {
                                    secrets.remove(provider)
                                    Toast.makeText(context, "密钥已移除", Toast.LENGTH_SHORT).show()
                                }) { Text("移除") }
                            }
                        }
                        if (provider == "microsoft") {
                            OutlinedTextField(
                                value = settings.translation.region,
                                onValueChange = {
                                    viewModel.updateSettings(settings.copy(translation = settings.translation.copy(region = it)))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("区域（如 eastasia，可留空）") },
                                singleLine = true
                            )
                        }
                        if (provider == "deepl") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = settings.translation.deeplPlan == "free",
                                    onClick = { viewModel.updateSettings(settings.copy(translation = settings.translation.copy(deeplPlan = "free"))) },
                                    label = { Text("Free") }
                                )
                                FilterChip(
                                    selected = settings.translation.deeplPlan == "pro",
                                    onClick = { viewModel.updateSettings(settings.copy(translation = settings.translation.copy(deeplPlan = "pro"))) },
                                    label = { Text("Pro") }
                                )
                            }
                        }
                    }
                    SettingSwitch("段落翻译带上下文", settings.translation.useContext, "提高连贯性，也会发送邻段文字") {
                        viewModel.updateSettings(settings.copy(translation = settings.translation.copy(useContext = it)))
                    }
                    Text(
                        "微软网页即 Edge 浏览器自带的翻译服务，无需密钥，但属于非公开接口，可能被限流；此时可切换到微软 API 或 DeepL。MyMemory 免费但配额有限，长段落会自动分段。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SettingSection("阅读与存储") {
                    SettingSwitch("记录阅读足迹与时长", settings.recordHistory, null) {
                        viewModel.updateSettings(settings.copy(recordHistory = it))
                    }
                    SettingSwitch("轻量存储", settings.lowStorage, "重启后停止持久化文章/译文/各站快照") {
                        viewModel.updateSettings(settings.copy(lowStorage = it))
                    }
                    SettingSwitch("启动时自动更新", settings.autoRefresh, "仅在上次更新超过 30 分钟后触发") {
                        viewModel.updateSettings(settings.copy(autoRefresh = it))
                    }
                    TextButton(onClick = { confirmClear = "cache" }) { Text("清理网页缓存") }
                    TextButton(onClick = { confirmClear = "cookies" }) { Text("清除网站登录与存储") }
                    TextButton(onClick = { confirmClear = "history" }) { Text("清空阅读足迹") }
                }
            }
            item {
                SettingSection("学习资料") {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("导入桌面版学习备份")
                    }
                    OutlinedButton(onClick = { exportJsonLauncher.launch("Lumen-学习备份-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
                        Text("导出学习备份 JSON")
                    }
                    OutlinedButton(onClick = { exportCsvLauncher.launch("Lumen-生词本-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) {
                        Text("导出词句 CSV")
                    }
                }
            }
            item {
                SettingSection("插件") {
                    Text(
                        "${plugins.count { it.enabled }} / ${plugins.size} 个插件已启用",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "爬虫与去广告规则由插件提供，应用不内置插件。导入官方 ZIP 或声明式 JS 即可启用来源与广告规则。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenPlugins, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("打开插件中心")
                    }
                }
            }
            item {
                SettingSection("关于") {
                    Text("Lumen · 观读 Android 1.0.0", style = MaterialTheme.typography.titleMedium)
                    Text("Material 3 · WebView 阅读 · 数据保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("原站阅读受站点登录与网络影响；登录、订阅和地区限制仍由原站决定。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmClear != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text("确认清理") },
            text = {
                Text(
                    when (confirmClear) {
                        "cache" -> "清理 WebView 网页缓存？登录态与词句本保留。"
                        "cookies" -> "清除所有网站登录与本地存储？词句本、收藏和足迹保留。"
                        else -> "清空阅读足迹和阅读时长？词句本和收藏保留。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (confirmClear) {
                        "cache" -> {
                            WebView(context).apply {
                                clearCache(true)
                                clearHistory()
                                destroy()
                            }
                        }
                        "cookies" -> {
                            CookieManager.getInstance().removeAllCookies(null)
                            WebStorage.getInstance().deleteAllData()
                        }
                        else -> viewModel.clearHistory()
                    }
                    confirmClear = null
                    Toast.makeText(context, "已清理", Toast.LENGTH_SHORT).show()
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, subtitle: String?, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun WeightSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.size(width = 76.dp, height = 24.dp), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f)
        )
        Text("$value", style = MaterialTheme.typography.labelSmall, modifier = Modifier.size(width = 32.dp, height = 24.dp))
    }
}
