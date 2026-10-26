package studio.lumen.reader.data

import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Ranker {
    private val stopWords = setOf(
        "this", "that", "with", "from", "have", "will", "what", "when", "where", "which",
        "their", "about", "into", "over", "after", "more", "could", "would", "should",
        "says", "said", "they", "them", "than", "your", "been", "being", "also", "amid",
        "here", "through", "first", "these", "those", "world", "news", "live"
    )
    private val wordPattern = Regex("[a-z]{4,}")
    private val frontierPattern = Regex("(?i)\\b(research|discover\\w*|breakthrough|artificial intelligence|AI|quantum|robot\\w*|climate|energy|trial|genom\\w*|semiconductor\\w*|space|innovation|technology|science)\\b")
    private val learningPattern = Regex("(?i)\\b(why|how|analysis|explained|essay|research|opinion)\\b")
    private val tagPattern = Regex("<[^>]*>")
    private val formulaPattern = Regex("^[=+\\-@\\t\\r]")

    fun safeUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return try {
            val uri = URI(value.trim())
            if ((uri.scheme != "http" && uri.scheme != "https") || uri.host.isNullOrBlank() || uri.userInfo != null) null
            else uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun clean(value: String?): String = org.jsoup.Jsoup.parse(value ?: "").text().trim()

    fun normalize(
        title: String?,
        link: String?,
        description: String?,
        publishedAt: Long?,
        source: Source,
        fetchedAt: Long = System.currentTimeMillis()
    ): Article? {
        val url = safeUrl(link) ?: return null
        val cleanTitle = clean(title)
        if (cleanTitle.isBlank()) return null
        val cleanDescription = clean(description).take(360)
        val id = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(20)
        return Article(
            id = id,
            title = cleanTitle,
            url = url,
            sourceId = source.id,
            source = source.name,
            category = source.category,
            region = source.region,
            reach = source.reach,
            description = cleanDescription,
            publishedAt = publishedAt,
            fetchedAt = fetchedAt
        )
    }

    fun qualitySignals(article: Article): Quality {
        val text = (article.title + " " + article.description).lowercase(Locale.ROOT)
        var score = 45
        val reasons = mutableListOf<String>()
        if (Regex("\\b(why|how|analysis|explained|investigation|essay|in.depth|what .+ means)\\b").containsMatchIn(text)) {
            score += 18; reasons += "含解释、分析或调查信号"
        }
        if (Regex("\\b(study|research|evidence|data|survey|trial|report|peer.review|scientists)\\b").containsMatchIn(text)) {
            score += 15; reasons += "摘要出现研究或数据证据信号"
        }
        if (Regex("\\b(climate|health|economy|education|energy|housing|inequality|democracy|policy|trade|artificial intelligence|public|society|war)\\b").containsMatchIn(text)) {
            score += 12; reasons += "具有公共议题关联"
        }
        if (Regex("\\b(may|might|uncertain|limits|limitations|suggest|risk|however|although)\\b").containsMatchIn(text)) {
            score += 7; reasons += "摘要包含不确定性或边界表述"
        }
        if (article.description.length >= 160) {
            score += 6; reasons += "摘要信息较充足"
        }
        if (Regex("\\b(live updates?|liveblog|watch live|breaking|minute.by.minute)\\b").containsMatchIn(article.title.lowercase(Locale.ROOT))) {
            score -= 22; reasons += "滚动快讯精读降权"
        }
        if (Regex("\\b(you won't believe|shocking|must.see|deals|coupon|buy now)\\b").containsMatchIn(text)) {
            score -= 25; reasons += "促销或吸睛措辞降权"
        }
        if (article.sourceId == "arxiv") {
            score -= 12; reasons += "预印本，同行评审状态未核验"
        }
        if (reasons.isEmpty()) reasons += "标题与摘要不足以判断全文深度"
        score = score.coerceIn(0, 100)
        val label = when {
            score >= 80 -> "优先精读"
            score >= 65 -> "值得深入"
            else -> "拓展阅读"
        }
        return Quality(score, reasons, label, "标题与摘要启发式信号，非全文事实核查")
    }

    private fun tokens(text: String): Set<String> =
        wordPattern.findAll(text.lowercase(Locale.ROOT)).map { it.value }.filter { it !in stopWords }.toSet()

    fun rank(articles: List<Article>, weights: Weights, now: Long, interests: List<String>): List<Article> {
        val valid = articles.filter { safeUrl(it.url) != null }
        if (valid.isEmpty()) return emptyList()
        val sets = valid.map { tokens(it.title) }
        val postings = HashMap<String, MutableList<Int>>()
        val overlap = Array(valid.size) { HashMap<Int, Int>() }
        sets.forEachIndexed { i, set ->
            for (word in set) {
                val prior = postings.getOrPut(word) { mutableListOf() }
                for (j in prior) {
                    overlap[i][j] = (overlap[i][j] ?: 0) + 1
                    overlap[j][i] = (overlap[j][i] ?: 0) + 1
                }
                prior += i
            }
        }
        val duplicateNeighbors = Array(valid.size) { mutableListOf<Int>() }
        val peerCounts = IntArray(valid.size)
        for (i in valid.indices) {
            val peers = HashSet<String>()
            for ((j, shared) in overlap[i]) {
                val denominator = max(1, min(sets[i].size, sets[j].size))
                val ratio = shared.toDouble() / denominator
                if (ratio > 0.65) duplicateNeighbors[i] += j
                if (valid[i].sourceId != valid[j].sourceId && shared >= 3 && ratio >= 0.38) peers += valid[j].sourceId
            }
            peerCounts[i] = peers.size
        }
        val interestSet = interests.toSet()
        val sumWeights = max(1, weights.freshness + weights.reach + weights.buzz + weights.frontier + weights.learning)
        val scored = valid.mapIndexed { i, article ->
            val ageHours = article.publishedAt?.let { (now - it).toDouble() / 3_600_000 } ?: Double.NaN
            val freshness = if (ageHours.isNaN() || ageHours < -1) 20 else {
                val window = if (article.category == "学术") 168.0 else 60.0
                (100 * exp(-max(0.0, ageHours) / window)).roundToInt()
            }
            val buzz = min(100, peerCounts[i] * 20)
            val frontierMatches = frontierPattern.findAll(article.title + " " + article.description).map { it.value.lowercase() }.toSet()
            val baseFrontier = if (article.category == "科技" || article.category == "学术") 45 else 15
            val frontier = min(100, baseFrontier + frontierMatches.size * 15)
            var learning = 40
            if (article.description.length > 100) learning += 20
            if (learningPattern.containsMatchIn(article.title)) learning += 30
            if (article.title.split(Regex("\\s+")).size >= 8) learning += 10
            learning = min(100, learning)
            val metrics = Metrics(
                freshness = freshness,
                reach = (if (article.reach == 0) 3 else article.reach) * 20,
                buzz = buzz,
                frontier = frontier,
                learning = learning
            )
            val baseScore = (metrics.freshness * weights.freshness + metrics.reach * weights.reach +
                metrics.buzz * weights.buzz + metrics.frontier * weights.frontier +
                metrics.learning * weights.learning).toDouble() / sumWeights
            val quality = qualitySignals(article)
            val score = (baseScore * 0.8 + quality.score * 0.2).roundToInt()
            Triple(
                i,
                article.copy(
                    score = score,
                    quality = quality,
                    metrics = metrics,
                    peerCount = peerCounts[i],
                    interest = if (article.category in interestSet) 8 else 0
                ),
                score + if (article.category in interestSet) 8 else 0
            )
        }.sortedByDescending { it.third }
        val used = HashMap<String, Int>()
        val cats = HashMap<String, Int>()
        val duplicated = BooleanArray(valid.size)
        val remaining = scored.toMutableList()
        val result = mutableListOf<Article>()
        while (remaining.isNotEmpty()) {
            var best = 0
            var bestScore = Double.NEGATIVE_INFINITY
            remaining.forEachIndexed { index, item ->
                val article = item.second
                var candidate = (article.score + article.interest).toDouble() -
                    (used[article.sourceId] ?: 0) * 13 -
                    (cats[article.category] ?: 0) * 3
                if (duplicated[item.first]) candidate -= 16
                if (candidate > bestScore) {
                    bestScore = candidate
                    best = index
                }
            }
            val chosen = remaining.removeAt(best)
            for (neighbor in duplicateNeighbors[chosen.first]) duplicated[neighbor] = true
            result += chosen.second
            used[chosen.second.sourceId] = (used[chosen.second.sourceId] ?: 0) + 1
            cats[chosen.second.category] = (cats[chosen.second.category] ?: 0) + 1
        }
        return result
    }

    fun merge(oldItems: List<Article>, newItems: List<Article>): List<Article> {
        val map = LinkedHashMap<String, Article>()
        for (article in oldItems + newItems) {
            if (safeUrl(article.url) == null) continue
            map[article.id] = article
        }
        return map.values.sortedByDescending { it.published() }.take(2500)
    }

    fun reviewCard(card: Vocab, known: Boolean, now: Long): Vocab {
        val level = if (known) min(card.level + 1, 6) else 0
        val due = if (known) now + longArrayOf(1, 1, 3, 7, 14, 30, 60)[level] * 86_400_000L else now + 600_000L
        return card.copy(level = level, dueAt = due, reviewedAt = now)
    }

    fun csvCell(value: Any?): String {
        var text = value?.toString() ?: ""
        if (formulaPattern.containsMatchIn(text)) text = "'$text"
        return "\"" + text.replace("\"", "\"\"") + "\""
    }
}
