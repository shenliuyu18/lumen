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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.Article
import studio.lumen.reader.data.SavedItem
import studio.lumen.reader.data.Vocab
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.relativeTime
import studio.lumen.reader.ui.components.CircleIconButton
import studio.lumen.reader.ui.components.PageHeader
import studio.lumen.reader.ui.components.UnderlineTabs
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import studio.lumen.reader.ui.components.ArcGauge
import studio.lumen.reader.ui.components.GlassCard
import studio.lumen.reader.ui.components.StatCard

private val studyTabs = listOf("概览", "词句本", "复习", "收藏", "足迹")

@Composable
fun StudyScreen(viewModel: MainViewModel, onOpenArticle: (Article) -> Unit) {
    val store by viewModel.store.collectAsState()
    val dailyGoal = store.settings.dailyGoal.coerceAtLeast(1)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            Column {
                PageHeader(
                    title = "学习空间",
                    subtitle = "词句、收藏、足迹与复习",
                    actions = {
                        if (tab == 4) {
                            CircleIconButton(Icons.Outlined.DeleteSweep, "清空足迹", onClick = { viewModel.clearHistory() })
                        }
                    }
                )
                UnderlineTabs(
                    titles = studyTabs,
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> OverviewTab(store, dailyGoal, Modifier.padding(padding))
            1 -> VocabTab(store.vocabulary, viewModel, Modifier.padding(padding))
            2 -> ReviewTab(store.vocabulary.filter { it.dueAt <= System.currentTimeMillis() }, viewModel, Modifier.padding(padding))
            3 -> SavedTab(store.saved, viewModel, onOpenArticle, Modifier.padding(padding))
            else -> HistoryTab(store.history, store.readingSeconds, viewModel, onOpenArticle, Modifier.padding(padding))
        }
    }
}

@Composable
private fun OverviewTab(store: studio.lumen.reader.data.Store, dailyGoal: Int, modifier: Modifier = Modifier) {
    val today = LocalDate.now(ZoneId.systemDefault())
    val todayRead = store.history.count {
        LocalDate.ofInstant(Instant.ofEpochMilli(it.readAt), ZoneId.systemDefault()) == today
    }
    val hours = store.readingSeconds / 3600f
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日阅读目标", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "打开 $dailyGoal 篇，认真读一点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArcGauge(progress = todayRead.toFloat() / dailyGoal, center = "$todayRead / $dailyGoal", unit = "篇 · 今日")
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Outlined.Article, "${store.articles.size} 篇", "已收录", Modifier.weight(1f))
                StatCard(Icons.Outlined.Timer, "${hours.roundToInt()} 小时", "累计时长", Modifier.weight(1f))
            }
        }
        GlassCard {
            Text("学习数据", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Outlined.MenuBook, "${store.vocabulary.size} 条", "词句收藏", Modifier.weight(1f))
                StatCard(Icons.Outlined.Bookmark, "${store.saved.size} 篇", "稍后阅读", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Outlined.History, "${store.history.size} 次", "阅读足迹", Modifier.weight(1f))
                StatCard(
                    Icons.Outlined.Style,
                    "${store.vocabulary.count { it.dueAt <= System.currentTimeMillis() }} 条",
                    "待复习",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VocabTab(vocabulary: List<Vocab>, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    if (vocabulary.isEmpty()) {
        EmptyStudy("还没有词句", "在阅读页划词，或从桌面版导入学习备份。", modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalFloatingNavPadding.current), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vocabulary, key = { it.id }) { card ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        AssistChip(onClick = {}, label = { Text(if (card.dueAt <= System.currentTimeMillis()) "待复习" else "Lv.${card.level}") })
                        IconButton(onClick = { viewModel.deleteVocab(card.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    }
                    if (card.meaning.isNotBlank()) {
                        Text(card.meaning, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (card.context.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(card.context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.reviewVocab(card.id, false) }) { Text("还没记住") }
                        Button(onClick = { viewModel.reviewVocab(card.id, true) }) { Text("记住了") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewTab(due: List<Vocab>, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    if (due.isEmpty()) {
        EmptyStudy("今天没有待复习", "继续阅读，遇到值得记住的表达再存入词句本。", modifier)
        return
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val card = due.getOrNull(index % due.size) ?: return
    Column(modifier = modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("间隔复习", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.text, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                if (revealed) {
                    Text(card.meaning, style = MaterialTheme.typography.bodyLarge)
                    if (card.context.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(card.context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    TextButton(onClick = { revealed = true }) { Text("显示释义") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    viewModel.reviewVocab(card.id, false)
                    revealed = false
                    index++
                }) { Text("还没记住") }
                Button(onClick = {
                    viewModel.reviewVocab(card.id, true)
                    revealed = false
                    index++
                }) {
                    Text("记住了")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("本轮 ${(index + 1).coerceAtMost(due.size)}/${due.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedTab(saved: List<SavedItem>, viewModel: MainViewModel, onOpenArticle: (Article) -> Unit, modifier: Modifier = Modifier) {
    if (saved.isEmpty()) {
        EmptyStudy("还没有稍后阅读", "在文章卡片或阅读页点收藏。", modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalFloatingNavPadding.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(saved, key = { it.article.url }) { item ->
            Card(onClick = { onOpenArticle(item.article) }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.article.title, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
                        Text(item.article.source + " · " + relativeTime(item.savedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.toggleSaved(item.article) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "取消收藏")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<studio.lumen.reader.data.HistoryItem>,
    readingSeconds: Long,
    viewModel: MainViewModel,
    onOpenArticle: (Article) -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) {
        EmptyStudy("还没有阅读足迹", "记录功能开启后，打开文章会自动记录。", modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalFloatingNavPadding.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("累计阅读 ${readingSeconds / 60} 分钟", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        items(history, key = { it.article.url }) { item ->
            Card(onClick = { onOpenArticle(item.article) }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.article.title, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
                        Text(item.article.source + " · " + relativeTime(item.readAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.deleteHistory(item.article.url) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除足迹")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStudy(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
