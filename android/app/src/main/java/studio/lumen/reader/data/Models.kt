package studio.lumen.reader.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Weights(
    val freshness: Int = 30,
    val reach: Int = 20,
    val buzz: Int = 15,
    val frontier: Int = 20,
    val learning: Int = 15
)

@Serializable
data class TranslationSettings(
    val provider: String = "microsoft-web",
    val region: String = "",
    val deeplPlan: String = "free",
    val useContext: Boolean = true,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "gpt-4o-mini"
)

@Serializable
data class Settings(
    val themeMode: String = "system",
    val colorScheme: String = "gr",
    val dynamicColor: Boolean = true,
    val weights: Weights = Weights(),
    val interests: List<String> = emptyList(),
    val disabledSources: List<String> = emptyList(),
    val fontSize: Int = 18,
    val autoRefresh: Boolean = false,
    val recordHistory: Boolean = true,
    val lowStorage: Boolean = false,
    val glassEffect: String = "liquid",
    val cornerRadius: Int = 16,
    val dailyGoal: Int = 10,
    val disabledPlugins: List<String> = emptyList(),
    val translation: TranslationSettings = TranslationSettings()
)

@Serializable
data class Source(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val region: String = "",
    val url: String = "",
    val feed: String = "",
    val reach: Int = 3,
    val description: String = ""
)

@Serializable
data class Quality(
    val score: Int = 0,
    val reasons: List<String> = emptyList(),
    val label: String = "",
    val basis: String = ""
)

@Serializable
data class Metrics(
    val freshness: Int = 0,
    val reach: Int = 0,
    val buzz: Int = 0,
    val frontier: Int = 0,
    val learning: Int = 0
)

@Serializable
data class Article(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val sourceId: String = "",
    val source: String = "",
    val category: String = "",
    val region: String = "",
    val reach: Int = 3,
    val description: String = "",
    val publishedAt: Long? = null,
    val fetchedAt: Long = 0,
    val score: Int = 0,
    val quality: Quality? = null,
    val metrics: Metrics? = null,
    val peerCount: Int = 0,
    val interest: Int = 0
) {
    fun published(): Long = publishedAt ?: 0L
}

@Serializable
data class SavedItem(
    val article: Article = Article(),
    val savedAt: Long = 0
)

@Serializable
data class HistoryItem(
    val article: Article = Article(),
    val readAt: Long = 0
)

@Serializable
data class Vocab(
    val id: String = "",
    val text: String = "",
    val meaning: String = "",
    val context: String = "",
    val url: String = "",
    val createdAt: Long = 0,
    val dueAt: Long = 0,
    val level: Int = 0,
    val reviewedAt: Long = 0
)

@Serializable
data class Translation(
    val text: String = "",
    val provider: String = "",
    val at: Long = 0,
    val cached: Boolean = false,
    val web: Boolean = false
)

@Serializable
data class Status(
    val ok: Boolean = false,
    val count: Int = 0,
    val error: String = "",
    val at: Long = 0
)

@Serializable
data class NewsroomGroup(
    val sourceId: String = "",
    val basis: String? = null,
    val items: List<Article> = emptyList(),
    val checkedAt: Long = 0,
    val fetchedAt: Long? = null,
    val stale: Boolean = false,
    val error: String? = null
)

@Serializable
data class Newsroom(
    val updatedAt: Long = 0,
    val groups: List<NewsroomGroup> = emptyList(),
    val sourceCount: Int = 0,
    val coveredCount: Int = 0,
    val storyCount: Int = 0
)

@Serializable
data class Store(
    val version: Int = 1,
    val settings: Settings = Settings(),
    val articles: List<Article> = emptyList(),
    val statuses: Map<String, Status> = emptyMap(),
    val vocabulary: List<Vocab> = emptyList(),
    val saved: List<SavedItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val translations: Map<String, Translation> = emptyMap(),
    @SerialName("readingSeconds") val readingSeconds: Long = 0,
    val lastRefresh: Long? = null,
    val newsroom: Newsroom? = null,
    val newsroomCleared: Boolean = false,
    val articlesCleared: Boolean = false
)

@Serializable
data class SnapshotSeed(
    val fetchedAt: Long = 0,
    val articles: List<Article> = emptyList(),
    val statuses: Map<String, Status> = emptyMap()
)

@Serializable
data class ExtractedParagraph(
    val text: String = "",
    val tag: String = "",
    val continued: Boolean = false
)

@Serializable
data class ExtractionInfo(
    val method: String = "",
    val removed: Int = 0,
    val paragraphs: Int = 0,
    val characters: Int = 0,
    val candidateCharacters: Int = 0,
    val warnings: List<String> = emptyList(),
    val scope: String = ""
)

@Serializable
data class ExtractedArticle(
    val title: String = "",
    val url: String = "",
    val paragraphs: List<ExtractedParagraph> = emptyList(),
    val truncated: Boolean = false,
    val extraction: ExtractionInfo? = null
)

@Serializable
data class ExtractionEnvelope(
    val ok: Boolean = false,
    val data: ExtractedArticle? = null,
    val error: String? = null
)

@Serializable
data class BilingualParagraph(
    val text: String = "",
    val tag: String = "",
    val translation: String? = null,
    val error: String? = null
)

data class BilingualArticle(
    val title: String = "",
    val url: String = "",
    val paragraphs: List<BilingualParagraph> = emptyList(),
    val warnings: List<String> = emptyList(),
    val provider: String = ""
)

data class RefreshProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val source: String = ""
)

data class SelectionInfo(
    val text: String = "",
    val context: String = "",
    val url: String = ""
)

@Serializable
data class NewsroomRule(
    val excludeSelector: String = "",
    val headingSelector: String = "h1,h2,h3,h4",
    val titleMin: Int = 20,
    val titleMax: Int = 260,
    val denyPathPattern: String = "",
    val denyTitlePattern: String = "",
    val denyTitles: List<String> = emptyList(),
    val institutionPathPattern: String = "",
    val storyPathPattern: String = "",
    val maxItems: Int = 3
)

@Serializable
data class NewsroomSpec(
    val default: NewsroomRule = NewsroomRule(),
    val institutionRegion: String = "国际机构",
    val institutionSources: List<String> = emptyList(),
    val overrides: Map<String, NewsroomRule> = emptyMap()
)

@Serializable
data class AdRuleSet(
    val selectors: List<String> = emptyList(),
    val textPatterns: List<String> = emptyList(),
    val blockHosts: List<String> = emptyList()
)

@Serializable
data class AdRules(
    val global: AdRuleSet = AdRuleSet(),
    val domains: Map<String, AdRuleSet> = emptyMap()
)

@Serializable
data class Plugin(
    val format: Int = 1,
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val type: String = "",
    val description: String = "",
    val author: String = "",
    val license: String = "",
    val sources: List<Source> = emptyList(),
    val newsroom: NewsroomSpec? = null,
    val rules: AdRules? = null
)

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val type: String,
    val description: String,
    val author: String,
    val license: String,
    val enabled: Boolean,
    val sourceCount: Int,
    val ruleCount: Int
)
