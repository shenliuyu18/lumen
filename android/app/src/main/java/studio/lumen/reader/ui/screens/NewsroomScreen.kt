@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package studio.lumen.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.Article
import studio.lumen.reader.data.NewsroomGroup
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.PluginRequiredCard
import studio.lumen.reader.ui.components.SectionHeader
import studio.lumen.reader.ui.components.relativeTime
import studio.lumen.reader.ui.components.CircleIconButton
import studio.lumen.reader.ui.components.PageHeader

@Composable
fun NewsroomScreen(viewModel: MainViewModel, onOpenArticle: (Article) -> Unit, onOpenPlugins: () -> Unit) {
    val store by viewModel.store.collectAsState()
    val progress by viewModel.refreshProgress.collectAsState()
    val sourceNames = rememberSourceMap(viewModel)
    Scaffold(
        topBar = {
            Column {
                PageHeader(
                    title = "各站前三",
                    subtitle = "123 个来源的首页候选与订阅顺序",
                    actions = {
                        CircleIconButton(Icons.Outlined.Refresh, "更新各站前三") { if (!progress.running) viewModel.refresh() }
                    }
                )
                if (progress.running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "更新中 ${progress.done}/${progress.total} · ${progress.source}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        val newsroom = store.newsroom
        if (viewModel.sources.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                PluginRequiredCard(onOpenPlugins)
            }
            return@Scaffold
        }
        if (newsroom == null || newsroom.groups.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                if (viewModel.sources.isEmpty()) {
                    PluginRequiredCard(onOpenPlugins)
                } else {
                    Text("还没有各站前三", style = MaterialTheme.typography.titleLarge)
                    Text("点击右上角更新，或先更新一次桌面版资料库。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp + LocalFloatingNavPadding.current)
        ) {
            item {
                SectionHeader(
                    "FRONT PAGES",
                    "${newsroom.coveredCount}/${newsroom.sourceCount} 个来源 · ${newsroom.storyCount} 条头条",
                    action = {
                        Text(
                            "更新于 ${relativeTime(newsroom.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
            items(newsroom.groups, key = { it.sourceId }) { group ->
                GroupCard(group, sourceNames[group.sourceId] ?: group.sourceId, onOpenArticle)
            }
        }
    }
}

@Composable
private fun rememberSourceMap(viewModel: MainViewModel): Map<String, String> =
    androidx.compose.runtime.remember(viewModel.sources) { viewModel.sources.associate { it.id to it.name } }

@Composable
private fun GroupCard(group: NewsroomGroup, sourceName: String, onOpenArticle: (Article) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row {
                Text(sourceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        group.stale -> "旧快照"
                        group.basis == "feed" -> "订阅顺序"
                        else -> "首页顺序"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (group.items.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    group.error ?: "暂无可用头条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                group.items.forEachIndexed { index, article ->
                    TextButton(
                        onClick = { onOpenArticle(article) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                article.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Serif,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (article.description.isNotBlank()) {
                                Text(
                                    article.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
