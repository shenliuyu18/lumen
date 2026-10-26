package studio.lumen.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.lumen.reader.data.Article
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SectionHeader(title: String, kicker: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (kicker != null) Text(kicker, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        action?.invoke()
    }
}

@Composable
fun CategoryChips(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            GlassChip(label = category, selected = selected == category, onClick = { onSelect(category) })
        }
    }
}

@Composable
fun ArticleCard(
    article: Article,
    saved: Boolean,
    onClick: () -> Unit,
    onToggleSaved: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        article.source,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(article.category + " · " + relativeTime(article.publishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (article.score > 0) {
                    GlassChip(label = "推荐 ${article.score}", selected = false, onClick = onAsk)
                }
                GlassIconButton(
                    icon = if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    label = "收藏",
                    onClick = onToggleSaved
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                article.title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (article.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    article.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                article.quality?.label?.takeIf { it.isNotBlank() }?.let {
                    GlassChip(label = it, selected = false, onClick = onAsk)
                }
                Spacer(Modifier.weight(1f))
                GlassButton(label = "提问", onClick = onAsk)
                Spacer(Modifier.width(8.dp))
                GlassButton(label = "阅读", onClick = onClick, icon = Icons.Outlined.ArrowForward)
            }
        }
}

fun relativeTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "时间未知"
    val delta = System.currentTimeMillis() - timestamp
    val minutes = delta / 60_000
    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)} 分钟前"
        minutes < 1440 -> "${minutes / 60} 小时前"
        minutes < 10080 -> "${minutes / 1440} 天前"
        else -> DateTimeFormatter.ofPattern("M月d日").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
    }
}
