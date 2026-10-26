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
import studio.lumen.reader.ui.components.liquidGlass
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.Article
import studio.lumen.reader.ui.components.ArcGauge
import studio.lumen.reader.ui.components.ArticleCard
import studio.lumen.reader.ui.components.CircleIconButton
import studio.lumen.reader.ui.components.GlassButton
import studio.lumen.reader.ui.components.GlassCard
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

@Composable
fun DiscoverScreen(
    viewModel: MainViewModel,
    onOpenArticle: (Article) -> Unit,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .liquidGlass(shape = RoundedCornerShape(percent = 50), blurRadius = 10.dp, lensRadius = 16.dp, interactive = false)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; visibleCount = 30 },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "搜索文章、媒体或关键词",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }
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
private fun HeroCard(article: Article, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    GlassCard(
        modifier = Modifier.padding(horizontal = 20.dp),
        onClick = onOpen,
        contentPadding = 0.dp
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
                GlassButton(label = "开始阅读", onClick = onOpen, icon = Icons.Outlined.ArrowForward)
                Spacer(Modifier.width(8.dp))
                GlassButton(label = if (saved) "已收藏" else "稍后阅读", onClick = onSave)
            }
        }
    }
}
