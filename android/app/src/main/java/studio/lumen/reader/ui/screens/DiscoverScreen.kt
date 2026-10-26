@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package studio.lumen.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.Article
import studio.lumen.reader.ui.components.ArcGauge
import studio.lumen.reader.ui.components.ArticleCard
import studio.lumen.reader.ui.components.CircleIconButton
import studio.lumen.reader.ui.components.ListRow
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.PageHeader
import studio.lumen.reader.ui.components.PanelCard
import studio.lumen.reader.ui.components.PluginRequiredCard
import studio.lumen.reader.ui.components.SectionLabel
import studio.lumen.reader.ui.components.StatCard
import studio.lumen.reader.ui.components.UnderlineTabs
import studio.lumen.reader.ui.components.relativeTime
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val todayCategories = listOf("全部", "新闻", "商业", "科技", "学术", "社会", "人文", "环境", "健康")
private const val dailyGoal = 3

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
    val filtered = remember(ranked, query, category) {
        ranked.filter {
            (category == "全部" || it.category == category) &&
                (query.isBlank() || it.title.contains(query, true) || it.source.contains(query, true) ||
                    it.description.contains(query, true))
        }
    }
    val savedUrls = remember(store.saved) { store.saved.map { it.article.url }.toSet() }
    val history = store.history
    val todayRead = remember(history) {
        val today = LocalDate.now(ZoneId.systemDefault())
        history.count { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.readAt), ZoneId.systemDefault()) == today }
    }
    val hours = (store.readingSeconds / 3600f)

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp + LocalFloatingNavPadding.current),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PageHeader(
                    title = "精选",
                    subtitle = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)) +
                        " · 保持好奇，读真实的英文",
                    actions = {
                        CircleIconButton(Icons.Outlined.Refresh, "更新文章") { if (!progress.running) viewModel.refresh() }
                        CircleIconButton(Icons.Outlined.Style, "各站前三", onClick = onOpenNewsroom)
                    }
                )
            }
            if (progress.running) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "更新中 ${progress.done}/${progress.total} ${progress.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                ReadingGoalCard(todayRead = todayRead, hours = hours, articles = store.articles.size)
            }
            if (history.isNotEmpty()) {
                item {
                    SectionLabel("最近阅读", Modifier.padding(horizontal = 20.dp))
                }
                item {
                    PanelCard(Modifier.padding(horizontal = 20.dp)) {
                        history.take(3).forEachIndexed { index, entry ->
                            if (index > 0) Divider()
                            ListRow(
                                icon = Icons.Outlined.History,
                                title = entry.article.title,
                                subtitle = "${entry.article.source} · ${relativeTime(entry.readAt)}",
                                onClick = { onOpenArticle(entry.article) },
                                trailing = {
                                    Icon(
                                        Icons.Outlined.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
                item {
                    CoverStrip(history.map { it.article }.distinctBy { it.url }.take(12)) { onOpenArticle(it) }
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
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            item {
                UnderlineTabs(
                    titles = todayCategories,
                    selected = todayCategories.indexOf(category).coerceAtLeast(0),
                    onSelect = { category = todayCategories[it]; visibleCount = 30 },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
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
                    SectionLabel("规则推荐 · 今日精选", Modifier.padding(horizontal = 20.dp))
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
private fun ReadingGoalCard(todayRead: Int, hours: Float, articles: Int) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        PanelCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("今日阅读目标", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "打开 $dailyGoal 篇，认真读一点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArcGauge(
                    progress = todayRead.toFloat() / dailyGoal,
                    center = "$todayRead / $dailyGoal",
                    unit = "篇 · 今日"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(Icons.Outlined.History, "$articles 篇", "已收录", Modifier.weight(1f))
                StatCard(Icons.Outlined.Timer, "${hours.roundToInt()} 小时", "累计时长", Modifier.weight(1f))
            }
        }
    }
}

/** 封面横滚：没有真实封面，用来源首字与色块代替。 */
@Composable
private fun CoverStrip(articles: List<Article>, onOpen: (Article) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(articles, key = { it.id }) { article ->
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .clickable { onOpen(article) }
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 104.dp, height = 140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        article.source.take(2),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    article.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun HeroCard(article: Article, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "今日首读 · ${article.category}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(14.dp))
            Text(
                article.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                article.source + " · " + (article.quality?.label ?: "拓展阅读"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (article.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    article.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
