@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package studio.lumen.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.Article
import studio.lumen.reader.ui.components.ArticleCard
import studio.lumen.reader.ui.components.CategoryChips
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.PluginRequiredCard
import studio.lumen.reader.ui.components.SectionHeader

@Composable
fun DiscoverScreen(
    viewModel: MainViewModel,
    onOpenArticle: (Article) -> Unit,
    onOpenNewsroom: () -> Unit,
    onOpenPlugins: () -> Unit
) {
    val store by viewModel.store.collectAsState()
    val ranked by viewModel.ranked.collectAsState()
    val progress by viewModel.refreshProgress.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var visibleCount by rememberSaveable { mutableIntStateOf(30) }
    val categories = remember { listOf("全部", "新闻", "商业", "科技", "学术", "社会", "人文", "环境", "健康") }
    val filtered = remember(ranked, query, category) {
        ranked.filter {
            (category == "全部" || it.category == category) &&
                (query.isBlank() || it.title.contains(query, true) || it.source.contains(query, true) ||
                    it.description.contains(query, true))
        }
    }
    val savedUrls = remember(store.saved) { store.saved.map { it.article.url }.toSet() }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Lumen", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
                            Text("观读 · 英文世界", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }, enabled = !progress.running) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "更新文章")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                if (progress.running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "更新中 ${progress.done}/${progress.total} ${progress.source}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp + LocalFloatingNavPadding.current),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("YOUR READING SPACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "从一篇好文章，打开世界。",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    val today = java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)
                    )
                    Text(
                        "$today · 保持好奇，读真实的英文。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (viewModel.sources.isEmpty()) {
                item {
                    PluginRequiredCard(
                        onOpenPlugins = onOpenPlugins,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; visibleCount = 30 },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("搜索文章、媒体或关键词") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                CategoryChips(categories, category) { category = it; visibleCount = 30 }
            }
            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("没有匹配的文章", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { query = ""; category = "全部" }) { Text("清除筛选") }
                    }
                }
            } else {
                item {
                    val hero = filtered.first()
                    HeroCard(
                        article = hero,
                        saved = hero.url in savedUrls,
                        onOpen = { onOpenArticle(hero) },
                        onSave = { viewModel.toggleSaved(hero) }
                    )
                }
                item {
                    SectionHeader("THE EDITOR'S ALGORITHM", "规则推荐 · 今日精选") {
                        TextButton(onClick = onOpenNewsroom) {
                            Text("各站前三")
                            Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                        }
                    }
                }
                items(filtered.drop(1).take(visibleCount - 1), key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        saved = article.url in savedUrls,
                        onClick = { onOpenArticle(article) },
                        onToggleSaved = { viewModel.toggleSaved(article) },
                        onAsk = { onOpenArticle(article) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                if (visibleCount < filtered.size) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { visibleCount += 30 }) { Text("加载更多") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(article: Article, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = cardColors,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("TODAY'S FIRST READ · ${article.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(
                article.title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                article.source + " · " + (article.quality?.label ?: "拓展阅读"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (article.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    article.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onOpen) {
                    Text("开始阅读")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSave) { Text(if (saved) "已收藏" else "稍后阅读") }
            }
        }
    }
}
