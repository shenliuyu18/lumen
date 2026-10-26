@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package studio.lumen.reader.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.Article
import studio.lumen.reader.data.Source
import studio.lumen.reader.ui.components.CategoryChips
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.PageHeader

@Composable
fun SourcesScreen(viewModel: MainViewModel, onOpenSource: (Article) -> Unit, onOpenPlugins: () -> Unit) {
    val store by viewModel.store.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    val categories = remember { listOf("全部", "新闻", "商业", "科技", "学术", "社会", "人文", "环境", "健康") }
    val filtered = remember(query, category, store.settings.disabledSources) {
        viewModel.sources.filter {
            (category == "全部" || it.category == category) &&
                (query.isBlank() || it.name.contains(query, true) || it.region.contains(query, true) ||
                    it.description.contains(query, true))
        }
    }
    Scaffold(
        topBar = {
            Column {
                PageHeader(
                    title = "媒体与机构",
                    subtitle = "订阅文章与原站入口，清晰区分"
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("搜索媒体、地区或关键词") }, 
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(8.dp))
                CategoryChips(categories, category) { category = it }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp + LocalFloatingNavPadding.current)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 90.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Extension,
                            contentDescription = null,
                            modifier = Modifier.width(34.dp).height(34.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (viewModel.sources.isEmpty()) "还没有可用的来源" else "没有匹配的来源",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (viewModel.sources.isEmpty()) "来源由爬虫插件提供；Lumen 不内置插件。导入官方 ZIP 后，订阅、媒体目录与「各站前三」会恢复。"
                            else "换一个关键词或分类试试。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (viewModel.sources.isEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onOpenPlugins) { Text("打开插件中心") }
                        }
                    }
                }
            }
            items(filtered, key = { it.id }) { source ->
                val enabled = source.id !in store.settings.disabledSources
                SourceRow(
                    source = source,
                    enabled = enabled,
                    onToggle = { viewModel.toggleSource(source, it) },
                    onOpen = {
                        onOpenSource(
                            Article(
                                id = "source-" + source.id,
                                title = source.name,
                                url = source.url,
                                sourceId = source.id,
                                source = source.name,
                                category = source.category,
                                region = source.region,
                                reach = source.reach,
                                description = source.description,
                                fetchedAt = System.currentTimeMillis()
                            )
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SourceRow(source: Source, enabled: Boolean, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "${source.category} · ${source.region} · 影响力 ${source.reach}/5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (source.description.isNotBlank()) {
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.TextButton(onClick = onOpen) { Text("打开原站") }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
