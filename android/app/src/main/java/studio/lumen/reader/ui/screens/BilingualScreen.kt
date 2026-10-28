@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package studio.lumen.reader.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.data.BilingualParagraph
import java.net.URI
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import studio.lumen.reader.ui.OffscreenExtractor
import studio.lumen.reader.ui.components.GlassButton
import studio.lumen.reader.ui.components.GlassCard
import studio.lumen.reader.ui.components.GlassIconButton

@Composable
fun ReadingScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSite: () -> Unit
) {
    val context = LocalContext.current
    val bilingual by viewModel.bilingual.collectAsState()
    val current by viewModel.currentArticle.collectAsState()
    val store by viewModel.store.collectAsState()
    var saveParagraph by remember { mutableStateOf<BilingualParagraph?>(null) }
    var meaning by remember { mutableStateOf("") }
    var extracting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var bilingualOn by remember { mutableStateOf(false) }
    val scripts = remember { viewModel.catalogScripts() }
    val url = current?.url.orEmpty()
    val extractor = remember(url) { OffscreenExtractor(context, scripts, viewModel::adblockScript) }
    DisposableEffect(extractor) { onDispose { extractor.destroy() } }
    LaunchedEffect(url, attempt) {
        if (url.isBlank() || bilingual != null) return@LaunchedEffect
        extracting = true
        failure = null
        extractor.load(url) { result ->
            extracting = false
            result.onSuccess { viewModel.startReading(it, url) }.onFailure { failure = it.message }
        }
    }
    val article = bilingual
    val translated = article?.paragraphs?.count { it.translation != null } ?: 0
    val progress by animateFloatAsState(
        targetValue = if (article == null || article.paragraphs.isEmpty()) 1f
        else translated.toFloat() / article.paragraphs.size,
        label = "translation-progress"
    )
    val host = remember(article?.url) {
        runCatching { URI(article?.url.orEmpty()).host?.removePrefix("www.") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: article?.url.orEmpty()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("阅读模式", fontFamily = FontFamily.Serif)
                        Text(
                            when {
                                article != null -> "${article.paragraphs.size} 段 · " + if (bilingualOn) "中英对照" else "原文"
                                failure != null -> "抽取失败"
                                else -> "正在后台抽取正文…"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Outlined.Translate,
                        label = "双语",
                        onClick = {
                            bilingualOn = !bilingualOn
                            if (bilingualOn) viewModel.translateAll() else viewModel.clearTranslations()
                        },
                        enabled = article != null,
                        tint = if (bilingualOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    GlassIconButton(
                        icon = if (current?.let { store.saved.any { saved -> saved.article.url == it.url } } == true) {
                            Icons.Outlined.BookmarkAdded
                        } else {
                            Icons.Outlined.BookmarkAdd
                        },
                        label = "收藏",
                        onClick = { current?.let(viewModel::toggleSaved) },
                        enabled = current != null
                    )
                    GlassIconButton(Icons.Outlined.Refresh, "刷新", { attempt++ }, enabled = url.isNotBlank())
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        label = "原站",
                        onClick = onOpenSite,
                        enabled = url.isNotBlank()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (article == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (failure != null) {
                    Text("没能提取到正文", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        failure.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("重新抽取", onClick = { attempt++ })
                        GlassButton("打开原站", onClick = onOpenSite)
                    }
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "正在后台读取原文并抽取正文…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                HeaderCard(article, translated, progress, host, bilingualOn)
            }
            itemsIndexed(article.paragraphs, key = { index, _ -> index }) { index, paragraph ->
                ParagraphBlock(
                    paragraph = paragraph,
                    index = index,
                    fontSize = store.settings.fontSize,
                    showTranslation = bilingualOn,
                    onSave = {
                        saveParagraph = paragraph
                        meaning = ""
                    },
                    onRetry = { viewModel.retryParagraph(index) }
                )
            }
            item {
                FooterCard(onBack = onBack)
            }
        }
    }
    if (saveParagraph != null && article != null) {
        AlertDialog(
            onDismissRequest = { saveParagraph = null },
            title = { Text("保存到词句本") },
            text = {
                Column {
                    Text(
                        saveParagraph!!.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Serif,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = meaning,
                        onValueChange = { meaning = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("释义 / 笔记") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val paragraph = saveParagraph ?: return@TextButton
                    viewModel.addVocab(paragraph.text, meaning, article.title + "\n" + article.url, article.url)
                    saveParagraph = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { saveParagraph = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun HeaderCard(
    article: studio.lumen.reader.data.BilingualArticle,
    translated: Int,
    progress: Float,
    host: String,
    bilingualOn: Boolean
) {
    GlassCard(contentPadding = 22.dp) {
            Text(
                "BILINGUAL READING",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.6.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                article.title,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip("${article.paragraphs.size} 段", MaterialTheme.colorScheme.surface)
                if (bilingualOn) {
                    MetaChip(article.provider, MaterialTheme.colorScheme.surface)
                    if (translated == article.paragraphs.size) {
                        MetaChip("翻译完成", MaterialTheme.colorScheme.tertiaryContainer)
                    }
                } else {
                    MetaChip("原文", MaterialTheme.colorScheme.surface)
                }
            }
            if (bilingualOn) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    !bilingualOn -> "当前只显示原文；点顶栏「双语」可逐段翻译。"
                    translated < article.paragraphs.size -> "正在翻译 $translated / ${article.paragraphs.size} 段 · 原文与译文依次对照"
                    else -> "已翻译 ${article.paragraphs.size} 段 · 机器译文仅供参考，原文完整保留"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (article.warnings.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                article.warnings.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MetaChip(text: String, color: Color) {
    Surface(shape = CircleShape, color = color) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FooterCard(onBack: () -> Unit) {
    Spacer(Modifier.height(28.dp))
    GlassCard(contentPadding = 20.dp) {
            Text(
                "读到这里",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "原文与译文都已保留。可以回到原站继续浏览，也可以把不理解的段落收进词句本，稍后复习。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("回到原文") }
            }
    }
}

@Composable
private fun ParagraphBlock(
    paragraph: BilingualParagraph,
    index: Int,
    fontSize: Int,
    showTranslation: Boolean,
    onSave: () -> Unit,
    onRetry: () -> Unit
) {
    val isHeading = paragraph.tag.startsWith("h")
    val originalStyle = if (isHeading) {
        MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, lineHeight = 32.sp)
    } else {
        MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = fontSize.sp, lineHeight = (fontSize * 1.72).sp)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(top = if (isHeading) 26.dp else 20.dp, bottom = 2.dp)
    ) {
        if (isHeading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text("SECTION ${(index + 1).toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                ParagraphActions(onSave = onSave)
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
        }
        SelectionContainer {
            Text(
                paragraph.text,
                style = originalStyle,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (showTranslation) {
            Spacer(Modifier.height(if (isHeading) 8.dp else 12.dp))
            TranslationBlock(paragraph, isHeading, onRetry)
        }
        if (!isHeading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ParagraphActions(onSave = onSave)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun ParagraphActions(onSave: () -> Unit) {
    Row {
        IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Outlined.BookmarkAdd,
                contentDescription = "保存这段话",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TranslationBlock(paragraph: BilingualParagraph, isHeading: Boolean, onRetry: () -> Unit) {
    val translation = paragraph.translation
    val error = paragraph.error
    if (translation == null && error == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.6.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "正在翻译…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    if (translation == null && error != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                error,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("重试")
            }
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                if (isHeading) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                RoundedCornerShape(14.dp)
            )
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
        )
        Text(
            translation ?: "",
            modifier = Modifier.weight(1f).padding(horizontal = 15.dp, vertical = 13.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 24.sp,
                fontStyle = if (isHeading) FontStyle.Italic else FontStyle.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
