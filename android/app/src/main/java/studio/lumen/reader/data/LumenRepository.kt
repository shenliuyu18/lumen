package studio.lumen.reader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// 多数站点会拒绝非浏览器 UA，用桌面 Edge 的标识抓 RSS 才不会被 403。
private const val UserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"

class LumenRepository(private val context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val catalog = Catalog(context)
    private val pluginManager = PluginManager(context, context.filesDir)
    private val storeFile = File(context.filesDir, "library.json")
    private val refreshMutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var current = loadOrSeed()
    var sources: List<Source> = pluginManager.sources(current.settings.disabledPlugins)
        private set
    val plugins = kotlinx.coroutines.flow.MutableStateFlow(pluginManager.infos(current.settings.disabledPlugins))

    val store: Store get() = current
    val refreshProgress = kotlinx.coroutines.flow.MutableStateFlow(RefreshProgress())
    private val _store = kotlinx.coroutines.flow.MutableStateFlow(current)
    val storeFlow: kotlinx.coroutines.flow.StateFlow<Store> get() = _store

    fun catalogScripts(): Pair<String, String> = catalog.readabilityScript() to catalog.extractionScript()
    fun blockHosts(): Set<String> =
        pluginManager.blockHosts(current.settings.disabledPlugins)

    fun adblockScript(): String = pluginManager.adScript(current.settings.disabledPlugins)
    fun pluginDirectory(): File = pluginManager.directory

    fun togglePlugin(id: String, enabled: Boolean): Store {
        val disabled = current.settings.disabledPlugins.toMutableList()
        disabled.remove(id)
        if (!enabled) disabled += id
        val next = current.copy(settings = current.settings.copy(disabledPlugins = disabled))
        publish(next); save(next)
        return next
    }

    fun installPlugin(uri: android.net.Uri): PluginInfo {
        val info = pluginManager.install(uri)
        refreshSources()
        return info
    }

    fun removePlugin(id: String) {
        pluginManager.remove(id)
        refreshSources()
    }

    private fun refreshSources() {
        sources = pluginManager.sources(current.settings.disabledPlugins)
        plugins.value = pluginManager.infos(current.settings.disabledPlugins)
    }

    private fun publish(next: Store) {
        current = next
        _store.value = next
        refreshSources()
    }

    private fun loadOrSeed(): Store {
        runCatching {
            if (storeFile.exists()) {
                return json.decodeFromString<Store>(storeFile.readText())
            }
        }
        val seed = catalog.snapshot()
        val seeded = Store(
            articles = seed?.articles ?: emptyList(),
            statuses = seed?.statuses ?: emptyMap(),
            newsroom = catalog.newsroom(),
            lastRefresh = seed?.fetchedAt
        )
        runCatching { save(seeded) }
        return seeded
    }

    private fun diskStore(value: Store): Store = if (!value.settings.lowStorage) value else value.copy(
        articles = emptyList(),
        translations = emptyMap(),
        statuses = emptyMap(),
        newsroom = null,
        newsroomCleared = true,
        articlesCleared = true
    )

    private fun save(value: Store) {
        runCatching {
            val tmp = File(storeFile.parentFile, "library.json.tmp")
            tmp.writeText(json.encodeToString(diskStore(value)))
            if (storeFile.exists()) storeFile.delete()
            tmp.renameTo(storeFile)
        }
    }

    fun updateSettings(settings: Settings) {
        val next = current.copy(settings = settings)
        publish(next)
        save(next)
        refreshSources()
    }

    fun addVocab(text: String, meaning: String, contextText: String, url: String): Store {
        val cleanText = text.trim().take(1500)
        if (cleanText.isBlank()) return current
        if (current.vocabulary.any { it.text.equals(cleanText, ignoreCase = true) }) return current
        val now = System.currentTimeMillis()
        val card = Vocab(
            id = UUID.randomUUID().toString(),
            text = cleanText,
            meaning = meaning.take(3000),
            context = contextText.take(2000),
            url = Ranker.safeUrl(url) ?: "",
            createdAt = now,
            dueAt = now
        )
        val next = current.copy(vocabulary = listOf(card) + current.vocabulary)
        publish(next); save(next); return next
    }

    fun deleteVocab(id: String) {
        val next = current.copy(vocabulary = current.vocabulary.filterNot { it.id == id })
        publish(next); save(next)
    }

    fun reviewVocab(id: String, known: Boolean) {
        val next = current.copy(vocabulary = current.vocabulary.map {
            if (it.id == id) Ranker.reviewCard(it, known, System.currentTimeMillis()) else it
        })
        publish(next); save(next)
    }

    fun toggleSaved(article: Article) {
        val url = Ranker.safeUrl(article.url) ?: return
        val existing = current.saved.any { it.article.url == url }
        val saved = if (existing) current.saved.filterNot { it.article.url == url } else
            listOf(SavedItem(article.copy(url = url), System.currentTimeMillis())) + current.saved
        val next = current.copy(saved = saved)
        publish(next); save(next)
    }

    fun addHistory(article: Article) {
        if (!current.settings.recordHistory) return
        val url = Ranker.safeUrl(article.url) ?: return
        val history = listOf(HistoryItem(article.copy(url = url), System.currentTimeMillis())) +
            current.history.filterNot { it.article.url == url }
        val next = current.copy(history = history.take(500))
        publish(next); save(next)
    }

    fun deleteHistory(url: String) {
        val next = current.copy(history = current.history.filterNot { it.article.url == url })
        publish(next); save(next)
    }

    fun clearHistory() {
        val next = current.copy(history = emptyList(), readingSeconds = 0)
        publish(next); save(next)
    }

    fun addReadingSeconds(seconds: Long) {
        if (seconds <= 0) return
        val next = current.copy(readingSeconds = current.readingSeconds + seconds.coerceAtMost(1800))
        publish(next); save(next)
    }

    fun findTranslation(key: String): Translation? = current.translations[key]

    fun storeTranslation(key: String, translation: Translation) {
        val translations = current.translations.toMutableMap()
        translations[key] = translation
        if (translations.size > 1500) {
            translations.entries.minByOrNull { it.value.at }?.let { translations.remove(it.key) }
        }
        val next = current.copy(translations = translations)
        publish(next); save(next)
    }

    fun importBackup(text: String): ImportedBackup {
        val imported = BackupManager.import(text)
        val vocabSeen = current.vocabulary.map { it.text.lowercase() }.toMutableSet()
        val vocabulary = current.vocabulary.toMutableList()
        imported.vocabulary.forEach {
            if (vocabSeen.add(it.text.lowercase())) vocabulary += it
        }
        val savedSeen = current.saved.map { it.article.url }.toMutableSet()
        val saved = current.saved.toMutableList()
        imported.saved.forEach {
            if (savedSeen.add(it.article.url)) saved += it
        }
        val historySeen = current.history.map { it.article.url }.toMutableSet()
        val history = current.history.toMutableList()
        imported.history.forEach {
            if (historySeen.add(it.article.url)) history += it
        }
        val next = current.copy(
            vocabulary = vocabulary.take(10000),
            saved = saved.take(10000),
            history = history.take(10000)
        )
        publish(next); save(next)
        return imported
    }

    fun backupJson(): String = BackupManager.export(current)

    fun vocabularyCsv(): String = BackupManager.vocabularyCsv(current.vocabulary)

    suspend fun refresh(): Store = refreshMutex.withLock {
        val base = current
        val enabled = sources.filter { it.id !in base.settings.disabledSources }
        val originals = enabled.filter { it.feed.isNotBlank() }
        val desk = enabled
        val total = originals.size + desk.size
        var done = AtomicInteger(0)
        refreshProgress.value = RefreshProgress(true, 0, total)
        fun report(name: String) {
            val value = done.incrementAndGet()
            refreshProgress.value = RefreshProgress(true, value, total, name)
        }

        val feedItems = ConcurrentHashMap<String, List<Article>>()
        val statuses = ConcurrentHashMap<String, Status>()
        val queue = java.util.ArrayDeque(originals)
        val queueMutex = Mutex()
        val newsroomDeferred = coroutineScope {
            val newsroomJob = async(Dispatchers.IO) {
                updateNewsroom(desk, base.newsroom, feedItems, ::report)
            }
            coroutineScope {
                repeat(12) {
                    launch(Dispatchers.IO) {
                        while (true) {
                            val source = queueMutex.withLock { queue.pollFirst() } ?: break
                            val at = System.currentTimeMillis()
                            runCatching {
                                val body = fetch(source.feed, 18)
                                val items = parseFeed(body, source)
                                if (items.isEmpty()) error("未发现有效文章")
                                feedItems[source.id] = items
                                statuses[source.id] = Status(true, items.size, "", at)
                            }.onFailure {
                                statuses[source.id] = Status(false, 0, it.message ?: "抓取失败", at)
                            }
                            report(source.name)
                        }
                    }
                }
            }
            newsroomJob.await()
        }

        val merged = Ranker.merge(base.articles, feedItems.values.flatten())
        val next = base.copy(
            articles = merged,
            statuses = base.statuses + statuses,
            newsroom = newsroomDeferred,
            newsroomCleared = false,
            articlesCleared = false,
            lastRefresh = System.currentTimeMillis()
        )
        publish(next); save(next)
        refreshProgress.value = RefreshProgress(false, total, total)
        next
    }

    private suspend fun updateNewsroom(
        sources: List<Source>,
        previous: Newsroom?,
        feedItems: Map<String, List<Article>>,
        report: (String) -> Unit
    ): Newsroom {
        val previousBySource = previous?.groups?.associateBy { it.sourceId } ?: emptyMap()
        val groups = ConcurrentHashMap<String, NewsroomGroup>()
        val queue = java.util.ArrayDeque(sources)
        val queueMutex = Mutex()
        coroutineScope {
            repeat(6) {
                launch(Dispatchers.IO) {
                    while (true) {
                        val source = queueMutex.withLock { queue.pollFirst() } ?: break
                        val checkedAt = System.currentTimeMillis()
                        var items = emptyList<Article>()
                        var basis = "homepage"
                        var error: String? = null
                        runCatching {
                            val spec = pluginManager.newsroomSpec(current.settings.disabledPlugins)
                            val rule = spec?.overrides?.get(source.id)
                                ?: spec?.default
                                ?: studio.lumen.reader.data.NewsroomRule()
                            items = homepageHeadlines(fetch(source.url, 11), source, rule, spec)
                        }.onFailure { error = it.message }
                        if (items.size < 3 && source.feed.isNotBlank()) {
                            val feed = feedItems[source.id]?.take(3) ?: runCatching {
                                parseFeed(fetch(source.feed, 11), source).take(3)
                            }.getOrElse {
                                if (items.isEmpty()) error = it.message
                                emptyList()
                            }
                            if (feed.size > items.size) {
                                items = feed
                                basis = "feed"
                                error = null
                            }
                        }
                        val fetchedAt = System.currentTimeMillis()
                        val group = if (items.isNotEmpty()) {
                            NewsroomGroup(source.id, basis, items, checkedAt, fetchedAt, false, null)
                        } else {
                            val old = previousBySource[source.id]
                            if (old != null && old.items.isNotEmpty()) {
                                old.copy(checkedAt = checkedAt, stale = true, error = error ?: "未识别到正文链接")
                            } else {
                                NewsroomGroup(
                                    sourceId = source.id,
                                    basis = null,
                                    items = emptyList(),
                                    checkedAt = checkedAt,
                                    fetchedAt = null,
                                    stale = false,
                                    error = error ?: "首页需要动态加载或未找到文章入口"
                                )
                            }
                        }
                        groups[source.id] = group
                        report(source.name)
                    }
                }
            }
        }
        val ordered = sources.mapNotNull { groups[it.id] }
        return Newsroom(
            updatedAt = System.currentTimeMillis(),
            groups = ordered,
            sourceCount = sources.size,
            coveredCount = ordered.count { it.items.isNotEmpty() },
            storyCount = ordered.sumOf { it.items.size }
        )
    }

    private fun fetch(url: String, timeoutSeconds: Long): String = try {
        fetchOnce(url, timeoutSeconds)
    } catch (error: Exception) {
        // 超时或连接被重置时换一条连接再试一次；403 之类的拒绝不重试。
        if (!retryable(error)) throw friendly(error)
        Thread.sleep(300)
        try {
            fetchOnce(url, timeoutSeconds)
        } catch (retryError: Exception) {
            throw friendly(retryError)
        }
    }

    private fun fetchOnce(url: String, timeoutSeconds: Long): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, text/html;q=0.7, */*;q=0.5")
            .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")
            .build()
        val client = http.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("空响应")
            if (body.length > 6_000_000) error("内容过大")
            return body
        }
    }

    private fun retryable(error: Exception): Boolean {
        val text = error.message ?: return false
        return error is java.net.SocketTimeoutException ||
            text.contains("timeout", ignoreCase = true) ||
            text.contains("reset", ignoreCase = true) ||
            text.contains("EOF", ignoreCase = true)
    }

    private fun friendly(error: Exception): IllegalStateException {
        val text = error.message ?: "抓取失败"
        val message = when {
            error is java.net.SocketTimeoutException || text.contains("timeout", ignoreCase = true) ->
                "网络超时：该站点当前不可达，稍后会自动重试"
            text.contains("HTTP 403") -> "HTTP 403：站点拒绝抓取，可能需要更换网络或代理"
            text.contains("HTTP 429") -> "HTTP 429：请求过于频繁，请稍后再试"
            text.contains("HTTP 5") -> "$text：站点暂时故障"
            text.contains("Unable to resolve host") -> "域名解析失败：检查网络或 DNS"
            else -> text
        }
        return IllegalStateException(message)
    }

    private fun parseFeed(xml: String, source: Source): List<Article> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val items = if (doc.select("item").isNotEmpty()) doc.select("item") else doc.select("entry")
        return items.take(25).mapNotNull { item ->
            val title = item.selectFirst("title")?.text()
            val link = item.selectFirst("link")?.let {
                it.attr("href").ifBlank { it.text() }
            }
            val description = item.selectFirst("description")?.text()
                ?: item.selectFirst("summary")?.text()
                ?: item.selectFirst("content")?.text()
            val dateText = item.selectFirst("pubDate")?.text()
                ?: item.selectFirst("published")?.text()
                ?: item.selectFirst("updated")?.text()
            Ranker.normalize(title, link, description, parseDate(dateText), source)
        }
    }

    private fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        runCatching { return DateTimeFormatter.RFC_1123_DATE_TIME.parse(raw, ZonedDateTime::from).toInstant().toEpochMilli() }
        runCatching { return Date.parse(raw) }
        return null
    }

    private fun homepageHeadlines(
        html: String,
        source: Source,
        rule: NewsroomRule,
        spec: NewsroomSpec?
    ): List<Article> {
        val doc = Jsoup.parse(html, source.url)
        val base = URI(source.url)
        val seen = HashSet<String>()
        val items = mutableListOf<Article>()
        fun regex(value: String, fallback: String): Regex =
            runCatching { Regex(value.ifBlank { fallback }) }.getOrElse { Regex("$^") }
        val exclude = rule.excludeSelector.ifBlank { "nav,footer,[role=navigation],.nav,.breadcrumb,.advertisement" }
        val headingSelector = rule.headingSelector.ifBlank { "h1,h2,h3,h4" }
        val titleMin = if (rule.titleMin > 0) rule.titleMin else 20
        val titleMax = if (rule.titleMax > 0) rule.titleMax else 260
        val maxItems = if (rule.maxItems > 0) rule.maxItems else 3
        val denyPath = regex(rule.denyPathPattern, """(?i)/(?:topics?|tags?|authors?|categories|search|subscribe|login|privacy|about|bios|contact|careers|events)\b""")
        val denyTitle = regex(rule.denyTitlePattern, """(?i)^(read more|learn more|view all|sign in|subscribe|latest news|all news|news and events|see all)""")
        val institutionPath = regex(rule.institutionPathPattern, """(?i)news|press|release|stories|story|article|blog|insight|publication|speech|update|\d{4}""")
        val storyPath = regex(rule.storyPathPattern, """\d{4}|/article|/story|/news/.+|/press""")
        val denyTitles = rule.denyTitles.map { it.lowercase() }.toSet()
        val institution = source.region == (spec?.institutionRegion ?: "国际机构") ||
            source.id in (spec?.institutionSources ?: emptyList())
        for (anchor in doc.select("a[href]")) {
            if (anchor.closest(exclude) != null) continue
            val heading = anchor.selectFirst(headingSelector)
            val candidates = listOfNotNull(heading?.text(), anchor.text(), anchor.attr("aria-label").ifBlank { null })
            val title = candidates.firstOrNull {
                val clean = it.replace(Regex("""\s+"""), " ").trim()
                clean.length in titleMin..titleMax && !Regex("""(?i)^(https?:|www\.)""").containsMatchIn(clean)
            }?.replace(Regex("""\s+"""), " ")?.trim() ?: continue
            if (denyTitle.containsMatchIn(title) || title.lowercase() in denyTitles) continue
            val target = runCatching { base.resolve(anchor.attr("href")) }.getOrNull() ?: continue
            val url = Ranker.safeUrl(target.toString()) ?: continue
            val resolved = URI(url)
            if (resolved.host?.removePrefix("www.") != base.host?.removePrefix("www.")) continue
            val path = resolved.path ?: "/"
            if (path == base.path || path == "/" || denyPath.containsMatchIn(path)) continue
            if (institution && !institutionPath.containsMatchIn(path)) continue
            val inArticle = anchor.closest("article") != null
            if (heading == null && !inArticle && !storyPath.containsMatchIn(path)) continue
            val key = "${resolved.scheme}://${resolved.host}$path"
            if (!seen.add(key)) continue
            Ranker.normalize(title, url, "", null, source)?.let { items += it }
            if (items.size >= maxItems) break
        }
        return items
    }
}
