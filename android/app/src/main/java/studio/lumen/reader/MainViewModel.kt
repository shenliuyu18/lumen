package studio.lumen.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import studio.lumen.reader.data.Article
import studio.lumen.reader.data.BilingualArticle
import studio.lumen.reader.data.BilingualParagraph
import studio.lumen.reader.data.Catalog
import studio.lumen.reader.data.ExtractedArticle
import studio.lumen.reader.data.LumenRepository
import studio.lumen.reader.data.Ranker
import studio.lumen.reader.data.SecretStore
import studio.lumen.reader.data.SelectionInfo
import studio.lumen.reader.data.Settings
import studio.lumen.reader.data.Source
import studio.lumen.reader.data.Store
import studio.lumen.reader.data.Translation
import studio.lumen.reader.data.Translator

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LumenRepository(application)
    private val secrets = SecretStore(application)
    private val translator = Translator(secrets)
    private val catalog = Catalog(application)

    val sources: List<Source> get() = repository.sources
    val store: StateFlow<Store> = repository.storeFlow
    val plugins = repository.plugins
    val refreshProgress = repository.refreshProgress

    private val _ranked = MutableStateFlow<List<Article>>(emptyList())
    val ranked: StateFlow<List<Article>> = _ranked.asStateFlow()

    private val _selected = MutableStateFlow<SelectionInfo?>(null)
    val selected: StateFlow<SelectionInfo?> = _selected.asStateFlow()

    private val _currentArticle = MutableStateFlow<Article?>(null)
    val currentArticle: StateFlow<Article?> = _currentArticle.asStateFlow()

    private val _bilingual = MutableStateFlow<BilingualArticle?>(null)
    val bilingual: StateFlow<BilingualArticle?> = _bilingual.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()
    private val _autoBilingualUrl = MutableStateFlow<String?>(null)

    private var readingStartedAt = 0L

    init {
        viewModelScope.launch {
            val initial = store.value
            if (initial.settings.autoRefresh) {
                val last = initial.lastRefresh ?: 0L
                if (System.currentTimeMillis() - last > 30 * 60 * 1000L) refresh()
            }
        }
        viewModelScope.launch {
            store.collectLatest { current ->
                val ranked = withContext(Dispatchers.Default) {
                    Ranker.rank(
                        current.articles,
                        current.settings.weights,
                        System.currentTimeMillis(),
                        current.settings.interests
                    )
                }
                _ranked.value = ranked
            }
        }
    }

    fun refresh() {
        if (refreshProgress.value.running) return
        viewModelScope.launch {
            runCatching { repository.refresh() }
                .onFailure { _messages.emit("更新失败：${it.message ?: "请检查网络"}") }
        }
    }

    fun updateSettings(settings: Settings) {
        repository.updateSettings(settings)
    }

    fun toggleSource(source: Source, enabled: Boolean) {
        val disabled = store.value.settings.disabledSources.toMutableSet()
        if (enabled) disabled.remove(source.id) else disabled.add(source.id)
        repository.updateSettings(store.value.settings.copy(disabledSources = disabled.toList()))
    }

    fun openArticle(article: Article) {
        finishReading()
        _selected.value = null
        _currentArticle.value = article
        _autoBilingualUrl.value = article.url.takeUnless { article.id.startsWith("source-") }
        repository.addHistory(article)
        readingStartedAt = System.currentTimeMillis()
    }

    fun consumeAutoBilingual(): Boolean {
        if (_autoBilingualUrl.value != null) {
            _autoBilingualUrl.value = null
            return true
        }
        return false
    }

    fun togglePlugin(id: String, enabled: Boolean) {
        repository.togglePlugin(id, enabled)
    }

    fun installPlugin(uri: android.net.Uri): Result<studio.lumen.reader.data.PluginInfo> = runCatching {
        repository.installPlugin(uri)
    }

    fun removePlugin(id: String) {
        repository.removePlugin(id)
    }

    fun adblockScript(): String = repository.adblockScript()

    fun blockedHosts(): Set<String> = repository.blockHosts()

    fun pluginDirectory(): java.io.File = repository.pluginDirectory()

    fun finishReading() {
        if (readingStartedAt > 0 && store.value.settings.recordHistory) {
            val seconds = ((System.currentTimeMillis() - readingStartedAt) / 1000).coerceAtMost(1800)
            if (seconds > 0) repository.addReadingSeconds(seconds)
        }
        readingStartedAt = 0
    }

    fun setSelection(selection: SelectionInfo?) {
        _selected.value = selection
    }

    fun toggleSaved(article: Article) {
        repository.toggleSaved(article)
        val saved = repository.store.saved.any { it.article.url == article.url }
        viewModelScope.launch { _messages.emit(if (saved) "已加入稍后阅读" else "已取消收藏") }
    }

    fun addVocab(text: String, meaning: String, context: String, url: String) {
        repository.addVocab(text, meaning, context, url)
        viewModelScope.launch { _messages.emit("已加入词句本") }
    }

    fun deleteVocab(id: String) = repository.deleteVocab(id)
    fun reviewVocab(id: String, known: Boolean) = repository.reviewVocab(id, known)
    fun deleteHistory(url: String) = repository.deleteHistory(url)

    fun clearHistory() {
        repository.clearHistory()
        viewModelScope.launch { _messages.emit("阅读足迹和时长已清空") }
    }

    /** 打开阅读模式：只抽取正文，译文由用户点「双语」后再开始。 */
    fun startReading(extracted: ExtractedArticle) {
        val article = BilingualArticle(
            title = extracted.title,
            url = extracted.url,
            paragraphs = extracted.paragraphs.map { BilingualParagraph(it.text, it.tag) },
            warnings = extracted.extraction?.warnings ?: emptyList(),
            provider = store.value.settings.translation.provider
        )
        _bilingual.value = article
    }

    fun translateAll() {
        val article = _bilingual.value ?: return
        autoTranslate(article)
    }

    fun clearTranslations() {
        val article = _bilingual.value ?: return
        _bilingual.value = article.copy(
            paragraphs = article.paragraphs.map { it.copy(translation = null, error = null) }
        )
    }

    fun clearBilingual() {
        _bilingual.value = null
    }

    private fun autoTranslate(article: BilingualArticle) {
        val settings = store.value.settings.translation
        viewModelScope.launch {
            val semaphore = Semaphore(2)
            supervisorScope {
                article.paragraphs.forEachIndexed { index, paragraph ->
                    launch {
                        semaphore.withPermit {
                            val current = _bilingual.value ?: return@withPermit
                            if (current.paragraphs.getOrNull(index)?.translation != null) return@withPermit
                            runCatching {
                                val context = article.paragraphs
                                    .subList(maxOf(0, index - 1), minOf(article.paragraphs.size, index + 2))
                                    .joinToString("\n") { it.text }
                                translateText(paragraph.text, context, settings)
                            }.onSuccess { translation ->
                                updateParagraph(index, translation = translation.text)
                            }.onFailure { error ->
                                updateParagraph(index, error = error.message ?: "翻译失败")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateParagraph(index: Int, translation: String? = null, error: String? = null) {
        val current = _bilingual.value ?: return
        val paragraphs = current.paragraphs.toMutableList()
        val old = paragraphs.getOrNull(index) ?: return
        paragraphs[index] = old.copy(translation = translation ?: old.translation, error = error ?: old.error)
        _bilingual.value = current.copy(paragraphs = paragraphs)
    }

    fun retryParagraph(index: Int) {
        val current = _bilingual.value ?: return
        val paragraph = current.paragraphs.getOrNull(index) ?: return
        val paragraphs = current.paragraphs.toMutableList()
        paragraphs[index] = paragraph.copy(translation = null, error = null)
        _bilingual.value = current.copy(paragraphs = paragraphs)
        val settings = store.value.settings.translation
        viewModelScope.launch {
            val context = current.paragraphs
                .subList(maxOf(0, index - 1), minOf(current.paragraphs.size, index + 2))
                .joinToString("\n") { it.text }
            runCatching { translateText(paragraph.text, context, settings) }
                .onSuccess { updateParagraph(index, translation = it.text) }
                .onFailure { updateParagraph(index, error = it.message ?: "翻译失败") }
        }
    }

    suspend fun translateText(
        text: String,
        context: String,
        settings: studio.lumen.reader.data.TranslationSettings = store.value.settings.translation
    ): Translation {
        val key = translator.cacheKey(settings.provider, text, if (settings.useContext) context else "")
        repository.findTranslation(key)?.let { return it.copy(cached = true) }
        val result = translator.translate(text, if (settings.useContext) context else "", settings)
        repository.storeTranslation(key, result)
        return result
    }

    suspend fun dictionary(word: String): List<String> = translator.dictionary(word)

    fun catalogScripts(): Pair<String, String> = catalog.readabilityScript() to catalog.extractionScript()

    fun backupJson(): String = repository.backupJson()
    fun vocabularyCsv(): String = repository.vocabularyCsv()

    fun importBackup(text: String): Result<Int> = runCatching {
        val imported = repository.importBackup(text)
        imported.vocabulary.size + imported.saved.size + imported.history.size
    }

    fun secrets(): SecretStore = secrets
}
